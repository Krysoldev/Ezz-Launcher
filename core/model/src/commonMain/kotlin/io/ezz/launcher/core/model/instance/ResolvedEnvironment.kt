package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Authoritative, immutable descriptor of a Minecraft instance's execution environment.
 * Guarantees that compatibility resolution is never executed with incomplete or blank metadata.
 */
@Serializable
data class ResolvedEnvironment(
    val instanceId: String,
    val minecraftVersion: String,
    val loader: String,
    val loaderVersion: String? = null,
    val minecraftDirectoryPath: String
) {
    init {
        require(instanceId.isNotBlank()) { "Instance ID must not be blank" }
        require(minecraftVersion.isNotBlank()) { "Minecraft version must not be blank" }
        require(loader.isNotBlank()) { "Loader must not be blank" }
        require(minecraftDirectoryPath.isNotBlank()) { "Minecraft directory path must not be blank" }
    }

    val minecraftDirectory: File
        get() = File(minecraftDirectoryPath)

    val modsDirectory: File
        get() = File(minecraftDirectory, "mods")

    val normalizedLoader: String
        get() = loader.trim().lowercase()

    val normalizedMinecraftVersion: String
        get() = minecraftVersion.trim()
}
