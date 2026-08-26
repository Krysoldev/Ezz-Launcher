package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherConfigDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface LauncherConfigRepository {
    val configMap: StateFlow<Map<String, String>>
    suspend fun loadConfig(forceRefresh: Boolean = false): Map<String, String>
    suspend fun getConfig(key: String, defaultValue: String = ""): String
    suspend fun isMaintenanceMode(): Pair<Boolean, String>
}

class SupabaseLauncherConfigRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : LauncherConfigRepository {

    private val _configMap = MutableStateFlow<Map<String, String>>(emptyMap())
    override val configMap: StateFlow<Map<String, String>> = _configMap.asStateFlow()

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
}
