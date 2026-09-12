package io.ezz.launcher.core.storage.github

import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.storage.repository.LauncherReleaseRepository
import io.ezz.launcher.core.storage.vault.SecureVault
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

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
    data class Validating(val message: String) : ReleasePublishState
    data class PublishingGitHub(val version: String, val message: String) : ReleasePublishState
    data class UploadingArtifact(
        val fileName: String,
        val progress: Float,
        val currentFileIndex: Int,
        val totalFiles: Int
    ) : ReleasePublishState
    data class GitHubPublished(
        val version: String,
        val gitHubUrl: String,
        val assets: List<GitHubAssetDto>
    ) : ReleasePublishState
    data class SyncingSupabase(val version: String) : ReleasePublishState
    data class Published(
        val version: String,
        val gitHubUrl: String,
        val installerUrl: String?,
        val exeUrl: String?
    ) : ReleasePublishState
    data class Failed(
        val error: String,
        val stateBeforeFailure: String = "",
        val isPartialSuccess: Boolean = false,
        val existingRelease: GitHubReleaseDto? = null
    ) : ReleasePublishState
}

@Serializable
data class GitHubUserDto(val login: String = "")

@Serializable
data class GitHubAssetDto(
    val id: Long = 0L,
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GitHubReleaseDto(
    val id: Long = 0L,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList()
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

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val transferClient: HttpClient = HttpClientFactory.createLargeTransferClient()
    private val _connectionStatus = MutableStateFlow<GitHubConnectionStatus>(GitHubConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<GitHubConnectionStatus> = _connectionStatus.asStateFlow()

    init {
        checkExistingToken()
    }

    suspend fun getStoredToken(): String? = withContext(dispatcher) {
        val fromVault = vault.getString(VAULT_KEY_GITHUB_TOKEN)?.trim()?.takeIf { it.isNotBlank() }
        if (fromVault != null) return@withContext fromVault
        val envToken = System.getenv("GH_TOKEN")?.trim()?.takeIf { it.isNotBlank() }
            ?: System.getenv("GITHUB_TOKEN")?.trim()?.takeIf { it.isNotBlank() }
        envToken
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

    suspend fun fetchGitHubReleases(): Result<List<GitHubReleaseDto>> = withContext(dispatcher) {
        try {
            val token = getStoredToken()
            val response = httpClient.get("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases") {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                header("Accept", "application/vnd.github.v3+json")
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(Exception("GitHub API HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }
            val list = json.decodeFromString<List<GitHubReleaseDto>>(response.bodyAsText())
            Result.success(list)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getReleaseByTag(tag: String): Result<GitHubReleaseDto?> = withContext(dispatcher) {
        try {
            val token = getStoredToken()
            val cleanTag = if (tag.startsWith("v", ignoreCase = true)) tag else "v$tag"
            val response = httpClient.get("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/tags/$cleanTag") {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                header("Accept", "application/vnd.github.v3+json")
            }
            if (response.status.value == 404) {
                return@withContext Result.success(null)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(Exception("GitHub API HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }
            val release = json.decodeFromString<GitHubReleaseDto>(response.bodyAsText())
            Result.success(release)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun publishRelease(
        adminUsername: String,
        version: String,
        releaseTitle: String,
        releaseNotes: String,
        installerFile: File?,
        exeFile: File? = null,
        isDraft: Boolean = false,
        isRequired: Boolean = false
    ): Flow<ReleasePublishState> = flow {
        emit(ReleasePublishState.Validating("Validating release parameters and semantic versioning..."))

        val cleanVer = version.trim().removePrefix("v").removePrefix("V")
        val semverRegex = Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$""")
        if (!cleanVer.matches(semverRegex)) {
            emit(ReleasePublishState.Failed("Invalid semantic version '$version'. Must follow semver format (e.g. 1.0.1).", stateBeforeFailure = "VALIDATING"))
            return@flow
        }

        val token = getStoredToken()
        if (token.isNullOrBlank()) {
            emit(ReleasePublishState.Failed("GitHub is not connected. Connect your authorized GitHub account in Admin Manager.", stateBeforeFailure = "VALIDATING"))
            return@flow
        }

        // Validate artifacts
        val artifactsToUpload = mutableListOf<File>()
        val checksums = mutableMapOf<String, String>()

        if (installerFile != null) {
            if (!installerFile.exists() || !installerFile.isFile || installerFile.length() == 0L) {
                emit(ReleasePublishState.Failed("Installer artifact is invalid or empty: ${installerFile.absolutePath}", stateBeforeFailure = "VALIDATING"))
                return@flow
            }
            artifactsToUpload.add(installerFile)
            checksums[installerFile.name] = calculateSha256(installerFile)
        }

        if (exeFile != null) {
            if (!exeFile.exists() || !exeFile.isFile || exeFile.length() == 0L) {
                emit(ReleasePublishState.Failed("Standalone EXE artifact is invalid or empty: ${exeFile.absolutePath}", stateBeforeFailure = "VALIDATING"))
                return@flow
            }
            artifactsToUpload.add(exeFile)
            checksums[exeFile.name] = calculateSha256(exeFile)
        }

        if (artifactsToUpload.isEmpty()) {
            emit(ReleasePublishState.Failed("At least one release artifact (Installer or Standalone EXE) is required to publish.", stateBeforeFailure = "VALIDATING"))
            return@flow
        }

        // Check for existing release on GitHub (idempotent / duplicate protection)
        emit(ReleasePublishState.Validating("Checking for existing GitHub release tag 'v$cleanVer'..."))
        val existingCheck = getReleaseByTag(cleanVer)
        val existingRelease = existingCheck.getOrNull()

        // Build comprehensive release body with notes + SHA-256 Checksums
        val formattedBody = buildString {
            append(releaseNotes.ifBlank { "Ezz Launcher production release v$cleanVer" })
            append("\n\n### Official Release Artifacts & SHA-256 Checksums\n")
            artifactsToUpload.forEach { file ->
                val hash = checksums[file.name] ?: ""
                val sizeMb = String.format("%.2f MB", file.length() / (1024.0 * 1024.0))
                append("- **`${file.name}`** ($sizeMb)\n  `SHA-256: $hash`\n")
            }
        }

        val finalTitle = releaseTitle.ifBlank { "Ezz Launcher v$cleanVer" }

        // 1. Create or retrieve GitHub Release
        emit(ReleasePublishState.PublishingGitHub(cleanVer, if (existingRelease != null) "Reusing existing GitHub release (tag v$cleanVer)..." else "Creating official GitHub release tag v$cleanVer..."))

        val releaseResponse: GitHubReleaseDto = if (existingRelease != null) {
            existingRelease
        } else {
            val releasePayload = buildJsonObject {
                put("tag_name", "v$cleanVer")
                put("name", finalTitle)
                put("body", formattedBody)
                put("draft", isDraft)
                put("prerelease", false)
            }.toString()

            try {
                val response = httpClient.post("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Accept", "application/vnd.github.v3+json")
                    contentType(ContentType.Application.Json)
                    setBody(releasePayload)
                }
                if (!response.status.isSuccess()) {
                    emit(ReleasePublishState.Failed("GitHub API error creating release (HTTP ${response.status.value}): ${response.bodyAsText()}", stateBeforeFailure = "PUBLISHING_GITHUB"))
                    return@flow
                }
                json.decodeFromString<GitHubReleaseDto>(response.bodyAsText())
            } catch (e: Throwable) {
                emit(ReleasePublishState.Failed("Failed to create GitHub release: ${e.message}", stateBeforeFailure = "PUBLISHING_GITHUB"))
                return@flow
            }
        }

        // 2. Upload Artifacts
        val uploadBaseUrl = releaseResponse.uploadUrl.substringBefore("{")
        val uploadedAssets = mutableListOf<GitHubAssetDto>()
        var installerDownloadUrl: String? = null
        var exeDownloadUrl: String? = null

        val totalArtifacts = artifactsToUpload.size
        artifactsToUpload.forEachIndexed { index, artifact ->
            val fileIndex = index + 1
            emit(ReleasePublishState.UploadingArtifact(artifact.name, 0.05f, fileIndex, totalArtifacts))

            try {
                // If asset already exists on this release, delete it first to replace cleanly
                val existingAsset = releaseResponse.assets.find { it.name.equals(artifact.name, ignoreCase = true) }
                if (existingAsset != null && existingAsset.id > 0) {
                    try {
                        httpClient.delete("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/assets/${existingAsset.id}") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            header("Accept", "application/vnd.github.v3+json")
                        }
                    } catch (_: Throwable) {}
                }

                val fileBytes = artifact.readBytes()
                emit(ReleasePublishState.UploadingArtifact(artifact.name, 0.4f, fileIndex, totalArtifacts))

                val uploadUrl = "$uploadBaseUrl?name=${artifact.name}"
                val uploadResponse = transferClient.post(uploadUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Accept", "application/vnd.github.v3+json")
                    header("Content-Type", "application/octet-stream")
                    setBody(fileBytes)
                }

                if (!uploadResponse.status.isSuccess()) {
                    emit(ReleasePublishState.Failed(
                        "GitHub release created, but upload failed for ${artifact.name} (HTTP ${uploadResponse.status.value}): ${uploadResponse.bodyAsText()}",
                        stateBeforeFailure = "UPLOADING_ARTIFACT",
                        isPartialSuccess = true,
                        existingRelease = releaseResponse
                    ))
                    return@flow
                }

                val asset = json.decodeFromString<GitHubAssetDto>(uploadResponse.bodyAsText())
                uploadedAssets.add(asset)

                if (artifact.name.contains("Setup", ignoreCase = true)) {
                    installerDownloadUrl = asset.browserDownloadUrl
                } else if (artifact.name.endsWith(".exe", ignoreCase = true)) {
                    exeDownloadUrl = asset.browserDownloadUrl
                }

                emit(ReleasePublishState.UploadingArtifact(artifact.name, 1.0f, fileIndex, totalArtifacts))
            } catch (e: Throwable) {
                emit(ReleasePublishState.Failed(
                    "GitHub release created, but artifact upload failed for ${artifact.name}: ${e.message}",
                    stateBeforeFailure = "UPLOADING_ARTIFACT",
                    isPartialSuccess = true,
                    existingRelease = releaseResponse
                ))
                return@flow
            }
        }

        // Primary download URL is preferably the Setup installer, fallback to first asset or html url
        val canonicalInstallerUrl = installerDownloadUrl ?: uploadedAssets.firstOrNull()?.browserDownloadUrl ?: releaseResponse.htmlUrl
        val canonicalExeUrl = exeDownloadUrl

        emit(ReleasePublishState.GitHubPublished(
            version = cleanVer,
            gitHubUrl = releaseResponse.htmlUrl,
            assets = uploadedAssets
        ))

        // 3. Synchronize with Supabase
        emit(ReleasePublishState.SyncingSupabase(cleanVer))
        if (releaseRepository != null) {
            val syncResult = releaseRepository.publishRelease(
                adminUsername = adminUsername,
                version = cleanVer,
                platform = "windows",
                downloadUrl = canonicalInstallerUrl,
                releaseNotes = formattedBody,
                isLatest = !isDraft,
                isRequired = isRequired
            )

            if (syncResult.isFailure) {
                val syncError = syncResult.exceptionOrNull()?.message ?: "Supabase sync error"
                emit(ReleasePublishState.Failed(
                    "GitHub release published successfully (${releaseResponse.htmlUrl}), but Supabase catalog synchronization failed: $syncError. Distribution metadata can be synchronized manually.",
                    stateBeforeFailure = "SYNCING_SUPABASE",
                    isPartialSuccess = true,
                    existingRelease = releaseResponse
                ))
                return@flow
            }
        }

        emit(ReleasePublishState.Published(
            version = cleanVer,
            gitHubUrl = releaseResponse.htmlUrl,
            installerUrl = canonicalInstallerUrl,
            exeUrl = canonicalExeUrl
        ))
    }.flowOn(dispatcher)

    suspend fun syncReleaseToSupabase(
        adminUsername: String,
        release: GitHubReleaseDto,
        isLatest: Boolean = true,
        isRequired: Boolean = false
    ): Result<Unit> = withContext(dispatcher) {
        if (releaseRepository == null) {
            return@withContext Result.failure(Exception("Release repository is unavailable."))
        }

        val cleanVer = release.tagName.trim().removePrefix("v").removePrefix("V")
        val installerAsset = release.assets.find { it.name.contains("Setup", ignoreCase = true) }
            ?: release.assets.firstOrNull()

        val downloadUrl = installerAsset?.browserDownloadUrl ?: release.htmlUrl

        releaseRepository.publishRelease(
            adminUsername = adminUsername,
            version = cleanVer,
            platform = "windows",
            downloadUrl = downloadUrl,
            releaseNotes = release.body,
            isLatest = isLatest,
            isRequired = isRequired
        )
    }
}
