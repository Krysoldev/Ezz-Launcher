package io.ezz.launcher.core.storage.mrpack

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.ModrinthIndex
import io.ezz.launcher.core.model.modrinth.MrpackExportOptions
import io.ezz.launcher.core.model.modrinth.MrpackImportProgress
import io.ezz.launcher.core.model.modrinth.MrpackImportStage
import io.ezz.launcher.core.model.modrinth.MrpackPreview
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.InstanceRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Production-grade engine for Modrinth Modpack (.mrpack) operations:
 * - Structured logging at every lifecycle stage ([MRPACK] / [MRPACK][ERROR]).
 * - Strict .mrpack archive & manifest validation with clear error feedback.
 * - Non-blocking metadata preview.
 * - Multi-stage asynchronous, cancellable import pipeline with SHA-1 / SHA-512 checksum validation.
 * - Atomic instance creation via isolated temporary directory with guaranteed cleanup on failure.
 * - Comprehensive path sanitization protecting against Zip Slip and directory traversal attacks.
 * - Multi-location icon extraction and image validation ensuring persistent instance artwork.
 * - Spec-compliant .mrpack packaging and export for seamless round-trip compatibility.
 */
class MrpackManager(
    private val pathProvider: PathProvider,
    private val instanceRepository: InstanceRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun log(message: String) {
        println("[MRPACK] $message")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        System.err.println("[MRPACK][ERROR] $message" + if (throwable != null) ": ${throwable.message}" else "")
    }

    /**
     * Inspects a file and verifies if it is a valid .mrpack archive.
     * Extracts preview metadata without unzipping the full contents.
     */
    suspend fun previewMrpack(file: File): Result<MrpackPreview> = withContext(dispatcher) {
        log("File selected for preview: ${file.absolutePath}")

        if (!file.exists()) {
            val err = "File not found: ${file.absolutePath}"
            logError(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (file.length() == 0L) {
            val err = "Selected file is empty (0 bytes): ${file.name}"
            logError(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        try {
            log("Reading archive: ${file.name}")
            ZipFile(file).use { zip ->
                val indexEntry = zip.getEntry("modrinth.index.json")
                    ?: return@use Result.failure(
                        IllegalArgumentException("This file is not a valid Modrinth modpack (missing required 'modrinth.index.json' manifest).")
                    )

                log("Reading modrinth.index.json")
                val indexJson = zip.getInputStream(indexEntry).bufferedReader().use { it.readText() }
                val index = try {
                    json.decodeFromString<ModrinthIndex>(indexJson)
                } catch (e: Throwable) {
                    logError("Malformed 'modrinth.index.json' in ${file.name}", e)
                    return@use Result.failure(
                        IllegalArgumentException("Malformed 'modrinth.index.json' manifest: ${e.message}")
                    )
                }

                if (index.game.isNotBlank() && !index.game.equals("minecraft", ignoreCase = true)) {
                    val err = "Unsupported game type '${index.game}'. Expected 'minecraft'."
                    logError(err)
                    return@use Result.failure(IllegalArgumentException(err))
                }

                val mcVersion = index.dependencies["minecraft"]
                    ?: return@use Result.failure(
                        IllegalArgumentException("Invalid modpack: Missing 'minecraft' version dependency in manifest.")
                    )

                val loaderResult = parseLoader(index.dependencies)
                if (loaderResult.isFailure) {
                    val err = loaderResult.exceptionOrNull()?.message ?: "Unsupported mod loader"
                    logError(err)
                    return@use Result.failure(IllegalArgumentException(err))
                }

                val (loaderType, loaderVersion) = loaderResult.getOrNull()!!

                // Comprehensive icon discovery & validation
                val iconBytes = findIconBytes(zip)

                val clientFilesCount = index.files.count { it.env?.client != "unsupported" }

                log("Validating pack: Name='${index.name}', MC=$mcVersion, Loader=$loaderType ($loaderVersion), Files=$clientFilesCount, HasIcon=${iconBytes != null}")

                Result.success(
                    MrpackPreview(
                        name = index.name.ifBlank { file.nameWithoutExtension },
                        summary = index.summary,
                        versionId = index.versionId.ifBlank { "1.0.0" },
                        minecraftVersion = mcVersion,
                        loaderType = loaderType,
                        loaderVersion = loaderVersion,
                        totalFiles = clientFilesCount,
                        fileSize = file.length(),
                        iconBytes = iconBytes
                    )
                )
            }
        } catch (e: Throwable) {
            logError("Failed to read .mrpack archive ${file.name}", e)
            Result.failure(IllegalArgumentException("Failed to read .mrpack archive: ${e.message}", e))
        }
    }

    /**
     * Executes full atomic import of a .mrpack archive into a newly registered Instance.
     * Supports cooperative cancellation, hash verification, retries, and detailed progress callbacks.
     */
    suspend fun importMrpack(
        file: File,
        targetInstanceName: String? = null,
        onProgress: (MrpackImportProgress) -> Unit = {}
    ): Result<Instance> = withContext(dispatcher) {
        log("==========================================")
        log("STARTING MRPACK IMPORT PIPELINE")
        log("File: ${file.absolutePath} (${file.length()} bytes)")
        log("==========================================")

        var tempInstanceDir: File? = null

        try {
            // Stage 1: Reading Manifest
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.READING_MANIFEST,
                    message = "Reading modpack manifest...",
                    progress = 0.05f
                )
            )

            if (!file.exists()) {
                throw IllegalArgumentException("Modpack file not found: ${file.absolutePath}")
            }

            val zip = try {
                ZipFile(file)
            } catch (e: Throwable) {
                logError("Corrupted zip archive ${file.name}", e)
                throw IllegalArgumentException("Corrupted archive: ${e.message}", e)
            }

            val indexEntry = zip.getEntry("modrinth.index.json")
                ?: throw IllegalArgumentException("Missing required 'modrinth.index.json' in archive.")

            log("Reading modrinth.index.json")
            val indexJson = zip.getInputStream(indexEntry).bufferedReader().use { it.readText() }
            val index = json.decodeFromString<ModrinthIndex>(indexJson)

            // Stage 2: Validating Structure & Dependencies
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.VALIDATING_STRUCTURE,
                    message = "Validating package integrity...",
                    progress = 0.10f
                )
            )

            val mcVersion = index.dependencies["minecraft"]
                ?: throw IllegalArgumentException("Invalid modpack: Missing 'minecraft' version dependency in manifest.")

            val loaderResult = parseLoader(index.dependencies)
            if (loaderResult.isFailure) {
                throw loaderResult.exceptionOrNull()!!
            }
            val (loaderType, loaderVersion) = loaderResult.getOrNull()!!

            log("Minecraft version: $mcVersion")
            log("Loader: $loaderType ($loaderVersion)")
            log("Files declared in manifest: ${index.files.size}")

            val instanceName = targetInstanceName?.trim()?.takeIf { it.isNotBlank() }
                ?: index.name.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension

            // Stage 3: Creating Temporary Instance Directory
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.CREATING_INSTANCE,
                    message = "Preparing isolated instance workspace...",
                    progress = 0.15f
                )
            )

            val newId = UUID.randomUUID().toString()
            val instancesRoot = pathProvider.instancesDirectory.toFile()
            if (!instancesRoot.exists()) instancesRoot.mkdirs()

            // Use atomic temporary directory during import
            val tempDir = File(instancesRoot, ".tmp_import_$newId")
            tempInstanceDir = tempDir
            tempDir.mkdirs()

            val gameDir = File(tempDir, ".minecraft")
            gameDir.mkdirs()

            log("Creating instance workspace: ${tempDir.name}")

            // Stage 4: Extracting Overrides
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.EXTRACTING_OVERRIDES,
                    message = "Extracting configurations and local overrides...",
                    progress = 0.20f
                )
            )

            log("Extracting overrides (configs, options, local resources)...")
            val entries = zip.entries()
            var extractedOverridesCount = 0

            while (entries.hasMoreElements()) {
                if (!coroutineContext.isActive) {
                    zip.close()
                    cleanupDirectory(tempInstanceDir)
                    logError("Import cancelled by user during override extraction")
                    return@withContext Result.failure(IllegalStateException("Import cancelled by user."))
                }

                val entry = entries.nextElement()
                val normalizedEntryName = entry.name.replace('\\', '/')

                val targetRelPath = when {
                    normalizedEntryName.startsWith("overrides/") -> normalizedEntryName.removePrefix("overrides/").trimStart('/')
                    normalizedEntryName.startsWith("client-overrides/") -> normalizedEntryName.removePrefix("client-overrides/").trimStart('/')
                    else -> null
                }

                if (targetRelPath != null && targetRelPath.isNotBlank()) {
                    val outFile = File(gameDir, targetRelPath)

                    // Strict Zip Slip / Path Traversal Protection
                    if (!outFile.canonicalPath.startsWith(gameDir.canonicalPath)) {
                        zip.close()
                        cleanupDirectory(tempInstanceDir)
                        val err = "Security error: Zip Slip detected in archive entry '${entry.name}'"
                        logError(err)
                        throw SecurityException(err)
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedOverridesCount++
                    }
                }
            }

            log("Extracted $extractedOverridesCount override files into .minecraft/")

            // Stage 4.5: Discover & Extract Modpack Icon
            val iconBytes = findIconBytes(zip)
            if (iconBytes != null && isValidImage(iconBytes)) {
                val iconTarget = File(tempDir, "icon.png")
                iconTarget.writeBytes(iconBytes)
                log("Extracted and saved valid instance icon: icon.png (${iconBytes.size} bytes)")
            } else {
                // Check if an icon was extracted in .minecraft/ during overrides
                val overrideIcon = listOf(
                    File(gameDir, "icon.png"),
                    File(gameDir, "pack.png"),
                    File(gameDir, "icon.webp")
                ).firstOrNull { it.exists() && it.length() > 0 }

                if (overrideIcon != null) {
                    val target = File(tempDir, "icon.png")
                    overrideIcon.copyTo(target, overwrite = true)
                    log("Restored instance icon from override: ${overrideIcon.name}")
                }
            }

            zip.close()

            // Stage 5: Downloading Client Mod Files
            val clientFiles = index.files.filter { it.env?.client != "unsupported" && it.downloads.isNotEmpty() }
            val totalFiles = clientFiles.size
            log("Downloading files: $totalFiles required client files")

            if (totalFiles > 0) {
                val httpClient = HttpClientFactory.create()
                clientFiles.forEachIndexed { indexItem, fileItem ->
                    if (!coroutineContext.isActive) {
                        cleanupDirectory(tempInstanceDir)
                        logError("Import cancelled by user during mod download")
                        return@withContext Result.failure(IllegalStateException("Import cancelled by user."))
                    }

                    val sanitizedPath = fileItem.path.replace('\\', '/').trim().trimStart('/')
                    if (sanitizedPath.isBlank()) {
                        throw SecurityException("Security error: Empty path in modpack file entry")
                    }

                    val targetFile = File(gameDir, sanitizedPath)

                    // Path Traversal Protection
                    if (!targetFile.canonicalPath.startsWith(gameDir.canonicalPath)) {
                        cleanupDirectory(tempInstanceDir)
                        val err = "Security error: Malicious path traversal in file declaration '${fileItem.path}'"
                        logError(err)
                        throw SecurityException(err)
                    }

                    val fileName = targetFile.name
                    val currentProgress = 0.25f + ((indexItem.toFloat() / totalFiles.toFloat()) * 0.65f)

                    onProgress(
                        MrpackImportProgress(
                            stage = MrpackImportStage.DOWNLOADING_MODS,
                            message = "Downloading $fileName",
                            progress = currentProgress,
                            currentFile = fileName,
                            currentFileIndex = indexItem + 1,
                            totalFiles = totalFiles,
                            downloadedBytes = 0L,
                            totalBytes = fileItem.fileSize
                        )
                    )

                    targetFile.parentFile?.mkdirs()

                    var downloadSuccess = false
                    var lastError: Throwable? = null

                    // Attempt download from declared URLs with retries
                    for (url in fileItem.downloads) {
                        if (downloadSuccess) break

                        for (attempt in 1..3) {
                            if (!coroutineContext.isActive) {
                                cleanupDirectory(tempInstanceDir)
                                return@withContext Result.failure(IllegalStateException("Import cancelled by user."))
                            }

                            try {
                                val response: HttpResponse = httpClient.get(url)
                                if (response.status.isSuccess()) {
                                    val bytes: ByteArray = response.body()
                                    targetFile.writeBytes(bytes)

                                    // Checksum Verification
                                    val sha1Expected = fileItem.hashes["sha1"]
                                    val sha512Expected = fileItem.hashes["sha512"]

                                    if (sha1Expected != null) {
                                        val actualSha1 = calculateSha1(targetFile)
                                        if (!actualSha1.equals(sha1Expected, ignoreCase = true)) {
                                            targetFile.delete()
                                            throw IllegalStateException("Checksum mismatch for $fileName (SHA1 expected $sha1Expected, got $actualSha1)")
                                        }
                                    } else if (sha512Expected != null) {
                                        val actualSha512 = calculateSha512(targetFile)
                                        if (!actualSha512.equals(sha512Expected, ignoreCase = true)) {
                                            targetFile.delete()
                                            throw IllegalStateException("Checksum mismatch for $fileName (SHA512 mismatch)")
                                        }
                                    }

                                    downloadSuccess = true
                                    break
                                } else {
                                    throw IllegalStateException("HTTP ${response.status.value} ${response.status.description}")
                                }
                            } catch (e: Throwable) {
                                lastError = e
                                if (targetFile.exists()) targetFile.delete()
                                if (attempt < 3) delay(500L * attempt)
                            }
                        }
                    }

                    if (!downloadSuccess) {
                        cleanupDirectory(tempInstanceDir)
                        val errMsg = "Failed to download required file '$fileName' (${fileItem.path}). Reason: ${lastError?.message ?: "Unknown error"}"
                        logError(errMsg, lastError)
                        throw IllegalStateException(errMsg, lastError)
                    }
                }
            }

            // Stage 6: Finalizing & Committing Instance
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.FINALIZING,
                    message = "Finalizing instance...",
                    progress = 0.95f
                )
            )

            log("Finalizing instance...")

            // Move temp instance directory to final location
            val finalInstanceDir = pathProvider.getInstanceDirectory(newId).toFile()
            if (finalInstanceDir.exists()) {
                finalInstanceDir.deleteRecursively()
            }

            val moved = tempDir.renameTo(finalInstanceDir)
            if (!moved) {
                // Fallback copy if rename across drives/mounts
                tempDir.copyRecursively(finalInstanceDir, overwrite = true)
                tempDir.deleteRecursively()
            }
            tempInstanceDir = null

            // Find persistent local icon file
            val finalIconFile = listOf(
                File(finalInstanceDir, "icon.png"),
                File(finalInstanceDir, "pack.png"),
                File(finalInstanceDir, "icon.webp"),
                File(finalInstanceDir, ".minecraft/icon.png"),
                File(finalInstanceDir, ".minecraft/pack.png")
            ).firstOrNull { it.exists() && it.length() > 0 }

            val newInstance = Instance(
                id = newId,
                name = instanceName,
                minecraftVersion = mcVersion,
                loaderType = loaderType,
                loaderVersion = loaderVersion,
                customIconPath = finalIconFile?.absolutePath,
                createdAt = System.currentTimeMillis()
            )

            instanceRepository.registerInstance(newInstance)
            instanceRepository.loadAll()

            log("Instance registered successfully: '${newInstance.name}' (ID: ${newInstance.id}, Icon: ${newInstance.customIconPath})")

            // Stage 7: Complete
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.COMPLETE,
                    message = "Instance successfully imported!",
                    progress = 1.0f,
                    totalFiles = totalFiles
                )
            )

            log("==========================================")
            log("MRPACK IMPORT FINISHED SUCCESSFULLY: ${newInstance.name}")
            log("==========================================")

            Result.success(newInstance)
        } catch (e: Throwable) {
            logError("Import failed", e)
            cleanupDirectory(tempInstanceDir)
            onProgress(
                MrpackImportProgress(
                    stage = MrpackImportStage.FAILED,
                    message = e.message ?: "Failed to import modpack.",
                    progress = 0f
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Exports an existing Instance into a structurally valid .mrpack archive.
     * Complies with Modrinth specification: includes modrinth.index.json manifest and overrides/ directory.
     */
    suspend fun exportMrpack(
        instance: Instance,
        targetFile: File,
        options: MrpackExportOptions = MrpackExportOptions(),
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<File> = withContext(dispatcher) {
        try {
            log("Exporting instance '${instance.name}' to ${targetFile.absolutePath}")
            onProgress("Preparing instance files...", 0.1f)
            val instanceDir = pathProvider.getInstanceDirectory(instance.id).toFile()
            val gameDir = File(instanceDir, ".minecraft")

            if (targetFile.parentFile != null && !targetFile.parentFile.exists()) {
                targetFile.parentFile.mkdirs()
            }

            val packName = options.customName?.trim()?.takeIf { it.isNotBlank() } ?: instance.name
            val packSummary = options.customSummary?.trim()?.takeIf { it.isNotBlank() } ?: "Exported from Ezz Launcher"
            val versionId = options.versionId.trim().ifBlank { "1.0.0" }

            val dependencies = mutableMapOf<String, String>()
            dependencies["minecraft"] = instance.minecraftVersion
            when (instance.loaderType) {
                LoaderType.FABRIC -> dependencies["fabric-loader"] = instance.loaderVersion ?: "0.16.9"
                LoaderType.OPTIFINE -> dependencies["optifine"] = instance.loaderVersion ?: "latest"
                LoaderType.VANILLA -> {}
            }

            val manifest = ModrinthIndex(
                formatVersion = 1,
                game = "minecraft",
                versionId = versionId,
                name = packName,
                summary = packSummary,
                files = emptyList(),
                dependencies = dependencies
            )

            onProgress("Building .mrpack archive...", 0.3f)
            val manifestJson = json.encodeToString(ModrinthIndex.serializer(), manifest)

            ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
                // 1. Write modrinth.index.json
                val indexEntry = ZipEntry("modrinth.index.json")
                zos.putNextEntry(indexEntry)
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Include icon if present
                val iconFile = instance.customIconPath?.let { File(it) }?.takeIf { it.exists() }
                    ?: File(instanceDir, "icon.png").takeIf { it.exists() }
                if (iconFile != null) {
                    val iconEntry = ZipEntry("icon.png")
                    zos.putNextEntry(iconEntry)
                    FileInputStream(iconFile).use { it.copyTo(zos) }
                    zos.closeEntry()
                }

                // 3. Write overrides
                val foldersToInclude = mutableListOf<String>()
                if (options.includeConfigs) foldersToInclude.add("config")
                if (options.includeMods) foldersToInclude.add("mods")
                if (options.includeResourcePacks) foldersToInclude.add("resourcepacks")
                if (options.includeShaderPacks) foldersToInclude.add("shaderpacks")

                foldersToInclude.forEach { folderName ->
                    val folder = File(gameDir, folderName)
                    if (folder.exists() && folder.isDirectory) {
                        folder.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val rel = file.relativeTo(gameDir).path.replace('\\', '/')
                                val entry = ZipEntry("overrides/$rel")
                                zos.putNextEntry(entry)
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                // Include options.txt if configs are included
                if (options.includeConfigs) {
                    val optionsTxt = File(gameDir, "options.txt")
                    if (optionsTxt.exists() && optionsTxt.isFile) {
                        val entry = ZipEntry("overrides/options.txt")
                        zos.putNextEntry(entry)
                        FileInputStream(optionsTxt).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            onProgress("Export complete!", 1.0f)
            log("Modpack export completed successfully: ${targetFile.name}")
            Result.success(targetFile)
        } catch (e: Throwable) {
            logError("Modpack export failed", e)
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Parses and strictly validates loader dependencies declared in modrinth.index.json.
     */
    private fun parseLoader(dependencies: Map<String, String>): Result<Pair<LoaderType, String?>> {
        return when {
            dependencies.containsKey("fabric-loader") -> {
                val rawVer = dependencies["fabric-loader"]?.trim()?.removePrefix("v") ?: "0.16.9"
                Result.success(Pair(LoaderType.FABRIC, rawVer))
            }
            dependencies.containsKey("quilt-loader") -> {
                // Quilt loader modpacks run with Fabric compatibility
                val rawVer = dependencies["quilt-loader"]?.trim()?.removePrefix("v") ?: "0.16.9"
                Result.success(Pair(LoaderType.FABRIC, rawVer))
            }
            dependencies.containsKey("optifine") -> {
                val rawVer = dependencies["optifine"]?.trim()
                Result.success(Pair(LoaderType.OPTIFINE, rawVer))
            }
            dependencies.containsKey("forge") -> {
                val forgeVer = dependencies["forge"]
                Result.failure(
                    IllegalArgumentException(
                        "This modpack requires Minecraft Forge ($forgeVer), which is currently not supported by Ezz Launcher (Fabric & Vanilla supported)."
                    )
                )
            }
            dependencies.containsKey("neoforge") -> {
                val neoVer = dependencies["neoforge"]
                Result.failure(
                    IllegalArgumentException(
                        "This modpack requires NeoForge ($neoVer), which is currently not supported by Ezz Launcher (Fabric & Vanilla supported)."
                    )
                )
            }
            else -> {
                // Vanilla (no mod loader declared)
                Result.success(Pair(LoaderType.VANILLA, null))
            }
        }
    }

    /**
     * Comprehensive icon discovery across standard archive locations.
     */
    private fun findIconBytes(zip: ZipFile): ByteArray? {
        val candidates = listOf(
            "icon.png", "pack.png", "icon.webp", "icon.jpg", "icon.jpeg",
            "overrides/icon.png", "overrides/pack.png", "overrides/icon.webp",
            "client-overrides/icon.png", "client-overrides/pack.png"
        )
        for (name in candidates) {
            val entry = zip.getEntry(name)
            if (entry != null && !entry.isDirectory) {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (isValidImage(bytes)) return bytes
            }
        }

        // Search through entries case-insensitively
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val normalized = entry.name.replace('\\', '/').lowercase()
            val fileName = normalized.substringAfterLast('/')
            if (fileName in listOf("icon.png", "pack.png", "icon.webp", "icon.jpg", "icon.jpeg", "logo.png")) {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (isValidImage(bytes)) return bytes
            }
        }
        return null
    }

    /**
     * Header byte validation for image formats (PNG, JPEG, GIF, WebP).
     */
    private fun isValidImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // GIF
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return true
        // WebP (RIFF....WEBP)
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) {
            return true
        }
        return false
    }

    private fun cleanupDirectory(dir: File?) {
        if (dir != null && dir.exists()) {
            try {
                dir.deleteRecursively()
            } catch (_: Throwable) {}
        }
    }

    private fun calculateSha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateSha512(file: File): String {
        val md = MessageDigest.getInstance("SHA-512")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
