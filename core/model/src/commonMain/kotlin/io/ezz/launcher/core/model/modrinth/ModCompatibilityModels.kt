package io.ezz.launcher.core.model.modrinth

import io.ezz.launcher.core.model.instance.LocalMod
import kotlinx.serialization.Serializable

/**
 * Details of a conflict between a candidate mod version and an installed mod.
 */
@Serializable
data class ModConflict(
    val modId: String,
    val modName: String,
    val installedVersion: String?,
    val candidateVersion: String,
    val reason: String,
    val isBreaking: Boolean = true
)

/**
 * Evaluation of a single candidate ModrinthVersion against the instance.
 */
@Serializable
data class VersionCompatibilityEvaluation(
    val versionId: String,
    val versionNumber: String,
    val isCompatible: Boolean,
    val hasMcMatch: Boolean,
    val hasLoaderMatch: Boolean,
    val conflicts: List<ModConflict> = emptyList(),
    val satisfiedDependencies: List<String> = emptyList(),
    val missingDependencies: List<String> = emptyList(),
    val summaryText: String = ""
)

/**
 * Co-upgrade recommendation when updating an installed mod would unlock a newer target mod version.
 */
@Serializable
data class CoUpgradeOption(
    val existingModId: String,
    val existingModName: String,
    val currentVersion: String,
    val targetModName: String,
    val targetVersion: String,
    val explanation: String
)

/**
 * Comprehensive whole-instance resolution result for a requested mod.
 */
data class ModResolutionResult(
    val recommendedVersion: ModrinthVersion?,
    val latestVersion: ModrinthVersion?,
    val isLatestCompatible: Boolean,
    val selectionReason: String?,
    val candidateEvaluations: Map<String, VersionCompatibilityEvaluation>,
    val hasCompatibleVersion: Boolean,
    val primaryConflict: ModConflict? = null,
    val coUpgradeOption: CoUpgradeOption? = null
)
