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
    val enabled: Boolean = true,
    val dependencies: Map<String, String> = emptyMap(),
    val breaks: Map<String, String> = emptyMap(),
    val conflicts: Map<String, String> = emptyMap()
)

fun ModMetadata.toLocalMod(): LocalMod = LocalMod(
    id = id,
    name = name,
    version = version,
    fileName = fileName,
    fileSize = fileSize,
    loader = loader,
    enabled = enabled,
    author = authors.firstOrNull(),
    description = description,
    dependencies = dependencies,
    breaks = breaks,
    conflicts = conflicts
)

