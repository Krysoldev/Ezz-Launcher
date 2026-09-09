package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.minecraft.mod.ModBytecodeValidator
import io.ezz.launcher.core.minecraft.mod.ModCompatibilityResult
import io.ezz.launcher.core.model.instance.InstallationPlan
import io.ezz.launcher.core.model.instance.PlanActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Transaction coordinator for atomic Minecraft mod installations.
 *
 * Enforces:
 * 1. Concurrency isolation: Mutex per instance ID prevents race conditions.
 * 2. Stale Plan Protection: Compares mods directory with initial plan state.
 * 3. Staged Downloads: Downloads to a temporary staging folder; never writes directly to mods/ during download.
 * 4. Comprehensive Pre-Commit Verification: File existence, zip integrity, and bytecode validation.
 * 5. Atomic Commit: Moves files into destination.
 * 6. Hard Safety Invariant: Detects unexpected mod deletions (UNEXPECTED_MOD_REMOVAL_BLOCKED) and triggers instant rollback.
 * 7. Clean Rollback: Restores original state on any failure or cancellation.
 */
object ModInstallationTransaction {

    private val instanceLocks = ConcurrentHashMap<String, Mutex>()

    private fun getLock(instanceId: String): Mutex {
        return instanceLocks.getOrPut(instanceId) { Mutex() }
    }

    suspend fun execute(
        plan: InstallationPlan,
        javaMajorVersion: Int? = null,
        downloader: suspend (url: String, targetFile: File, onProgress: (downloaded: Long, total: Long) -> Unit) -> Boolean,
        onProgress: (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val env = plan.environment
        val instanceId = env.instanceId
        val modsDir = env.modsDirectory
        val lock = getLock(instanceId)

        println("[TRANSACTION] ======================================================")
        println("[TRANSACTION] EVENT: INSTALL_REQUEST for instance $instanceId (target: ${plan.targetModName} v${plan.selectedVersionNumber})")

        // 1. Concurrency Lock
        lock.withLock {
            val opId = UUID.randomUUID().toString()
            val stagingDir = File(env.minecraftDirectory, ".install_staging_$opId")
            val backupDir = File(env.minecraftDirectory, ".install_backup_$opId")
            val newlyCreatedFiles = mutableListOf<File>()
            val backedUpFiles = mutableListOf<Pair<File, File>>() // original -> backup

            try {
                if (!modsDir.exists()) {
                    modsDir.mkdirs()
                }

                // 2. Stale Plan Verification
                onProgress("CHECKING INSTANCE", 0.04f)
                val currentFilesOnDisk = modsDir.listFiles { f ->
                    f.isFile && (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".jar.disabled", ignoreCase = true))
                }?.map { it.name }?.toSet() ?: emptySet()

                onProgress("READING MOD ENVIRONMENT", 0.08f)

                if (currentFilesOnDisk != plan.initialModFileNames) {
                    val added = currentFilesOnDisk - plan.initialModFileNames
                    val removed = plan.initialModFileNames - currentFilesOnDisk
                    val msg = "Instance state changed while resolving (added: $added, removed: $removed). Aborting stale plan."
                    println("[TRANSACTION] STALE_PLAN_REJECTED: $msg")
                    throw IllegalStateException(msg)
                }

                println("[TRANSACTION] EVENT: ENVIRONMENT_READY for instance $instanceId")
                stagingDir.mkdirs()

                // 3. Download all planned files to temporary staging
                onProgress("PREPARING INSTALLATION", 0.10f)
                val itemsToInstall = plan.itemsToInstall
                val totalDownloads = itemsToInstall.size
                println("[TRANSACTION] EVENT: DOWNLOAD_STARTED (${totalDownloads} files to download)")

                for ((index, item) in itemsToInstall.withIndex()) {
                    val url = item.downloadUrl
                    if (url.isNullOrBlank()) {
                        throw IllegalStateException("No download URL provided for ${item.modName} (${item.fileName})")
                    }

                    val stagedFile = File(stagingDir, item.fileName)
                    val baseProgress = 0.12f + (index.toFloat() / totalDownloads.toFloat()) * 0.58f
                    onProgress("DOWNLOADING: ${item.modName}...", baseProgress)

                    val ok = downloader(url, stagedFile) { downloaded, total ->
                        if (total > 0) {
                            val fraction = downloaded.toFloat() / total.toFloat()
                            val overall = 0.12f + ((index.toFloat() + fraction) / totalDownloads.toFloat()) * 0.58f
                            onProgress("DOWNLOADING: ${item.modName} (${(fraction * 100).toInt()}%)", overall)
                        }
                    }

                    if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                        throw IllegalStateException("Failed to download file '${item.fileName}' for ${item.modName}")
                    }
                }
                println("[TRANSACTION] EVENT: DOWNLOAD_COMPLETE")

                // 4. Verify Downloaded Files
                println("[TRANSACTION] EVENT: VERIFICATION_STARTED")
                onProgress("VERIFYING", 0.75f)

                for (item in itemsToInstall) {
                    val stagedFile = File(stagingDir, item.fileName)
                    if (!stagedFile.exists() || stagedFile.length() == 0L) {
                        throw IllegalStateException("Verification failed: Staged file does not exist or is empty: ${item.fileName}")
                    }

                    // A. Zip archive integrity check
                    try {
                        ZipFile(stagedFile).use { zip ->
                            val hasFabric = zip.getEntry("fabric.mod.json") != null
                            val hasForge = zip.getEntry("META-INF/mods.toml") != null || zip.getEntry("mcmod.info") != null
                            val hasQuilt = zip.getEntry("quilt.mod.json") != null
                            val hasNeoForge = zip.getEntry("META-INF/neoforge.mods.toml") != null
                            if (!hasFabric && !hasForge && !hasQuilt && !hasNeoForge) {
                                println("[TRANSACTION] Warning: Archive ${item.fileName} has no recognized mod metadata entry.")
                            }
                        }
                    } catch (e: Throwable) {
                        throw IllegalStateException("Corrupted archive for '${item.fileName}': ${e.message}")
                    }

                    // B. Bytecode validation
                    if (javaMajorVersion != null && item.fileName.endsWith(".jar", ignoreCase = true)) {
                        val compat = ModBytecodeValidator.validateJarFile(stagedFile, javaMajorVersion)
                        if (compat is ModCompatibilityResult.Incompatible) {
                            throw IllegalStateException("Bytecode incompatibility in '${item.fileName}': ${compat.errorMessage}")
                        }
                    }
                }
                println("[TRANSACTION] EVENT: VERIFICATION_COMPLETE")

                // 5. Snapshot / Backup any planned replacements/removals
                val itemsToRemove = plan.itemsToRemove
                val itemsToReplace = plan.itemsToReplace
                if (itemsToRemove.isNotEmpty() || itemsToReplace.isNotEmpty()) {
                    backupDir.mkdirs()
                    for (item in itemsToRemove + itemsToReplace) {
                        val existingFile = File(modsDir, item.fileName)
                        if (existingFile.exists()) {
                            val backupFile = File(backupDir, item.fileName)
                            if (existingFile.renameTo(backupFile)) {
                                backedUpFiles.add(existingFile to backupFile)
                            }
                        }
                    }
                }

                // 6. Atomic Commit: Move staged files into mods/
                println("[TRANSACTION] EVENT: COMMIT_STARTED")
                onProgress("INSTALLING", 0.85f)

                val preCommitFiles = modsDir.listFiles { f ->
                    f.isFile && (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".jar.disabled", ignoreCase = true))
                }?.map { it.name }?.toSet() ?: emptySet()

                for (item in itemsToInstall) {
                    val stagedFile = File(stagingDir, item.fileName)
                    val finalTarget = File(modsDir, item.fileName)

                    if (finalTarget.exists()) {
                        finalTarget.delete()
                    }

                    if (!stagedFile.renameTo(finalTarget)) {
                        stagedFile.copyTo(finalTarget, overwrite = true)
                        stagedFile.delete()
                    }

                    if (!finalTarget.exists() || finalTarget.length() == 0L) {
                        throw IllegalStateException("Failed to commit '${item.fileName}' into mods directory")
                    }
                    newlyCreatedFiles.add(finalTarget)
                }
                println("[TRANSACTION] EVENT: COMMIT_COMPLETE")

                // 7. Post-Commit Invariant Check: Verify NO unexpected mod was removed
                onProgress("FINAL CHECK", 0.95f)
                val postCommitFiles = modsDir.listFiles { f ->
                    f.isFile && (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".jar.disabled", ignoreCase = true))
                }?.map { it.name }?.toSet() ?: emptySet()

