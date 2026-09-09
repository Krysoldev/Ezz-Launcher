package io.ezz.launcher.ui.instance.installation.model

import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeMod

enum class ContentType(val displayName: String) {
    MOD("Mod"),
    RESOURCE_PACK("Resource Pack"),
    SHADER("Shader Pack"),
    WORLD("World")
}

enum class InstallationStage(val stepNumber: Int, val label: String) {
    SELECTED(1, "Selected"),
    VALIDATING(2, "Validating Compatibility"),
    RESOLVING_DEPENDENCIES(3, "Resolving Dependencies"),
    DOWNLOADING_MAIN(4, "Downloading Content"),
    DOWNLOADING_DEPENDENCIES(5, "Downloading Dependencies"),
    INSTALLING(6, "Installing to Instance"),
    VERIFYING(7, "Verifying Integrity"),
    COMPLETED(8, "Installed Successfully"),
    FAILED(-1, "Installation Failed"),
    CANCELLED(-2, "Cancelled")
}

enum class DependencyInstallStatus(val label: String) {
    VERIFIED_INSTALLED("Already Installed"),
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    COMPLETED("Installed"),
    FAILED("Failed"),
    OPTIONAL_SKIPPED("Optional Skipped")
}

data class InstallationDependencyItem(
    val modId: Long,
    val name: String,
    val relationType: String, // "Required", "Optional", "Incompatible"
    val isRequired: Boolean,
    val status: DependencyInstallStatus = DependencyInstallStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val versionText: String? = null,
    val candidateFile: CurseForgeFile? = null,
    val errorText: String? = null
)

data class ContentInstallationItem(
    val id: String, // UUID / unique operation ID
    val instanceId: String,
    val instanceName: String,
    val contentType: ContentType,
    val name: String,
    val iconUrl: String? = null,
    val author: String? = null,
    val versionName: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val stage: InstallationStage = InstallationStage.SELECTED,
    val progress: Float = 0f, // 0f..1f, or -1f if indeterminate
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = "",
    val dependencies: List<InstallationDependencyItem> = emptyList(),
    val targetFileAbsolutePath: String? = null,
    val errorSummary: String? = null,
    val errorDetails: String? = null,
    val canRetry: Boolean = true,
    val startTimeMs: Long = System.currentTimeMillis(),
    val completedTimeMs: Long? = null,
    val mod: CurseForgeMod? = null,
    val chosenFile: CurseForgeFile? = null
) {
    val isFinished: Boolean
        get() = stage == InstallationStage.COMPLETED || stage == InstallationStage.FAILED || stage == InstallationStage.CANCELLED

    val isRunning: Boolean
        get() = !isFinished

    val progressPercent: Int
        get() = if (progress < 0f) 0 else (progress * 100).toInt().coerceIn(0, 100)

    val downloadedFormatted: String
        get() = formatBytes(downloadedBytes)

    val totalFormatted: String
        get() = if (totalBytes > 0) formatBytes(totalBytes) else "Unknown"

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024.0 && unitIndex < units.size - 1) {
                size /= 1024.0
                unitIndex++
            }
            return if (unitIndex == 0) "${bytes} B" else String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
        }
    }
}

data class InstallationQueueState(
    val activeItems: List<ContentInstallationItem> = emptyList(),
    val queuedItems: List<ContentInstallationItem> = emptyList(),
    val completedHistory: List<ContentInstallationItem> = emptyList(),
    val isDrawerOpen: Boolean = false
) {
    val totalActiveCount: Int
        get() = activeItems.size + queuedItems.size
}

data class LocalImportRequest(
    val file: java.io.File,
    val contentType: ContentType,
    val instance: io.ezz.launcher.core.model.instance.Instance,
    val targetDirectory: java.io.File
)
