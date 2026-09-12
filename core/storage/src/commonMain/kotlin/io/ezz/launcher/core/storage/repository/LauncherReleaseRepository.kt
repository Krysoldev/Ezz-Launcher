package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface LauncherReleaseRepository {
    val latestRelease: StateFlow<SupabaseLauncherReleaseDto?>
    suspend fun getLatestRelease(platform: String = "windows"): SupabaseLauncherReleaseDto?
    suspend fun getAllReleases(platform: String = "windows"): List<SupabaseLauncherReleaseDto>
    suspend fun checkForUpdates(currentVersion: String, platform: String = "windows"): UpdateCheckResult
    suspend fun checkGitHubLatestRelease(): SupabaseLauncherReleaseDto?
    suspend fun isAdminUser(username: String): Boolean
    suspend fun publishRelease(
        adminUsername: String,
        version: String,
        platform: String = "windows",
        downloadUrl: String?,
        releaseNotes: String?,
        isLatest: Boolean = true,
        isRequired: Boolean = false
    ): Result<Unit>
    suspend fun deleteRelease(adminUsername: String, releaseId: String): Result<Unit>
    suspend fun toggleReleaseLatest(adminUsername: String, releaseId: String, isLatest: Boolean): Result<Unit>
}

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val isRequired: Boolean,
    val currentVersion: String,
    val latestRelease: SupabaseLauncherReleaseDto?
)

class SupabaseLauncherReleaseRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : LauncherReleaseRepository {

    private val _latestRelease = MutableStateFlow<SupabaseLauncherReleaseDto?>(null)
    override val latestRelease: StateFlow<SupabaseLauncherReleaseDto?> = _latestRelease.asStateFlow()

    override suspend fun getLatestRelease(platform: String): SupabaseLauncherReleaseDto? = withContext(dispatcher) {
        try {
            val releases: List<SupabaseLauncherReleaseDto> = supabaseClient.select(
                table = "launcher_releases",
                params = mapOf(
                    "platform" to "eq.$platform",
                    "is_latest" to "eq.true",
                    "is_active" to "eq.true",
                    "limit" to "1",
                    "select" to "*"
                )
            )
            val latest = releases.firstOrNull()
            _latestRelease.value = latest
            latest
        } catch (e: Throwable) {
            _latestRelease.value
        }
    }

