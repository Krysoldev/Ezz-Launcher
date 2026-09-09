package io.ezz.launcher.ui.instance.installation.service

import io.ezz.launcher.core.minecraft.mods.CurseForgeDependencyResolver
import io.ezz.launcher.core.model.curseforge.CurseForgeDependencyRelationType
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.curseforge.CurseForgeModLoaderType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.network.curseforge.CurseForgeService
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import io.ezz.launcher.ui.instance.installation.model.ContentType
import io.ezz.launcher.ui.instance.installation.model.DependencyInstallStatus
import io.ezz.launcher.ui.instance.installation.model.InstallationDependencyItem
import io.ezz.launcher.ui.instance.installation.model.InstallationQueueState
import io.ezz.launcher.ui.instance.installation.model.InstallationStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class ContentInstallationManager(
    private val curseForgeService: CurseForgeService,
    private val getInstanceDir: (String) -> File,
    private val getInstalledMods: (String) -> List<LocalMod>,
    private val onContentChanged: (String) -> Unit,
    private val scope: CoroutineScope
) {
    private val _queueState = MutableStateFlow(InstallationQueueState())
    val queueState: StateFlow<InstallationQueueState> = _queueState.asStateFlow()

    private val _focusedItem = MutableStateFlow<ContentInstallationItem?>(null)
    val focusedItem: StateFlow<ContentInstallationItem?> = _focusedItem.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    fun setFocusedItem(item: ContentInstallationItem?) {
        _focusedItem.value = item
    }

    fun dismissFocusedItem() {
        _focusedItem.value = null
    }

    fun openDrawer() {
        _queueState.update { it.copy(isDrawerOpen = true) }
    }

    fun closeDrawer() {
        _queueState.update { it.copy(isDrawerOpen = false) }
    }

    fun toggleDrawer() {
        _queueState.update { it.copy(isDrawerOpen = !it.isDrawerOpen) }
    }

    fun clearCompleted() {
        _queueState.update { it.copy(completedHistory = emptyList()) }
    }

    fun cancelInstallation(id: String) {
        val job = activeJobs.remove(id)
        job?.cancel()
        _queueState.update { state ->
            val updatedActive = state.activeItems.filterNot { it.id == id }
            val cancelledItem = state.activeItems.find { it.id == id }?.copy(
                stage = InstallationStage.CANCELLED,
                errorSummary = "Installation cancelled by user."
            )
            state.copy(
                activeItems = updatedActive,
                completedHistory = if (cancelledItem != null) listOf(cancelledItem) + state.completedHistory else state.completedHistory
            )
        }
        if (_focusedItem.value?.id == id) {
            _focusedItem.update { it?.copy(stage = InstallationStage.CANCELLED) }
        }
    }

    fun installCurseForgeMod(
        instance: Instance,
        mod: CurseForgeMod,
        chosenFile: CurseForgeFile? = null
    ): String = startCurseForgeModInstall(instance, mod, chosenFile)

    fun startCurseForgeModInstall(
        instance: Instance,
        mod: CurseForgeMod,
        chosenFile: CurseForgeFile? = null
    ): String {
        val opId = UUID.randomUUID().toString()
        val initialItem = ContentInstallationItem(
            id = opId,
            instanceId = instance.id,
            instanceName = instance.name,
            contentType = ContentType.MOD,
            name = mod.name,
            iconUrl = mod.logo?.thumbnailUrl ?: mod.logo?.url,
            author = mod.authors.firstOrNull()?.name,
            versionName = chosenFile?.displayName?.ifBlank { chosenFile.fileName } ?: "",
            fileName = chosenFile?.fileName ?: "",
            fileSize = chosenFile?.fileLength ?: 0L,
            stage = InstallationStage.SELECTED,
            progress = 0.05f,
            mod = mod,
            chosenFile = chosenFile
        )

        _queueState.update { state ->
            state.copy(activeItems = listOf(initialItem) + state.activeItems)
        }
        _focusedItem.value = initialItem

        val job = scope.launch(Dispatchers.IO) {
            executeModInstallPipeline(opId, instance, mod, chosenFile)
        }
        activeJobs[opId] = job
        return opId
    }

    private suspend fun executeModInstallPipeline(
        opId: String,
        instance: Instance,
        mod: CurseForgeMod,
        explicitFile: CurseForgeFile?
    ) {
        val createdFiles = mutableListOf<File>()
        val modsDir = File(getInstanceDir(instance.id), ".minecraft/mods")
        if (!modsDir.exists()) modsDir.mkdirs()

        try {
            // STAGE 2: VALIDATING COMPATIBILITY
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.VALIDATING,
                    progress = 0.10f
                )
            }

            val targetMc = instance.minecraftVersion.trim()
            val loaderName = instance.loaderType.name
            val loaderType = CurseForgeModLoaderType.fromLoaderName(loaderName)
            val installedMods = getInstalledMods(instance.id)

            // Resolve file to install
            val targetFile: CurseForgeFile
            if (explicitFile != null) {
                targetFile = explicitFile
            } else {
                val candidateFiles = curseForgeService.getModFiles(
                    modId = mod.id,
                    gameVersion = targetMc,
                    modLoaderType = loaderType,
                    pageSize = 30
                )

                val resolution = CurseForgeDependencyResolver.resolveCompatibility(
                    minecraftVersion = targetMc,
                    loader = loaderName,
                    installedMods = installedMods,
                    mod = mod,
                    candidateFiles = candidateFiles
                )

                val resolved = resolution.recommendedFile ?: resolution.latestFile
                if (resolved == null) {
                    throw IllegalStateException(
                        resolution.selectionReason ?: "No compatible version found for Minecraft $targetMc ($loaderName)."
                    )
                }
                targetFile = resolved
            }

            updateItem(opId) {
                it.copy(
                    chosenFile = targetFile,
                    fileName = targetFile.fileName,
                    versionName = targetFile.displayName.ifBlank { targetFile.fileName },
                    fileSize = targetFile.fileLength
                )
            }

            // STAGE 3: RESOLVING DEPENDENCIES
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.RESOLVING_DEPENDENCIES,
                    progress = 0.20f
                )
            }

            val resolvedDeps = CurseForgeDependencyResolver.resolveDependencies(
                curseForgeService = curseForgeService,
                file = targetFile,
                targetMc = targetMc,
                targetLoader = loaderName,
                installedMods = installedMods
            )

            val depItems = resolvedDeps.map { dep ->
                val isReq = dep.relationType == CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY ||
                        dep.relationType == CurseForgeDependencyRelationType.INCLUDE
                InstallationDependencyItem(
                    modId = dep.depModId,
                    name = dep.mod?.name ?: "Mod #${dep.depModId}",
                    relationType = dep.relationType.displayName,
                    isRequired = isReq,
                    status = if (dep.isAlreadyInstalled) DependencyInstallStatus.VERIFIED_INSTALLED else DependencyInstallStatus.QUEUED,
                    versionText = dep.installedVersion ?: dep.candidateFile?.displayName ?: dep.candidateFile?.fileName,
                    candidateFile = dep.candidateFile,
                    errorText = dep.failureReason
                )
            }

            // Check if any required dependency failed resolution
            val brokenRequired = depItems.firstOrNull { it.isRequired && it.status != DependencyInstallStatus.VERIFIED_INSTALLED && it.candidateFile == null }
            if (brokenRequired != null) {
                throw IllegalStateException(
                    "Missing required dependency: '${brokenRequired.name}'. ${brokenRequired.errorText ?: "No compatible version available for Minecraft $targetMc."}"
                )
            }

            updateItem(opId) {
                it.copy(
                    dependencies = depItems,
                    progress = 0.30f
                )
            }

            // STAGE 4: DOWNLOADING MAIN CONTENT
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.DOWNLOADING_MAIN,
                    progress = 0.35f
                )
            }

            val downloadUrl = targetFile.downloadUrl ?: curseForgeService.getModFileDownloadUrl(mod.id, targetFile.id)
            if (downloadUrl.isNullOrBlank()) {
                throw IllegalStateException("Direct download URL is blocked or unavailable for ${targetFile.fileName}.")
            }

            val tempMainFile = File(modsDir, "${targetFile.fileName}.tmp")
            val finalMainFile = File(modsDir, targetFile.fileName)
            createdFiles.add(tempMainFile)

            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            val downloadOk = curseForgeService.downloadContent(downloadUrl, tempMainFile) { downloaded, total ->
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTime).coerceAtLeast(1)
                val bytesSince = downloaded - lastBytes
                val speed = (bytesSince * 1000.0) / elapsed
                val speedText = if (speed > 0) "${ContentInstallationItem.formatBytes(speed.toLong())}/s" else ""

                if (elapsed > 400) {
                    lastTime = now
                    lastBytes = downloaded
                }

                val fraction = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
                val stageProgress = 0.35f + fraction * 0.35f // 35% to 70%

                updateItem(opId) {
                    it.copy(
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        progress = stageProgress,
                        speedText = speedText
                    )
                }
            }

            if (!downloadOk || !tempMainFile.exists() || tempMainFile.length() <= 0) {
                throw IllegalStateException("Failed to download main file: ${targetFile.fileName}")
            }

            // STAGE 5: DOWNLOADING REQUIRED DEPENDENCIES
            val depsToDownload = depItems.filter { it.isRequired && it.status != DependencyInstallStatus.VERIFIED_INSTALLED && it.candidateFile != null }
            if (depsToDownload.isNotEmpty()) {
                updateItem(opId) {
                    it.copy(
                        stage = InstallationStage.DOWNLOADING_DEPENDENCIES,
                        progress = 0.70f
                    )
                }

                depsToDownload.forEachIndexed { index, depItem ->
                    val depFile = depItem.candidateFile!!
                    val depUrl = depFile.downloadUrl ?: curseForgeService.getModFileDownloadUrl(depItem.modId, depFile.id)
                    if (!depUrl.isNullOrBlank()) {
                        val tempDepFile = File(modsDir, "${depFile.fileName}.tmp")
                        createdFiles.add(tempDepFile)

                        updateItem(opId) { current ->
                            val updatedDeps = current.dependencies.map {
                                if (it.modId == depItem.modId) it.copy(status = DependencyInstallStatus.DOWNLOADING) else it
                            }
                            current.copy(dependencies = updatedDeps)
                        }

                        val ok = curseForgeService.downloadContent(depUrl, tempDepFile) { downloaded, total ->
                            updateItem(opId) { current ->
                                val depFraction = if (total > 0) downloaded.toFloat() / total else 0.5f
                                val updatedDeps = current.dependencies.map {
                                    if (it.modId == depItem.modId) it.copy(progress = depFraction, downloadedBytes = downloaded, totalBytes = total) else it
                                }
                                val overallProgress = 0.70f + ((index + depFraction) / depsToDownload.size) * 0.15f
                                current.copy(dependencies = updatedDeps, progress = overallProgress)
                            }
                        }

                        if (!ok || !tempDepFile.exists() || tempDepFile.length() <= 0) {
                            throw IllegalStateException("Failed to download required dependency: ${depFile.fileName}")
                        }

                        updateItem(opId) { current ->
                            val updatedDeps = current.dependencies.map {
                                if (it.modId == depItem.modId) it.copy(status = DependencyInstallStatus.COMPLETED, progress = 1f) else it
                            }
                            current.copy(dependencies = updatedDeps)
                        }
                    }
                }
            }

            // STAGE 6: INSTALLING & RENAMING FILES TO FINAL DESTINATIONS
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.INSTALLING,
                    progress = 0.88f
                )
            }

            // Move main temp file
            if (finalMainFile.exists()) finalMainFile.delete()
            if (!tempMainFile.renameTo(finalMainFile)) {
                tempMainFile.copyTo(finalMainFile, overwrite = true)
                tempMainFile.delete()
            }
            createdFiles.remove(tempMainFile)
            createdFiles.add(finalMainFile)

            // Move dependency temp files
            depsToDownload.forEach { depItem ->
                val depFile = depItem.candidateFile!!
                val tempDep = File(modsDir, "${depFile.fileName}.tmp")
                val finalDep = File(modsDir, depFile.fileName)
                if (tempDep.exists()) {
                    if (finalDep.exists()) finalDep.delete()
                    if (!tempDep.renameTo(finalDep)) {
                        tempDep.copyTo(finalDep, overwrite = true)
                        tempDep.delete()
                    }
                    createdFiles.remove(tempDep)
                    createdFiles.add(finalDep)
                }
            }

            // STAGE 7: VERIFYING INTEGRITY
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.VERIFYING,
                    progress = 0.95f
                )
            }

            if (!finalMainFile.exists() || finalMainFile.length() <= 0) {
                throw IllegalStateException("Verification failed: Installed mod file does not exist or is empty.")
            }

            try {
                ZipFile(finalMainFile).use { /* ensure valid zip archive */ }
            } catch (e: Throwable) {
                throw IllegalStateException("Verification failed: The installed jar archive is corrupted or unreadable (${e.message}).")
            }

            // STAGE 8: COMPLETED
            val completedTime = System.currentTimeMillis()
            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.COMPLETED,
                    progress = 1.0f,
                    completedTimeMs = completedTime,
                    targetFileAbsolutePath = finalMainFile.absolutePath,
                    speedText = ""
                )
            }

            withContext(Dispatchers.Main) {
                onContentChanged(instance.id)
            }

            // Move from active to completed history in queue state after a brief delay
            delay(1200)
            _queueState.update { state ->
                val item = state.activeItems.find { it.id == opId }
                if (item != null) {
                    state.copy(
                        activeItems = state.activeItems.filterNot { it.id == opId },
                        completedHistory = listOf(item) + state.completedHistory
                    )
                } else state
            }
        } catch (e: Throwable) {
            println("[ContentInstallationManager] Installation failed for opId=$opId: ${e.message}")
            // SAFE ROLLBACK: Remove any partial or incomplete files to prevent broken state
            for (file in createdFiles) {
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Throwable) {}
            }

            updateItem(opId) {
                it.copy(
                    stage = InstallationStage.FAILED,
                    errorSummary = e.message ?: "Failed to install mod",
                    errorDetails = e.stackTraceToString().take(600),
                    speedText = ""
                )
            }

            // Move to history
            _queueState.update { state ->
                val item = state.activeItems.find { it.id == opId }
                if (item != null) {
                    state.copy(
                        activeItems = state.activeItems.filterNot { it.id == opId },
                        completedHistory = listOf(item) + state.completedHistory
                    )
                } else state
            }
        } finally {
            activeJobs.remove(opId)
        }
    }

    private fun updateItem(opId: String, transform: (ContentInstallationItem) -> ContentInstallationItem) {
        _queueState.update { state ->
            val updatedActive = state.activeItems.map { if (it.id == opId) transform(it) else it }
            state.copy(activeItems = updatedActive)
        }
        if (_focusedItem.value?.id == opId) {
            _focusedItem.update { current -> current?.let(transform) }
        }
    }
}
