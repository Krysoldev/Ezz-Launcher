package io.ezz.launcher.core.model.curseforge

import io.ezz.launcher.core.model.instance.LocalMod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurseForgeSingleResponse<T>(
    @SerialName("data") val data: T
)

@Serializable
data class CurseForgeListResponse<T>(
    @SerialName("data") val data: List<T> = emptyList(),
    @SerialName("pagination") val pagination: CurseForgePagination? = null
)

@Serializable
data class CurseForgePagination(
    @SerialName("index") val index: Int = 0,
    @SerialName("pageSize") val pageSize: Int = 20,
    @SerialName("resultCount") val resultCount: Int = 0,
    @SerialName("totalCount") val totalCount: Long = 0L
)

@Serializable
data class CurseForgeMod(
    @SerialName("id") val id: Long,
    @SerialName("gameId") val gameId: Int = 432,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String = "",
    @SerialName("links") val links: CurseForgeLinks? = null,
    @SerialName("summary") val summary: String = "",
    @SerialName("status") val status: Int = 0,
    @SerialName("downloadCount") val downloadCount: Double = 0.0,
    @SerialName("isFeatured") val isFeatured: Boolean = false,
    @SerialName("primaryCategoryId") val primaryCategoryId: Int = 0,
    @SerialName("categories") val categories: List<CurseForgeCategory> = emptyList(),
    @SerialName("classId") val classId: Int? = null,
    @SerialName("authors") val authors: List<CurseForgeAuthor> = emptyList(),
    @SerialName("logo") val logo: CurseForgeAsset? = null,
    @SerialName("screenshots") val screenshots: List<CurseForgeAsset> = emptyList(),
    @SerialName("mainFileId") val mainFileId: Long = 0L,
    @SerialName("latestFiles") val latestFiles: List<CurseForgeFile> = emptyList(),
    @SerialName("latestFilesIndexes") val latestFilesIndexes: List<CurseForgeFileIndex> = emptyList(),
    @SerialName("dateCreated") val dateCreated: String = "",
    @SerialName("dateModified") val dateModified: String = "",
    @SerialName("dateReleased") val dateReleased: String = "",
    @SerialName("allowModDistribution") val allowModDistribution: Boolean? = null,
    @SerialName("gamePopularityRank") val gamePopularityRank: Int = 0
)

@Serializable
data class CurseForgeLinks(
    @SerialName("websiteUrl") val websiteUrl: String? = null,
    @SerialName("wikiUrl") val wikiUrl: String? = null,
    @SerialName("issuesUrl") val issuesUrl: String? = null,
    @SerialName("sourceUrl") val sourceUrl: String? = null
)

@Serializable
data class CurseForgeCategory(
    @SerialName("id") val id: Int,
    @SerialName("gameId") val gameId: Int = 432,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String = "",
    @SerialName("url") val url: String? = null,
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("dateModified") val dateModified: String? = null,
    @SerialName("isClass") val isClass: Boolean? = null,
    @SerialName("classId") val classId: Int? = null,
    @SerialName("parentCategoryId") val parentCategoryId: Int? = null
)

@Serializable
data class CurseForgeAuthor(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("url") val url: String? = null
)

@Serializable
data class CurseForgeAsset(
    @SerialName("id") val id: Long = 0L,
    @SerialName("modId") val modId: Long = 0L,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("url") val url: String? = null
)

