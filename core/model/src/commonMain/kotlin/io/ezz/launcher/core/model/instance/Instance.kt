package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

@Serializable
enum class LoaderType {
    VANILLA,
    FABRIC,
    OPTIFINE
}

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loaderType: LoaderType = LoaderType.VANILLA,
    val loaderVersion: String? = null,
    val iconId: String = "grass_block",
    val javaPath: String? = null,
    val minMemoryMb: Int = 1024,
    val maxMemoryMb: Int = 4096,
    val customJvmArgs: List<String> = emptyList(),
    val windowWidth: Int = 1280,
    val windowHeight: Int = 720,
    val createdAt: Long = 0L,
    val lastPlayedAt: Long? = null,
    val totalPlayTimeSeconds: Long = 0L,
    val customIconPath: String? = null
)
