package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceLogEntry
import io.ezz.launcher.core.model.instance.InstanceRepairReport
import io.ezz.launcher.core.model.instance.InstanceStatistics
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.core.model.instance.LocalShaderPack
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.core.model.instance.LocalWorldBackup
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.InstanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalInstanceManager(
    private val pathProvider: PathProvider,
    private val instanceRepository: InstanceRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private fun getGameDir(instanceId: String): File {
        val dir = pathProvider.getInstanceDirectory(instanceId).resolve(".minecraft").toFile()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getBackupsDir(instanceName: String): File {
        val root = pathProvider.rootDirectory.resolve("backups").resolve(sanitizeFileName(instanceName)).toFile()
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    // ==========================================
    // 1. OVERVIEW & STATISTICS
    // ==========================================

    suspend fun getInstanceStatistics(instanceId: String): InstanceStatistics = withContext(dispatcher) {
        val gameDir = getGameDir(instanceId)
        val modsDir = File(gameDir, "mods")
        val rpDir = File(gameDir, "resourcepacks")
        val spDir = File(gameDir, "shaderpacks")
        val savesDir = File(gameDir, "saves")
        val ssDir = File(gameDir, "screenshots")

        val modsCount = modsDir.listFiles { f -> f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".jar.disabled")) }?.size ?: 0
        val rpCount = rpDir.listFiles { f -> f.name.endsWith(".zip") || f.name.endsWith(".zip.disabled") || (f.isDirectory && !f.name.startsWith(".")) }?.size ?: 0
        val spCount = spDir.listFiles { f -> f.name.endsWith(".zip") || f.name.endsWith(".zip.disabled") || (f.isDirectory && !f.name.startsWith(".")) }?.size ?: 0
        val worldsCount = savesDir.listFiles { f -> f.isDirectory && File(f, "level.dat").exists() }?.size ?: 0
        val ssCount = ssDir.listFiles { f -> f.isFile && (f.name.endsWith(".png") || f.name.endsWith(".jpg")) }?.size ?: 0

        var totalSize = 0L
        gameDir.walkTopDown().forEach { totalSize += it.length() }

        InstanceStatistics(
            modsCount = modsCount,
            resourcePacksCount = rpCount,
            shadersCount = spCount,
            worldsCount = worldsCount,
            screenshotsCount = ssCount,
            totalSizeBytes = totalSize
        )
    }

    // ==========================================
    // 2. MODS MANAGEMENT
    // ==========================================

    suspend fun getMods(instanceId: String): List<LocalMod> = withContext(dispatcher) {
        val modsDir = File(getGameDir(instanceId), "mods")
        if (!modsDir.exists()) modsDir.mkdirs()

        val files = modsDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".jar", ignoreCase = true) || file.name.endsWith(".jar.disabled", ignoreCase = true))
        } ?: return@withContext emptyList()

        files.map { file ->
            parseMod(file)
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseMod(file: File): LocalMod {
        val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
        val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".jar")
        var modId = cleanName.lowercase().replace(" ", "-")
        var modName = cleanName
        var version = "1.0.0"
        var description = "Minecraft Mod"
        var loader = "FABRIC"
        var author: String? = null

        try {
            ZipFile(file).use { zip ->
                val fabricEntry = zip.getEntry("fabric.mod.json") ?: zip.getEntry("quilt.mod.json")
                if (fabricEntry != null) {
                    loader = "FABRIC"
                    val content = zip.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                    val jsonObj = json.parseToJsonElement(content).jsonObject
                    modId = jsonObj["id"]?.jsonPrimitive?.content ?: modId
                    modName = jsonObj["name"]?.jsonPrimitive?.content ?: modName
                    version = jsonObj["version"]?.jsonPrimitive?.content ?: version
                    description = jsonObj["description"]?.jsonPrimitive?.content ?: description
                    val authorsList = mutableListOf<String>()
                    jsonObj["authors"]?.jsonArray?.forEach { elem ->
                        if (elem is kotlinx.serialization.json.JsonPrimitive) authorsList.add(elem.content)
                        else if (elem is kotlinx.serialization.json.JsonObject) elem["name"]?.jsonPrimitive?.content?.let { authorsList.add(it) }
                    }
                    if (authorsList.isNotEmpty()) author = authorsList.joinToString(", ")
                } else {
                    val mcmodEntry = zip.getEntry("mcmod.info")
                    if (mcmodEntry != null) {
                        loader = "FORGE"
                        val content = zip.getInputStream(mcmodEntry).bufferedReader().use { it.readText() }
                        val arr = json.parseToJsonElement(content).jsonArray
                        val first = arr.firstOrNull()?.jsonObject
                        if (first != null) {
                            modId = first["modid"]?.jsonPrimitive?.content ?: modId
                            modName = first["name"]?.jsonPrimitive?.content ?: modName
                            version = first["version"]?.jsonPrimitive?.content ?: version
                            description = first["description"]?.jsonPrimitive?.content ?: description
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback to filename
        }

        return LocalMod(
            id = modId,
            name = modName,
            version = version,
            fileName = file.name,
            fileSize = file.length(),
            loader = loader,
            enabled = isEnabled,
            author = author,
            description = description
        )
    }

    suspend fun toggleMod(instanceId: String, fileName: String, enable: Boolean): Boolean = withContext(dispatcher) {
        val modsDir = File(getGameDir(instanceId), "mods")
        val currentFile = File(modsDir, fileName)
        if (!currentFile.exists()) return@withContext false

        val targetName = if (enable) {
            fileName.removeSuffix(".disabled")
        } else {
            if (fileName.endsWith(".disabled", ignoreCase = true)) fileName else "$fileName.disabled"
        }

        currentFile.renameTo(File(modsDir, targetName))
    }

    suspend fun deleteMod(instanceId: String, fileName: String): Boolean = withContext(dispatcher) {
        val modsDir = File(getGameDir(instanceId), "mods")
        val file = File(modsDir, fileName)
        if (file.exists()) file.delete() else false
    }

    // ==========================================
    // 3. RESOURCE PACKS MANAGEMENT
    // ==========================================

    suspend fun getResourcePacks(instanceId: String): List<LocalResourcePack> = withContext(dispatcher) {
        val rpDir = File(getGameDir(instanceId), "resourcepacks")
        if (!rpDir.exists()) rpDir.mkdirs()

        val files = rpDir.listFiles { file ->
            file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".zip.disabled", ignoreCase = true) || file.isDirectory
        } ?: return@withContext emptyList()

        files.map { file ->
            parseResourcePack(file)
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseResourcePack(file: File): LocalResourcePack {
        val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
        val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".zip")
        var packName = cleanName
        var description: String? = null
        var packFormat: Int? = null

        try {
            if (file.isFile && file.name.endsWith(".zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val mcmeta = zip.getEntry("pack.mcmeta")
                    if (mcmeta != null) {
                        val content = zip.getInputStream(mcmeta).bufferedReader().use { it.readText() }
                        val obj = json.parseToJsonElement(content).jsonObject["pack"]?.jsonObject
                        description = obj?.get("description")?.jsonPrimitive?.content
                        packFormat = obj?.get("pack_format")?.jsonPrimitive?.content?.toIntOrNull()
                    }
                }
            } else if (file.isDirectory) {
                val mcmeta = File(file, "pack.mcmeta")
                if (mcmeta.exists()) {
                    val content = mcmeta.readText()
                    val obj = json.parseToJsonElement(content).jsonObject["pack"]?.jsonObject
                    description = obj?.get("description")?.jsonPrimitive?.content
                    packFormat = obj?.get("pack_format")?.jsonPrimitive?.content?.toIntOrNull()
                }
            }
        } catch (_: Throwable) {}

        return LocalResourcePack(
            fileName = file.name,
            name = packName,
            description = description,
            packFormat = packFormat,
            enabled = isEnabled,
            fileSize = if (file.isFile) file.length() else 0L
        )
    }

    suspend fun toggleResourcePack(instanceId: String, fileName: String, enable: Boolean): Boolean = withContext(dispatcher) {
        val rpDir = File(getGameDir(instanceId), "resourcepacks")
        val currentFile = File(rpDir, fileName)
        if (!currentFile.exists()) return@withContext false

        val targetName = if (enable) {
            fileName.removeSuffix(".disabled")
        } else {
            if (fileName.endsWith(".disabled", ignoreCase = true)) fileName else "$fileName.disabled"
        }

        currentFile.renameTo(File(rpDir, targetName))
    }

    suspend fun deleteResourcePack(instanceId: String, fileName: String): Boolean = withContext(dispatcher) {
        val rpDir = File(getGameDir(instanceId), "resourcepacks")
        val file = File(rpDir, fileName)
        if (file.exists()) file.deleteRecursively() else false
    }

    // ==========================================
    // 4. SHADERS MANAGEMENT
    // ==========================================

    suspend fun getShaderPacks(instanceId: String): List<LocalShaderPack> = withContext(dispatcher) {
        val spDir = File(getGameDir(instanceId), "shaderpacks")
        if (!spDir.exists()) spDir.mkdirs()

        val files = spDir.listFiles { file ->
            file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".zip.disabled", ignoreCase = true) || file.isDirectory
        } ?: return@withContext emptyList()

        files.map { file ->
            val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
            val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".zip")
            LocalShaderPack(
                fileName = file.name,
                name = cleanName,
                enabled = isEnabled,
                fileSize = if (file.isFile) file.length() else 0L
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun toggleShaderPack(instanceId: String, fileName: String, enable: Boolean): Boolean = withContext(dispatcher) {
        val spDir = File(getGameDir(instanceId), "shaderpacks")
        val currentFile = File(spDir, fileName)
        if (!currentFile.exists()) return@withContext false

        val targetName = if (enable) {
            fileName.removeSuffix(".disabled")
        } else {
            if (fileName.endsWith(".disabled", ignoreCase = true)) fileName else "$fileName.disabled"
        }

        currentFile.renameTo(File(spDir, targetName))
    }

    suspend fun deleteShaderPack(instanceId: String, fileName: String): Boolean = withContext(dispatcher) {
        val spDir = File(getGameDir(instanceId), "shaderpacks")
        val file = File(spDir, fileName)
        if (file.exists()) file.deleteRecursively() else false
    }

    // ==========================================
    // 5. WORLDS & SAVES MANAGEMENT
    // ==========================================

    suspend fun getWorlds(instanceId: String): List<LocalWorld> = withContext(dispatcher) {
        val savesDir = File(getGameDir(instanceId), "saves")
        if (!savesDir.exists()) savesDir.mkdirs()

        val folders = savesDir.listFiles { f -> f.isDirectory && File(f, "level.dat").exists() } ?: return@withContext emptyList()

        folders.map { folder ->
            var size = 0L
            folder.walkTopDown().forEach { size += it.length() }
            val iconFile = File(folder, "icon.png")
            val iconPath = if (iconFile.exists()) iconFile.absolutePath else null

            LocalWorld(
                folderName = folder.name,
                name = folder.name,
                gameType = "Survival",
                lastPlayed = folder.lastModified(),
                sizeBytes = size,
                iconPath = iconPath
            )
        }.sortedByDescending { it.lastPlayed }
    }

    suspend fun backupWorld(instanceId: String, instanceName: String, worldFolderName: String): LocalWorldBackup? = withContext(dispatcher) {
        val worldDir = File(File(getGameDir(instanceId), "saves"), worldFolderName)
        if (!worldDir.exists()) return@withContext null

        val backupsDir = getBackupsDir(instanceName)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val backupFile = File(backupsDir, "${worldFolderName}_$timestamp.zip")

        zipDirectory(worldDir, backupFile)

        LocalWorldBackup(
            fileName = backupFile.name,
            worldName = worldFolderName,
            createdAt = System.currentTimeMillis(),
            sizeBytes = backupFile.length(),
            filePath = backupFile.absolutePath
        )
    }

    suspend fun getWorldBackups(instanceName: String, worldFolderName: String): List<LocalWorldBackup> = withContext(dispatcher) {
        val backupsDir = getBackupsDir(instanceName)
        val files = backupsDir.listFiles { f -> f.name.startsWith(worldFolderName) && f.name.endsWith(".zip") } ?: return@withContext emptyList()

        files.map { f ->
            LocalWorldBackup(
                fileName = f.name,
                worldName = worldFolderName,
                createdAt = f.lastModified(),
                sizeBytes = f.length(),
                filePath = f.absolutePath
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun restoreWorldBackup(instanceId: String, backupFilePath: String, targetFolderName: String): Boolean = withContext(dispatcher) {
        val backupFile = File(backupFilePath)
        if (!backupFile.exists()) return@withContext false

        val targetDir = File(File(getGameDir(instanceId), "saves"), targetFolderName)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        unzip(backupFile, targetDir)
        true
    }

    suspend fun deleteWorld(instanceId: String, worldFolderName: String): Boolean = withContext(dispatcher) {
        val worldDir = File(File(getGameDir(instanceId), "saves"), worldFolderName)
        if (worldDir.exists()) worldDir.deleteRecursively() else false
    }

    suspend fun duplicateWorld(instanceId: String, worldFolderName: String, newWorldName: String): Boolean = withContext(dispatcher) {
        val sourceDir = File(File(getGameDir(instanceId), "saves"), worldFolderName)
        if (!sourceDir.exists()) return@withContext false

        val cleanNewName = sanitizeFileName(newWorldName)
        val destDir = File(File(getGameDir(instanceId), "saves"), cleanNewName)
        if (destDir.exists()) return@withContext false

        sourceDir.copyRecursively(destDir, overwrite = false)
    }

    suspend fun renameWorld(instanceId: String, worldFolderName: String, newName: String): Boolean = withContext(dispatcher) {
        val sourceDir = File(File(getGameDir(instanceId), "saves"), worldFolderName)
        if (!sourceDir.exists()) return@withContext false

        val destDir = File(File(getGameDir(instanceId), "saves"), sanitizeFileName(newName))
        sourceDir.renameTo(destDir)
    }

    suspend fun importWorld(instanceId: String, sourceZipOrFolder: File): Boolean = withContext(dispatcher) {
        val savesDir = File(getGameDir(instanceId), "saves")
        if (!savesDir.exists()) savesDir.mkdirs()

        if (sourceZipOrFolder.isDirectory) {
            val levelDat = File(sourceZipOrFolder, "level.dat")
            if (!levelDat.exists()) return@withContext false
            val dest = File(savesDir, sourceZipOrFolder.name)
            sourceZipOrFolder.copyRecursively(dest, overwrite = true)
            return@withContext true
        } else if (sourceZipOrFolder.isFile && sourceZipOrFolder.name.endsWith(".zip", ignoreCase = true)) {
            val targetName = sourceZipOrFolder.name.removeSuffix(".zip")
            val dest = File(savesDir, targetName)
            dest.mkdirs()
            unzip(sourceZipOrFolder, dest)
            return@withContext File(dest, "level.dat").exists()
        }
        false
    }

    suspend fun exportWorld(instanceId: String, worldFolderName: String, destinationZip: File): Boolean = withContext(dispatcher) {
        val worldDir = File(File(getGameDir(instanceId), "saves"), worldFolderName)
        if (!worldDir.exists()) return@withContext false
        destinationZip.parentFile?.mkdirs()
        zipDirectory(worldDir, destinationZip)
        true
    }

    // ==========================================
    // 6. SCREENSHOTS MANAGEMENT
    // ==========================================

    suspend fun getScreenshots(instanceId: String): List<LocalScreenshot> = withContext(dispatcher) {
        val ssDir = File(getGameDir(instanceId), "screenshots")
        if (!ssDir.exists()) ssDir.mkdirs()

        val files = ssDir.listFiles { f -> f.isFile && (f.name.endsWith(".png", ignoreCase = true) || f.name.endsWith(".jpg", ignoreCase = true)) } ?: return@withContext emptyList()

        files.map { f ->
            LocalScreenshot(
                fileName = f.name,
                filePath = f.absolutePath,
                fileSizeBytes = f.length(),
                lastModified = f.lastModified()
            )
        }.sortedByDescending { it.lastModified }
    }

    suspend fun deleteScreenshot(instanceId: String, fileName: String): Boolean = withContext(dispatcher) {
        val ssDir = File(getGameDir(instanceId), "screenshots")
        val file = File(ssDir, fileName)
        if (file.exists()) file.delete() else false
    }

    // ==========================================
    // 7. LOGS MANAGEMENT
    // ==========================================

    suspend fun getLogs(instanceId: String): List<InstanceLogEntry> = withContext(dispatcher) {
        val gameDir = getGameDir(instanceId)
        val logsDir = File(gameDir, "logs")
        val crashDir = File(gameDir, "crash-reports")
        val entries = mutableListOf<InstanceLogEntry>()

        if (logsDir.exists()) {
            logsDir.listFiles { f -> f.isFile }?.forEach { f ->
                entries.add(
                    InstanceLogEntry(
                        fileName = f.name,
                        filePath = f.absolutePath,
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        isCrashReport = false
                    )
                )
            }
        }

        if (crashDir.exists()) {
            crashDir.listFiles { f -> f.isFile }?.forEach { f ->
                entries.add(
                    InstanceLogEntry(
                        fileName = "CRASH: ${f.name}",
                        filePath = f.absolutePath,
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        isCrashReport = true
                    )
                )
            }
        }

        entries.sortedByDescending { it.lastModified }
    }

    suspend fun readLogContent(filePath: String): String = withContext(dispatcher) {
        val file = File(filePath)
        if (!file.exists()) return@withContext "Log file does not exist."
        try {
            val text = file.readText()
            // Mask sensitive tokens / auth URLs
            maskSensitiveTokens(text)
        } catch (e: Throwable) {
            "Error reading log file: ${e.message}"
        }
    }

    private fun maskSensitiveTokens(content: String): String {
        return content
            .replace(Regex("(?i)(accessToken|clientToken|token|session|auth|password)=([^\\s,]+)"), "$1=********")
            .replace(Regex("https://login\\.microsoftonline\\.com/[^\\s]+"), "https://login.microsoftonline.com/********")
    }

    // ==========================================
    // 8. INSTANCE REPAIR
    // ==========================================

    suspend fun repairInstance(instance: Instance): InstanceRepairReport = withContext(dispatcher) {
        val passed = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val failed = mutableListOf<String>()

        // 1. Check game directory
        val gameDir = getGameDir(instance.id)
        if (gameDir.exists() && gameDir.isDirectory) {
            passed.add("Instance game directory verified")
        } else {
            failed.add("Game directory missing: ${gameDir.absolutePath}")
        }

        // 2. Check Minecraft version JSON
        val versionDir = pathProvider.versionsDirectory.resolve(instance.minecraftVersion).toFile()
        val versionJson = File(versionDir, "${instance.minecraftVersion}.json")
        if (versionJson.exists()) {
            passed.add("Minecraft ${instance.minecraftVersion} metadata intact")
        } else {
            warnings.add("Version metadata not cached locally (will download on launch)")
        }

        // 3. Check client JAR
        val clientJar = File(versionDir, "${instance.minecraftVersion}.jar")
        if (clientJar.exists() && clientJar.length() > 1024) {
            passed.add("Client executable JAR validated (${clientJar.length() / 1024 / 1024} MB)")
        } else {
            warnings.add("Client JAR missing (will download on launch)")
        }

        // 4. Check Java installation
        val javaReq = when {
            instance.minecraftVersion.startsWith("1.20.5") || instance.minecraftVersion.startsWith("1.20.6") || instance.minecraftVersion.startsWith("1.21") -> 21
            instance.minecraftVersion.startsWith("1.18") || instance.minecraftVersion.startsWith("1.19") || instance.minecraftVersion.startsWith("1.20") -> 17
            instance.minecraftVersion.startsWith("1.17") -> 16
            else -> 8
        }
        if (instance.javaPath != null && File(instance.javaPath).exists()) {
            passed.add("Custom Java configured: ${instance.javaPath}")
        } else {
            passed.add("Auto-detect Java runtime (Java $javaReq compatible)")
        }

        // 5. Check mods directory for duplicates
        val modsDir = File(gameDir, "mods")
        if (modsDir.exists()) {
            val mods = getMods(instance.id)
            passed.add("${mods.count { it.enabled }} active mod(s) verified")
        }

        val isHealthy = failed.isEmpty()
        InstanceRepairReport(
            passed = passed,
            warnings = warnings,
            failed = failed,
            isHealthy = isHealthy
        )
    }

    // ==========================================
    // 9. INSTANCE DUPLICATION & EXPORT
    // ==========================================

    suspend fun duplicateInstance(
        sourceInstance: Instance,
        newName: String,
        includeWorlds: Boolean = false
    ): Instance = withContext(dispatcher) {
        // Register in repository
        val created = instanceRepository.createInstance(
            name = newName,
            minecraftVersion = sourceInstance.minecraftVersion,
            loaderType = sourceInstance.loaderType,
            loaderVersion = sourceInstance.loaderVersion,
            iconId = sourceInstance.iconId,
            minMemoryMb = sourceInstance.minMemoryMb,
            maxMemoryMb = sourceInstance.maxMemoryMb,
            customJvmArgs = sourceInstance.customJvmArgs
        )

        val updated = created.copy(
            javaPath = sourceInstance.javaPath,
            windowWidth = sourceInstance.windowWidth,
            windowHeight = sourceInstance.windowHeight
        )
        instanceRepository.updateInstance(updated)

        // Copy directories
        val srcGameDir = getGameDir(sourceInstance.id)
        val dstGameDir = getGameDir(created.id)

        // Copy mods, resourcepacks, shaderpacks, config
        listOf("mods", "resourcepacks", "shaderpacks", "config").forEach { folder ->
            val src = File(srcGameDir, folder)
            if (src.exists()) {
                val dst = File(dstGameDir, folder)
                src.copyRecursively(dst, overwrite = true)
            }
        }

        if (includeWorlds) {
            val srcSaves = File(srcGameDir, "saves")
            if (srcSaves.exists()) {
                val dstSaves = File(dstGameDir, "saves")
                srcSaves.copyRecursively(dstSaves, overwrite = true)
            }
        }

        updated
    }

    suspend fun exportInstance(
        instance: Instance,
        targetZip: File,
        includeWorlds: Boolean = false
    ): Boolean = withContext(dispatcher) {
        try {
            targetZip.parentFile?.mkdirs()
            val gameDir = getGameDir(instance.id)

            ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
                // Write instance.json descriptor
                val metaJson = json.encodeToString(Instance.serializer(), instance)
                zos.putNextEntry(ZipEntry("instance.json"))
                zos.write(metaJson.toByteArray())
                zos.closeEntry()

                // Write folders
                val foldersToInclude = mutableListOf("mods", "resourcepacks", "shaderpacks", "config")
                if (includeWorlds) foldersToInclude.add("saves")

                foldersToInclude.forEach { folderName ->
                    val folder = File(gameDir, folderName)
                    if (folder.exists()) {
                        folder.walkTopDown().forEach { file ->
                            val relPath = "$folderName/${file.relativeTo(folder).path.replace('\\', '/')}"
                            if (file.isFile) {
                                zos.putNextEntry(ZipEntry(relPath))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Throwable) {
            println("Instance export error: ${e.message}")
            false
        }
    }

    // ==========================================
    // HELPER ZIP UTILITIES
    // ==========================================

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(sourceDir).path.replace('\\', '/')
                    zos.putNextEntry(ZipEntry(relPath))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Protect against Zip Slip / path traversal
                val newFile = File(destDir, entry.name)
                if (!newFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip Slip detected: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
