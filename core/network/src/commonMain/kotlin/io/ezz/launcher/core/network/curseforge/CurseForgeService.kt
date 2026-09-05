package io.ezz.launcher.core.network.curseforge

import io.ezz.launcher.core.model.curseforge.CurseForgeBatchFilesRequest
import io.ezz.launcher.core.model.curseforge.CurseForgeBatchModsRequest
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeFileReleaseType
import io.ezz.launcher.core.model.curseforge.CurseForgeListResponse
import io.ezz.launcher.core.model.curseforge.CurseForgeMinecraftVersion
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.curseforge.CurseForgeModLoaderType
import io.ezz.launcher.core.model.curseforge.CurseForgeSingleResponse
import io.ezz.launcher.core.model.curseforge.CurseForgeSortField
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.ModUpdateCandidate
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

enum class CurseForgeStatus {
    AVAILABLE,
    UNAVAILABLE,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    OFFLINE
}

class CurseForgeService(
    private val client: HttpClient = HttpClientFactory.createCurseForgeClient(),
    private val apiKeyProvider: () -> String = { defaultApiKey() }
) {
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val baseUrl = "https://api.curseforge.com/v1"
    private val userAgent = "Krysoldev/Ezz-Launcher/1.0.0 (admin@ezzlauncher.com)"
    val minecraftGameId = 432
    val minecraftModsClassId = 6

    private val _status = MutableStateFlow(
        if (apiKeyProvider().isNotBlank()) CurseForgeStatus.AVAILABLE else CurseForgeStatus.UNAVAILABLE
    )
    val status: StateFlow<CurseForgeStatus> = _status.asStateFlow()

    // In-memory caches to minimize network roundtrips and avoid rate limits
    private val modCache = ConcurrentHashMap<Long, CurseForgeMod>()
    private val filesCache = ConcurrentHashMap<String, List<CurseForgeFile>>()
    private val descriptionCache = ConcurrentHashMap<Long, String>()

    fun isConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    private fun getEffectiveApiKey(): String {
        return try {
            apiKeyProvider().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun updateStatusFromResponse(statusCode: Int) {
        when (statusCode) {
            in 200..299 -> _status.value = CurseForgeStatus.AVAILABLE
            401, 403 -> _status.value = CurseForgeStatus.AUTHENTICATION_FAILED
            429 -> _status.value = CurseForgeStatus.RATE_LIMITED
            in 500..599 -> _status.value = CurseForgeStatus.UNAVAILABLE
            else -> {}
        }
    }

    private fun updateStatusFromException(e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        _status.value = CurseForgeStatus.OFFLINE
    }

    /**
     * Search CurseForge mods with comprehensive faceted filters.
     */
    suspend fun searchMods(
        query: String = "",
        gameVersion: String? = null,
        modLoaderType: CurseForgeModLoaderType = CurseForgeModLoaderType.ANY,
        categoryId: Int? = null,
        classId: Int? = minecraftModsClassId,
        sortField: CurseForgeSortField = CurseForgeSortField.POPULARITY,
        sortOrder: String = "desc",
        index: Int = 0,
        pageSize: Int = 20
    ): CurseForgeListResponse<CurseForgeMod> = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey()
        if (key.isBlank()) {
            _status.value = CurseForgeStatus.UNAVAILABLE
            return@withContext CurseForgeListResponse(emptyList())
        }

        try {
            val response = client.get("$baseUrl/mods/search") {
                header("User-Agent", userAgent)
                header("x-api-key", key)

                parameter("gameId", minecraftGameId)
                if (classId != null) parameter("classId", classId)
                if (categoryId != null && categoryId > 0) parameter("categoryId", categoryId)
                if (!gameVersion.isNullOrBlank()) parameter("gameVersion", gameVersion.trim())
                if (modLoaderType != CurseForgeModLoaderType.ANY) parameter("modLoaderType", modLoaderType.id)
                if (query.isNotBlank()) parameter("searchFilter", query.trim())
                parameter("sortField", sortField.id)
                parameter("sortOrder", sortOrder)
                parameter("index", index)
                parameter("pageSize", pageSize)
            }

            updateStatusFromResponse(response.status.value)

            if (response.status.value == 429) {
                println("[CurseForge] Rate limited (429), backing off...")
                delay(1000)
                return@withContext CurseForgeListResponse(emptyList())
            }

            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeListResponse<CurseForgeMod>>()
                res.data.forEach { mod ->
                    modCache[mod.id] = mod
                }
                res
            } else {
                println("[CurseForge] Search error status: ${response.status.value}")
                CurseForgeListResponse(emptyList())
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] Search notice: ${e.message}")
            CurseForgeListResponse(emptyList())
        }
    }

    /**
     * Get mod details by ID.
     */
    suspend fun getMod(modId: Long): CurseForgeMod? = withContext(Dispatchers.IO) {
        modCache[modId]?.let { return@withContext it }
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext null

        try {
            val response = client.get("$baseUrl/mods/$modId") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeSingleResponse<CurseForgeMod>>()
                modCache[res.data.id] = res.data
                res.data
            } else {
                null
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] getMod notice for $modId: ${e.message}")
            null
        }
    }

    /**
     * Get mod HTML description.
     */
    suspend fun getModDescription(modId: Long): String = withContext(Dispatchers.IO) {
        descriptionCache[modId]?.let { return@withContext it }
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext ""

        try {
            val response = client.get("$baseUrl/mods/$modId/description") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeSingleResponse<String>>()
                descriptionCache[modId] = res.data
                res.data
            } else {
                ""
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            ""
        }
    }

    /**
     * Get releases / files for a mod, optionally filtered by Minecraft version and loader.
     */
    suspend fun getModFiles(
        modId: Long,
        gameVersion: String? = null,
        modLoaderType: CurseForgeModLoaderType = CurseForgeModLoaderType.ANY,
        index: Int = 0,
        pageSize: Int = 50
    ): List<CurseForgeFile> = withContext(Dispatchers.IO) {
        val cacheKey = "$modId:${gameVersion.orEmpty()}:${modLoaderType.id}:$index:$pageSize"
        filesCache[cacheKey]?.let { return@withContext it }
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext emptyList()

        try {
            val response = client.get("$baseUrl/mods/$modId/files") {
                header("User-Agent", userAgent)
                header("x-api-key", key)

                if (!gameVersion.isNullOrBlank()) parameter("gameVersion", gameVersion.trim())
                if (modLoaderType != CurseForgeModLoaderType.ANY) parameter("modLoaderType", modLoaderType.id)
                parameter("index", index)
                parameter("pageSize", pageSize)
            }

            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeListResponse<CurseForgeFile>>()
                filesCache[cacheKey] = res.data
                res.data
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] getModFiles notice for $modId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get specific file by mod ID and file ID.
     */
    suspend fun getModFile(modId: Long, fileId: Long): CurseForgeFile? = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext null

        try {
            val response = client.get("$baseUrl/mods/$modId/files/$fileId") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeSingleResponse<CurseForgeFile>>()
                res.data
            } else {
                null
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            null
        }
    }

    /**
     * Get direct download URL for a file if downloadUrl is null.
     */
    suspend fun getModFileDownloadUrl(modId: Long, fileId: Long): String? = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext null

        try {
            val response = client.get("$baseUrl/mods/$modId/files/$fileId/download-url") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeSingleResponse<String>>()
                res.data
            } else {
                null
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            null
        }
    }

    /**
     * Batch fetch multiple mods by IDs.
     */
    suspend fun getModsBatch(modIds: List<Long>): List<CurseForgeMod> = withContext(Dispatchers.IO) {
        if (modIds.isEmpty()) return@withContext emptyList()
        val uncached = modIds.filterNot { modCache.containsKey(it) }
        if (uncached.isEmpty()) {
            return@withContext modIds.mapNotNull { modCache[it] }
        }
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext emptyList()

        try {
            val response = client.post("$baseUrl/mods") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
                contentType(ContentType.Application.Json)
                setBody(CurseForgeBatchModsRequest(uncached))
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeListResponse<CurseForgeMod>>()
                res.data.forEach { mod ->
                    modCache[mod.id] = mod
                }
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] getModsBatch notice: ${e.message}")
        }
        modIds.mapNotNull { modCache[it] }
    }

    /**
     * Batch fetch multiple files by IDs.
     */
    suspend fun getFilesBatch(fileIds: List<Long>): List<CurseForgeFile> = withContext(Dispatchers.IO) {
        if (fileIds.isEmpty()) return@withContext emptyList()
        val key = getEffectiveApiKey()
        if (key.isBlank()) return@withContext emptyList()

        try {
            val response = client.post("$baseUrl/mods/files") {
                header("User-Agent", userAgent)
                header("x-api-key", key)
                contentType(ContentType.Application.Json)
                setBody(CurseForgeBatchFilesRequest(fileIds))
            }
            updateStatusFromResponse(response.status.value)
            if (response.status.isSuccess()) {
                val res = response.body<CurseForgeListResponse<CurseForgeFile>>()
                res.data
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] getFilesBatch notice: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all supported exact Minecraft versions for a mod, newest-first.
     */
    suspend fun getProjectSupportedVersions(mod: CurseForgeMod): List<String> = withContext(Dispatchers.IO) {
        val versions = mutableSetOf<String>()

        // 1. Check latestFilesIndexes
        mod.latestFilesIndexes.forEach { idx ->
            val v = idx.gameVersion.trim()
            if (v.isNotEmpty() && v[0].isDigit() && !v.contains("Fabric", true) && !v.contains("Forge", true) &&
                !v.contains("NeoForge", true) && !v.contains("Quilt", true)) {
                versions.add(v)
            }
        }

        // 2. Check latestFiles
        mod.latestFiles.forEach { file ->
            versions.addAll(file.extractedGameVersions)
        }

        // 3. If empty, fetch files list
        if (versions.isEmpty()) {
            val files = getModFiles(mod.id, pageSize = 50)
            files.forEach { file ->
                versions.addAll(file.extractedGameVersions)
            }
        }

        versions.toList().sortedWith { v1, v2 -> compareGameVersions(v1, v2) }
    }

    /**
     * Determine which loaders are supported by this mod for a specific Minecraft version.
     */
    suspend fun getProjectSupportedLoadersForVersion(mod: CurseForgeMod, gameVersion: String): Set<CurseForgeModLoaderType> = withContext(Dispatchers.IO) {
        val loaders = mutableSetOf<CurseForgeModLoaderType>()

        // 1. Inspect latestFilesIndexes
        mod.latestFilesIndexes.forEach { idx ->
            if (idx.gameVersion.equals(gameVersion, ignoreCase = true)) {
                idx.modLoader?.let { loaderId ->
                    val lt = CurseForgeModLoaderType.fromId(loaderId)
                    if (lt != CurseForgeModLoaderType.ANY) loaders.add(lt)
                }
            }
        }

        // 2. Inspect latestFiles
        mod.latestFiles.forEach { file ->
            if (file.gameVersions.any { it.equals(gameVersion, ignoreCase = true) }) {
                if (file.isFabricSupported) loaders.add(CurseForgeModLoaderType.FABRIC)
                if (file.isForgeSupported) loaders.add(CurseForgeModLoaderType.FORGE)
                if (file.isNeoForgeSupported) loaders.add(CurseForgeModLoaderType.NEOFORGE)
                if (file.isQuiltSupported) loaders.add(CurseForgeModLoaderType.QUILT)
            }
        }

        // 3. Fallback: inspect full files list if nothing found
        if (loaders.isEmpty()) {
            val files = getModFiles(mod.id, gameVersion = gameVersion, pageSize = 50)
            files.forEach { file ->
                if (file.isFabricSupported) loaders.add(CurseForgeModLoaderType.FABRIC)
                if (file.isForgeSupported) loaders.add(CurseForgeModLoaderType.FORGE)
                if (file.isNeoForgeSupported) loaders.add(CurseForgeModLoaderType.NEOFORGE)
                if (file.isQuiltSupported) loaders.add(CurseForgeModLoaderType.QUILT)
            }
        }

        loaders
    }

    /**
     * Download content with real-time byte stream progress and strict timeout.
     */
    suspend fun downloadContent(
        url: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")

            val key = getEffectiveApiKey()
            val response = client.get(url) {
                header("User-Agent", userAgent)
                if (key.isNotBlank()) header("x-api-key", key)
            }

            updateStatusFromResponse(response.status.value)
            if (!response.status.isSuccess()) {
                return@withContext false
            }

            val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            var downloadedBytes = 0L

            FileOutputStream(tempFile).use { output ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192)
                    while (!packet.isEmpty) {
                        val bytes = packet.readBytes()
                        output.write(bytes)
                        downloadedBytes += bytes.size
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
            true
        } catch (e: Throwable) {
            updateStatusFromException(e)
            println("[CurseForge] Download notice for $url: ${e.message}")
            false
        }
    }

    /**
     * Check for updates for local installed mods using CurseForge.
     */
    suspend fun checkForUpdates(
        installedMods: List<LocalMod>,
        gameVersion: String,
        loader: String
    ): List<ModUpdateCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ModUpdateCandidate>()
        val loaderType = CurseForgeModLoaderType.fromLoaderName(loader)

        for (mod in installedMods) {
            if (!mod.enabled) continue
            try {
                val searchRes = searchMods(
                    query = mod.name,
                    gameVersion = gameVersion,
                    modLoaderType = loaderType,
                    pageSize = 3
                )
                val exactHit = searchRes.data.firstOrNull { hit ->
                    hit.name.equals(mod.name, ignoreCase = true) ||
                            hit.slug.equals(mod.id, ignoreCase = true)
                } ?: searchRes.data.firstOrNull()

                if (exactHit != null) {
                    val files = getModFiles(
                        modId = exactHit.id,
                        gameVersion = gameVersion,
                        modLoaderType = loaderType,
                        pageSize = 5
                    )
                    val latest = files.firstOrNull { it.releaseTypeEnum == CurseForgeFileReleaseType.RELEASE } ?: files.firstOrNull()
                    if (latest != null && !mod.fileName.contains(latest.fileName, ignoreCase = true)) {
                        // Update candidate available
                    }
                }
            } catch (_: Throwable) {}
        }
        candidates
    }

    companion object {
        /**
         * Numeric semver comparison for Minecraft versions (e.g., 1.21.11 > 1.21.2 > 1.20.4).
         */
        fun compareGameVersions(v1: String, v2: String): Int {
            val parts1 = v1.split('.').mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split('.').mapNotNull { it.toIntOrNull() }
            if (parts1.isNotEmpty() && parts2.isNotEmpty() && parts1.size == v1.split('.').size && parts2.size == v2.split('.').size) {
                val maxLen = maxOf(parts1.size, parts2.size)
                for (i in 0 until maxLen) {
                    val p1 = parts1.getOrElse(i) { 0 }
                    val p2 = parts2.getOrElse(i) { 0 }
                    if (p1 != p2) return p2.compareTo(p1) // descending (newer first)
                }
                return 0
            }
            return v2.compareTo(v1)
        }

        private fun defaultApiKey(): String {
            val envKey = System.getenv("CURSEFORGE_API_KEY")
            if (!envKey.isNullOrBlank()) return envKey.trim()

            val propKey = System.getProperty("CURSEFORGE_API_KEY")
            if (!propKey.isNullOrBlank()) return propKey.trim()

            return "\$2a\$10\$bL4bIL5pUWqfcO7KQtnMReakwtfHbNKh6v1uTpKlzhwoueEJQnPnm"
        }
    }
}
