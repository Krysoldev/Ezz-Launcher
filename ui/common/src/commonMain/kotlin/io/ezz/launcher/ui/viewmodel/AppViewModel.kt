package io.ezz.launcher.ui.viewmodel

import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.core.auth.microsoft.MicrosoftLoginProgress
import io.ezz.launcher.core.minecraft.loader.fabric.FabricMetaClient
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.core.model.runtime.JavaRuntime
import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.core.runtime.LaunchEngine
import io.ezz.launcher.core.runtime.LaunchEvent
import io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.AccountRepository
import io.ezz.launcher.core.storage.repository.EzzProfile
import io.ezz.launcher.core.storage.repository.InstanceRepository
import io.ezz.launcher.core.storage.repository.ModRepository
import io.ezz.launcher.core.storage.repository.ProfileRepository
import io.ezz.launcher.core.storage.repository.SettingsRepository
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import io.ezz.launcher.ui.platform.PlatformBridge

enum class NavigationScreen {
    HOME,
    INSTANCES,
    ACCOUNTS,
    SETTINGS,
    CONSOLE
}

data class ConsoleLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)

class AppViewModel(
    val instanceRepository: InstanceRepository,
    val accountRepository: AccountRepository,
    val settingsRepository: SettingsRepository,
    val versionManifestService: VersionManifestService,
    val fabricMetaClient: FabricMetaClient,
    val authManager: AuthManager,
    val launchEngine: LaunchEngine,
    val pathProvider: PathProvider,
    val supabaseClient: SupabaseClient? = null,
    val profileRepository: ProfileRepository? = null,
    val modRepository: ModRepository? = null,
    val platformBridge: PlatformBridge = DefaultPlatformBridge(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _currentScreen = MutableStateFlow(NavigationScreen.HOME)
    val currentScreen: StateFlow<NavigationScreen> = _currentScreen.asStateFlow()

    private val _selectedInstance = MutableStateFlow<Instance?>(null)
    val selectedInstance: StateFlow<Instance?> = _selectedInstance.asStateFlow()

    private val _processState = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    private val _logs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val logs: StateFlow<List<ConsoleLogEntry>> = _logs.asStateFlow()

    private val _availableVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val availableVersions: StateFlow<List<VersionSummary>> = _availableVersions.asStateFlow()

    private val _detectedJavaRuntimes = MutableStateFlow<List<JavaRuntime>>(emptyList())
    val detectedJavaRuntimes: StateFlow<List<JavaRuntime>> = _detectedJavaRuntimes.asStateFlow()

    private val _isSupabaseConnected = MutableStateFlow<Boolean?>(null)
    val isSupabaseConnected: StateFlow<Boolean?> = _isSupabaseConnected.asStateFlow()

    val currentProfile: StateFlow<EzzProfile?> = profileRepository?.currentProfile ?: MutableStateFlow(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Dialog States
    val showCreateInstanceDialog = MutableStateFlow(false)
    val showEditInstanceDialog = MutableStateFlow<Instance?>(null)
    val showAddOfflineAccountDialog = MutableStateFlow(false)
    val showMicrosoftLoginDialog = MutableStateFlow(false)
    val showEzzAuthDialog = MutableStateFlow(false)
    val microsoftLoginProgress = MutableStateFlow<MicrosoftLoginProgress?>(null)

    init {
        scope.launch {
            try {
                pathProvider.initializeDirectories()
                supabaseClient?.let {
                    _isSupabaseConnected.value = it.checkConnection()
                }
                profileRepository?.loadProfile()
                instanceRepository.loadAll()
                val loadedAccounts = accountRepository.loadAll()
                settingsRepository.loadSettings()
                refreshAvailableVersions()
                refreshJavaRuntimes()

                // If no accounts exist on first start, create a default offline account
                if (loadedAccounts.isEmpty()) {
                    authManager.createOfflineAccount("Player")
                }
            } catch (e: Throwable) {
                _errorMessage.value = "Database connection note: ${e.message}"
            }
        }

        scope.launch {
            try {
                instanceRepository.instances.collect { list ->
                    if (_selectedInstance.value == null || list.none { it.id == _selectedInstance.value?.id }) {
                        _selectedInstance.value = list.firstOrNull()
                    }
                }
            } catch (e: Throwable) {
                println("Error collecting instances: ${e.message}")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun navigateTo(screen: NavigationScreen) {
        _currentScreen.value = screen
    }

    fun selectInstance(instance: Instance) {
        _selectedInstance.value = instance
    }

    fun selectAccount(account: Account) {
        scope.launch {
            try {
                accountRepository.selectAccount(account.id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to select account: ${e.message}"
            }
        }
    }

    fun refreshAvailableVersions() {
        scope.launch {
            try {
                val manifest = versionManifestService.getVersionManifest()
                val releases = manifest.versions.filter { it.type == "release" }
                _availableVersions.value = if (releases.isNotEmpty()) releases else manifest.versions
            } catch (e: Exception) {
                println("Warning: failed to refresh versions: ${e.message}")
            }
        }
    }

    fun refreshJavaRuntimes() {
        scope.launch(Dispatchers.IO) {
            try {
                val detected = JavaRuntimeDetector.detectInstalledRuntimes()
                _detectedJavaRuntimes.value = detected
            } catch (e: Exception) {
                println("Warning: failed to detect Java runtimes: ${e.message}")
            }
        }
    }

    fun launchInstance(instance: Instance? = _selectedInstance.value) {
        val targetInstance = instance ?: _selectedInstance.value
        if (targetInstance == null) {
            _logs.value = _logs.value + ConsoleLogEntry(message = "Error: No instance selected to launch.", isError = true)
            return
        }

        var account = accountRepository.selectedAccount.value
        if (account == null) {
            account = accountRepository.accounts.value.firstOrNull()
            if (account != null) {
                scope.launch { accountRepository.selectAccount(account.id) }
            }
        }

        if (account == null) {
            _logs.value = _logs.value + ConsoleLogEntry(message = "Error: No account selected. Please add an offline or Microsoft account.", isError = true)
            showAddOfflineAccountDialog.value = true
            return
        }

        val launchAccount = account

        scope.launch {
            _logs.value = listOf(ConsoleLogEntry(message = "=== Launching ${targetInstance.name} (${targetInstance.minecraftVersion}) as ${launchAccount.username} ==="))
            launchEngine.launch(targetInstance, launchAccount).collect { event ->
                when (event) {
                    is LaunchEvent.StateChanged -> {
                        val state = event.state
                        _processState.value = state
                        when (state) {
                            is ProcessState.Running -> {
                                _logs.value = _logs.value + ConsoleLogEntry(message = "=== Process started (PID: ${state.processId}) ===")
                            }
                            is ProcessState.Exited -> {
                                _logs.value = _logs.value + ConsoleLogEntry(message = "=== Process exited with code ${state.exitCode} ===")
                            }
                            else -> {}
                        }
                    }
                    is LaunchEvent.ProgressUpdate -> {
                        // Progress update
                    }
                    is LaunchEvent.LogReceived -> {
                        _logs.value = _logs.value + ConsoleLogEntry(message = event.line, isError = event.isError)
                    }
                }
            }
        }
    }

    fun createInstance(
        name: String,
        minecraftVersion: String,
        loaderType: LoaderType,
        loaderVersion: String?,
        minMemoryMb: Int,
        maxMemoryMb: Int,
        customJvmArgs: List<String>
    ) {
        scope.launch {
            try {
                val newInstance = instanceRepository.createInstance(
                    name = name,
                    minecraftVersion = minecraftVersion,
                    loaderType = loaderType,
                    loaderVersion = loaderVersion,
                    minMemoryMb = minMemoryMb,
                    maxMemoryMb = maxMemoryMb,
                    customJvmArgs = customJvmArgs
                )
                _selectedInstance.value = newInstance
                showCreateInstanceDialog.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create instance: ${e.message}"
            }
        }
    }

    fun updateInstance(instance: Instance) {
        scope.launch {
            try {
                instanceRepository.updateInstance(instance)
                if (_selectedInstance.value?.id == instance.id) {
                    _selectedInstance.value = instance
                }
                showEditInstanceDialog.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update instance: ${e.message}"
            }
        }
    }

    fun deleteInstance(id: String) {
        scope.launch {
            try {
                instanceRepository.deleteInstance(id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete instance: ${e.message}"
            }
        }
    }

    fun duplicateInstance(id: String, newName: String) {
        scope.launch {
            try {
                val duplicated = instanceRepository.duplicateInstance(id, newName)
                _selectedInstance.value = duplicated
            } catch (e: Exception) {
                _errorMessage.value = "Failed to duplicate instance: ${e.message}"
            }
        }
    }

    fun openInstanceFolder(instanceId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val gameDir = pathProvider.getInstanceGameDirectory(instanceId)
                platformBridge.openFolder(gameDir)
            } catch (e: Exception) {
                println("Failed to open instance folder: ${e.message}")
            }
        }
    }

    fun addOfflineAccount(username: String) {
        scope.launch {
            try {
                authManager.createOfflineAccount(username)
                showAddOfflineAccountDialog.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add offline account: ${e.message}"
            }
        }
    }

    fun startMicrosoftLogin() {
        showMicrosoftLoginDialog.value = true
        scope.launch {
            try {
                authManager.startMicrosoftLogin().collect { progress ->
                    microsoftLoginProgress.value = progress
                    if (progress is MicrosoftLoginProgress.Success) {
                        showMicrosoftLoginDialog.value = false
                        microsoftLoginProgress.value = null
                    }
                }
            } catch (e: Exception) {
                microsoftLoginProgress.value = MicrosoftLoginProgress.Error(e.message ?: "Login failed")
            }
        }
    }

    fun removeAccount(id: String) {
        scope.launch {
            try {
                accountRepository.removeAccount(id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove account: ${e.message}"
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
