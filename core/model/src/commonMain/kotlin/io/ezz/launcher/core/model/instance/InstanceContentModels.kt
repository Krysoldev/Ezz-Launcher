package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

/**
 * Tabs available within the Instance Manager UI.
 */
enum class InstanceManagerTab(val title: String) {
    OVERVIEW("Overview"),
    MODS("Mods"),
    RESOURCE_PACKS("Resource Packs"),
    SHADERS("Shaders"),
    WORLDS("Worlds"),
    SCREENSHOTS("Screenshots"),
    SETTINGS("Settings"),
    FILES("Files"),
    LOGS("Logs")
}

/**
 * Local Mod parsed from instance `mods/` directory.
 */
@Serializable
data class LocalMod(
    val id: String,
    val name: String,
    val version: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val loader: String = "FABRIC",
    val enabled: Boolean = true,
    val author: String? = null,
    val description: String? = null,
    val iconPath: String? = null,
    val isModrinthLinked: Boolean = false
)

/**
 * Local Resource Pack found in `resourcepacks/`.
 */
@Serializable
data class LocalResourcePack(
    val fileName: String,
    val name: String,
    val description: String? = null,
    val packFormat: Int? = null,
    val iconPath: String? = null,
    val enabled: Boolean = true,
    val fileSize: Long = 0L
)

/**
 * Local Shaderpack found in `shaderpacks/`.
 */
@Serializable
data class LocalShaderPack(
    val fileName: String,
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val fileSize: Long = 0L,
    val iconPath: String? = null
)

/**
 * Local Minecraft World/Save found in `saves/`.
 */
@Serializable
data class LocalWorld(
    val folderName: String,
    val name: String,
    val gameType: String = "Survival",
    val lastPlayed: Long = 0L,
    val sizeBytes: Long = 0L,
    val iconPath: String? = null,
    val version: String? = null,
    val isHardcore: Boolean = false,
    val isCheats: Boolean = false
)

/**
 * Local World Backup archive.
 */
@Serializable
data class LocalWorldBackup(
    val fileName: String,
    val worldName: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val filePath: String
)

/**
 * Local Screenshot found in `screenshots/`.
 */
@Serializable
data class LocalScreenshot(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val lastModified: Long = 0L
)

/**
 * Local Log File (latest.log, crash reports, launcher logs).
 */
@Serializable
data class InstanceLogEntry(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val isCrashReport: Boolean = false
)

/**
 * Diagnostic Instance Repair Result.
 */
@Serializable
data class InstanceRepairReport(
    val passed: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val isHealthy: Boolean = true
)

/**
 * Instance Overview Statistics.
 */
@Serializable
data class InstanceStatistics(
    val modsCount: Int = 0,
    val resourcePacksCount: Int = 0,
    val shadersCount: Int = 0,
    val worldsCount: Int = 0,
    val screenshotsCount: Int = 0,
    val totalSizeBytes: Long = 0L
)
