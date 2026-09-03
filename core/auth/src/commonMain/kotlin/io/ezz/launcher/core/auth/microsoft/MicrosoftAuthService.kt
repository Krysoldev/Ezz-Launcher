package io.ezz.launcher.core.auth.microsoft

import com.microsoft.aad.msal4j.IAccount
import com.microsoft.aad.msal4j.IAuthenticationResult
import com.microsoft.aad.msal4j.InteractiveRequestParameters
import com.microsoft.aad.msal4j.MsalClientException
import com.microsoft.aad.msal4j.MsalInteractionRequiredException
import com.microsoft.aad.msal4j.Prompt
import com.microsoft.aad.msal4j.PublicClientApplication
import com.microsoft.aad.msal4j.SilentParameters
import com.microsoft.aad.msal4j.SystemBrowserOptions
import com.microsoft.aad.msal4jextensions.PersistenceSettings
import com.microsoft.aad.msal4jextensions.PersistenceTokenCacheAccessAspect
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.CompletableFuture

sealed interface MicrosoftAuthState {
    data object Idle : MicrosoftAuthState
    data class OpeningBrowser(val message: String = "Opening Microsoft sign-in...") : MicrosoftAuthState
    data class EmbeddedBrowser(val authUrl: String) : MicrosoftAuthState
    data class CompletingMicrosoftAuth(val message: String = "Completing Microsoft authentication...") : MicrosoftAuthState
    data class ConnectingToXboxLive(val message: String = "Connecting to Xbox Live...") : MicrosoftAuthState
    data class ConnectingToMinecraftServices(val message: String = "Connecting to Minecraft Services...") : MicrosoftAuthState
    data class Success(val account: MicrosoftAccount) : MicrosoftAuthState
    data object Cancelled : MicrosoftAuthState
    data class Failed(val message: String, val technicalDetails: String? = null, val canRetry: Boolean = true) : MicrosoftAuthState

    // Backward-compatibility aliases
    data object ConnectingToMicrosoft : MicrosoftAuthState
    data object WaitingForMicrosoft : MicrosoftAuthState
    data object SigningIn : MicrosoftAuthState
    data class CompletingMinecraftAuth(val message: String = "Completing Minecraft authentication...") : MicrosoftAuthState
    data class Ready(val account: MicrosoftAccount) : MicrosoftAuthState
    data object Authenticating : MicrosoftAuthState
    data class MinecraftProfileLoading(val message: String = "Connecting to Minecraft Services...") : MicrosoftAuthState
}

