package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseOptiFineVersionDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface OptiFineVersionRepository {
    val optifineVersions: StateFlow<List<SupabaseOptiFineVersionDto>>
    suspend fun getOptiFineVersions(minecraftVersion: String? = null, forceRefresh: Boolean = false): List<SupabaseOptiFineVersionDto>
    suspend fun getOptiFineVersion(minecraftVersion: String): SupabaseOptiFineVersionDto?
}

class SupabaseOptiFineVersionRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : OptiFineVersionRepository {

    private val _optifineVersions = MutableStateFlow<List<SupabaseOptiFineVersionDto>>(emptyList())
    override val optifineVersions: StateFlow<List<SupabaseOptiFineVersionDto>> = _optifineVersions.asStateFlow()

    override suspend fun getOptiFineVersions(minecraftVersion: String?, forceRefresh: Boolean): List<SupabaseOptiFineVersionDto> = withContext(dispatcher) {
        if (!forceRefresh && _optifineVersions.value.isNotEmpty()) {
            return@withContext if (minecraftVersion != null) {
                _optifineVersions.value.filter { it.minecraftVersion == minecraftVersion }
            } else {
                _optifineVersions.value
            }
        }

        try {
            val params = mutableMapOf(
                "is_supported" to "eq.true",
                "is_active" to "eq.true",
                "select" to "*"
            )
            if (minecraftVersion != null) {
                params["minecraft_version"] = "eq.$minecraftVersion"
            }

            val versions: List<SupabaseOptiFineVersionDto> = supabaseClient.select(
                table = "optifine_versions",
                params = params
            )
            if (minecraftVersion == null) {
                _optifineVersions.value = versions
            }
            versions
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun getOptiFineVersion(minecraftVersion: String): SupabaseOptiFineVersionDto? = withContext(dispatcher) {
        val versions = getOptiFineVersions(minecraftVersion)
        versions.firstOrNull()
    }
}
