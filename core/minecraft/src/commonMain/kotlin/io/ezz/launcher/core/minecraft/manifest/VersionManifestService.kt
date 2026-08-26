package io.ezz.launcher.core.minecraft.manifest

import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.model.minecraft.VersionManifest
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.core.storage.path.PathProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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

    suspend fun getVersionManifest(forceRefresh: Boolean = false): VersionManifest = withContext(dispatcher) {
        if (!forceRefresh && cachedManifest != null) {
            return@withContext cachedManifest!!
        }

        val cacheFile = pathProvider.versionsDirectory.resolve("version_manifest_v2.json")
        if (!forceRefresh && fileSystem.exists(cacheFile)) {
            try {
                val content = fileSystem.read(cacheFile) { readUtf8() }
                val manifest = json.decodeFromString<VersionManifest>(content)
                cachedManifest = manifest
                return@withContext manifest
            } catch (e: Exception) {
                // If cache reading fails, fallback to network
            }
        }

        try {
            val responseText = httpClient.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").bodyAsText()
            val manifest = json.decodeFromString<VersionManifest>(responseText)

            val parent = cacheFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(cacheFile) {
                writeUtf8(responseText)
            }

            cachedManifest = manifest
            manifest
        } catch (e: Exception) {
            println("Warning: failed to fetch version manifest from network: ${e.message}")
            val fallback = VersionManifest(
                latest = io.ezz.launcher.core.model.minecraft.LatestVersion(release = "1.21.4", snapshot = "1.21.4"),
                versions = defaultFallbackVersions
            )
            cachedManifest = fallback
            fallback
        }
    }

    private val defaultFallbackVersions: List<VersionSummary> = listOf(
        VersionSummary(id = "1.21.4", type = "release", url = "https://piston-meta.mojang.com/v1/packages/8a594892c9fb2a4d33930b656b270054d5885fe2/1.21.4.json", time = "2024-12-03T10:00:00Z", releaseTime = "2024-12-03T10:00:00Z"),
        VersionSummary(id = "1.21.1", type = "release", url = "https://piston-meta.mojang.com/v1/packages/207011d61596e1b6443c7b74ba0175b5b9c02ff4/1.21.1.json", time = "2024-08-08T10:00:00Z", releaseTime = "2024-08-08T10:00:00Z"),
        VersionSummary(id = "1.20.4", type = "release", url = "https://piston-meta.mojang.com/v1/packages/f7cbe802f067417e2ddb4b882ea2dccebb568461/1.20.4.json", time = "2023-12-07T10:00:00Z", releaseTime = "2023-12-07T10:00:00Z"),
        VersionSummary(id = "1.20.1", type = "release", url = "https://piston-meta.mojang.com/v1/packages/27bfe4122d2ee64906f3630f658ffda5ca5f128c/1.20.1.json", time = "2023-06-12T10:00:00Z", releaseTime = "2023-06-12T10:00:00Z"),
        VersionSummary(id = "1.19.4", type = "release", url = "https://piston-meta.mojang.com/v1/packages/e1ff13f56b6c039d91f8d46db9ee6fcfe5f39649/1.19.4.json", time = "2023-03-14T10:00:00Z", releaseTime = "2023-03-14T10:00:00Z"),
        VersionSummary(id = "1.18.2", type = "release", url = "https://piston-meta.mojang.com/v1/packages/dfb2be7c3df99f1ed748d56b464010a30b503023/1.18.2.json", time = "2022-02-28T10:00:00Z", releaseTime = "2022-02-28T10:00:00Z"),
        VersionSummary(id = "1.16.5", type = "release", url = "https://piston-meta.mojang.com/v1/packages/375b42050974c2e6467fd8971f11ae883f3e1a66/1.16.5.json", time = "2021-01-15T10:00:00Z", releaseTime = "2021-01-15T10:00:00Z"),
        VersionSummary(id = "1.12.2", type = "release", url = "https://piston-meta.mojang.com/v1/packages/0c1964d50937a0988ad177ba6bf2ddbf26442654/1.12.2.json", time = "2017-09-18T10:00:00Z", releaseTime = "2017-09-18T10:00:00Z"),
        VersionSummary(id = "1.8.9", type = "release", url = "https://piston-meta.mojang.com/v1/packages/58a62372f883cc2878fe90e7552ea6374f67643b/1.8.9.json", time = "2015-12-03T10:00:00Z", releaseTime = "2015-12-03T10:00:00Z")
    )

    suspend fun getReleaseVersions(): List<VersionSummary> {
        val manifest = getVersionManifest()
        return manifest.versions.filter { it.type == "release" }
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
