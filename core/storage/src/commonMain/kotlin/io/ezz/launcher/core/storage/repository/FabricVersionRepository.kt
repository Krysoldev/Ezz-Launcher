package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseFabricVersionDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface FabricVersionRepository {
    val fabricVersions: StateFlow<List<SupabaseFabricVersionDto>>
    suspend fun getFabricVersions(minecraftVersion: String? = null, forceRefresh: Boolean = false): List<SupabaseFabricVersionDto>
    suspend fun getLatestLoaderVersion(minecraftVersion: String): String?
}

class SupabaseFabricVersionRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FabricVersionRepository {

    private val _fabricVersions = MutableStateFlow<List<SupabaseFabricVersionDto>>(emptyList())
    override val fabricVersions: StateFlow<List<SupabaseFabricVersionDto>> = _fabricVersions.asStateFlow()

    override suspend fun getFabricVersions(minecraftVersion: String?, forceRefresh: Boolean): List<SupabaseFabricVersionDto> = withContext(dispatcher) {
        if (!forceRefresh && _fabricVersions.value.isNotEmpty()) {
            return@withContext if (minecraftVersion != null) {
                _fabricVersions.value.filter { it.minecraftVersion == minecraftVersion }
            } else {
                _fabricVersions.value
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

            val versions: List<SupabaseFabricVersionDto> = supabaseClient.select(
                table = "fabric_versions",
                params = params
            )
            if (minecraftVersion == null) {
                _fabricVersions.value = versions
            }
            versions
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun getLatestLoaderVersion(minecraftVersion: String): String? = withContext(dispatcher) {
        val versions = getFabricVersions(minecraftVersion)
        versions.firstOrNull()?.loaderVersion
    }
}
