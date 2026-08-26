package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherNewsDto
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReleaseRepository {
    suspend fun getLatestRelease(): SupabaseLauncherReleaseDto?
    suspend fun getAllReleases(): List<SupabaseLauncherReleaseDto>
    suspend fun getLauncherNews(): List<SupabaseLauncherNewsDto>
}

class SupabaseReleaseRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReleaseRepository {

    override suspend fun getLatestRelease(): SupabaseLauncherReleaseDto? = withContext(dispatcher) {
        val releases: List<SupabaseLauncherReleaseDto> = supabaseClient.select(
            table = "launcher_releases",
            params = mapOf("is_latest" to "eq.true", "limit" to "1", "select" to "*")
        )
        releases.firstOrNull()
    }

    override suspend fun getAllReleases(): List<SupabaseLauncherReleaseDto> = withContext(dispatcher) {
        supabaseClient.select(
            table = "launcher_releases",
            params = mapOf("order" to "released_at.desc", "select" to "*")
        )
    }

    override suspend fun getLauncherNews(): List<SupabaseLauncherNewsDto> = withContext(dispatcher) {
        supabaseClient.select(
            table = "launcher_news",
            params = mapOf("order" to "published_at.desc", "limit" to "10", "select" to "*")
        )
    }
}
