package io.ezz.launcher.core.model.modrinth

import io.ezz.launcher.core.model.instance.LocalMod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modrinth content types.
 */
enum class ModrinthContentType(val apiValue: String, val displayName: String) {
    MOD("mod", "Mods"),
    MODPACK("modpack", "Modpacks"),
    RESOURCE_PACK("resourcepack", "Resource Packs"),
    SHADER("shader", "Shaders")
}

/**
 * Search Response from Modrinth API v2 (/v2/search).
 */
@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthProjectHit> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 20,
    @SerialName("total_hits") val totalHits: Int = 0
)

/**
 * Single project hit from Modrinth search.
 */
@Serializable
data class ModrinthProjectHit(
    @SerialName("project_id") val projectId: String,
    @SerialName("project_type") val projectType: String = "mod",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("client_side") val clientSide: String = "optional",
    @SerialName("server_side") val serverSide: String = "optional",
    @SerialName("icon_url") val iconUrl: String? = null,
    val downloads: Long = 0L,
    val follows: Long = 0L,
    val versions: List<String> = emptyList(),
    @SerialName("latest_version") val latestVersion: String? = null,
    val author: String = "",
    @SerialName("display_categories") val displayCategories: List<String> = emptyList(),
    val gallery: List<String> = emptyList(),
    @SerialName("featured_gallery") val featuredGallery: String? = null
) {
    val previewImageUrl: String?
        get() = iconUrl?.takeIf { it.isNotBlank() }
            ?: featuredGallery?.takeIf { it.isNotBlank() }
            ?: gallery.firstOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Version metadata from Modrinth API (/v2/project/{id}/version or /v2/version/{id}).
 */
@Serializable
data class ModrinthVersion(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val name: String,
    @SerialName("version_number") val versionNumber: String,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    @SerialName("version_type") val versionType: String = "release",
    val featured: Boolean = false,
    val changelog: String? = null,
    @SerialName("date_published") val datePublished: String? = null,
    val downloads: Long = 0L,
    val files: List<ModrinthVersionFile> = emptyList(),
    val dependencies: List<ModrinthDependency> = emptyList()
)

/**
 * File attachment for a Modrinth version.
 */
@Serializable
data class ModrinthVersionFile(
    val url: String,
    val filename: String,
    val primary: Boolean = true,
    val size: Long = 0L,
    val hashes: Map<String, String> = emptyMap()
)

/**
 * Mod dependency relationship on Modrinth.
 */
@Serializable
data class ModrinthDependency(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("dependency_type") val dependencyType: String = "required" // "required", "optional", "incompatible"
)

/**
 * Candidate for updating an installed mod.
 */
data class ModUpdateCandidate(
    val localMod: LocalMod,
    val latestVersion: ModrinthVersion,
    val projectTitle: String
)

/**
 * Modrinth .mrpack manifest (modrinth.index.json).
 */
@Serializable
data class ModrinthIndex(
    val formatVersion: Int = 1,
    val game: String = "minecraft",
    @SerialName("versionId") val versionId: String = "",
    val name: String = "",
    val summary: String? = null,
    val files: List<ModrinthIndexFile> = emptyList(),
    val dependencies: Map<String, String> = emptyMap()
)

@Serializable
data class ModrinthIndexFile(
    val path: String,
    val hashes: Map<String, String> = emptyMap(),
    val env: ModrinthIndexEnv? = null,
    val downloads: List<String> = emptyList(),
    val fileSize: Long = 0L
)

@Serializable
data class ModrinthIndexEnv(
    val client: String = "required",
    val server: String = "required"
)

/**
 * Metadata preview of a .mrpack archive before import.
 */
data class MrpackPreview(
    val name: String,
    val summary: String?,
    val versionId: String,
    val minecraftVersion: String,
    val loaderType: io.ezz.launcher.core.model.instance.LoaderType,
    val loaderVersion: String?,
    val totalFiles: Int,
    val fileSize: Long,
    val iconBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MrpackPreview) return false
        return name == other.name && versionId == other.versionId && minecraftVersion == other.minecraftVersion
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + versionId.hashCode()
        result = 31 * result + minecraftVersion.hashCode()
        return result
    }
}

/**
 * User options when exporting an instance to .mrpack format.
 */
data class MrpackExportOptions(
    val customName: String? = null,
    val customSummary: String? = null,
    val versionId: String = "1.0.0",
    val includeConfigs: Boolean = true,
    val includeMods: Boolean = true,
    val includeResourcePacks: Boolean = true,
    val includeShaderPacks: Boolean = true
)

/**
 * Stages in the MRPACK import lifecycle.
 */
enum class MrpackImportStage(val label: String) {
    READING_MANIFEST("Reading modpack manifest"),
    VALIDATING_STRUCTURE("Validating package integrity"),
    CREATING_INSTANCE("Creating Minecraft environment"),
    EXTRACTING_OVERRIDES("Extracting configurations & files"),
    DOWNLOADING_MODS("Downloading modpack files"),
    FINALIZING("Finalizing installation"),
    COMPLETE("Modpack installed successfully"),
    FAILED("Import failed")
}

/**
 * Real-time progress update for MRPACK import.
 */
data class MrpackImportProgress(
    val stage: MrpackImportStage = MrpackImportStage.READING_MANIFEST,
    val message: String = "",
    val progress: Float = 0f,
    val currentFile: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)