                val approvedPlannedRemovals = (itemsToRemove + itemsToReplace).map { it.fileName }.toSet()
                val unexpectedRemovals = (preCommitFiles - postCommitFiles) - approvedPlannedRemovals

                if (unexpectedRemovals.isNotEmpty()) {
                    println("[TRANSACTION] UNEXPECTED_MOD_REMOVAL_BLOCKED: Unexpected mod removal detected: $unexpectedRemovals")
                    throw IllegalStateException("UNEXPECTED_MOD_REMOVAL_BLOCKED: Mods disappeared unexpectedly: $unexpectedRemovals. Triggering automatic rollback.")
                }

                // 8. Verify all planned files exist
                for (item in itemsToInstall) {
                    val file = File(modsDir, item.fileName)
                    if (!file.exists() || file.length() == 0L) {
                        throw IllegalStateException("Final verification failed: Expected file missing: ${item.fileName}")
                    }
                }

                // Clean up temporary directories
                stagingDir.deleteRecursively()
                backupDir.deleteRecursively()

                println("[TRANSACTION] EVENT: FINAL_STATE_VERIFIED (mods count: ${postCommitFiles.size})")
                println("[TRANSACTION] EVENT: INSTALL_SUCCESS")
                println("[TRANSACTION] ======================================================")

                onProgress("INSTALLED", 1.0f)
                Result.success(Unit)
            } catch (e: Throwable) {
                // ROLLBACK
                println("[TRANSACTION] Transaction failed, initiating full rollback: ${e.message}")
                try {
                    // Remove newly added files
                    for (file in newlyCreatedFiles) {
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    // Restore backed up files
                    for ((original, backup) in backedUpFiles) {
                        if (backup.exists()) {
                            backup.renameTo(original)
                        }
                    }
                    stagingDir.deleteRecursively()
                    backupDir.deleteRecursively()
                } catch (rollbackEx: Throwable) {
                    println("[TRANSACTION] Error during rollback: ${rollbackEx.message}")
                }
                println("[TRANSACTION] Rollback completed. Original state restored.")
                println("[TRANSACTION] ======================================================")
                Result.failure(e)
            }
        }
    }
}
