package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.runtime.LauncherSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<LauncherSettings>
    suspend fun loadSettings(): LauncherSettings
    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings): LauncherSettings
}
