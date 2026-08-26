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
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val fileSize: Long = 0L,
    val enabled: Boolean = true
)
