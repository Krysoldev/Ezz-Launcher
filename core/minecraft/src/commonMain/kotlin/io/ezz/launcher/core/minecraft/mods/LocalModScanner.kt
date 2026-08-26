package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.ModMetadata
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

class LocalModScanner(
    private val pathProvider: PathProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun scanMods(instanceId: String): List<ModMetadata> = withContext(dispatcher) {
        val instanceDir = pathProvider.getInstanceDirectory(instanceId)
        val modsDir = instanceDir.resolve(".minecraft").resolve("mods").toFile()
        if (!modsDir.exists() || !modsDir.isDirectory) {
            modsDir.mkdirs()
            return@withContext emptyList()
        }

        val files = modsDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".jar", ignoreCase = true) || file.name.endsWith(".jar.disabled", ignoreCase = true))
        } ?: return@withContext emptyList()

        files.mapNotNull { file ->
            try {
                parseModFile(instanceId, file)
            } catch (e: Throwable) {
                // Fallback for corrupted/unparseable jars
                val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
                val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".jar")
                ModMetadata(
                    id = cleanName.lowercase().replace(" ", "-"),
                    instanceId = instanceId,
                    name = cleanName,
                    version = "1.0.0",
                    fileName = file.name,
                    loader = "FABRIC",
                    description = "Local Mod",
                    fileSize = file.length(),
                    enabled = isEnabled
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseModFile(instanceId: String, file: File): ModMetadata {
        val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
        val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".jar")
        var modId = cleanName.lowercase().replace(" ", "-")
        var modName = cleanName
        var version = "1.0.0"
        var description = "Minecraft Mod"
        var loader = "FABRIC"
        val authors = mutableListOf<String>()

        ZipFile(file).use { zip ->
            // 1. Try fabric.mod.json / quilt.mod.json
            val fabricEntry = zip.getEntry("fabric.mod.json") ?: zip.getEntry("quilt.mod.json")
            if (fabricEntry != null) {
                loader = "FABRIC"
                val content = zip.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                try {
                    val jsonObj = json.parseToJsonElement(content).jsonObject
                    modId = jsonObj["id"]?.jsonPrimitive?.content ?: modId
                    modName = jsonObj["name"]?.jsonPrimitive?.content ?: modName
                    version = jsonObj["version"]?.jsonPrimitive?.content ?: version
                    description = jsonObj["description"]?.jsonPrimitive?.content ?: description
                    jsonObj["authors"]?.jsonArray?.forEach { elem ->
                        if (elem is kotlinx.serialization.json.JsonPrimitive) {
                            authors.add(elem.content)
                        } else if (elem is kotlinx.serialization.json.JsonObject) {
                            elem["name"]?.jsonPrimitive?.content?.let { n -> authors.add(n) }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore json parse error
                }
            } else {
                // 2. Try mcmod.info (Forge legacy)
                val mcmodEntry = zip.getEntry("mcmod.info")
                if (mcmodEntry != null) {
                    loader = "FORGE"
                    val content = zip.getInputStream(mcmodEntry).bufferedReader().use { it.readText() }
                    try {
                        val arr = json.parseToJsonElement(content).jsonArray
                        val first = arr.firstOrNull()?.jsonObject
                        if (first != null) {
                            modId = first["modid"]?.jsonPrimitive?.content ?: modId
                            modName = first["name"]?.jsonPrimitive?.content ?: modName
                            version = first["version"]?.jsonPrimitive?.content ?: version
                            description = first["description"]?.jsonPrimitive?.content ?: description
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            Unit
        }

        return ModMetadata(
            id = modId,
            instanceId = instanceId,
            name = modName,
            version = version,
            fileName = file.name,
            loader = loader,
            description = description,
            authors = authors,
            fileSize = file.length(),
            enabled = isEnabled
        )
    }

    suspend fun toggleMod(instanceId: String, fileName: String, enable: Boolean): String? = withContext(dispatcher) {
        val modsDir = pathProvider.getInstanceDirectory(instanceId).resolve(".minecraft").resolve("mods").toFile()
        val currentFile = File(modsDir, fileName)
        if (!currentFile.exists()) return@withContext null

        val targetFileName = if (enable) {
            fileName.removeSuffix(".disabled")
        } else {
            if (fileName.endsWith(".disabled", ignoreCase = true)) fileName else "$fileName.disabled"
        }

        val targetFile = File(modsDir, targetFileName)
        if (currentFile.renameTo(targetFile)) {
            targetFileName
        } else {
            null
        }
    }

    suspend fun deleteMod(instanceId: String, fileName: String): Boolean = withContext(dispatcher) {
        val modsDir = pathProvider.getInstanceDirectory(instanceId).resolve(".minecraft").resolve("mods").toFile()
        val file = File(modsDir, fileName)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun importMod(instanceId: String, sourcePath: java.nio.file.Path): Boolean = withContext(dispatcher) {
        val modsDir = pathProvider.getInstanceDirectory(instanceId).resolve(".minecraft").resolve("mods").toFile()
        if (!modsDir.exists()) modsDir.mkdirs()
        val target = File(modsDir, sourcePath.fileName.toString()).toPath()
        Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
        true
    }
}
