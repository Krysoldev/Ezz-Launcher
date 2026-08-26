package io.ezz.launcher.core.model.modrinth

/**
 * State container for an independent, paginated Modrinth browse section (Mods, Resource Packs, or Shaders).
 */
data class ModrinthBrowseState(
    val contentType: ModrinthContentType = ModrinthContentType.MOD,
    val items: List<ModrinthProjectHit> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalHits: Int = 0,
    val totalPages: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedLoader: String? = null,
    val selectedGameVersion: String? = null,
    val selectedCategory: String? = null,
    val selectedResolution: String? = null,
    val selectedSort: String = "relevance" // "relevance", "downloads", "newest", "updated"
) {
    val hasPrevPage: Boolean get() = page > 1 && !isLoading
    val hasNextPage: Boolean get() = page < totalPages && !isLoading && items.isNotEmpty()
}

/**
 * Available sorting options for Modrinth searches.
 */
enum class ModrinthSortOption(val apiValue: String, val displayName: String) {
    RELEVANCE("relevance", "Relevance"),
    DOWNLOADS("downloads", "Most Downloads"),
    FOLLOWS("follows", "Most Popular"),
    NEWEST("newest", "Recently Added"),
    UPDATED("updated", "Recently Updated")
}
