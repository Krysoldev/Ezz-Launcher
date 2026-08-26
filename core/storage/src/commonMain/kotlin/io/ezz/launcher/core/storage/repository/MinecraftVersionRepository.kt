package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftVersionDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface MinecraftVersionRepository {
    val supportedVersions: StateFlow<List<SupabaseMinecraftVersionDto>>
    suspend fun getSupportedVersions(forceRefresh: Boolean = false): List<SupabaseMinecraftVersionDto>
    suspend fun getVersion(version: String): SupabaseMinecraftVersionDto?
}

class SupabaseMinecraftVersionRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : MinecraftVersionRepository {

    private val _supportedVersions = MutableStateFlow<List<SupabaseMinecraftVersionDto>>(emptyList())
    override val supportedVersions: StateFlow<List<SupabaseMinecraftVersionDto>> = _supportedVersions.asStateFlow()

    override suspend fun getSupportedVersions(forceRefresh: Boolean): List<SupabaseMinecraftVersionDto> = withContext(dispatcher) {
        if (!forceRefresh && _supportedVersions.value.isNotEmpty()) {
            return@withContext _supportedVersions.value
        }
        try {
            val versions: List<SupabaseMinecraftVersionDto> = supabaseClient.select(
                table = "minecraft_versions",
                params = mapOf(
                    "is_supported" to "eq.true",
                    "is_available" to "eq.true",
                    "order" to "release_date.desc.nullslast",
                    "select" to "*"
                )
            )
            _supportedVersions.value = versions
            versions
        } catch (e: Throwable) {
            _supportedVersions.value
        }
    }

    override suspend fun getVersion(version: String): SupabaseMinecraftVersionDto? = withContext(dispatcher) {
        val cached = _supportedVersions.value.find { it.version == version }
        if (cached != null) return@withContext cached

        try {
            val versions: List<SupabaseMinecraftVersionDto> = supabaseClient.select(
                table = "minecraft_versions",
                params = mapOf("version" to "eq.$version", "limit" to "1", "select" to "*")
            )
            versions.firstOrNull()
        } catch (e: Throwable) {
            null
        }
    }
}
