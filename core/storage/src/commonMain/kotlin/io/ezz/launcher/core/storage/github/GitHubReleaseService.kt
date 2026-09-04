package io.ezz.launcher.core.storage.github

import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.storage.repository.LauncherReleaseRepository
import io.ezz.launcher.core.storage.vault.SecureVault
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

sealed interface GitHubConnectionStatus {
    data class Connected(
        val username: String,
        val repository: String = "Krysoldev/Ezz-Launcher",
        val hasReleasePermission: Boolean = true
    ) : GitHubConnectionStatus

    data object Disconnected : GitHubConnectionStatus
    data class Error(val message: String) : GitHubConnectionStatus
}

sealed interface ReleasePublishState {
    data object Idle : ReleasePublishState
    data class Preparing(val message: String) : ReleasePublishState
    data class UploadingArtifact(val fileName: String, val progress: Float) : ReleasePublishState
    data class PublishingRelease(val version: String) : ReleasePublishState
    data class SyncingSupabase(val version: String) : ReleasePublishState
    data class Published(
        val version: String,
        val gitHubUrl: String,
        val downloadUrl: String?
    ) : ReleasePublishState
    data class Failed(val error: String, val isPartialSuccess: Boolean = false) : ReleasePublishState
}

@Serializable
private data class GitHubUserDto(val login: String = "")

@Serializable
private data class GitHubReleaseResponse(
    val id: Long = 0L,
    val html_url: String = "",
    val upload_url: String = ""
)

@Serializable
private data class GitHubAssetResponse(
    val id: Long = 0L,
    val browser_download_url: String = ""
)

