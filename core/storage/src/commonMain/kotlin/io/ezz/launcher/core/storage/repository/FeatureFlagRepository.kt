package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseFeatureFlagDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface FeatureFlagRepository {
    val flags: StateFlow<Map<String, Boolean>>
    suspend fun loadFlags(platform: String = "windows", forceRefresh: Boolean = false): Map<String, Boolean>
    suspend fun getAllFlags(platform: String = "windows", forceRefresh: Boolean = false): List<SupabaseFeatureFlagDto>
    suspend fun isFeatureEnabled(featureKey: String, platform: String = "windows", default: Boolean = true): Boolean
    suspend fun updateFlag(adminUsername: String, featureKey: String, enabled: Boolean, platform: String = "windows"): Result<Unit>
}

class SupabaseFeatureFlagRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeatureFlagRepository {

    private val _flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    private suspend fun isAdmin(adminUsername: String): Boolean {
        return try {
            val response = supabaseClient.rpc(
                functionName = "is_admin_user",
                params = buildJsonObject {
                    put("lookup_username", adminUsername)
                }
            )
            response.trim().equals("true", ignoreCase = true)
        } catch (e: Throwable) {
            false
        }
    }

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

    override suspend fun getAllFlags(platform: String, forceRefresh: Boolean): List<SupabaseFeatureFlagDto> = withContext(dispatcher) {
        try {
            supabaseClient.select(
                table = "feature_flags",
                params = mapOf("platform" to "eq.$platform", "order" to "feature_key.asc", "select" to "*")
            )
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun isFeatureEnabled(featureKey: String, platform: String, default: Boolean): Boolean = withContext(dispatcher) {
        if (_flags.value.isEmpty()) {
            loadFlags(platform)
        }
        _flags.value[featureKey] ?: default
    }

    override suspend fun updateFlag(
        adminUsername: String,
        featureKey: String,
        enabled: Boolean,
        platform: String
    ): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdmin(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            supabaseClient.update<JsonObject, JsonObject>(
                table = "feature_flags",
                filterParams = mapOf(
                    "feature_key" to "eq.$featureKey",
                    "platform" to "eq.$platform"
                ),
                bodyData = buildJsonObject {
                    put("enabled", enabled)
                }
            )
            loadFlags(platform, forceRefresh = true)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
