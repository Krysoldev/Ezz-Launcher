package io.ezz.launcher.core.storage.supabase

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.runtime.LauncherSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseProfileDto(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String = "Ezz Player",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseMinecraftAccountDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    val uuid: String,
    val type: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_selected") val isSelected: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toAccount(msaRefreshToken: String = "", mcAccessToken: String = "", expiresAt: Long = 0L): Account {
        return if (type.equals("MICROSOFT", ignoreCase = true)) {
            MicrosoftAccount(
                id = id,
                username = username,
                uuid = uuid,
                msaRefreshToken = msaRefreshToken,
                mcAccessToken = mcAccessToken,
                expiresAt = expiresAt,
                avatarUrl = avatarUrl,
                createdAt = 0L
            )
        } else {
            OfflineAccount(
                id = id,
                username = username,
                uuid = uuid,
                avatarUrl = avatarUrl,
                createdAt = 0L
            )
        }
    }
}

@Serializable
data class SupabaseInstanceDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("minecraft_version") val minecraftVersion: String,
    @SerialName("loader_type") val loaderType: String = "VANILLA",
    @SerialName("loader_version") val loaderVersion: String? = null,
    @SerialName("icon_id") val iconId: String = "grass_block",
    @SerialName("java_path") val javaPath: String? = null,
    @SerialName("min_memory_mb") val minMemoryMb: Int = 1024,
    @SerialName("max_memory_mb") val maxMemoryMb: Int = 4096,
    @SerialName("custom_jvm_args") val customJvmArgs: List<String> = emptyList(),
    @SerialName("window_width") val windowWidth: Int = 1280,
    @SerialName("window_height") val windowHeight: Int = 720,
    @SerialName("last_played_at") val lastPlayedAt: String? = null,
    @SerialName("total_play_time_seconds") val totalPlayTimeSeconds: Long = 0L,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toInstance(): Instance {
        val parsedLoader = try {
            LoaderType.valueOf(loaderType.uppercase())
        } catch (e: Exception) {
            LoaderType.VANILLA
        }
        return Instance(
            id = id,
            name = name,
            minecraftVersion = minecraftVersion,
            loaderType = parsedLoader,
            loaderVersion = loaderVersion,
            iconId = iconId,
            javaPath = javaPath,
            minMemoryMb = minMemoryMb,
            maxMemoryMb = maxMemoryMb,
            customJvmArgs = customJvmArgs,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            createdAt = 0L,
            lastPlayedAt = null,
            totalPlayTimeSeconds = totalPlayTimeSeconds
        )
    }

    companion object {
        fun fromInstance(instance: Instance, userId: String): SupabaseInstanceDto {
            return SupabaseInstanceDto(
                id = instance.id,
                userId = userId,
                name = instance.name,
                minecraftVersion = instance.minecraftVersion,
                loaderType = instance.loaderType.name,
                loaderVersion = instance.loaderVersion,
                iconId = instance.iconId,
                javaPath = instance.javaPath,
                minMemoryMb = instance.minMemoryMb,
                maxMemoryMb = instance.maxMemoryMb,
                customJvmArgs = instance.customJvmArgs,
                windowWidth = instance.windowWidth,
                windowHeight = instance.windowHeight,
                totalPlayTimeSeconds = instance.totalPlayTimeSeconds
            )
        }
    }
}

@Serializable
data class SupabaseInstanceModDto(
    val id: String,
    @SerialName("instance_id") val instanceId: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val version: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_hash") val fileHash: String? = null,
    val loader: String = "FABRIC",
    val enabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseUserSettingsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("default_min_memory_mb") val defaultMinMemoryMb: Int = 1024,
    @SerialName("default_max_memory_mb") val defaultMaxMemoryMb: Int = 4096,
    @SerialName("default_java_path") val defaultJavaPath: String? = null,
    @SerialName("global_jvm_args") val globalJvmArgs: List<String> = listOf(
        "-XX:+UseG1GC",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=20",
        "-XX:G1ReservePercent=20",
        "-XX:MaxGCPauseMillis=50",
        "-XX:G1HeapRegionSize=32M"
    ),
    @SerialName("close_launcher_on_launch") val closeLauncherOnLaunch: Boolean = false,
    @SerialName("dark_theme") val darkTheme: Boolean = true,
    @SerialName("selected_instance_id") val selectedInstanceId: String? = null,
    @SerialName("selected_account_id") val selectedAccountId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toLauncherSettings(): LauncherSettings {
        return LauncherSettings(
            defaultMinMemoryMb = defaultMinMemoryMb,
            defaultMaxMemoryMb = defaultMaxMemoryMb,
            defaultJavaPath = defaultJavaPath,
            globalJvmArgs = globalJvmArgs,
            closeLauncherOnLaunch = closeLauncherOnLaunch,
            darkTheme = darkTheme,
            selectedInstanceId = selectedInstanceId,
            selectedAccountId = selectedAccountId
        )
    }

    companion object {
        fun fromLauncherSettings(settings: LauncherSettings, userId: String): SupabaseUserSettingsDto {
            return SupabaseUserSettingsDto(
                userId = userId,
                defaultMinMemoryMb = settings.defaultMinMemoryMb,
                defaultMaxMemoryMb = settings.defaultMaxMemoryMb,
                defaultJavaPath = settings.defaultJavaPath,
                globalJvmArgs = settings.globalJvmArgs,
                closeLauncherOnLaunch = settings.closeLauncherOnLaunch,
                darkTheme = settings.darkTheme,
                selectedInstanceId = settings.selectedInstanceId,
                selectedAccountId = settings.selectedAccountId
            )
        }
    }
}

@Serializable
data class SupabaseAuthUserDto(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: Map<String, String> = emptyMap()
)

@Serializable
data class SupabaseAuthSessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: SupabaseAuthUserDto
)

@Serializable
data class SupabaseErrorDto(
    val message: String? = null,
    val code: String? = null,
    val details: String? = null,
    val hint: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
) {
    fun formatSafeMessage(): String {
        return message ?: errorDescription ?: error ?: "Unknown Supabase database error"
    }
}

@Serializable
data class SupabaseLauncherReleaseDto(
    val id: String = "",
    val version: String,
    val platform: String = "windows",
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("is_latest") val isLatest: Boolean = true,
    @SerialName("is_required") val isRequired: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseMinecraftVersionDto(
    val id: String = "",
    val version: String,
    @SerialName("version_type") val versionType: String = "release",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("is_supported") val isSupported: Boolean = true,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("metadata_url") val metadataUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseFabricVersionDto(
    val id: String = "",
    @SerialName("minecraft_version") val minecraftVersion: String,
    @SerialName("loader_version") val loaderVersion: String,
    @SerialName("installer_version") val installerVersion: String = "1.0.1",
    @SerialName("is_supported") val isSupported: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseOptiFineVersionDto(
    val id: String = "",
    @SerialName("minecraft_version") val minecraftVersion: String,
    @SerialName("optifine_version") val optifineVersion: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("is_supported") val isSupported: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseAnnouncementDto(
    val id: String = "",
    val title: String,
    val message: String,
    val type: String = "info",
    @SerialName("is_active") val isActive: Boolean = true,
    val priority: Int = 0,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseLauncherConfigDto(
    val key: String,
    val value: String,
    val description: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseFeatureFlagDto(
    val id: String = "",
    @SerialName("feature_key") val featureKey: String,
    val enabled: Boolean = true,
    val platform: String = "windows",
    @SerialName("minimum_launcher_version") val minimumLauncherVersion: String = "1.0.0",
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)


