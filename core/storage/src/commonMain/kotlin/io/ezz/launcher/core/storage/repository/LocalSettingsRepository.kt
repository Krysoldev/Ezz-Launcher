package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Local-First repository for launcher settings.
 * Persists all configuration purely to the local filesystem (settings.json).
 */
class LocalSettingsRepository(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val mutex = Mutex()
    private val _settings = MutableStateFlow(LauncherSettings())
    override val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private val settingsFile: Path get() = pathProvider.rootDirectory.resolve("settings.json")

    init {
        _settings.value = readFromDisk()
    }

    private fun readFromDisk(): LauncherSettings {
        return try {
            if (fileSystem.exists(settingsFile)) {
                val content = fileSystem.read(settingsFile) { readUtf8() }
                json.decodeFromString<LauncherSettings>(content)
            } else {
                LauncherSettings()
            }
        } catch (e: Exception) {
            println("Warning reading local settings: ${e.message}")
            LauncherSettings()
        }
    }

    private fun saveToDisk(settings: LauncherSettings) {
        try {
            val parent = settingsFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(settingsFile) {
                writeUtf8(json.encodeToString(settings))
            }
        } catch (e: Exception) {
            println("Error saving local settings: ${e.message}")
        }
    }

    override suspend fun loadSettings(): LauncherSettings = withContext(dispatcher) {
        mutex.withLock {
            val loaded = readFromDisk()
            _settings.value = loaded
            loaded
        }
    }

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings): LauncherSettings = withContext(dispatcher) {
        mutex.withLock {
            val current = _settings.value
            val updated = transform(current)
            _settings.value = updated
            saveToDisk(updated)
            updated
        }
    }
}
