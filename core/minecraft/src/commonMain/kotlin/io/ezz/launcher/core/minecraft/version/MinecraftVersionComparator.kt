package io.ezz.launcher.core.minecraft.version

import io.ezz.launcher.core.model.minecraft.VersionSummary

enum class VersionSortOrder(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    NAME_ASC("Version (A-Z)"),
    NAME_DESC("Version (Z-A)"),
    RELEASE_DATE("Release Date")
}

enum class VersionCategoryFilter(val displayName: String) {
    RELEASES("Releases"),
    SNAPSHOTS("Snapshots"),
    OLD_BETA_ALPHA("Beta / Alpha"),
    ALL("All Versions")
}

/**
 * Intelligent comparator for Minecraft versions that handles semantic versions,
 * snapshots, and legacy alpha/beta versions accurately.
 */
object MinecraftVersionComparator : Comparator<VersionSummary> {

    override fun compare(v1: VersionSummary, v2: VersionSummary): Int {
        // 1. Primary: Compare by releaseTime if both are valid ISO timestamps
        val timeCompare = compareReleaseTimes(v1.releaseTime, v2.releaseTime)
        if (timeCompare != 0) {
            return timeCompare
        }

        // 2. Secondary: Semantic version comparison for release strings (e.g. 1.21.10 vs 1.21.9)
        val semCompare = compareSemanticStrings(v1.id, v2.id)
        if (semCompare != 0) {
            return semCompare
        }

        // 3. Fallback: string comparison
        return v1.id.compareTo(v2.id)
    }

    private fun compareReleaseTimes(t1: String, t2: String): Int {
        if (t1.isBlank() && t2.isBlank()) return 0
        if (t1.isBlank()) return -1
        if (t2.isBlank()) return 1
        return t1.compareTo(t2)
    }

    fun compareSemanticStrings(id1: String, id2: String): Int {
        val p1 = parseVersionTokens(id1)
        val p2 = parseVersionTokens(id2)

        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    private fun parseVersionTokens(versionId: String): List<Int> {
        val clean = versionId.replace(Regex("[^0-9.]"), "")
        if (clean.isBlank()) return emptyList()
        return clean.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Sorts a list of VersionSummary according to the specified sort order.
     */
    fun sort(versions: List<VersionSummary>, order: VersionSortOrder): List<VersionSummary> {
        return when (order) {
            VersionSortOrder.NEWEST_FIRST -> versions.sortedWith(this.reversed())
            VersionSortOrder.OLDEST_FIRST -> versions.sortedWith(this)
            VersionSortOrder.NAME_ASC -> versions.sortedBy { it.id }
            VersionSortOrder.NAME_DESC -> versions.sortedByDescending { it.id }
            VersionSortOrder.RELEASE_DATE -> versions.sortedByDescending { it.releaseTime }
        }
    }
}
