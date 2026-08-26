package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseUserSettingsDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SupabaseSettingsRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val _settings = MutableStateFlow(LauncherSettings())
    override val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    override suspend fun loadSettings(): LauncherSettings = withContext(dispatcher) {
        val dtos: List<SupabaseUserSettingsDto> = try {
            supabaseClient.select(
                table = "user_settings",
                params = mapOf("user_id" to "eq.$effectiveUserId", "select" to "*")
            )
        } catch (e: Exception) {
            emptyList()
        }

        val loaded = if (dtos.isEmpty()) {
            val defaultDto = SupabaseUserSettingsDto(userId = effectiveUserId)
            try {
                val inserted: List<SupabaseUserSettingsDto> = supabaseClient.insert("user_settings", defaultDto)
                (inserted.firstOrNull() ?: defaultDto).toLauncherSettings()
            } catch (e: Exception) {
                defaultDto.toLauncherSettings()
            }
        } else {
            dtos.first().toLauncherSettings()
        }

        _settings.value = loaded
        loaded
    }

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings): LauncherSettings = withContext(dispatcher) {
        val current = _settings.value
        val updated = transform(current)
        val dto = SupabaseUserSettingsDto.fromLauncherSettings(updated, effectiveUserId)

        try {
            supabaseClient.update<SupabaseUserSettingsDto, SupabaseUserSettingsDto>(
                table = "user_settings",
                filterParams = mapOf("user_id" to "eq.$effectiveUserId"),
                bodyData = dto
            )
        } catch (e: Exception) {
            // Upsert if not existing
            try {
                supabaseClient.insert<SupabaseUserSettingsDto, SupabaseUserSettingsDto>(
                    table = "user_settings",
                    bodyData = dto
                )
            } catch (ignored: Exception) { }
        }

        _settings.value = updated
        updated
    }
}
