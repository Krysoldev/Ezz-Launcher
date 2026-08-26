package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseFeatureFlagDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface FeatureFlagRepository {
    val flags: StateFlow<Map<String, Boolean>>
    suspend fun loadFlags(platform: String = "windows", forceRefresh: Boolean = false): Map<String, Boolean>
    suspend fun isFeatureEnabled(featureKey: String, platform: String = "windows", default: Boolean = true): Boolean
}

class SupabaseFeatureFlagRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeatureFlagRepository {

    private val _flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    override suspend fun loadFlags(platform: String, forceRefresh: Boolean): Map<String, Boolean> = withContext(dispatcher) {
        if (!forceRefresh && _flags.value.isNotEmpty()) {
            return@withContext _flags.value
        }

        try {
            val list: List<SupabaseFeatureFlagDto> = supabaseClient.select(
                table = "feature_flags",
                params = mapOf("platform" to "eq.$platform", "select" to "*")
            )
            val mapped = list.associate { it.featureKey to it.enabled }
            _flags.value = mapped
            mapped
        } catch (e: Throwable) {
            _flags.value
        }
    }

    override suspend fun isFeatureEnabled(featureKey: String, platform: String, default: Boolean): Boolean = withContext(dispatcher) {
        if (_flags.value.isEmpty()) {
            loadFlags(platform)
        }
        _flags.value[featureKey] ?: default
    }
}
