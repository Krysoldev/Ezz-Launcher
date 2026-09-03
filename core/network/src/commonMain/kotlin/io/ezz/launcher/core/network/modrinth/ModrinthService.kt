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

            // Content Type facet (strict filtering)
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
                val res = response.body<ModrinthSearchResponse>()
                // Double safety layer: ensure only requested content type is returned
                val filteredHits = res.hits.filter { it.projectType.equals(contentType.apiValue, ignoreCase = true) }
                res.copy(hits = filteredHits)
            } else {
                ModrinthSearchResponse()
            }
        } catch (e: Throwable) {
            println("Modrinth search warning: ${e.message}")
            ModrinthSearchResponse()
        }
    }

    /**
     * Search specifically for MODS.
     */
    suspend fun searchMods(
        query: String,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null,
        categories: List<String>? = null,
        index: String = "relevance",
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse {
        return searchProjects(
            query = query,
            contentType = ModrinthContentType.MOD,
            loaders = loaders,
            gameVersions = gameVersions,
            categories = categories,
            index = index,
            offset = offset,
            limit = limit
        )
    }

    /**
     * Search specifically for MODPACKS.
     */
    suspend fun searchModpacks(
        query: String,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null,
        categories: List<String>? = null,
        index: String = "relevance",
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse {
        return searchProjects(
            query = query,
            contentType = ModrinthContentType.MODPACK,
            loaders = loaders,
            gameVersions = gameVersions,
            categories = categories,
            index = index,
            offset = offset,
            limit = limit
        )
    }

    /**
     * Search specifically for RESOURCE PACKS.
     */
    suspend fun searchResourcePacks(
        query: String,
        gameVersions: List<String>? = null,
        categories: List<String>? = null,
        index: String = "relevance",
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse {
        return searchProjects(
            query = query,
            contentType = ModrinthContentType.RESOURCE_PACK,
            loaders = null, // Resource packs are loader-independent
            gameVersions = gameVersions,
            categories = categories,
            index = index,
            offset = offset,
            limit = limit
        )
    }

    /**
     * Search specifically for SHADERS.
     */
    suspend fun searchShaders(
        query: String,
        gameVersions: List<String>? = null,
        categories: List<String>? = null,
        index: String = "relevance",
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse {
        return searchProjects(
            query = query,
            contentType = ModrinthContentType.SHADER,
            loaders = null,
            gameVersions = gameVersions,
            categories = categories,
            index = index,
            offset = offset,
            limit = limit
        )
    }

    // In-memory caches to reduce redundant API queries
    private val projectCache = java.util.concurrent.ConcurrentHashMap<String, ModrinthProjectHit>()
    private val versionsCache = java.util.concurrent.ConcurrentHashMap<String, List<ModrinthVersion>>()
    private val allProjectVersionsCache = java.util.concurrent.ConcurrentHashMap<String, List<ModrinthVersion>>()

    fun clearCache() {
        projectCache.clear()
        versionsCache.clear()
        allProjectVersionsCache.clear()
    }

    /**
     * Get full project details by project ID or slug (with cache).
     */
    suspend fun getProject(projectIdOrSlug: String, forceRefresh: Boolean = false): ModrinthProjectHit? = withContext(Dispatchers.IO) {
        if (!forceRefresh && projectCache.containsKey(projectIdOrSlug)) {
            return@withContext projectCache[projectIdOrSlug]
        }
        try {
            val response = client.get("$baseUrl/project/$projectIdOrSlug") {
                header("User-Agent", userAgent)
            }
            if (response.status.isSuccess()) {
                val hit = response.body<ModrinthProjectHit>()
                projectCache[projectIdOrSlug] = hit
                projectCache[hit.projectId] = hit
                if (hit.slug.isNotBlank()) projectCache[hit.slug] = hit
                hit
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
        gameVersions: List<String>? = null,
        forceRefresh: Boolean = false
    ): List<ModrinthVersion> = withContext(Dispatchers.IO) {
        val cacheKey = "$projectIdOrSlug|${loaders?.sorted()?.joinToString(",")}|${gameVersions?.sorted()?.joinToString(",")}"
        if (!forceRefresh && versionsCache.containsKey(cacheKey)) {
            return@withContext versionsCache[cacheKey] ?: emptyList()
        }
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
                val list = response.body<List<ModrinthVersion>>()
                versionsCache[cacheKey] = list
                if (loaders.isNullOrEmpty() && gameVersions.isNullOrEmpty()) {
                    allProjectVersionsCache[projectIdOrSlug] = list
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Extract all unique supported exact Minecraft versions for a project, sorted newest first.
     */
    suspend fun getProjectSupportedVersions(
        projectIdOrSlug: String,
        forceRefresh: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val allVersions = if (!forceRefresh && allProjectVersionsCache.containsKey(projectIdOrSlug)) {
            allProjectVersionsCache[projectIdOrSlug] ?: emptyList()
        } else {
            getProjectVersions(projectIdOrSlug, loaders = null, gameVersions = null, forceRefresh = forceRefresh)
        }

        val gameVersionsSet = mutableSetOf<String>()
        allVersions.forEach { ver ->
            gameVersionsSet.addAll(ver.gameVersions)
        }

        gameVersionsSet.sortedWith { v1, v2 ->
            compareGameVersions(v1, v2)
        }
    }

    /**
     * Extract all supported mod loaders for a specific Minecraft version of a project.
     */
    suspend fun getProjectSupportedLoadersForVersion(
        projectIdOrSlug: String,
        gameVersion: String,
        forceRefresh: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val versionsForMc = getProjectVersions(
            projectIdOrSlug = projectIdOrSlug,
            loaders = null,
            gameVersions = listOf(gameVersion),
            forceRefresh = forceRefresh
        )

        val loaders = mutableSetOf<String>()
        versionsForMc.forEach { ver ->
            loaders.addAll(ver.loaders.map { it.lowercase() })
        }
        loaders.sorted()
    }

    /**
     * Recursively resolve dependencies for a version (queries project info and matching version for target MC version and loader).
     * Handles transitive dependency trees, cycle detection, and constraint evaluation.
     */
    /**
     * Recursively resolves dependency projects and installable versions for a Modrinth version.
     * Evaluates candidate versions against the entire active instance graph (all installed mods
     * + queued mods) to prevent installing mutually incompatible versions.
     */
    suspend fun resolveDependencies(
        dependencies: List<io.ezz.launcher.core.model.modrinth.ModrinthDependency>,
        gameVersion: String,
        loader: String,
        installedMods: List<io.ezz.launcher.core.model.instance.LocalMod> = emptyList(),
        installedModIds: Set<String> = emptySet(),
        rootProjectTitle: String = "Requested Mod",
        rootVersionNumber: String? = null,
        rootProjectIdOrSlug: String? = null
    ): List<io.ezz.launcher.core.model.modrinth.ResolvedModDependency> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<io.ezz.launcher.core.model.modrinth.ResolvedModDependency>()
        val visitedProjects = mutableSetOf<String>()
        val activeInstalled = installedMods.filter { it.enabled }
        val effectiveInstalledIds = (installedModIds + activeInstalled.map { it.id.lowercase() } + activeInstalled.map { it.name.lowercase() }).toMutableSet()

        // Active graph tracks all mods in the instance + queued mods with their versions, conflicts, and breaks
        data class GraphNode(
            val id: String,
            val name: String,
            val version: String,
            val aliases: Set<String>,
            val dependencies: Map<String, String> = emptyMap(),
            val conflicts: Map<String, String> = emptyMap(),
            val breaks: Map<String, String> = emptyMap()
        )

        val activeGraph = mutableListOf<GraphNode>()

        // 1. Add active installed mods to active graph
        for (m in activeInstalled) {
            val aliases = setOf(
                m.id.lowercase(),
                m.name.lowercase(),
                m.name.lowercase().replace(" ", "-"),
                m.name.lowercase().replace(" ", "_")
            )
            activeGraph.add(
                GraphNode(
                    id = m.id,
                    name = m.name,
                    version = m.version,
                    aliases = aliases,
                    dependencies = m.dependencies,
                    conflicts = m.conflicts,
                    breaks = m.breaks
                )
            )
        }

        // 2. Add root mod being installed to active graph
        if (rootProjectIdOrSlug != null && rootVersionNumber != null) {
            val aliases = setOf(
                rootProjectIdOrSlug.lowercase(),
                rootProjectTitle.lowercase(),
                rootProjectTitle.lowercase().replace(" ", "-"),
                rootProjectTitle.lowercase().replace(" ", "_")
            )
            activeGraph.add(
                GraphNode(
                    id = rootProjectIdOrSlug,
                    name = rootProjectTitle,
                    version = rootVersionNumber,
                    aliases = aliases
                )
            )
        }

        val queue = ArrayDeque<Pair<io.ezz.launcher.core.model.modrinth.ModrinthDependency, String>>() // dependency to parentTitle
        for (dep in dependencies) {
            queue.add(dep to rootProjectTitle)
        }

        while (queue.isNotEmpty()) {
            val (dep, parentTitle) = queue.removeFirst()
            val isRequired = dep.dependencyType.equals("required", ignoreCase = true)
            val isOptional = dep.dependencyType.equals("optional", ignoreCase = true)
            if (!isRequired && !isOptional) continue // skip embedded/incompatible

            var targetProjectId = dep.projectId
            var specificVersion: io.ezz.launcher.core.model.modrinth.ModrinthVersion? = null
            val verId = dep.versionId
            if (verId != null) {
                specificVersion = getVersion(verId)
                if (targetProjectId == null && specificVersion != null) {
                    targetProjectId = specificVersion.projectId
                }
            }

            val depProjId = targetProjectId ?: specificVersion?.projectId ?: continue
            val normId = depProjId.lowercase().trim()
            if (visitedProjects.contains(normId)) continue
            visitedProjects.add(normId)

            val projectHit = getProject(depProjId)
            val cleanSlug = projectHit?.slug?.lowercase() ?: normId
            val cleanTitle = projectHit?.title?.lowercase() ?: normId
            visitedProjects.add(cleanSlug)
            visitedProjects.add(cleanTitle)

            val depAliases = setOf(
                normId,
                cleanSlug,
                cleanTitle,
                cleanTitle.replace(" ", "-"),
                cleanTitle.replace(" ", "_")
            )

            val alreadyInstalledMod = activeInstalled.firstOrNull { instMod ->
                val instAliases = setOf(
                    instMod.id.lowercase(),
                    instMod.name.lowercase(),
                    instMod.name.lowercase().replace(" ", "-")
                )
                instAliases.any { depAliases.contains(it) } || instMod.fileName.lowercase().startsWith(cleanSlug)
            }
            val isAlreadyInstalled = alreadyInstalledMod != null || effectiveInstalledIds.any { depAliases.contains(it) }

            // Find matching version for the dependency using full-graph candidate search & backtracking
            val matchingVersion = specificVersion ?: run {
                val depVersions = getProjectVersions(
                    projectIdOrSlug = depProjId,
                    loaders = if (loader.isNotBlank()) listOf(loader) else null,
                    gameVersions = if (gameVersion.isNotBlank()) listOf(gameVersion) else null
                )

                // Sort candidates: Release > Beta > Alpha, newest version first
                val sortedCandidates = depVersions.sortedWith { v1, v2 ->
                    val type1 = when (v1.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
                    val type2 = when (v2.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
                    if (type1 != type2) type1.compareTo(type2)
                    else compareVersionParts(v2.versionNumber, v1.versionNumber)
                }

                println("[Resolver] Evaluating ${sortedCandidates.size} candidates for dependency ${projectHit?.title ?: depProjId}...")

                // Backtrack through candidates to find one that satisfies all active graph constraints
                sortedCandidates.firstOrNull { candidate ->
                    val candVer = candidate.versionNumber
                    println("[Resolver] Testing candidate $candVer for ${projectHit?.title ?: depProjId}...")

                    // 1. Candidate must not declare incompatible dependency with any mod in active graph
                    val candIncompatible = candidate.dependencies.any { d ->
                        d.dependencyType.equals("incompatible", ignoreCase = true) &&
                        activeGraph.any { node -> node.aliases.contains(d.projectId?.lowercase() ?: "") }
                    }
                    if (candIncompatible) {
                        println("[Resolver] Candidate $candVer REJECTED: declares incompatible dependency with active mod")
                        return@firstOrNull false
                    }

                    // 2. Candidate must not conflict with or break on any mod in active graph
                    for (node in activeGraph) {
                        // Check if node has conflict on this candidate
                        val conflictConstraint = node.conflicts.entries.firstOrNull { entry ->
                            depAliases.contains(entry.key.lowercase()) ||
                            depAliases.contains(entry.key.lowercase().replace("-", "_")) ||
                            depAliases.contains(entry.key.lowercase().replace("_", "-"))
                        }?.value

                        if (conflictConstraint != null && (conflictConstraint == "*" || isConstraintSatisfied(candVer, conflictConstraint))) {
                            println("[Resolver] Candidate $candVer REJECTED: node '${node.name}' has conflict $conflictConstraint")
                            return@firstOrNull false
                        }

                        // Check if node breaks on this candidate
                        val breakConstraint = node.breaks.entries.firstOrNull { entry ->
                            depAliases.contains(entry.key.lowercase()) ||
                            depAliases.contains(entry.key.lowercase().replace("-", "_")) ||
                            depAliases.contains(entry.key.lowercase().replace("_", "-"))
                        }?.value

                        if (breakConstraint != null && isConstraintSatisfied(candVer, breakConstraint)) {
                            println("[Resolver] Candidate $candVer REJECTED: node '${node.name}' breaks on candidate $breakConstraint")
                            return@firstOrNull false
                        }

                        // Check if node has a dependency requirement on this mod (e.g. Iris requiring Sodium >=0.8.13 <0.9)
                        val depConstraint = node.dependencies.entries.firstOrNull { entry ->
                            depAliases.contains(entry.key.lowercase()) ||
                            depAliases.contains(entry.key.lowercase().replace("-", "_")) ||
                            depAliases.contains(entry.key.lowercase().replace("_", "-"))
                        }?.value

                        if (depConstraint != null && depConstraint != "*" && depConstraint.isNotBlank()) {
                            if (!isConstraintSatisfied(candVer, depConstraint)) {
                                println("[Resolver] Candidate $candVer REJECTED: node '${node.name}' requires $depConstraint")
                                return@firstOrNull false
                            }
                        }
                    }

                    println("[Resolver] Candidate $candVer ACCEPTED for ${projectHit?.title ?: depProjId}")
                    true
                }
            }

            val failureReason = if (isRequired && !isAlreadyInstalled && matchingVersion == null) {
                "No compatible release of ${projectHit?.title ?: depProjId} found satisfying all instance mod constraints for Minecraft $gameVersion ($loader)"
            } else null

            val item = io.ezz.launcher.core.model.modrinth.ResolvedModDependency(
                dependency = dep,
                project = projectHit,
                version = matchingVersion,
                isRequired = isRequired,
                isAlreadyInstalled = isAlreadyInstalled,
                installedVersion = alreadyInstalledMod?.version,
                selectedToInstall = isRequired && !isAlreadyInstalled && matchingVersion != null,
                requiredBy = parentTitle,
                failureReason = failureReason
            )
            resolved.add(item)

            // If accepted, add to active graph for downstream sub-dependencies
            if (matchingVersion != null) {
                activeGraph.add(
                    GraphNode(
                        id = depProjId,
                        name = projectHit?.title ?: depProjId,
                        version = matchingVersion.versionNumber,
                        aliases = depAliases
                    )
                )
            }

            // RECURSION: If this is a required dependency that we need to install, enqueue its own dependencies!
            if (isRequired && !isAlreadyInstalled && matchingVersion != null) {
                val childDeps = matchingVersion.dependencies
                for (child in childDeps) {
                    queue.add(child to (projectHit?.title ?: matchingVersion.name))
                }
            }
        }
        resolved
    }

    companion object {
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
     * Download an image and save to disk.
     */
    suspend fun downloadImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) {
                header("User-Agent", userAgent)
            }
            if (response.status.isSuccess()) {
                response.body<ByteArray>()
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
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
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
                val searchRes = searchMods(
                    query = mod.name,
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

    private fun compareVersionParts(v1: String, v2: String): Int {
        val clean1 = v1.substringBefore('+').replace(Regex("""^[a-zA-Z\-_]+[-_]"""), "").trim()
        val clean2 = v2.substringBefore('+').replace(Regex("""^[a-zA-Z\-_]+[-_]"""), "").trim()
        val p1 = clean1.filter { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
        val p2 = clean2.filter { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2) // standard ascending (>0 if v1 > v2)
        }
        return clean1.compareTo(clean2)
    }

    private fun isConstraintSatisfied(versionStr: String, constraint: String): Boolean {
        val cleanVer = versionStr.substringBefore('+').replace(Regex("""^[a-zA-Z\-_]+[-_]"""), "").trim()
        val c = constraint.trim()
        if (c.isBlank() || c == "*") return true

        val parts = c.split(Regex("""[,\s]+""")).filter { it.isNotBlank() }
        if (parts.size > 1) {
            return parts.all { isConstraintSatisfied(cleanVer, it) }
        }

        if (c.startsWith(">=")) return compareVersionParts(cleanVer, c.drop(2).trim()) >= 0
        if (c.startsWith("<=")) return compareVersionParts(cleanVer, c.drop(2).trim()) <= 0
        if (c.startsWith(">")) return compareVersionParts(cleanVer, c.drop(1).trim()) > 0
        if (c.startsWith("<")) return compareVersionParts(cleanVer, c.drop(1).trim()) < 0
        if (c.startsWith("=")) return compareVersionParts(cleanVer, c.drop(1).trim()) == 0

        return cleanVer.equals(c, ignoreCase = true)
    }

    private fun compareVersionStrings(v1: String, v2: String): Int {
        val p1 = v1.filter { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
        val p2 = v2.filter { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num2.compareTo(num1) // descending
        }
        return v2.compareTo(v1)
    }
}
