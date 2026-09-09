package io.ezz.launcher.ui.instance.model

import io.ezz.launcher.core.model.instance.LoaderType

/**
 * View presentation mode for the Instance Fleet Manager.
 */
enum class InstanceViewMode(val label: String) {
    CINEMATIC("Showcase"),
    COMPACT("Compact"),
    LIST("List")
}

/**
 * Sort options for instances in the Fleet Manager.
 */
enum class InstanceSortOrder(val label: String) {
    RECENT("Recently Played"),
    NAME("Name (A–Z)"),
    VERSION("Version"),
    PLAYTIME("Total Playtime"),
    CREATED("Date Created")
}

/**
 * Quick filter presets for the fleet header.
 */
enum class InstanceFilterChip(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RUNNING("Running"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    QUILT("Quilt"),
    VANILLA("Vanilla")
}

val LoaderType.displayName: String
    get() = when (this) {
        LoaderType.VANILLA -> "Vanilla"
        LoaderType.FABRIC -> "Fabric"
        LoaderType.OPTIFINE -> "OptiFine"
    }
