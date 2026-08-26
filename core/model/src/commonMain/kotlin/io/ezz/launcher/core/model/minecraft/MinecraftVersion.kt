package io.ezz.launcher.core.model.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VersionManifest(
    val latest: LatestVersion,
    val versions: List<VersionSummary>
)

@Serializable
data class LatestVersion(
    val release: String,
    val snapshot: String
)

@Serializable
data class VersionSummary(
    val id: String,
    val type: String,
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String? = null,
    val complianceLevel: Int = 0
)

@Serializable
data class VersionInfo(
    val id: String,
    val type: String = "release",
    val mainClass: String = "net.minecraft.client.main.Main",
    val assets: String? = null,
    val assetIndex: AssetIndexReference? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: VersionArguments? = null,
    val minecraftArguments: String? = null,
    val javaVersion: JavaVersionInfo? = null,
    val inheritsFrom: String? = null
)

@Serializable
data class AssetIndexReference(
    val id: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long = 0L,
    val url: String
)

@Serializable
data class VersionDownloads(
    val client: DownloadArtifact? = null,
    val server: DownloadArtifact? = null
)

@Serializable
data class DownloadArtifact(
    val sha1: String,
    val size: Long,
    val url: String,
    val path: String? = null
)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    val url: String? = null
)

@Serializable
data class LibraryDownloads(
    val artifact: DownloadArtifact? = null,
    val classifiers: Map<String, DownloadArtifact>? = null
)

@Serializable
data class Rule(
    val action: String, // "allow" or "disallow"
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null
)

@Serializable
data class OsRule(
    val name: String? = null, // "windows", "osx", "linux"
    val version: String? = null,
    val arch: String? = null
)

@Serializable
data class VersionArguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList()
)

@Serializable
data class JavaVersionInfo(
    val component: String = "jre-legacy",
    val majorVersion: Int = 8
)

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap()
)

@Serializable
data class AssetObject(
    val hash: String,
    val size: Long
)
