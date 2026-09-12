package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherConfigDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface LauncherConfigRepository {
    val configMap: StateFlow<Map<String, String>>
    suspend fun loadConfig(forceRefresh: Boolean = false): Map<String, String>
    suspend fun getAllConfigs(forceRefresh: Boolean = false): List<SupabaseLauncherConfigDto>
    suspend fun getConfig(key: String, defaultValue: String = ""): String
    suspend fun isMaintenanceMode(): Pair<Boolean, String>
    suspend fun updateConfig(adminUsername: String, key: String, value: String): Result<Unit>
}

class SupabaseLauncherConfigRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : LauncherConfigRepository {

    private val _configMap = MutableStateFlow<Map<String, String>>(emptyMap())
    override val configMap: StateFlow<Map<String, String>> = _configMap.asStateFlow()

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

    override suspend fun loadConfig(forceRefresh: Boolean): Map<String, String> = withContext(dispatcher) {
        if (!forceRefresh && _configMap.value.isNotEmpty()) {
            return@withContext _configMap.value
        }

        try {
            val list: List<SupabaseLauncherConfigDto> = supabaseClient.select(
                table = "launcher_config",
                params = mapOf("is_active" to "eq.true", "select" to "*")
            )
            val mapped = list.associate { it.key to it.value }
            _configMap.value = mapped
            mapped
        } catch (e: Throwable) {
            _configMap.value
        }
    }

    override suspend fun getAllConfigs(forceRefresh: Boolean): List<SupabaseLauncherConfigDto> = withContext(dispatcher) {
        try {
            supabaseClient.select(
                table = "launcher_config",
                params = mapOf("order" to "key.asc", "select" to "*")
            )
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun getConfig(key: String, defaultValue: String): String = withContext(dispatcher) {
        if (_configMap.value.isEmpty()) {
            loadConfig()
        }
        _configMap.value[key] ?: defaultValue
    }

    override suspend fun isMaintenanceMode(): Pair<Boolean, String> = withContext(dispatcher) {
        val config = loadConfig()
        val isMaintenance = config["maintenance_mode"]?.equals("true", ignoreCase = true) == true
        val message = config["maintenance_message"] ?: "Ezz Launcher is currently under scheduled maintenance."
        Pair(isMaintenance, message)
    }

    override suspend fun updateConfig(
        adminUsername: String,
        key: String,
        value: String
    ): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdmin(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            supabaseClient.update<JsonObject, JsonObject>(
                table = "launcher_config",
                filterParams = mapOf("key" to "eq.$key"),
                bodyData = buildJsonObject {
                    put("value", value)
                }
            )
            loadConfig(forceRefresh = true)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
