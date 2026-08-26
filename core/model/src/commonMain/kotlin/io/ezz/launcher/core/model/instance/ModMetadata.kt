package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

@Serializable
data class ModMetadata(
    val id: String,
    val instanceId: String,
    val name: String,
    val version: String,
    val fileName: String,
    val fileHash: String? = null,
    val loader: String = "FABRIC",
    val enabled: Boolean = true
)