class GitHubReleaseService(
    private val vault: SecureVault,
    private val releaseRepository: LauncherReleaseRepository? = null,
    private val httpClient: HttpClient = HttpClientFactory.create(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val REPO_OWNER = "Krysoldev"
        const val REPO_NAME = "Ezz-Launcher"
        private const val VAULT_KEY_GITHUB_TOKEN = "admin_github_token"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _connectionStatus = MutableStateFlow<GitHubConnectionStatus>(GitHubConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<GitHubConnectionStatus> = _connectionStatus.asStateFlow()

    init {
        checkExistingToken()
    }

    suspend fun getStoredToken(): String? = withContext(dispatcher) {
        vault.getString(VAULT_KEY_GITHUB_TOKEN)?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun connectWithToken(token: String): GitHubConnectionStatus = withContext(dispatcher) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            val status = GitHubConnectionStatus.Error("Token cannot be empty.")
            _connectionStatus.value = status
            return@withContext status
        }

        try {
            val userResp = httpClient.get("https://api.github.com/user") {
                header(HttpHeaders.Authorization, "Bearer $cleanToken")
                header("Accept", "application/vnd.github.v3+json")
            }

            if (!userResp.status.isSuccess()) {
                val status = GitHubConnectionStatus.Error("Invalid GitHub token (HTTP ${userResp.status.value}).")
                _connectionStatus.value = status
                return@withContext status
            }

            val user = json.decodeFromString<GitHubUserDto>(userResp.bodyAsText())
            vault.putString(VAULT_KEY_GITHUB_TOKEN, cleanToken)

            val status = GitHubConnectionStatus.Connected(
                username = user.login,
                repository = "$REPO_OWNER/$REPO_NAME",
                hasReleasePermission = true
            )
            _connectionStatus.value = status
            status
        } catch (e: Throwable) {
            val status = GitHubConnectionStatus.Error("Failed to connect to GitHub: ${e.message}")
            _connectionStatus.value = status
            status
        }
    }

    suspend fun disconnect(): Unit = withContext(dispatcher) {
        vault.remove(VAULT_KEY_GITHUB_TOKEN)
        _connectionStatus.value = GitHubConnectionStatus.Disconnected
    }

    fun checkExistingToken() {
        CoroutineScope(dispatcher).launch {
            val token = getStoredToken()
            if (token == null) {
                _connectionStatus.value = GitHubConnectionStatus.Disconnected
                return@launch
            }

            try {
                val userResp = httpClient.get("https://api.github.com/user") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Accept", "application/vnd.github.v3+json")
                }
                if (userResp.status.isSuccess()) {
                    val user = json.decodeFromString<GitHubUserDto>(userResp.bodyAsText())
                    _connectionStatus.value = GitHubConnectionStatus.Connected(
                        username = user.login,
                        repository = "$REPO_OWNER/$REPO_NAME"
                    )
                } else {
                    _connectionStatus.value = GitHubConnectionStatus.Disconnected
                }
            } catch (e: Throwable) {
                _connectionStatus.value = GitHubConnectionStatus.Disconnected
            }
        }
    }

    fun publishRelease(
        adminUsername: String,
        version: String,
        releaseTitle: String,
        releaseNotes: String,
        artifactFile: File?,
        isDraft: Boolean = false
    ): Flow<ReleasePublishState> = flow {
        emit(ReleasePublishState.Preparing("Validating release parameters..."))

        val cleanVer = version.trim().removePrefix("v")
        if (cleanVer.isBlank() || !cleanVer.matches(Regex("""^\d+(\.\d+)+.*$"""))) {
            emit(ReleasePublishState.Failed("Invalid semver version format (e.g. 1.0.1)."))
            return@flow
        }

        val token = getStoredToken()
        if (token.isNullOrBlank()) {
            emit(ReleasePublishState.Failed("GitHub is not connected. Please connect your GitHub account in Admin Identity."))
            return@flow
        }

        if (artifactFile != null && (!artifactFile.exists() || !artifactFile.isFile || artifactFile.length() == 0L)) {
            emit(ReleasePublishState.Failed("Selected artifact file is invalid or empty: ${artifactFile.absolutePath}"))
            return@flow
        }

        // 1. Create GitHub Release
        emit(ReleasePublishState.PublishingRelease(cleanVer))
        val releasePayload = buildJsonObject {
            put("tag_name", "v$cleanVer")
            put("name", releaseTitle.ifBlank { "Ezz Launcher v$cleanVer" })
            put("body", releaseNotes.ifBlank { "Ezz Launcher production release v$cleanVer" })
            put("draft", isDraft)
            put("prerelease", false)
        }.toString()

        val releaseResponse = try {
            val response = httpClient.post("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(releasePayload)
            }
            if (!response.status.isSuccess()) {
                emit(ReleasePublishState.Failed("GitHub API error creating release (HTTP ${response.status.value}): ${response.bodyAsText()}"))
                return@flow
            }
            json.decodeFromString<GitHubReleaseResponse>(response.bodyAsText())
        } catch (e: Throwable) {
            emit(ReleasePublishState.Failed("Failed to create GitHub release: ${e.message}"))
            return@flow
        }

        // 2. Upload Artifact (if provided)
        var artifactDownloadUrl: String? = null
        if (artifactFile != null) {
            emit(ReleasePublishState.UploadingArtifact(artifactFile.name, 0.1f))

            try {
                val fileBytes = artifactFile.readBytes()
                val uploadBaseUrl = releaseResponse.upload_url.substringBefore("{")
                val uploadUrl = "$uploadBaseUrl?name=${artifactFile.name}"

                val uploadResponse = httpClient.post(uploadUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Accept", "application/vnd.github.v3+json")
                    header("Content-Type", "application/octet-stream")
                    setBody(fileBytes)
                }

                if (!uploadResponse.status.isSuccess()) {
                    emit(ReleasePublishState.Failed(
                        "Release created on GitHub, but artifact upload failed (HTTP ${uploadResponse.status.value}): ${uploadResponse.bodyAsText()}",
                        isPartialSuccess = true
                    ))
                    return@flow
                }

                val asset = json.decodeFromString<GitHubAssetResponse>(uploadResponse.bodyAsText())
                artifactDownloadUrl = asset.browser_download_url
                emit(ReleasePublishState.UploadingArtifact(artifactFile.name, 1.0f))
            } catch (e: Throwable) {
                emit(ReleasePublishState.Failed(
                    "Release created on GitHub, but artifact upload failed: ${e.message}",
                    isPartialSuccess = true
                ))
                return@flow
            }
        }

        val effectiveDownloadUrl = artifactDownloadUrl ?: releaseResponse.html_url

        // 3. Synchronize with Supabase
        emit(ReleasePublishState.SyncingSupabase(cleanVer))
        if (releaseRepository != null) {
            val syncResult = releaseRepository.publishRelease(
                adminUsername = adminUsername,
                version = cleanVer,
                platform = "windows",
                downloadUrl = effectiveDownloadUrl,
                releaseNotes = releaseNotes,
                isLatest = !isDraft,
                isRequired = false
            )

            if (syncResult.isFailure) {
                val syncError = syncResult.exceptionOrNull()?.message ?: "Supabase sync error"
                emit(ReleasePublishState.Failed(
                    "GitHub release published successfully (${releaseResponse.html_url}), but Supabase synchronization failed: $syncError. Distribution metadata is incomplete.",
                    isPartialSuccess = true
                ))
                return@flow
            }
        }

        emit(ReleasePublishState.Published(
            version = cleanVer,
            gitHubUrl = releaseResponse.html_url,
            downloadUrl = effectiveDownloadUrl
        ))
    }.flowOn(dispatcher)
}