class MicrosoftAuthService(
    private val httpClient: HttpClient,
    private val clientId: String = "074d6e3a-87dc-4d22-a3d7-0bde23144b0c",
    private var cacheDir: File? = null
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val authority = "https://login.microsoftonline.com/consumers/"
    private val scopes = setOf("XboxLive.signin", "offline_access")

    private var pca: PublicClientApplication? = null
    private var activeAuthFuture: CompletableFuture<IAuthenticationResult>? = null

    /**
     * Initializes the MSAL PublicClientApplication.
     * Configures DPAPI-backed token cache persistence if cacheDirectory is provided.
     */
    @Synchronized
    fun initialize(windowHandle: Long? = null, cacheDirectory: File? = null): Boolean {
        if (pca != null) return true
        this.cacheDir = cacheDirectory

        try {
            val builder = PublicClientApplication.builder(clientId)
                .authority(authority)

            attachPersistenceAspect(builder, cacheDirectory)

            pca = builder.build()
            println("[MicrosoftAuthService] Microsoft OAuth client initialized (PublicClientApplication).")
            return true
        } catch (e: Throwable) {
            println("[MicrosoftAuthService] Failed to initialize Microsoft OAuth client: ${e.message}")
            return false
        }
    }

    fun setWindowHandle(handle: Long) {
        // Retained for backward compatibility
    }

    /**
     * Cancels any currently pending interactive sign-in flow.
     */
    fun cancelActiveLogin() {
        try {
            activeAuthFuture?.cancel(true)
            activeAuthFuture = null
            println("[MicrosoftAuthService] Cancelled active authentication request.")
        } catch (e: Throwable) {
            println("[MicrosoftAuthService] Notice cancelling active login: ${e.message}")
        }
    }

    private fun attachPersistenceAspect(builder: PublicClientApplication.Builder, cacheDirectory: File?) {
        if (cacheDirectory == null) return
        try {
            cacheDirectory.mkdirs()
            val persistenceSettings = PersistenceSettings.builder("msal_cache.bin", cacheDirectory.toPath())
                .setLockRetry(1000, 50)
                .build()
            val aspect = PersistenceTokenCacheAccessAspect(persistenceSettings)
            builder.setTokenCacheAccessAspect(aspect)
            println("[MicrosoftAuthService] Persistent token cache attached.")
        } catch (e: Throwable) {
            println("[MicrosoftAuthService] Persistent token cache unavailable: ${e.message}. Using in-memory cache.")
        }
    }

    private fun ensurePca(): PublicClientApplication {
        return pca ?: run {
            initialize(cacheDirectory = cacheDir)
            pca ?: throw IllegalStateException("Microsoft authentication client could not be initialized.")
        }
    }

    /**
     * Executes interactive Microsoft sign-in using an in-launcher embedded browser
     * with OAuth 2.0 Authorization Code + PKCE, followed by the complete Minecraft authentication pipeline.
     */
    fun login(windowHandle: Long? = null): Flow<MicrosoftAuthState> = flow {
        println("[MicrosoftAuthService] OAuth flow started")
        emit(MicrosoftAuthState.OpeningBrowser())

        val app = ensurePca()
        val authResult: IAuthenticationResult
        val urlChannel = Channel<String>(1)

        val systemBrowserOptions = SystemBrowserOptions.builder()
            .openBrowserAction { url ->
                println("[MicrosoftAuthService] PKCE generated & Microsoft authorization URL created")
                println("[MicrosoftAuthService] Microsoft authorization opened in embedded browser")
                urlChannel.trySend(url.toString())
            }
            .build()

        val params = InteractiveRequestParameters.builder(URI("http://localhost"))
            .scopes(scopes)
            .prompt(Prompt.SELECT_ACCOUNT)
            .systemBrowserOptions(systemBrowserOptions)
            .build()

        val future = app.acquireToken(params)
        activeAuthFuture = future

        try {
            val authUrl = urlChannel.receive()
            emit(MicrosoftAuthState.EmbeddedBrowser(authUrl))

            authResult = withContext(Dispatchers.IO) {
                future.get()
            }
            println("[MicrosoftAuthService] Authorization response received & state validation passed")
            println("[MicrosoftAuthService] Authorization code exchange completed. Microsoft token acquired.")
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            if (isUserCancelled(cause) || future.isCancelled) {
                println("[MicrosoftAuthService] Microsoft authentication was cancelled by user.")
                emit(MicrosoftAuthState.Cancelled)
            } else {
                println("[MicrosoftAuthService] Microsoft authentication failed: ${cause.message}")
                emit(MicrosoftAuthState.Failed(
                    message = cause.message ?: "Microsoft authentication failed",
                    technicalDetails = cause.stackTraceToString()
                ))
            }
            return@flow
        } finally {
            activeAuthFuture = null
        }

        emit(MicrosoftAuthState.CompletingMicrosoftAuth())
        emit(MicrosoftAuthState.ConnectingToXboxLive())

        try {
            val msaToken = authResult.accessToken()
            val msalAccount = authResult.account()

            println("[MicrosoftAuthService] Xbox Live authentication started")
            val (xboxToken, userHash) = authenticateXboxLive(msaToken)

            emit(MicrosoftAuthState.ConnectingToMinecraftServices())
            println("[MicrosoftAuthService] XSTS authentication started")
            val xstsToken = authenticateXsts(xboxToken)

            println("[MicrosoftAuthService] Minecraft Services authentication started")
            val mcLogin = loginWithXbox(userHash, xstsToken)

            println("[MicrosoftAuthService] Minecraft profile retrieval started")
            val profile = fetchMinecraftProfile(mcLogin.accessToken)

            val activeSkin = profile.skins.find { it.state.equals("ACTIVE", ignoreCase = true) } ?: profile.skins.firstOrNull()
            val expiresAt = System.currentTimeMillis() + (mcLogin.expiresIn * 1000L)
            val skinTextureHash = activeSkin?.id ?: activeSkin?.url?.substringAfterLast("/")?.takeIf { it.isNotBlank() }

            val mcAccount = MicrosoftAccount(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
                username = profile.name,
                uuid = profile.id,
                msaRefreshToken = "",
                mcAccessToken = mcLogin.accessToken,
                expiresAt = expiresAt,
                msalAccountId = msalAccount?.homeAccountId(),
                avatarUrl = null,
                skinUrl = activeSkin?.url,
                skinModel = activeSkin?.variant ?: "classic",
                skinHash = skinTextureHash,
                createdAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )

            println("[MicrosoftAuthService] Login completed successfully for ${mcAccount.username} (${mcAccount.uuid})")
            emit(MicrosoftAuthState.Success(mcAccount))
            emit(MicrosoftAuthState.Ready(mcAccount))
        } catch (e: Throwable) {
            emit(mapMinecraftError(e))
        }
    }

    /**
     * Silently acquires a token for an existing account without prompting the user.
     */
    suspend fun silentLogin(account: MicrosoftAccount): Result<MicrosoftAccount> = withContext(Dispatchers.IO) {
        try {
            // If the Minecraft token is still valid with > 5 minutes remaining, use it directly
            val timeRemaining = account.expiresAt - System.currentTimeMillis()
            if (timeRemaining > 5 * 60 * 1000L && account.mcAccessToken.isNotBlank()) {
                return@withContext Result.success(account)
            }

            println("[MicrosoftAuthService] Silent token refresh starting for '${account.username}'...")
            val msaToken = acquireToken(account = account, interactiveIfRequired = false)
            println("[MicrosoftAuthService] Silent token refresh succeeded for '${account.username}'. Refreshing Minecraft session...")
            val refreshed = getMinecraftAccount(msaAccessToken = msaToken, existingAccount = account)
            Result.success(refreshed)
        } catch (e: Throwable) {
            println("[MicrosoftAuthService] Silent login notice for ${account.username}: ${e.message}")
            Result.failure(Exception("Your Microsoft session expired. Please sign in again."))
        }
    }

    /**
     * Refreshes the session for an existing Microsoft account.
     */
    suspend fun refreshSession(account: MicrosoftAccount): MicrosoftAccount {
        val refreshedResult = silentLogin(account)
        return refreshedResult.getOrThrow()
    }

    /**
     * Acquires an MSA access token, attempting silent acquisition first.
     */
    suspend fun acquireToken(
        account: MicrosoftAccount? = null,
        interactiveIfRequired: Boolean = false,
        windowHandle: Long? = null
    ): String = withContext(Dispatchers.IO) {
        val app = ensurePca()

        if (account != null) {
            val accounts = app.accounts.get() ?: emptySet()
            val msalAccount = accounts.firstOrNull {
                (account.msalAccountId != null && it.homeAccountId() == account.msalAccountId) ||
                it.username().equals(account.username, ignoreCase = true)
            } ?: accounts.firstOrNull()

            if (msalAccount != null) {
                try {
                    val silentParams = SilentParameters.builder(scopes, msalAccount).build()
                    val result = app.acquireTokenSilently(silentParams).get()
                    if (result?.accessToken() != null) {
                        return@withContext result.accessToken()
                    }
                } catch (e: Throwable) {
                    val cause = e.cause ?: e
                    if (cause !is MsalInteractionRequiredException && cause !is MsalClientException) {
                        println("[MicrosoftAuthService] Silent token notice: ${cause.message}")
                    }
                }
            }
        }

        throw IllegalStateException("Your Microsoft session expired. Please sign in again.")
    }

    /**
     * Removes the account from the MSAL token cache.
     */
    suspend fun logout(account: MicrosoftAccount) = withContext(Dispatchers.IO) {
        try {
            val app = pca ?: return@withContext
            val accounts = app.accounts.get() ?: return@withContext
            val matched = accounts.firstOrNull {
                (account.msalAccountId != null && it.homeAccountId() == account.msalAccountId) ||
                it.username().equals(account.username, ignoreCase = true)
            }
            if (matched != null) {
                app.removeAccount(matched).get()
                println("[MicrosoftAuthService] Logged out MSAL account: ${matched.username()}")
            }
        } catch (e: Throwable) {
            println("[MicrosoftAuthService] Notice during logout: ${e.message}")
        }
    }

    /**
     * Completes the entire Minecraft authentication pipeline:
     * MSA Token -> Xbox Live -> XSTS -> Minecraft Services -> Minecraft Profile & Java Ownership.
     */
    suspend fun getMinecraftAccount(
        msaAccessToken: String,
        msalAccount: IAccount? = null,
        existingAccount: MicrosoftAccount? = null
    ): MicrosoftAccount = withContext(Dispatchers.IO) {
        println("[MicrosoftAuthService] Authenticating with Xbox Live...")
        val (xboxToken, userHash) = authenticateXboxLive(msaAccessToken)
        println("[MicrosoftAuthService] Authenticating with XSTS...")
        val xstsToken = authenticateXsts(xboxToken)
        println("[MicrosoftAuthService] Authenticating with Minecraft Services...")
        val mcLogin = loginWithXbox(userHash, xstsToken)
        println("[MicrosoftAuthService] Fetching Minecraft profile...")
        val profile = fetchMinecraftProfile(mcLogin.accessToken)

        val activeSkin = profile.skins.find { it.state.equals("ACTIVE", ignoreCase = true) } ?: profile.skins.firstOrNull()
        val expiresAt = System.currentTimeMillis() + (mcLogin.expiresIn * 1000L)
        val skinTextureHash = activeSkin?.id ?: activeSkin?.url?.substringAfterLast("/")?.takeIf { it.isNotBlank() }

        val id = existingAccount?.id ?: UUID.randomUUID().toString()
        val msalId = msalAccount?.homeAccountId() ?: existingAccount?.msalAccountId

        MicrosoftAccount(
            id = id,
            username = profile.name,
            uuid = profile.id,
            msaRefreshToken = existingAccount?.msaRefreshToken ?: "",
            mcAccessToken = mcLogin.accessToken,
            expiresAt = expiresAt,
            msalAccountId = msalId,
            avatarUrl = existingAccount?.avatarUrl,
            skinUrl = activeSkin?.url ?: existingAccount?.skinUrl,
            skinModel = activeSkin?.variant ?: existingAccount?.skinModel ?: "classic",
            skinHash = skinTextureHash ?: existingAccount?.skinHash,
            createdAt = existingAccount?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
    }

    private suspend fun authenticateXboxLive(msaAccessToken: String): Pair<String, String> {
        val payload = """
            {
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": "d=$msaAccessToken"
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT"
            }
        """.trimIndent()

        val response = httpClient.post("https://user.auth.xboxlive.com/user/authenticate") {
            contentType(ContentType.Application.Json)
            header("x-xbl-contract-version", "1")
            setBody(payload)
        }

        if (!response.status.isSuccess()) {
            println("[MicrosoftAuthService] Xbox Live authentication failed: HTTP ${response.status.value}")
            throw IllegalStateException("Xbox Live authentication failed: ${response.bodyAsText()}")
        }

        val jsonTree = json.decodeFromString<JsonObject>(response.bodyAsText())
        val token = jsonTree["Token"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing Xbox token")
        val userHash = jsonTree["DisplayClaims"]?.jsonObject
            ?.get("xui")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("uhs")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing user hash")

        return Pair(token, userHash)
    }

    private suspend fun authenticateXsts(xboxToken: String): String {
        val payload = """
            {
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": ["$xboxToken"]
                },
                "RelyingParty": "rp://api.minecraftservices.com/",
                "TokenType": "JWT"
            }
        """.trimIndent()

        val response = httpClient.post("https://xsts.auth.xboxlive.com/xsts/authorize") {
            contentType(ContentType.Application.Json)
            header("x-xbl-contract-version", "1")
            setBody(payload)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            println("[MicrosoftAuthService] XSTS authorization failed: HTTP ${response.status.value}")
            if (body.contains("2148916238")) {
                throw IllegalStateException("The account is a child account and must be added to a Microsoft Family to play Minecraft.")
            }
            if (body.contains("2148916233")) {
                throw IllegalStateException("The account does not have an active Xbox Live account. Please sign in to xbox.com first.")
            }
            if (body.contains("2148916235")) {
                throw IllegalStateException("Xbox Live is not available in your country/region.")
            }
            throw IllegalStateException("XSTS authorization failed: $body")
        }

        val jsonTree = json.decodeFromString<JsonObject>(response.bodyAsText())
        return jsonTree["Token"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing XSTS token")
    }

    private suspend fun loginWithXbox(userHash: String, xstsToken: String): MinecraftLoginResponse {
        val payload = """
            {
                "identityToken": "XBL3.0 x=$userHash;$xstsToken"
            }
        """.trimIndent()

        val response = httpClient.post("https://api.minecraftservices.com/authentication/login_with_xbox") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (!response.status.isSuccess()) {
            println("[MicrosoftAuthService] Minecraft Services login failed: HTTP ${response.status.value}")
            throw IllegalStateException("Failed to login to Minecraft Services: ${response.bodyAsText()}")
        }

        return json.decodeFromString<MinecraftLoginResponse>(response.bodyAsText())
    }

    private suspend fun fetchMinecraftProfile(mcAccessToken: String): MinecraftProfileResponse {
        val response = httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
            header("Authorization", "Bearer $mcAccessToken")
        }

        if (!response.status.isSuccess()) {
            if (response.status.value == 404) {
                println("[MicrosoftAuthService] Minecraft Java Edition license 404: account does not own Minecraft Java Edition.")
                throw MinecraftJavaNotFoundException("Microsoft account connected, but Minecraft Java Edition was not found on this account.")
            }
            println("[MicrosoftAuthService] Failed to retrieve Minecraft profile: HTTP ${response.status.value}")
            throw IllegalStateException("Failed to retrieve Minecraft profile: ${response.bodyAsText()}")
        }

        return json.decodeFromString<MinecraftProfileResponse>(response.bodyAsText())
    }

    private fun isUserCancelled(e: Throwable): Boolean {
        if (e is java.util.concurrent.CancellationException) return true
        if (e is kotlinx.coroutines.CancellationException) return true
        val msg = (e.message ?: "").lowercase()
        return msg.contains("cancel") || msg.contains("window_closed") || msg.contains("user_cancel") || msg.contains("closed")
    }

    private fun mapMinecraftError(e: Throwable): MicrosoftAuthState {
        val cause = e.cause ?: e
        if (cause is MinecraftJavaNotFoundException) {
            return MicrosoftAuthState.Failed(
                cause.message ?: "Microsoft account connected, but Minecraft Java Edition was not found on this account.",
                canRetry = false
            )
        }
        if (cause is UnknownHostException || cause is ConnectException || cause is SocketTimeoutException || cause is SocketException) {
            return MicrosoftAuthState.Failed("Unable to connect to Microsoft services. Check your internet connection and try again.")
        }
        val msg = cause.message ?: "Minecraft authentication failed"
        return MicrosoftAuthState.Failed(msg)
    }
}

class MinecraftJavaNotFoundException(message: String) : Exception(message)

@Serializable
private data class MinecraftLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int = 86400
)

@Serializable
private data class MinecraftSkinDto(
    val id: String? = null,
    val state: String? = null,
    val url: String? = null,
    val variant: String? = null
)

@Serializable
private data class MinecraftProfileResponse(
    val id: String,
    val name: String,
    val skins: List<MinecraftSkinDto> = emptyList()
)
