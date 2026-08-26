package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

@Serializable
data class ServerEntry(
    val id: String,
    val name: String,
    val address: String,
    val iconBase64: String? = null,
    val isFeatured: Boolean = false,
    val motd: String? = null,
    val pingMs: Long? = null,
    val onlinePlayers: Int? = null,
    val maxPlayers: Int? = null,
    val version: String? = null
)