@Serializable
data class CurseForgeFile(
    @SerialName("id") val id: Long,
    @SerialName("gameId") val gameId: Int = 432,
    @SerialName("modId") val modId: Long,
    @SerialName("isAvailable") val isAvailable: Boolean = true,
    @SerialName("displayName") val displayName: String = "",
    @SerialName("fileName") val fileName: String,
    @SerialName("releaseType") val releaseType: Int = 1,
    @SerialName("fileStatus") val fileStatus: Int = 4,
    @SerialName("hashes") val hashes: List<CurseForgeFileHash> = emptyList(),
    @SerialName("fileDate") val fileDate: String = "",
    @SerialName("fileLength") val fileLength: Long = 0L,
    @SerialName("downloadCount") val downloadCount: Long = 0L,
    @SerialName("downloadUrl") val downloadUrl: String? = null,
    @SerialName("gameVersions") val gameVersions: List<String> = emptyList(),
    @SerialName("sortableGameVersions") val sortableGameVersions: List<CurseForgeSortableGameVersion> = emptyList(),
    @SerialName("dependencies") val dependencies: List<CurseForgeFileDependency> = emptyList(),
    @SerialName("alternateFileId") val alternateFileId: Long = 0L,
    @SerialName("isServerPack") val isServerPack: Boolean = false,
    @SerialName("serverPackFileId") val serverPackFileId: Long? = null,
    @SerialName("earlyAccessEndDate") val earlyAccessEndDate: String? = null
) {
    val releaseTypeEnum: CurseForgeFileReleaseType
        get() = CurseForgeFileReleaseType.fromId(releaseType)

    val sha1Hash: String?
        get() = hashes.firstOrNull { it.algo == 1 }?.value

    val md5Hash: String?
        get() = hashes.firstOrNull { it.algo == 2 }?.value

    val isFabricSupported: Boolean
        get() = gameVersions.any { it.equals("Fabric", ignoreCase = true) }

    val isForgeSupported: Boolean
        get() = gameVersions.any { it.equals("Forge", ignoreCase = true) }

    val isNeoForgeSupported: Boolean
        get() = gameVersions.any { it.equals("NeoForge", ignoreCase = true) }

    val isQuiltSupported: Boolean
        get() = gameVersions.any { it.equals("Quilt", ignoreCase = true) }

    val extractedGameVersions: List<String>
        get() = gameVersions.filter { ver ->
            val v = ver.trim()
            v.isNotEmpty() && v[0].isDigit() && !v.equals("Fabric", true) && !v.equals("Forge", true) &&
                    !v.equals("NeoForge", true) && !v.equals("Quilt", true) && !v.equals("Java", true)
        }
}

@Serializable
data class CurseForgeFileHash(
    @SerialName("value") val value: String,
    @SerialName("algo") val algo: Int // 1 = SHA1, 2 = MD5
)

@Serializable
data class CurseForgeSortableGameVersion(
    @SerialName("gameVersionName") val gameVersionName: String? = null,
    @SerialName("gameVersionPadded") val gameVersionPadded: String? = null,
    @SerialName("gameVersion") val gameVersion: String? = null,
    @SerialName("gameVersionReleaseDate") val gameVersionReleaseDate: String? = null,
    @SerialName("gameVersionTypeId") val gameVersionTypeId: Int? = null
)

@Serializable
data class CurseForgeFileDependency(
    @SerialName("modId") val modId: Long,
    @SerialName("relationType") val relationType: Int // 1=Embedded, 2=Optional, 3=Required, 4=Tool, 5=Incompatible, 6=Include
) {
    val relationTypeEnum: CurseForgeDependencyRelationType
        get() = CurseForgeDependencyRelationType.fromId(relationType)
}

@Serializable
data class CurseForgeFileIndex(
    @SerialName("gameVersion") val gameVersion: String,
    @SerialName("fileId") val fileId: Long,
    @SerialName("filename") val filename: String,
    @SerialName("releaseType") val releaseType: Int = 1,
    @SerialName("gameVersionTypeId") val gameVersionTypeId: Int? = null,
    @SerialName("modLoader") val modLoader: Int? = null
)

enum class CurseForgeDependencyRelationType(val id: Int, val displayName: String) {
    EMBEDDED_LIBRARY(1, "Embedded"),
    OPTIONAL_DEPENDENCY(2, "Optional"),
    REQUIRED_DEPENDENCY(3, "Required"),
    TOOL(4, "Tool"),
    INCOMPATIBLE(5, "Incompatible"),
    INCLUDE(6, "Include");

    companion object {
        fun fromId(id: Int): CurseForgeDependencyRelationType =
            entries.firstOrNull { it.id == id } ?: OPTIONAL_DEPENDENCY
    }
}

enum class CurseForgeFileReleaseType(val id: Int, val displayName: String) {
    RELEASE(1, "Release"),
    BETA(2, "Beta"),
    ALPHA(3, "Alpha");

