package io.ezz.launcher.core.auth.admin

import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthService
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.storage.repository.LauncherReleaseRepository
import io.ezz.launcher.core.storage.supabase.SupabaseClient
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
    private val supabaseClient: SupabaseClient? = null,
    private val microsoftAuthService: MicrosoftAuthService? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val AUTHORIZED_ADMIN_USERNAME = "KrysolDev"
        const val AUTHORIZED_ADMIN_UUID = "ad17221c781d4ec5aca6f5069fbced7b"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _adminStatus = MutableStateFlow<AdminStatus>(AdminStatus.NotAuthorized("Not evaluated"))
    val adminStatus: StateFlow<AdminStatus> = _adminStatus.asStateFlow()

    suspend fun verifyAdminStatus(account: Account?): AdminStatus = evaluateAccount(account)

    suspend fun evaluateAccount(account: Account?): AdminStatus = withContext(dispatcher) {
        println("[AUTH_DIAGNOSTIC] === ADMIN AUTHORIZATION EVALUATION START ===")
        if (account == null) {
            println("[AUTH_DIAGNOSTIC] 1. Account present: false (No account selected)")
            println("[AUTH_DIAGNOSTIC] 9. Final computed authorization state: NormalUser(not logged in)")
            println("[AUTH_DIAGNOSTIC] === ADMIN AUTHORIZATION EVALUATION END ===")
            val status = AdminStatus.NormalUser()
            _adminStatus.value = status
            return@withContext status
        }

        println("[AUTH_DIAGNOSTIC] 1. Microsoft login check:")
        println("[AUTH_DIAGNOSTIC]    - Account type: ${account.type}")
        println("[AUTH_DIAGNOSTIC]    - Username: '${account.username}'")
        println("[AUTH_DIAGNOSTIC]    - UUID: '${account.uuid}'")
        println("[AUTH_DIAGNOSTIC]    - Persisted account ID: '${account.id}'")

        if (account !is MicrosoftAccount) {
            println("[AUTH_DIAGNOSTIC] 2. Account is OFFLINE. Offline accounts cannot be granted admin privileges.")
            println("[AUTH_DIAGNOSTIC] 9. Final computed authorization state: NormalUser (offline)")
            println("[AUTH_DIAGNOSTIC] === ADMIN AUTHORIZATION EVALUATION END ===")
            val status = AdminStatus.NormalUser(
                minecraftUsername = account.username,
                minecraftUuid = account.uuid,
                microsoftConnected = false
            )
            _adminStatus.value = status
            return@withContext status
        }

        var activeAccount: MicrosoftAccount = account
        val timeRemaining = activeAccount.expiresAt - System.currentTimeMillis()
        val tokenExpired = activeAccount.mcAccessToken.isBlank() || timeRemaining <= 60 * 1000L

        println("[AUTH_DIAGNOSTIC] 2. Token status: hasToken=${activeAccount.mcAccessToken.isNotBlank()}, timeRemainingSec=${timeRemaining / 1000}, tokenExpired=$tokenExpired")

        if (tokenExpired && microsoftAuthService != null) {
            println("[AUTH_DIAGNOSTIC] Token expired or missing. Attempting silent session refresh via MSAL...")
            try {
                val refreshResult = microsoftAuthService.silentLogin(activeAccount)
                if (refreshResult.isSuccess) {
                    activeAccount = refreshResult.getOrThrow()
                    println("[AUTH_DIAGNOSTIC] Silent session refresh SUCCEEDED for '${activeAccount.username}'. New token valid until ${activeAccount.expiresAt}.")
                } else {
                    println("[AUTH_DIAGNOSTIC] Silent session refresh warning: ${refreshResult.exceptionOrNull()?.message}")
                }
            } catch (e: Throwable) {
                println("[AUTH_DIAGNOSTIC] Silent session refresh notice: ${e.message}")
            }
        }

        var verifiedMojangUsername: String? = null
        var verifiedMojangUuid: String? = null

        if (activeAccount.mcAccessToken.isNotBlank()) {
            try {
                println("[AUTH_DIAGNOSTIC] 2. Querying Mojang official profile endpoint...")
                val response = httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
                    header(HttpHeaders.Authorization, "Bearer ${activeAccount.mcAccessToken}")
                }
                println("[AUTH_DIAGNOSTIC] 2. Minecraft profile response: HTTP ${response.status.value}")

                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val profile = json.decodeFromString<MojangProfileResponse>(body)
                    if (profile.name.isNotBlank()) {
                        verifiedMojangUsername = profile.name
                        verifiedMojangUuid = profile.id
                        println("[AUTH_DIAGNOSTIC] 3. Minecraft username (Mojang verified): '$verifiedMojangUsername'")
                        println("[AUTH_DIAGNOSTIC] 4. Minecraft UUID/profile ID (Mojang verified): '$verifiedMojangUuid'")
                    }
                } else {
                    println("[AUTH_DIAGNOSTIC] 2. Mojang profile returned non-success HTTP ${response.status.value}")
                }
            } catch (e: Throwable) {
                println("[AUTH_DIAGNOSTIC] 2. Mojang profile request warning: ${e.message}")
            }
        } else {
            println("[AUTH_DIAGNOSTIC] 2. Minecraft access token unavailable for profile endpoint call.")
        }

        val effectiveUsername = verifiedMojangUsername ?: activeAccount.username
        val effectiveUuid = verifiedMojangUuid ?: activeAccount.uuid

        println("[AUTH_DIAGNOSTIC] 3. Minecraft username: '$effectiveUsername'")
        println("[AUTH_DIAGNOSTIC] 4. Minecraft UUID/profile ID: '$effectiveUuid'")
        println("[AUTH_DIAGNOSTIC] 5. Persisted account ID: '${activeAccount.id}'")

        val cleanAccountUuid = effectiveUuid.replace("-", "").lowercase()
        val cleanAdminUuid = AUTHORIZED_ADMIN_UUID.replace("-", "").lowercase()

        val usernameMatches = effectiveUsername.equals(AUTHORIZED_ADMIN_USERNAME, ignoreCase = true)
        val uuidMatches = cleanAccountUuid == cleanAdminUuid

        val isAuthorizedIdentity = usernameMatches || uuidMatches
        println("[AUTH_DIAGNOSTIC] Identity match check against KrysolDev:")
        println("[AUTH_DIAGNOSTIC]    - Username matches '$AUTHORIZED_ADMIN_USERNAME': $usernameMatches")
        println("[AUTH_DIAGNOSTIC]    - UUID matches '$cleanAdminUuid': $uuidMatches (found: '$cleanAccountUuid')")
        println("[AUTH_DIAGNOSTIC]    - Is authorized identity: $isAuthorizedIdentity")

        if (!isAuthorizedIdentity) {
            println("[AUTH_DIAGNOSTIC] 7. Admin lookup result: Not an authorized admin identity")
            println("[AUTH_DIAGNOSTIC] 9. Final computed authorization state: NormalUser('$effectiveUsername')")
            println("[AUTH_DIAGNOSTIC] === ADMIN AUTHORIZATION EVALUATION END ===")
            val status = AdminStatus.NormalUser(
                minecraftUsername = effectiveUsername,
                minecraftUuid = effectiveUuid,
                microsoftConnected = true
            )
            _adminStatus.value = status
            return@withContext status
        }

        val supabaseUserId = supabaseClient?.currentUserId
        println("[AUTH_DIAGNOSTIC] 6. Supabase authenticated user ID: ${supabaseUserId ?: "None (Anon/Local)"}")

        var backendApproved = true
        if (releaseRepository != null && supabaseClient?.config?.isConfigured == true) {
            try {
                val lookupByName = releaseRepository.isAdminUser(effectiveUsername)
                val lookupByUuid = releaseRepository.isAdminUser(cleanAccountUuid)
                val lookupSuccess = lookupByName || lookupByUuid
                println("[AUTH_DIAGNOSTIC] 7. Admin lookup result (Supabase RPC): name=$lookupByName, uuid=$lookupByUuid => $lookupSuccess")
                backendApproved = lookupSuccess
            } catch (e: Throwable) {
                println("[AUTH_DIAGNOSTIC] 7. Admin lookup result (Supabase RPC warning): ${e.message}. Preserving Mojang-verified admin identity.")
            }
        } else {
            println("[AUTH_DIAGNOSTIC] 7. Admin lookup result: Supabase unconfigured / local mode; verified via Microsoft & Mojang identity.")
        }

        val finalStatus = if (backendApproved && isAuthorizedIdentity) {
            println("[AUTH_DIAGNOSTIC] 9. Final computed authorization state: ADMIN VERIFIED!")
            AdminStatus.VerifiedAdmin(
                minecraftUsername = effectiveUsername,
                minecraftUuid = effectiveUuid,
                microsoftConnected = true
            )
        } else {
            println("[AUTH_DIAGNOSTIC] 9. Final computed authorization state: NOT AUTHORIZED (backend rejected)")
            AdminStatus.NotAuthorized(
                reason = "Backend rejected admin authorization for $effectiveUsername",
                minecraftUsername = effectiveUsername
            )
        }

        println("[AUTH_DIAGNOSTIC] === ADMIN AUTHORIZATION EVALUATION END ===")
        _adminStatus.value = finalStatus
        finalStatus
    }
}
