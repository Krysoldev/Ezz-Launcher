package io.ezz.launcher.core.model.packsmc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supported providers for browsing and installing Resource Packs.
 * Modrinth remains the primary default provider; PacksMC is added alongside it.
 */
enum class ResourcePackProvider(val title: String) {
    MODRINTH("Modrinth"),
    PACKSMC("PacksMC")
}

/**
 * Health & connection status of the PacksMC service.
 */
enum class PacksMcStatus {
    READY,
    NO_API_KEY,
    INVALID_KEY,
    RATE_LIMITED,
    OFFLINE,
    ERROR
}

/**
 * Pack object returned by GET /api/v1/packs (summary) and GET /api/v1/packs/{id} (details).
 */
@Serializable
data class PacksMcPack(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val resolution: String? = null,
    val gamemodes: List<String> = emptyList(),
    @SerialName("mc_versions")
    val mcVersions: List<String> = emptyList(),
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    val gallery: List<String> = emptyList(),
    val downloads: Long = 0L,
    val likes: Long = 0L,
    val views: Long = 0L,
    @SerialName("file_size_bytes")
    val fileSizeBytes: Long = 0L,
    val license: PacksMcLicense? = null,
    val author: PacksMcAuthor? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("is_exclusive")
    val isExclusive: Boolean = false,
    @SerialName("trending_award")
    val trendingAward: PacksMcTrendingAward? = null,
    val colors: PacksMcColors? = null,
    // Detailed fields (present on GET /api/v1/packs/{id})
    val tags: List<String> = emptyList(),
    val credits: List<PacksMcCredit> = emptyList(),
    val community: PacksMcCommunity? = null,
    val versions: List<PacksMcVersionBuild> = emptyList(),
    val features: List<String> = emptyList(),
    @SerialName("video_url")
    val videoUrl: String? = null
)

@Serializable
data class PacksMcAuthor(
    val username: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val verified: Boolean = false
)

@Serializable
data class PacksMcCredit(
    val username: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val verified: Boolean = false,
    val role: String? = null
)

@Serializable
data class PacksMcCommunity(
    val name: String,
    val slug: String,
    @SerialName("icon_url")
    val iconUrl: String? = null
)

@Serializable
data class PacksMcVersionBuild(
    @SerialName("mc_version")
    val mcVersion: String,
    @SerialName("verified_safe")
    val verifiedSafe: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class PacksMcLicense(
    val code: String,
    val label: String? = null,
    val description: String? = null,
    @SerialName("allows_redistribution")
    val allowsRedistribution: Boolean? = null,
    @SerialName("requires_credit")
    val requiresCredit: Boolean? = null
)

@Serializable
data class PacksMcTrendingAward(
    @SerialName("peak_rank")
    val peakRank: Int? = null,
    @SerialName("achieved_at")
    val achievedAt: String? = null
)

@Serializable
data class PacksMcColors(
    val accent: String? = null,
    val name: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Paginated list response from GET /api/v1/packs
 */
@Serializable
data class PacksMcListResponse(
    val data: List<PacksMcPack> = emptyList(),
    @SerialName("next_cursor")
    val nextCursor: String? = null
)

/**
 * Download page response from GET /api/v1/packs/{id}/download
 */
@Serializable
data class PacksMcDownloadResponse(
    @SerialName("pack_id")
    val packId: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("web_url")
    val webUrl: String? = null,
    @SerialName("requires_packs_plus")
    val requiresPacksPlus: Boolean = false,
    val instructions: String? = null
)

/**
 * Identity & quota response from GET /api/v1/me
 */
@Serializable
data class PacksMcIdentityResponse(
    val id: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val verified: Boolean = false,
    @SerialName("packs_plus")
    val packsPlus: Boolean = false,
    val tier: String? = null,
    val limits: PacksMcLimits? = null,
    val counts: PacksMcCounts? = null,
    val can: PacksMcCapabilities? = null
)

@Serializable
data class PacksMcLimits(
    @SerialName("daily_quota")
    val dailyQuota: Int? = null,
    @SerialName("per_minute")
    val perMinute: Int? = null,
    @SerialName("per_ip_per_minute")
    val perIpPerMinute: Int? = null
)

@Serializable
data class PacksMcCounts(
    @SerialName("total_packs")
    val totalPacks: Int? = null,
    @SerialName("total_downloads")
    val totalDownloads: Long? = null
)

@Serializable
data class PacksMcCapabilities(
    @SerialName("list_packs")
    val listPacks: Boolean = false,
    @SerialName("view_pack_metadata")
    val viewPackMetadata: Boolean = false,
    @SerialName("get_pack_web_urls")
    val getPackWebUrls: Boolean = false,
    @SerialName("submit_conversions")
    val submitConversions: Boolean = false
)

/**
 * Standard error shape returned by PacksMC API on 4xx/5xx responses.
 */
@Serializable
data class PacksMcErrorResponse(
    val error: PacksMcErrorDetail? = null
)

@Serializable
data class PacksMcErrorDetail(
    val code: String,
    val message: String
)

/**
 * UI browsing state specifically for PacksMC.
 */
data class PacksMcBrowseState(
    val searchQuery: String = "",
    val selectedSort: String = "recent", // "recent", "downloads", "likes"
    val selectedResolution: String? = null, // "ALL", "16x", "32x", "64x", "128x", etc.
    val filterMinecraftVersion: Boolean = false,
    val items: List<PacksMcPack> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val retryAfterSeconds: Int? = null,
    val status: PacksMcStatus = PacksMcStatus.READY
) {
    val isRateLimited: Boolean get() = status == PacksMcStatus.RATE_LIMITED
}
