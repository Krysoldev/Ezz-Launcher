package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface LauncherReleaseRepository {
    val latestRelease: StateFlow<SupabaseLauncherReleaseDto?>
    suspend fun getLatestRelease(platform: String = "windows"): SupabaseLauncherReleaseDto?
    suspend fun getAllReleases(platform: String = "windows"): List<SupabaseLauncherReleaseDto>
    suspend fun checkForUpdates(currentVersion: String, platform: String = "windows"): UpdateCheckResult
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
        val latest = getLatestRelease(platform) ?: return@withContext UpdateCheckResult(
            hasUpdate = false,
            isRequired = false,
            currentVersion = currentVersion,
            latestRelease = null
        )

        val hasUpdate = isNewerVersion(latest.version, currentVersion)
        UpdateCheckResult(
            hasUpdate = hasUpdate,
            isRequired = hasUpdate && latest.isRequired,
            currentVersion = currentVersion,
            latestRelease = latest
        )
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

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.equals(current, ignoreCase = true)) return false
        val latestParts = latest.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
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
