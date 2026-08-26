package io.ezz.launcher.core.network.modrinth

import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.ModrinthContentType
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthSearchResponse
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ModUpdateCandidate
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class ModrinthService(
    private val client: HttpClient = HttpClientFactory.create()
) {
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val baseUrl = "https://api.modrinth.com/v2"
    private val userAgent = "Krysoldev/Ezz-Launcher/1.0.0 (admin@ezzlauncher.com)"

    /**
     * Search Modrinth projects with faceted filters.
     */
    suspend fun searchProjects(
        query: String,
        contentType: ModrinthContentType = ModrinthContentType.MOD,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null,
        categories: List<String>? = null,
        index: String = "relevance", // "relevance", "downloads", "follows", "newest", "updated"
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse = withContext(Dispatchers.IO) {
        try {
            val facetGroups = mutableListOf<List<String>>()

            // Content Type facet
            facetGroups.add(listOf("project_type:${contentType.apiValue}"))

            // Loader facets (e.g., categories:fabric)
            if (!loaders.isNullOrEmpty()) {
                val loaderFacets = loaders.map { "categories:${it.lowercase()}" }
                facetGroups.add(loaderFacets)
            }

            // Game version facets (e.g., versions:1.21.1)
            if (!gameVersions.isNullOrEmpty()) {
                val versionFacets = gameVersions.map { "versions:$it" }
                facetGroups.add(versionFacets)
            }

            // Extra categories
            if (!categories.isNullOrEmpty()) {
                categories.forEach { cat ->
                    facetGroups.add(listOf("categories:$cat"))
                }
            }

            val facetsJson = json.encodeToString(facetGroups)

            val response = client.get("$baseUrl/search") {
                header("User-Agent", userAgent)
                parameter("query", query.trim())
                parameter("facets", facetsJson)
                parameter("index", index)
                parameter("offset", offset)
                parameter("limit", limit)
            }

            if (response.status.isSuccess()) {
                response.body<ModrinthSearchResponse>()
            } else {
                ModrinthSearchResponse()
            }
        } catch (e: Throwable) {
            println("Modrinth search warning: ${e.message}")
            ModrinthSearchResponse()
        }
    }

    /**
     * Get full project details by project ID or slug.
     */
    suspend fun getProject(projectIdOrSlug: String): ModrinthProjectHit? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$baseUrl/project/$projectIdOrSlug") {
                header("User-Agent", userAgent)
            }
            if (response.status.isSuccess()) {
                response.body<ModrinthProjectHit>()
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Get compatible versions for a specific project.
     */
    suspend fun getProjectVersions(
        projectIdOrSlug: String,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null
    ): List<ModrinthVersion> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$baseUrl/project/$projectIdOrSlug/version") {
                header("User-Agent", userAgent)
                if (!loaders.isNullOrEmpty()) {
                    parameter("loaders", json.encodeToString(loaders.map { it.lowercase() }))
                }
                if (!gameVersions.isNullOrEmpty()) {
                    parameter("game_versions", json.encodeToString(gameVersions))
                }
            }
            if (response.status.isSuccess()) {
                response.body<List<ModrinthVersion>>()
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Get version metadata by version ID.
     */
    suspend fun getVersion(versionId: String): ModrinthVersion? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$baseUrl/version/$versionId") {
                header("User-Agent", userAgent)
            }
            if (response.status.isSuccess()) {
                response.body<ModrinthVersion>()
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Stream download content file with progress reporting.
     */
    suspend fun downloadContent(
        url: String,
        targetFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")

            val response = client.get(url) {
                header("User-Agent", userAgent)
            }

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
            println("Download error from Modrinth: ${e.message}")
            false
        }
    }

    /**
     * Check for updates for local installed mods.
     */
    suspend fun checkForUpdates(
        installedMods: List<LocalMod>,
        gameVersion: String,
        loader: String
    ): List<ModUpdateCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ModUpdateCandidate>()
        for (mod in installedMods) {
            if (!mod.enabled) continue
            try {
                // Search Modrinth by mod name
                val searchRes = searchProjects(
                    query = mod.name,
                    contentType = ModrinthContentType.MOD,
                    loaders = listOf(loader),
                    gameVersions = listOf(gameVersion),
                    limit = 3
                )
                val exactHit = searchRes.hits.firstOrNull { hit ->
                    hit.title.equals(mod.name, ignoreCase = true) ||
                            hit.slug.equals(mod.id, ignoreCase = true)
                } ?: searchRes.hits.firstOrNull()

                if (exactHit != null) {
                    val versions = getProjectVersions(exactHit.projectId, listOf(loader), listOf(gameVersion))
                    val latest = versions.firstOrNull()
                    if (latest != null && latest.versionNumber != mod.version) {
                        candidates.add(ModUpdateCandidate(mod, latest, exactHit.title))
                    }
                }
            } catch (_: Throwable) {}
        }
        candidates
    }
}
