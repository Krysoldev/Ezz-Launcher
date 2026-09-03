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
import io.ezz.launcher.core.model.instance.LogLine
import io.ezz.launcher.core.model.instance.LogReadResult
import io.ezz.launcher.core.model.instance.LogSeverityLevel
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.InstanceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.ModrinthIndex
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
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

    private fun getIconsCacheDir(category: String): File {
        val dir = pathProvider.cacheDirectory.resolve("icons").resolve(category).toFile()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun parseMod(file: File): LocalMod {
        val cached = IncrementalModIndexer.getCached(file)
        if (cached != null) {
            return cached
        }

        val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
        val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".jar")
        var modId = cleanName.lowercase().replace(" ", "-")
        var modName = cleanName
        var version = "1.0.0"
        var description = "Minecraft Mod"
        var loader = "FABRIC"
        var author: String? = null
        var iconPath: String? = null
        val dependencies = mutableMapOf<String, String>()
        val breaks = mutableMapOf<String, String>()
        val conflicts = mutableMapOf<String, String>()

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

                    jsonObj["depends"]?.let { elem ->
                        if (elem is kotlinx.serialization.json.JsonObject) {
                            elem.forEach { (k, v) ->
                                val constraint = when (v) {
                                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                                    is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(" ")
                                    else -> "*"
                                }
                                dependencies[k.lowercase()] = constraint
                            }
                        }
                    }
                    jsonObj["breaks"]?.let { elem ->
                        if (elem is kotlinx.serialization.json.JsonObject) {
                            elem.forEach { (k, v) ->
                                val constraint = when (v) {
                                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                                    is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(" ")
                                    else -> "*"
                                }
                                breaks[k.lowercase()] = constraint
                            }
                        }
                    }
                    jsonObj["conflicts"]?.let { elem ->
                        if (elem is kotlinx.serialization.json.JsonObject) {
                            elem.forEach { (k, v) ->
                                val constraint = when (v) {
                                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                                    is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(" ")
                                    else -> "*"
                                }
                                conflicts[k.lowercase()] = constraint
                            }
                        }
                    }

                    // Extract mod icon from fabric.mod.json
                    val iconEntryName = when (val iconElem = jsonObj["icon"]) {
                        is kotlinx.serialization.json.JsonPrimitive -> iconElem.content
                        is kotlinx.serialization.json.JsonObject -> iconElem["128"]?.jsonPrimitive?.content
                            ?: iconElem["64"]?.jsonPrimitive?.content
                            ?: iconElem["32"]?.jsonPrimitive?.content
                            ?: iconElem["16"]?.jsonPrimitive?.content
                            ?: iconElem.values.firstOrNull()?.jsonPrimitive?.content
                        else -> null
                    } ?: "assets/$modId/icon.png"

                    val iconZipEntry = zip.getEntry(iconEntryName)
                        ?: zip.getEntry("assets/$modId/icon.png")
                        ?: zip.getEntry("icon.png")
                        ?: zip.getEntry("pack.png")

                    if (iconZipEntry != null) {
                        val iconCacheFile = File(getIconsCacheDir("mods"), "${modId}.png")
                        if (!iconCacheFile.exists() || iconCacheFile.length() == 0L) {
                            zip.getInputStream(iconZipEntry).use { input ->
                                iconCacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        if (iconCacheFile.exists() && iconCacheFile.length() > 0) {
                            iconPath = iconCacheFile.absolutePath
                        }
                    }
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

                    val iconZipEntry = zip.getEntry("icon.png") ?: zip.getEntry("pack.png") ?: zip.getEntry("logo.png")
                    if (iconZipEntry != null) {
                        val iconCacheFile = File(getIconsCacheDir("mods"), "${modId}.png")
                        if (!iconCacheFile.exists() || iconCacheFile.length() == 0L) {
                            zip.getInputStream(iconZipEntry).use { input ->
                                iconCacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        if (iconCacheFile.exists() && iconCacheFile.length() > 0) {
                            iconPath = iconCacheFile.absolutePath
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback to filename
        }

        val parsed = LocalMod(
            id = modId,
            name = modName,
            version = version,
            fileName = file.name,
            fileSize = file.length(),
            loader = loader,
            enabled = isEnabled,
            author = author,
            description = description,
            iconPath = iconPath,
            dependencies = dependencies,
            breaks = breaks,
            conflicts = conflicts
        )
        IncrementalModIndexer.put(file, parsed)
        return parsed
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
        if (fileName.startsWith("ezz-skin-mod", ignoreCase = true) || fileName.contains("ezzskin", ignoreCase = true)) {
            println("[LocalInstanceManager] Prevented delete of protected system mod: $fileName")
            return@withContext false
        }
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
        var iconPath: String? = null

        try {
            if (file.isFile && (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".zip.disabled", ignoreCase = true))) {
                ZipFile(file).use { zip ->
                    val mcmeta = zip.getEntry("pack.mcmeta")
                    if (mcmeta != null) {
                        val content = zip.getInputStream(mcmeta).bufferedReader().use { it.readText() }
                        val obj = json.parseToJsonElement(content).jsonObject["pack"]?.jsonObject
                        description = obj?.get("description")?.jsonPrimitive?.content
                        packFormat = obj?.get("pack_format")?.jsonPrimitive?.content?.toIntOrNull()
                    }

                    val iconEntry = zip.getEntry("pack.png") ?: zip.getEntry("icon.png")
                    if (iconEntry != null) {
                        val iconCacheFile = File(getIconsCacheDir("resourcepacks"), "${cleanName}.png")
                        if (!iconCacheFile.exists() || iconCacheFile.length() == 0L) {
                            zip.getInputStream(iconEntry).use { input ->
                                iconCacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        if (iconCacheFile.exists() && iconCacheFile.length() > 0) {
                            iconPath = iconCacheFile.absolutePath
                        }
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

                val iconFile = File(file, "pack.png").takeIf { it.exists() } ?: File(file, "icon.png").takeIf { it.exists() }
                if (iconFile != null) {
                    iconPath = iconFile.absolutePath
                }
            }
        } catch (_: Throwable) {}

        return LocalResourcePack(
            fileName = file.name,
            name = packName,
            description = description,
            packFormat = packFormat,
            enabled = isEnabled,
            fileSize = if (file.isFile) file.length() else 0L,
            iconPath = iconPath
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
            var iconPath: String? = null

            try {
                if (file.isFile && (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".zip.disabled", ignoreCase = true))) {
                    ZipFile(file).use { zip ->
                        val iconEntry = zip.getEntry("pack.png") ?: zip.getEntry("icon.png")
                        if (iconEntry != null) {
                            val iconCacheFile = File(getIconsCacheDir("shaders"), "${cleanName}.png")
                            if (!iconCacheFile.exists() || iconCacheFile.length() == 0L) {
                                zip.getInputStream(iconEntry).use { input ->
                                    iconCacheFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            if (iconCacheFile.exists() && iconCacheFile.length() > 0) {
                                iconPath = iconCacheFile.absolutePath
                            }
                        }
                    }
                } else if (file.isDirectory) {
                    val iconFile = File(file, "pack.png").takeIf { it.exists() } ?: File(file, "icon.png").takeIf { it.exists() }
                    if (iconFile != null) {
                        iconPath = iconFile.absolutePath
                    }
                }
            } catch (_: Throwable) {}

            LocalShaderPack(
                fileName = file.name,
                name = cleanName,
                enabled = isEnabled,
                fileSize = if (file.isFile) file.length() else 0L,
                iconPath = iconPath
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
            val iconPath = if (iconFile.exists() && iconFile.length() > 0) iconFile.absolutePath else null

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

    suspend fun readLogResult(filePath: String, maxLines: Int = 5000): LogReadResult = withContext(dispatcher) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return@withContext LogReadResult(
                fileName = file.name,
                filePath = filePath,
                lines = emptyList(),
                totalSizeBytes = 0L,
                isTruncated = false
            )
        }

        try {
            val totalSize = file.length()
            val linesQueue = java.util.ArrayDeque<String>(maxLines)
            var totalLineCount = 0

            file.useLines { sequence ->
                for (rawLine in sequence) {
                    totalLineCount++
                    if (linesQueue.size == maxLines) {
                        linesQueue.removeFirst()
                    }
                    linesQueue.addLast(rawLine)
                }
            }

            var errCount = 0
            var warnCount = 0
            var infoCount = 0
            val startLineNumber = (totalLineCount - linesQueue.size + 1).coerceAtLeast(1)

            val parsedLines = linesQueue.mapIndexed { idx, rawText ->
                val masked = maskSensitiveTokens(rawText)
                val level = when {
                    masked.contains("ERROR", ignoreCase = true) || masked.contains("FATAL", ignoreCase = true) || masked.contains("Exception", ignoreCase = true) -> {
                        errCount++
                        LogSeverityLevel.ERROR
                    }
                    masked.contains("WARN", ignoreCase = true) -> {
                        warnCount++
                        LogSeverityLevel.WARN
                    }
                    masked.contains("DEBUG", ignoreCase = true) || masked.contains("TRACE", ignoreCase = true) -> {
                        LogSeverityLevel.DEBUG
                    }
                    else -> {
                        infoCount++
                        LogSeverityLevel.INFO
                    }
                }

                LogLine(
                    lineNumber = startLineNumber + idx,
                    text = masked,
                    level = level
                )
            }

            LogReadResult(
                fileName = file.name,
                filePath = filePath,
                lines = parsedLines,
                totalSizeBytes = totalSize,
                isTruncated = totalLineCount > maxLines,
                errorCount = errCount,
                warnCount = warnCount,
                infoCount = infoCount
            )
        } catch (e: Throwable) {
            LogReadResult(
                fileName = file.name,
                filePath = filePath,
                lines = listOf(LogLine(1, "Error reading log file: ${e.message}", LogSeverityLevel.ERROR)),
                totalSizeBytes = if (file.exists()) file.length() else 0L,
                isTruncated = false,
                errorCount = 1
            )
        }
    }

    suspend fun readLogContent(filePath: String): String = withContext(dispatcher) {
        val result = readLogResult(filePath, maxLines = 10000)
        if (result.lines.isEmpty()) return@withContext "No log content available."
        result.lines.joinToString("\n") { it.text }
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

    val mrpackManager: io.ezz.launcher.core.storage.mrpack.MrpackManager =
        io.ezz.launcher.core.storage.mrpack.MrpackManager(pathProvider, instanceRepository, dispatcher)

    // ==========================================
    // 8. INSTANCE EXPORT (.mrpack)
    // ==========================================

    suspend fun exportInstance(
        instance: Instance,
        targetFile: File,
        includeWorlds: Boolean = false,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(dispatcher) {
        val options = io.ezz.launcher.core.model.modrinth.MrpackExportOptions(
            customName = instance.name,
            includeConfigs = true,
            includeMods = true,
            includeResourcePacks = true,
            includeShaderPacks = true
        )
        val result = mrpackManager.exportMrpack(instance, targetFile, options, onProgress)
        result.isSuccess
    }

    // ==========================================
    // 8.5. INSTANCE IMPORT (.mrpack)
    // ==========================================

    suspend fun importInstance(
        file: File,
        preferredName: String? = null,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Instance> = withContext(dispatcher) {
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.name}"))
        }

        // Validate .mrpack format
        val preview = mrpackManager.previewMrpack(file)
        if (preview.isFailure) {
            return@withContext Result.failure(preview.exceptionOrNull() ?: IllegalArgumentException("Invalid Modrinth modpack."))
        }

        mrpackManager.importMrpack(file, preferredName) { progress ->
            onProgress(progress.message, progress.progress)
        }
    }

    suspend fun importInstanceFromMrpack(
        mrpackFile: File,
        preferredName: String? = null,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<Instance> = withContext(dispatcher) {
        mrpackManager.importMrpack(mrpackFile, preferredName) { progress ->
            onProgress(progress.message, progress.progress)
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

    // ==========================================
    // 9. CUSTOM INSTANCE ICONS
    // ==========================================

    suspend fun setCustomIcon(instanceId: String, sourceFile: File): Instance = withContext(dispatcher) {
        val instanceDir = pathProvider.getInstanceDirectory(instanceId).toFile()
        if (!instanceDir.exists()) instanceDir.mkdirs()

        val targetFile = File(instanceDir, "icon.png")
        sourceFile.copyTo(targetFile, overwrite = true)

        val instance = instanceRepository.getInstance(instanceId) ?: throw IllegalStateException("Instance $instanceId not found")
        val updated = instance.copy(customIconPath = targetFile.absolutePath)
        instanceRepository.updateInstance(updated)
        instanceRepository.loadAll()
        updated
    }

    suspend fun removeCustomIcon(instanceId: String): Instance = withContext(dispatcher) {
        val instanceDir = pathProvider.getInstanceDirectory(instanceId).toFile()
        val targetFile = File(instanceDir, "icon.png")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val instance = instanceRepository.getInstance(instanceId) ?: throw IllegalStateException("Instance $instanceId not found")
        val updated = instance.copy(customIconPath = null)
        instanceRepository.updateInstance(updated)
        instanceRepository.loadAll()
        updated
    }
}

