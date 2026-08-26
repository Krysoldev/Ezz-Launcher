package io.ezz.launcher.core.model.account

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    OFFLINE,
    MICROSOFT
}

@Serializable
sealed interface Account {
    val id: String
    val username: String
    val uuid: String
    val type: AccountType
    val createdAt: Long
    val avatarUrl: String?
}

@Serializable
data class OfflineAccount(
    override val id: String,
    override val username: String,
    override val uuid: String,
    override val createdAt: Long = 0L,
    override val avatarUrl: String? = null
) : Account {
    override val type: AccountType = AccountType.OFFLINE
}

@Serializable
data class MicrosoftAccount(
    override val id: String,
    override val username: String,
    override val uuid: String,
    val msaRefreshToken: String,
    val mcAccessToken: String,
    val expiresAt: Long,
    override val avatarUrl: String? = null,
    override val createdAt: Long = 0L
) : Account {
    override val type: AccountType = AccountType.MICROSOFT
}
