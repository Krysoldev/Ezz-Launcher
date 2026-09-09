package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.ModMetadata
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.toLocalMod
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
        if (fileName.startsWith("ezz-skin-mod", ignoreCase = true) || fileName.contains("ezzskin", ignoreCase = true)) {
            println("[LocalModScanner] Prevented deletion of protected launcher-integrated mod: $fileName")
            return@withContext false
        }
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

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
        private val singleModCache = java.util.concurrent.ConcurrentHashMap<String, LocalMod>()

        fun scanSingleMod(file: File, instanceId: String = ""): LocalMod {
            val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
            return singleModCache.getOrPut(key) {
                parseModFile(instanceId, file).toLocalMod()
            }
        }

        fun parseModFile(instanceId: String, file: File): ModMetadata {
            val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
            val cleanName = file.name.removeSuffix(".disabled").removeSuffix(".jar")
            var modId = cleanName.lowercase().replace(" ", "-")
            var modName = cleanName
            var version = "1.0.0"
            var description = "Minecraft Mod"
            var loader = "FABRIC"
            val authors = mutableListOf<String>()
            val dependencies = mutableMapOf<String, String>()
            val breaks = mutableMapOf<String, String>()
            val conflicts = mutableMapOf<String, String>()
            val recommends = mutableMapOf<String, String>()
            val suggests = mutableMapOf<String, String>()

            try {
                ZipFile(file).use { zip ->
                    // 1. Try fabric.mod.json / quilt.mod.json
                    val fabricEntry = zip.getEntry("fabric.mod.json") ?: zip.getEntry("quilt.mod.json")
                    if (fabricEntry != null) {
                        loader = if (zip.getEntry("quilt.mod.json") != null && zip.getEntry("fabric.mod.json") == null) "QUILT" else "FABRIC"
                        val content = zip.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                        try {
                            val jsonObj = jsonParser.parseToJsonElement(content).jsonObject
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
                            jsonObj["recommends"]?.let { elem ->
                                if (elem is kotlinx.serialization.json.JsonObject) {
                                    elem.forEach { (k, v) ->
                                        val constraint = when (v) {
                                            is kotlinx.serialization.json.JsonPrimitive -> v.content
                                            is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(" ")
                                            else -> "*"
                                        }
                                        recommends[k.lowercase()] = constraint
                                    }
                                }
                            }
                            jsonObj["suggests"]?.let { elem ->
                                if (elem is kotlinx.serialization.json.JsonObject) {
                                    elem.forEach { (k, v) ->
                                        val constraint = when (v) {
                                            is kotlinx.serialization.json.JsonPrimitive -> v.content
                                            is kotlinx.serialization.json.JsonArray -> v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(" ")
                                            else -> "*"
                                        }
                                        suggests[k.lowercase()] = constraint
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore parsing error
                        }
                    } else {
                        // 2. Try META-INF/neoforge.mods.toml or META-INF/mods.toml
                        val neoForgeEntry = zip.getEntry("META-INF/neoforge.mods.toml")
                        val forgeEntry = zip.getEntry("META-INF/mods.toml")
                        val tomlEntry = neoForgeEntry ?: forgeEntry
                        if (tomlEntry != null) {
                            loader = if (neoForgeEntry != null) "NEOFORGE" else "FORGE"
                            val lines = zip.getInputStream(tomlEntry).bufferedReader().use { it.readLines() }
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.startsWith("modId=", ignoreCase = true) || trimmed.startsWith("modId =", ignoreCase = true)) {
                                    val idVal = trimmed.substringAfter('=').trim().trim('"', '\'')
                                    if (idVal.isNotBlank() && modId == cleanName.lowercase().replace(" ", "-")) {
                                        modId = idVal
                                    }
                                } else if (trimmed.startsWith("displayName=", ignoreCase = true) || trimmed.startsWith("displayName =", ignoreCase = true)) {
                                    val nameVal = trimmed.substringAfter('=').trim().trim('"', '\'')
                                    if (nameVal.isNotBlank() && modName == cleanName) {
                                        modName = nameVal
                                    }
                                } else if (trimmed.startsWith("version=", ignoreCase = true) || trimmed.startsWith("version =", ignoreCase = true)) {
                                    val verVal = trimmed.substringAfter('=').trim().trim('"', '\'')
                                    if (verVal.isNotBlank() && !verVal.startsWith("\${") && version == "1.0.0") {
                                        version = verVal
                                    }
                                } else if (trimmed.startsWith("description=", ignoreCase = true) || trimmed.startsWith("description =", ignoreCase = true)) {
                                    val descVal = trimmed.substringAfter('=').trim().trim('"', '\'')
                                    if (descVal.isNotBlank()) {
                                        description = descVal
                                    }
                                }
                            }
                        } else {
                            // 3. Try mcmod.info (Forge legacy)
                            val mcmodEntry = zip.getEntry("mcmod.info")
                            if (mcmodEntry != null) {
                                loader = "FORGE"
                                val content = zip.getInputStream(mcmodEntry).bufferedReader().use { it.readText() }
                                try {
                                    val arr = jsonParser.parseToJsonElement(content).jsonArray
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
                    }
                    Unit
                }
            } catch (e: Throwable) {
                // Ignore zip read errors
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
                enabled = isEnabled,
                dependencies = dependencies,
                breaks = breaks,
                conflicts = conflicts,
                recommends = recommends,
                suggests = suggests
            )
        }
    }
}
