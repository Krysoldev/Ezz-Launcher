package io.ezz.launcher.core.model.modrinth

import io.ezz.launcher.core.model.instance.LocalMod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modrinth content types.
 */
enum class ModrinthContentType(val apiValue: String, val displayName: String) {
    MOD("mod", "Mods"),
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
    val featured: Boolean = false,
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
