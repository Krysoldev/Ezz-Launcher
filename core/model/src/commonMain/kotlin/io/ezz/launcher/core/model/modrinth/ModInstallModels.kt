package io.ezz.launcher.core.model.modrinth

import io.ezz.launcher.core.model.instance.Instance
import kotlinx.serialization.Serializable

/**
 * Release channel for a Modrinth version (release, beta, alpha).
 */
enum class ModrinthVersionType(val apiValue: String, val displayName: String) {
    RELEASE("release", "Release"),
    BETA("beta", "Beta"),
    ALPHA("alpha", "Alpha");

    companion object {
        fun fromApiValue(value: String): ModrinthVersionType {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: RELEASE
        }
    }
}

/**
 * Resolved dependency project with its matching installable version for a target MC version and loader.
 */
data class ResolvedModDependency(
    val dependency: ModrinthDependency,
    val project: ModrinthProjectHit?,
    val version: ModrinthVersion?,
    val isRequired: Boolean,
    val isAlreadyInstalled: Boolean,
    val installedVersion: String? = null,
    var selectedToInstall: Boolean = isRequired && !isAlreadyInstalled,
    val requiredBy: String? = null,
    val constraint: String? = null,
    val failureReason: String? = null
)

/**
 * Complete installation plan for a mod and its full recursive dependency graph.
 */
data class ModInstallationPlan(
    val requestedTitle: String,
    val requestedVersion: String,
    val requiredDependencies: List<ResolvedModDependency> = emptyList(),
    val optionalDependencies: List<ResolvedModDependency> = emptyList(),
    val isComplete: Boolean = true,
    val failureReason: String? = null
)

/**
 * Complete state for the interactive mod installation wizard.
 */
data class ModInstallWizardState(
    val project: ModrinthProjectHit,
    val targetInstance: Instance?,
    val availableGameVersions: List<String> = emptyList(),
    val selectedGameVersion: String = "",
    val availableLoadersForVersion: List<String> = emptyList(),
    val selectedLoader: String = "",
    val compatibleVersions: List<ModrinthVersion> = emptyList(),
    val selectedVersion: ModrinthVersion? = null,
    val resolvedDependencies: List<ResolvedModDependency> = emptyList(),
    val isAlreadyInstalled: Boolean = false,
    val installedVersionString: String? = null,
    val isUpdateAvailable: Boolean = false,
    val isLoadingMetadata: Boolean = false,
    val isLoadingVersions: Boolean = false,
    val isLoadingDependencies: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Real-time progress and stage for downloading and verifying a mod and its dependencies.
 */
data class ModInstallProgress(
    val stage: String = "",
    val currentFileName: String = "",
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null
)