    override suspend fun getAllReleases(platform: String): List<SupabaseLauncherReleaseDto> = withContext(dispatcher) {
        try {
            supabaseClient.select(
                table = "launcher_releases",
                params = mapOf(
                    "platform" to "eq.$platform",
                    "is_active" to "eq.true",
                    "order" to "created_at.desc",
                    "select" to "*"
                )
            )
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun checkForUpdates(currentVersion: String, platform: String): UpdateCheckResult = withContext(dispatcher) {
        var latest = getLatestRelease(platform)
        if (latest == null) {
            // Fallback: check official GitHub release directly if Supabase is unreachable or empty
            latest = checkGitHubLatestRelease()
            if (latest != null) {
                _latestRelease.value = latest
            }
        }
        if (latest == null) {
            return@withContext UpdateCheckResult(
                hasUpdate = false,
                isRequired = false,
                currentVersion = currentVersion,
                latestRelease = null
            )
        }

        val hasUpdate = isNewerVersion(latest.version, currentVersion)
        UpdateCheckResult(
            hasUpdate = hasUpdate,
            isRequired = hasUpdate && latest.isRequired,
            currentVersion = currentVersion,
            latestRelease = latest
        )
    }

    override suspend fun checkGitHubLatestRelease(): SupabaseLauncherReleaseDto? = withContext(dispatcher) {
        try {
            val response = supabaseClient.httpClient.get("https://api.github.com/repos/Krysoldev/Ezz-Launcher/releases/latest") {
                header("Accept", "application/vnd.github.v3+json")
                header("User-Agent", "EzzLauncher")
            }
            if (!response.status.isSuccess()) return@withContext null
            val body = response.bodyAsText()
            val jsonElement = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(body)
            val jsonObject = jsonElement as? JsonObject ?: return@withContext null

            val tagName = (jsonObject["tag_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@withContext null
            val version = tagName.removePrefix("v").trim()
            val title = (jsonObject["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Ezz Launcher $version"
            val releaseNotes = (jsonObject["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val publishedAt = (jsonObject["published_at"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val htmlUrl = (jsonObject["html_url"] as? kotlinx.serialization.json.JsonPrimitive)?.content

            val assets = (jsonObject["assets"] as? kotlinx.serialization.json.JsonArray) ?: emptyList()
            var installerUrl: String? = null
            var exeUrl: String? = null
            var downloadUrl: String? = null
            var fileSize: Long? = null

            for (asset in assets) {
                val assetObj = asset as? JsonObject ?: continue
                val name = (assetObj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val browserDownloadUrl = (assetObj["browser_download_url"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val size = (assetObj["size"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()

                if (name.contains("Setup", ignoreCase = true) && name.endsWith(".exe", ignoreCase = true)) {
                    installerUrl = browserDownloadUrl
                    if (downloadUrl == null) {
                        downloadUrl = browserDownloadUrl
                        fileSize = size
                    }
                } else if (name.endsWith(".exe", ignoreCase = true)) {
                    exeUrl = browserDownloadUrl
                    if (downloadUrl == null) {
                        downloadUrl = browserDownloadUrl
                        fileSize = size
                    }
                }
            }

            // Extract sha256 checksum from release notes if present
            val sha256Regex = Regex("""SHA-?256:\s*([a-fA-F0-9]{64})""", RegexOption.IGNORE_CASE)
            val sha256 = releaseNotes?.let { sha256Regex.find(it)?.groupValues?.getOrNull(1) }

            SupabaseLauncherReleaseDto(
                id = "github-$tagName",
                version = version,
                title = title,
                platform = "windows",
                downloadUrl = downloadUrl ?: installerUrl ?: exeUrl,
                installerUrl = installerUrl,
                exeUrl = exeUrl,
                githubUrl = htmlUrl,
                sha256 = sha256,
                fileSize = fileSize,
                releaseNotes = releaseNotes,
                isLatest = true,
                isRequired = false,
                isActive = true,
                publishedAt = publishedAt
            )
        } catch (e: Throwable) {
            println("[SupabaseLauncherReleaseRepository] GitHub release check fallback failed: ${e.message}")
            null
        }
    }

    override suspend fun isAdminUser(username: String): Boolean = withContext(dispatcher) {
        try {
            val response = supabaseClient.rpc(
                functionName = "is_admin_user",
                params = buildJsonObject {
                    put("lookup_username", username)
                }
            )
            response.trim().equals("true", ignoreCase = true)
        } catch (e: Throwable) {
            println("[SupabaseLauncherReleaseRepository] Warning checking admin status for '$username': ${e.message}")
            false
        }
    }

    override suspend fun publishRelease(
        adminUsername: String,
        version: String,
        platform: String,
        downloadUrl: String?,
        releaseNotes: String?,
        isLatest: Boolean,
        isRequired: Boolean
    ): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdminUser(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            supabaseClient.rpc(
                functionName = "publish_launcher_release",
                params = buildJsonObject {
                    put("p_admin_username", adminUsername)
                    put("p_version", version)
                    put("p_platform", platform)
                    downloadUrl?.let { put("p_download_url", it) }
                    releaseNotes?.let { put("p_release_notes", it) }
                    put("p_is_latest", isLatest)
                    put("p_is_required", isRequired)
                }
            )
            // Immediately refresh the cached latest release so all subscribers get the updated version
            getLatestRelease(platform)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun deleteRelease(adminUsername: String, releaseId: String): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdminUser(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            supabaseClient.delete(
                table = "launcher_releases",
                filterParams = mapOf("id" to "eq.$releaseId")
            )
            getLatestRelease("windows")
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun toggleReleaseLatest(adminUsername: String, releaseId: String, isLatest: Boolean): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdminUser(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            if (isLatest) {
                supabaseClient.update<JsonObject, JsonObject>(
                    table = "launcher_releases",
                    filterParams = mapOf("platform" to "eq.windows"),
                    bodyData = buildJsonObject { put("is_latest", false) }
                )
            }
            supabaseClient.update<JsonObject, JsonObject>(
                table = "launcher_releases",
                filterParams = mapOf("id" to "eq.$releaseId"),
                bodyData = buildJsonObject { put("is_latest", isLatest) }
            )
            getLatestRelease("windows")
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.trim().removePrefix("v").substringBefore("-")
        val cleanCurrent = current.trim().removePrefix("v").substringBefore("-")
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
