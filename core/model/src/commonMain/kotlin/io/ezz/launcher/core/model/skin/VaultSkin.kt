package io.ezz.launcher.core.model.skin

import kotlinx.serialization.Serializable

@Serializable
enum class SkinModelType {
    STEVE,
    ALEX
}

@Serializable
data class VaultSkin(
    val id: String,
    val name: String,
    val fileName: String,
    val modelType: SkinModelType = SkinModelType.STEVE,
    val fileHash: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class VaultManifest(
    val activeSkinId: String? = null,
    val accountSkinMappings: Map<String, String> = emptyMap(), // accountId -> skinId
    val skins: List<VaultSkin> = emptyList()
)
