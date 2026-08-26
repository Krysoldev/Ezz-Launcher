package io.ezz.launcher.core.auth.microsoft

import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class DeviceCodeResponse(
    @SerialName("user_code") val userCode: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
    val message: String
)

sealed interface MicrosoftLoginProgress {
    data class AwaitingUserAction(
        val userCode: String,
        val verificationUrl: String,
        val expiresInSeconds: Int
    ) : MicrosoftLoginProgress

    data class Authenticating(val step: String) : MicrosoftLoginProgress
    data class Success(val account: MicrosoftAccount) : MicrosoftLoginProgress
    data class Error(val message: String) : MicrosoftLoginProgress
}

class MicrosoftAuthService(
    private val httpClient: HttpClient,
    private val clientId: String = "00000000402b5328" // Standard Minecraft OAuth Client ID
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun startLoginFlow(): Flow<MicrosoftLoginProgress> = flow {
        try {
            emit(MicrosoftLoginProgress.Authenticating("Requesting Microsoft device code..."))
            val deviceCodeResponse = requestDeviceCode()
            emit(
                MicrosoftLoginProgress.AwaitingUserAction(
                    userCode = deviceCodeResponse.userCode,
                    verificationUrl = deviceCodeResponse.verificationUri,
                    expiresInSeconds = deviceCodeResponse.expiresIn
                )
            )

            val msaToken = pollForMsaToken(
                deviceCode = deviceCodeResponse.deviceCode,
                expiresIn = deviceCodeResponse.expiresIn,
                intervalSeconds = deviceCodeResponse.interval.coerceAtLeast(3)
            )

            emit(MicrosoftLoginProgress.Authenticating("Authenticating with Xbox Live..."))
            val (xboxToken, userHash) = authenticateXboxLive(msaToken.accessToken)

            emit(MicrosoftLoginProgress.Authenticating("Requesting XSTS security token..."))
            val xstsToken = authenticateXsts(xboxToken)

            emit(MicrosoftLoginProgress.Authenticating("Logging into Minecraft Services..."))
            val mcLogin = loginWithXbox(userHash, xstsToken)

            emit(MicrosoftLoginProgress.Authenticating("Fetching Minecraft profile..."))
            val profile = fetchMinecraftProfile(mcLogin.accessToken)
            val activeSkin = profile.skins.find { it.state.equals("ACTIVE", ignoreCase = true) } ?: profile.skins.firstOrNull()

            val expiresAt = System.currentTimeMillis() + (mcLogin.expiresIn * 1000L)
            val skinTextureHash = activeSkin?.id ?: activeSkin?.url?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
            val account = MicrosoftAccount(
                id = UUID.randomUUID().toString(),
                username = profile.name,
                uuid = profile.id,
                msaRefreshToken = msaToken.refreshToken ?: "",
                mcAccessToken = mcLogin.accessToken,
                expiresAt = expiresAt,
                avatarUrl = null,
                skinUrl = activeSkin?.url,
                skinModel = activeSkin?.variant ?: "classic",
                skinHash = skinTextureHash,
                createdAt = System.currentTimeMillis()
            )

            emit(MicrosoftLoginProgress.Success(account))
        } catch (e: Exception) {
            emit(MicrosoftLoginProgress.Error(e.message ?: "Microsoft authentication failed"))
        }
    }

    suspend fun refreshToken(account: MicrosoftAccount): MicrosoftAccount {
        if (account.msaRefreshToken.isBlank()) {
            throw IllegalStateException("No refresh token available for account ${account.username}")
        }

        val refreshResponse = httpClient.submitForm(
            url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("grant_type", "refresh_token")
                append("refresh_token", account.msaRefreshToken)
                append("scope", "service::user.auth.xboxlive.com::MBI_SSL")
            }
        )

        if (!refreshResponse.status.isSuccess()) {
            throw IllegalStateException("Failed to refresh Microsoft token: ${refreshResponse.bodyAsText()}")
        }

        val msaToken = json.decodeFromString<MsaTokenResponse>(refreshResponse.bodyAsText())
        val (xboxToken, userHash) = authenticateXboxLive(msaToken.accessToken)
        val xstsToken = authenticateXsts(xboxToken)
        val mcLogin = loginWithXbox(userHash, xstsToken)
        val profile = fetchMinecraftProfile(mcLogin.accessToken)
        val activeSkin = profile.skins.find { it.state.equals("ACTIVE", ignoreCase = true) } ?: profile.skins.firstOrNull()

        val expiresAt = System.currentTimeMillis() + (mcLogin.expiresIn * 1000L)
        val skinTextureHash = activeSkin?.id ?: activeSkin?.url?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
        return account.copy(
            username = profile.name,
            uuid = profile.id,
            msaRefreshToken = msaToken.refreshToken ?: account.msaRefreshToken,
            mcAccessToken = mcLogin.accessToken,
            expiresAt = expiresAt,
            skinUrl = activeSkin?.url ?: account.skinUrl,
            skinModel = activeSkin?.variant ?: account.skinModel,
            skinHash = skinTextureHash ?: account.skinHash
        )
    }

    private suspend fun requestDeviceCode(): DeviceCodeResponse {
        val response = httpClient.submitForm(
            url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("scope", "service::user.auth.xboxlive.com::MBI_SSL")
            }
        )

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to request device code: ${response.bodyAsText()}")
        }

        return json.decodeFromString<DeviceCodeResponse>(response.bodyAsText())
    }

    private suspend fun pollForMsaToken(deviceCode: String, expiresIn: Int, intervalSeconds: Int): MsaTokenResponse {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = expiresIn * 1000L

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            delay(intervalSeconds * 1000L)

            val response = httpClient.submitForm(
                url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    append("device_code", deviceCode)
                }
            )

            val text = response.bodyAsText()
            if (response.status.isSuccess()) {
                return json.decodeFromString<MsaTokenResponse>(text)
            }

            val errorObj = try { json.decodeFromString<JsonObject>(text) } catch (e: Exception) { null }
            val error = errorObj?.get("error")?.jsonPrimitive?.content

            when (error) {
                "authorization_pending" -> continue
                "slow_down" -> delay(3000L)
                "expired_token" -> throw IllegalStateException("Microsoft login expired. Please try again.")
                else -> throw IllegalStateException("Microsoft login failed: ${error ?: text}")
            }
        }

        throw IllegalStateException("Microsoft login timed out")
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
            throw IllegalStateException("Xbox Live authentication failed: ${response.bodyAsText()}")
        }

        val jsonTree = json.decodeFromString<JsonObject>(response.bodyAsText())
        val token = jsonTree["Token"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing Xbox token")
        val userHash = jsonTree["DisplayClaims"]?.jsonObject
            ?.get("xui")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("uhs")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing Xbox User Hash (uhs)")

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
            val errorText = response.bodyAsText()
            if (errorText.contains("2148916238")) {
                throw IllegalStateException("Child account detected. An adult must add this account to a Microsoft Family.")
            } else if (errorText.contains("2148916233")) {
                throw IllegalStateException("This Microsoft account does not have an active Xbox Live profile.")
            }
            throw IllegalStateException("XSTS authorization failed: $errorText")
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
            throw IllegalStateException("Minecraft authentication failed: ${response.bodyAsText()}")
        }

        return json.decodeFromString<MinecraftLoginResponse>(response.bodyAsText())
    }

    private suspend fun fetchMinecraftProfile(mcAccessToken: String): MinecraftProfileResponse {
        val response = httpClient.get("https://api.minecraftservices.com/minecraft/profile") {
            header("Authorization", "Bearer $mcAccessToken")
        }

        if (!response.status.isSuccess()) {
            if (response.status.value == 404) {
                throw IllegalStateException("This Microsoft account does not own Minecraft Java Edition.")
            }
            throw IllegalStateException("Failed to retrieve Minecraft profile: ${response.bodyAsText()}")
        }

        return json.decodeFromString<MinecraftProfileResponse>(response.bodyAsText())
    }
}

@Serializable
private data class MsaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 3600
)

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
