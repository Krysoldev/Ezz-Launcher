package io.ezz.launcher.core.auth.admin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.storage.repository.LauncherReleaseRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AdminStatus {
    val isAuthorizedAdmin: Boolean get() = this is VerifiedAdmin

    data class VerifiedAdmin(
        val minecraftUsername: String,
        val minecraftUuid: String,
        val microsoftConnected: Boolean = true,
        val verifiedAt: Long = System.currentTimeMillis()
    ) : AdminStatus

    data class NormalUser(
        val minecraftUsername: String = "",
        val minecraftUuid: String = "",
        val microsoftConnected: Boolean = false
    ) : AdminStatus

    data class NotAuthorized(
        val reason: String = "Not authorized",
        val minecraftUsername: String? = null
    ) : AdminStatus
}

@Serializable
private data class MojangProfileResponse(
    val id: String = "",
    val name: String = ""
)

class AdminAuthorizationService(
    private val httpClient: HttpClient = HttpClientFactory.create(),
    private val releaseRepository: LauncherReleaseRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val AUTHORIZED_ADMIN_USERNAME = "KrysolDev"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _adminStatus = MutableStateFlow<AdminStatus>(AdminStatus.NotAuthorized("Not evaluated"))
    val adminStatus: StateFlow<AdminStatus> = _adminStatus.asStateFlow()

    suspend fun verifyAdminStatus(account: Account?): AdminStatus = evaluateAccount(account)

    suspend fun evaluateAccount(account: Account?): AdminStatus = withContext(dispatcher) {
        val result = when (account) {
            null -> AdminStatus.NotAuthorized("No account selected.")
            is OfflineAccount -> {
                // Offline accounts can NEVER be granted admin privileges, even if named "KrysolDev"
                AdminStatus.NormalUser(
                    minecraftUsername = account.username,
                    minecraftUuid = account.uuid,
                    microsoftConnected = false
                )
            }
            is MicrosoftAccount -> {
                verifyMicrosoftAccount(account)
            }
            else -> AdminStatus.NotAuthorized("Unsupported account type.", account.username)
        }

        _adminStatus.value = result
        result
    }

    private suspend fun verifyMicrosoftAccount(account: MicrosoftAccount): AdminStatus {
        if (account.mcAccessToken.isBlank()) {
            return AdminStatus.NotAuthorized("Missing Minecraft access token.", account.username)
        }

        return try {
            // Cryptographically verify identity against Mojang's official endpoint
            val response = httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
                header(HttpHeaders.Authorization, "Bearer ${account.mcAccessToken}")
            }

            if (!response.status.isSuccess()) {
                return if (response.status.value == 401) {
                    AdminStatus.NotAuthorized("Microsoft session expired. Please re-authenticate.", account.username)
                } else {
                    AdminStatus.NotAuthorized("Could not verify profile with Mojang (HTTP ${response.status.value}).", account.username)
                }
            }

            val body = response.bodyAsText()
            val profile = json.decodeFromString<MojangProfileResponse>(body)

            if (profile.name.isBlank()) {
                return AdminStatus.NotAuthorized("Mojang profile returned empty username.", account.username)
            }

            // Verify if username matches authorized admin
            if (profile.name.equals(AUTHORIZED_ADMIN_USERNAME, ignoreCase = true)) {
                // Cross-verify server-side in Supabase if repository is available
                val serverConfirmed = releaseRepository?.isAdminUser(AUTHORIZED_ADMIN_USERNAME) ?: true
                if (serverConfirmed) {
                    AdminStatus.VerifiedAdmin(
                        minecraftUsername = profile.name,
                        minecraftUuid = profile.id,
                        microsoftConnected = true
                    )
                } else {
                    AdminStatus.NotAuthorized("Backend rejected admin authorization for ${profile.name}.", profile.name)
                }
            } else {
                AdminStatus.NormalUser(
                    minecraftUsername = profile.name,
                    minecraftUuid = profile.id,
                    microsoftConnected = true
                )
            }
        } catch (e: Throwable) {
            println("[AdminAuthorizationService] Warning during identity verification: ${e.message}")
            // Fallback: If Mojang network fails, inspect local Microsoft account details safely
            if (account.username.equals(AUTHORIZED_ADMIN_USERNAME, ignoreCase = true) && account.expiresAt > System.currentTimeMillis()) {
                AdminStatus.VerifiedAdmin(
                    minecraftUsername = account.username,
                    minecraftUuid = account.uuid,
                    microsoftConnected = true
                )
            } else {
                AdminStatus.NormalUser(
                    minecraftUsername = account.username,
                    minecraftUuid = account.uuid,
                    microsoftConnected = true
                )
            }
        }
    }
}
