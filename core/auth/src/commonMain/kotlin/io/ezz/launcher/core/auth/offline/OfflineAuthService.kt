package io.ezz.launcher.core.auth.offline

import io.ezz.launcher.core.model.account.OfflineAccount
import java.nio.charset.StandardCharsets
import java.util.UUID

object OfflineAuthService {
    fun createAccount(username: String): OfflineAccount {
        val trimmed = username.trim()
        require(trimmed.isNotBlank()) { "Username cannot be blank" }
        require(trimmed.length in 3..16) { "Username must be between 3 and 16 characters" }
        require(trimmed.matches(Regex("^[a-zA-Z0-9_]+$"))) { "Username can only contain alphanumeric characters and underscores" }

        // Generate offline Minecraft UUID (UUID v3 based on MD5 of "OfflinePlayer:<username>")
        val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:$trimmed".toByteArray(StandardCharsets.UTF_8)).toString()
        val accountId = "offline_$offlineUuid"

        return OfflineAccount(
            id = accountId,
            username = trimmed,
            uuid = offlineUuid,
            avatarUrl = "https://minotar.net/avatar/$trimmed/128.png",
            createdAt = System.currentTimeMillis()
        )
    }
}
