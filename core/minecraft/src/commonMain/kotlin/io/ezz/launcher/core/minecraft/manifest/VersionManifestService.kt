package io.ezz.launcher.core.minecraft.manifest

import io.ezz.launcher.core.minecraft.version.JavaCompatibility
import io.ezz.launcher.core.minecraft.version.MinecraftVersionComparator
import io.ezz.launcher.core.minecraft.version.VersionCategoryFilter
import io.ezz.launcher.core.minecraft.version.VersionSortOrder
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.model.minecraft.VersionManifest
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.core.storage.path.PathProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem

class VersionManifestService(
    private val httpClient: HttpClient,
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private var cachedManifest: VersionManifest? = null

    private val primaryManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val fallbackManifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

    suspend fun getVersionManifest(forceRefresh: Boolean = false): VersionManifest = withContext(dispatcher) {
        if (!forceRefresh && cachedManifest != null) {
            return@withContext cachedManifest!!
        }

        val cacheFile = pathProvider.versionsDirectory.resolve("version_manifest_v2.json")

        // 1. Try Live Mojang Network Manifest
        try {
            val responseText = try {
                httpClient.get(primaryManifestUrl).bodyAsText()
            } catch (e: Exception) {
                httpClient.get(fallbackManifestUrl).bodyAsText()
            }

            val manifest = json.decodeFromString<VersionManifest>(responseText)

            // Cache to disk
            val parent = cacheFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(cacheFile) {
                writeUtf8(responseText)
            }

            cachedManifest = manifest

            logManifestStats(manifest, "Official Mojang API (Online)")
            return@withContext manifest
        } catch (netEx: Exception) {
            println("Notice: Failed to fetch online version manifest: ${netEx.message}. Falling back to disk cache...")
        }

        // 2. Fallback to Local Disk Cache
        if (fileSystem.exists(cacheFile)) {
            try {
                val content = fileSystem.read(cacheFile) { readUtf8() }
                val manifest = json.decodeFromString<VersionManifest>(content)
                cachedManifest = manifest
                logManifestStats(manifest, "Local Disk Cache (Offline)")
                return@withContext manifest
            } catch (diskEx: Exception) {
                println("Notice: Failed to read local disk cache: ${diskEx.message}")
            }
        }

        throw IllegalStateException("Unable to load official Minecraft version manifest. Check your network connection.")
    }

    private fun logManifestStats(manifest: VersionManifest, source: String) {
        val total = manifest.versions.size
        val releases = manifest.versions.count { it.type == "release" }
        val snapshots = manifest.versions.count { it.type == "snapshot" }
        val beta = manifest.versions.count { it.type == "old_beta" }
        val alpha = manifest.versions.count { it.type == "old_alpha" }

        println(
            """
            ==================================================
            [Minecraft Version Manifest]
            Source: $source
            Total: $total
            Release: $releases
            Snapshot: $snapshots
            Beta: $beta
            Alpha: $alpha
            Latest Release: ${manifest.latest.release}
            Latest Snapshot: ${manifest.latest.snapshot}
            ==================================================
            """.trimIndent()
        )
    }

    suspend fun queryVersions(
        category: VersionCategoryFilter = VersionCategoryFilter.RELEASES,
        searchQuery: String = "",
        sortOrder: VersionSortOrder = VersionSortOrder.NEWEST_FIRST
    ): List<VersionSummary> {
        val manifest = getVersionManifest()
        val filteredByCategory = when (category) {
            VersionCategoryFilter.RELEASES -> manifest.versions.filter { it.type == "release" }
            VersionCategoryFilter.SNAPSHOTS -> manifest.versions.filter { it.type == "snapshot" }
            VersionCategoryFilter.OLD_BETA_ALPHA -> manifest.versions.filter { it.type == "old_beta" || it.type == "old_alpha" }
            VersionCategoryFilter.ALL -> manifest.versions
        }

        val filteredByQuery = if (searchQuery.isBlank()) {
            filteredByCategory
        } else {
            val q = searchQuery.trim().lowercase()
            filteredByCategory.filter { it.id.lowercase().contains(q) }
        }

        return MinecraftVersionComparator.sort(filteredByQuery, sortOrder)
    }

    suspend fun getAllVersions(): List<VersionSummary> {
        val manifest = getVersionManifest()
        return manifest.versions
    }

    suspend fun getReleaseVersions(): List<VersionSummary> {
        val manifest = getVersionManifest()
        return manifest.versions.filter { it.type == "release" }
    }

    suspend fun getSnapshotVersions(): List<VersionSummary> {
        val manifest = getVersionManifest()
        return manifest.versions.filter { it.type == "snapshot" }
    }

    suspend fun getOldVersions(): List<VersionSummary> {
        val manifest = getVersionManifest()
        return manifest.versions.filter { it.type == "old_beta" || it.type == "old_alpha" }
    }

    suspend fun getRequiredJavaMajorVersion(versionId: String): Int {
        return try {
            val info = getVersionInfo(versionId)
            JavaCompatibility.getRequiredJavaMajorVersion(versionId, info)
        } catch (_: Exception) {
            JavaCompatibility.getRequiredJavaMajorVersion(versionId, null)
        }
    }

    suspend fun getVersionInfo(versionId: String): VersionInfo = withContext(dispatcher) {
        val versionDir = pathProvider.versionsDirectory.resolve(versionId)
        val versionFile = versionDir.resolve("$versionId.json")

        if (fileSystem.exists(versionFile)) {
            try {
                val content = fileSystem.read(versionFile) { readUtf8() }
                return@withContext json.decodeFromString<VersionInfo>(content)
            } catch (e: Exception) {
                // Cache corrupt or outdated, refetch
            }
        }

        val manifest = getVersionManifest()
        val summary = manifest.versions.find { it.id == versionId }
            ?: throw IllegalArgumentException("Minecraft version $versionId not found in manifest")

        val responseText = httpClient.get(summary.url).bodyAsText()
        val versionInfo = json.decodeFromString<VersionInfo>(responseText)

        fileSystem.createDirectories(versionDir)
        fileSystem.write(versionFile) {
            writeUtf8(responseText)
        }

        versionInfo
    }
}
