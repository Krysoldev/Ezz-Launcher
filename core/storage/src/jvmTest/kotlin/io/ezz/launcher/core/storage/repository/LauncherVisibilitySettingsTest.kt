package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.storage.supabase.SupabaseUserSettingsDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherVisibilitySettingsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun testDefaultHideLauncherWhileRunningIsOn() {
        val defaultSettings = LauncherSettings()
        assertTrue(
            defaultSettings.hideLauncherWhileRunning,
            "Hide Launcher While Minecraft Is Running MUST default to ON (true) for new installations"
        )
    }

    @Test
    fun testExistingUsersWithoutSettingReceiveDefaultOn() {
        // Simulates an existing user's settings.json that does not contain hide_launcher_while_running
        val legacyJson = """
            {
                "default_java_path": null,
                "default_min_memory_mb": 1024,
                "default_max_memory_mb": 4096,
                "enable_discord_rpc": true,
                "dark_theme": true
            }
        """.trimIndent()

        val settings = json.decodeFromString<LauncherSettings>(legacyJson)
        assertTrue(
            settings.hideLauncherWhileRunning,
            "Existing configurations missing the key MUST deserialize to default ON (true)"
        )
    }

    @Test
    fun testSettingPersistenceTurnOff() {
        val original = LauncherSettings(hideLauncherWhileRunning = false)
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<LauncherSettings>(serialized)

        assertFalse(
            deserialized.hideLauncherWhileRunning,
            "When user sets setting to OFF (false), it MUST persist as OFF after save and reload"
        )
    }

    @Test
    fun testSettingPersistenceTurnOn() {
        val original = LauncherSettings(hideLauncherWhileRunning = true)
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<LauncherSettings>(serialized)

        assertTrue(
            deserialized.hideLauncherWhileRunning,
            "When user sets setting to ON (true), it MUST persist as ON after save and reload"
        )
    }

    @Test
    fun testSupabaseDtoMappingPreservesVisibilitySetting() {
        val settingsOff = LauncherSettings(hideLauncherWhileRunning = false)
        val dtoOff = SupabaseUserSettingsDto.fromLauncherSettings(settingsOff, userId = "user-123")
        assertFalse(dtoOff.hideLauncherWhileRunning)
        val convertedBackOff = dtoOff.toLauncherSettings()
        assertFalse(convertedBackOff.hideLauncherWhileRunning)

        val settingsOn = LauncherSettings(hideLauncherWhileRunning = true)
        val dtoOn = SupabaseUserSettingsDto.fromLauncherSettings(settingsOn, userId = "user-123")
        assertTrue(dtoOn.hideLauncherWhileRunning)
        val convertedBackOn = dtoOn.toLauncherSettings()
        assertTrue(convertedBackOn.hideLauncherWhileRunning)
    }
}