    companion object {
        fun fromId(id: Int): CurseForgeFileReleaseType =
            entries.firstOrNull { it.id == id } ?: RELEASE
    }
}

enum class CurseForgeModLoaderType(val id: Int, val loaderName: String) {
    ANY(0, "Any"),
    FORGE(1, "Forge"),
    CAULDRON(2, "Cauldron"),
    LITELOADER(3, "LiteLoader"),
    FABRIC(4, "Fabric"),
    QUILT(5, "Quilt"),
    NEOFORGE(6, "NeoForge");

    companion object {
        fun fromId(id: Int): CurseForgeModLoaderType =
            entries.firstOrNull { it.id == id } ?: ANY

        fun fromLoaderName(name: String?): CurseForgeModLoaderType {
            if (name.isNullOrBlank()) return ANY
            val trimmed = name.trim().lowercase()
            return when {
                trimmed.contains("fabric") -> FABRIC
                trimmed.contains("neoforge") -> NEOFORGE
                trimmed.contains("quilt") -> QUILT
                trimmed.contains("forge") -> FORGE
                else -> ANY
            }
        }
    }
}

enum class CurseForgeSortField(val id: Int, val label: String) {
    FEATURED(1, "Featured"),
    POPULARITY(2, "Popularity"),
    LAST_UPDATED(3, "Last Updated"),
    NAME(4, "Name"),
    TOTAL_DOWNLOADS(6, "Total Downloads");

    companion object {
        fun fromLabel(label: String): CurseForgeSortField =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: POPULARITY
    }
}

@Serializable
data class CurseForgeBatchModsRequest(
    @SerialName("modIds") val modIds: List<Long>
)

@Serializable
data class CurseForgeBatchFilesRequest(
    @SerialName("fileIds") val fileIds: List<Long>
)

@Serializable
data class CurseForgeMinecraftVersion(
    @SerialName("id") val id: Int = 0,
    @SerialName("gameVersionId") val gameVersionId: Int = 0,
    @SerialName("versionString") val versionString: String,
    @SerialName("jarDownloadUrl") val jarDownloadUrl: String? = null
)

data class ModIdentity(
    val source: String = "curseforge",
    val modId: Long,
    val fileId: Long = 0L,
    val name: String = "",
    val slug: String = ""
)

data class CurseForgeBrowseState(
    val searchQuery: String = "",
    val selectedGameVersion: String? = null,
    val selectedLoader: CurseForgeModLoaderType = CurseForgeModLoaderType.ANY,
    val selectedCategoryId: Int? = null,
    val selectedSort: CurseForgeSortField = CurseForgeSortField.POPULARITY,
    val allowBetaAlpha: Boolean = false,
    val page: Int = 1,
    val pageSize: Int = 20,
    val items: List<CurseForgeMod> = emptyList(),
    val totalHits: Long = 0L,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class CurseForgeResolvedDependency(
    val depModId: Long,
    val relationType: CurseForgeDependencyRelationType,
    val mod: CurseForgeMod? = null,
    val candidateFile: CurseForgeFile? = null,
    val isAlreadyInstalled: Boolean = false,
    val installedVersion: String? = null,
    val selectedToInstall: Boolean = true,
    val failureReason: String? = null
)

data class CurseForgeResolutionResult(
    val recommendedFile: CurseForgeFile?,
    val latestFile: CurseForgeFile?,
    val isLatestCompatible: Boolean,
    val selectionReason: String?,
    val candidateEvaluations: Map<Long, CurseForgeVersionEvaluation>,
    val hasCompatibleVersion: Boolean,
    val primaryConflictText: String? = null,
    val resolvedDependencies: List<CurseForgeResolvedDependency> = emptyList()
)

data class CurseForgeVersionEvaluation(
    val fileId: Long,
    val fileName: String,
    val isCompatible: Boolean,
    val hasMcMatch: Boolean,
    val hasLoaderMatch: Boolean,
    val conflicts: List<String> = emptyList(),
    val requiredDependencies: List<Long> = emptyList(),
    val missingDependencies: List<String> = emptyList(),
    val optionalDependencies: List<Long> = emptyList(),
    val summaryText: String = ""
)
