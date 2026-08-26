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
import io.ezz.launcher.core.storage.repository.AnnouncementRepository
import io.ezz.launcher.core.storage.repository.EzzProfile
import io.ezz.launcher.core.storage.repository.FabricVersionRepository
import io.ezz.launcher.core.storage.repository.FeatureFlagRepository
import io.ezz.launcher.core.storage.repository.InstanceRepository
import io.ezz.launcher.core.storage.repository.LauncherConfigRepository
import io.ezz.launcher.core.storage.repository.LauncherReleaseRepository
import io.ezz.launcher.core.storage.repository.MinecraftVersionRepository
import io.ezz.launcher.core.storage.repository.ModRepository
import io.ezz.launcher.core.storage.repository.OptiFineVersionRepository
import io.ezz.launcher.core.storage.repository.ProfileRepository
import io.ezz.launcher.core.storage.repository.SettingsRepository
import io.ezz.launcher.core.storage.repository.UpdateCheckResult
import io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftVersionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.core.model.instance.InstanceStatistics
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.instance.LocalShaderPack
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.core.model.instance.LocalWorldBackup
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.core.model.instance.InstanceLogEntry
import io.ezz.launcher.core.model.instance.InstanceRepairReport
import io.ezz.launcher.core.model.modrinth.ModrinthContentType
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ModUpdateCandidate
import io.ezz.launcher.core.model.modrinth.ModrinthBrowseState
import io.ezz.launcher.core.storage.instance.LocalInstanceManager
import io.ezz.launcher.core.network.modrinth.ModrinthService
import io.ezz.launcher.ui.image.ModrinthImageLoader
import io.ezz.launcher.ui.platform.PlatformBridge
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import kotlinx.coroutines.Job

enum class NavigationScreen {
    HOME,
    INSTANCES,
    ACCOUNTS,
    MODS,
    SERVERS,
    PROFILES,
    SETTINGS,
    CONSOLE,
    INSTANCE_MANAGER
}

data class LaunchErrorData(
    val instanceName: String,
    val minecraftVersion: String,
    val javaVersion: String,
    val errorSummary: String,
    val details: String? = null
)

data class ActiveDownloadState(
    val stage: String = "PREPARING",
    val currentFile: String = "",
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = ""
)

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
    val releaseRepository: LauncherReleaseRepository? = null,
    val minecraftVersionRepository: MinecraftVersionRepository? = null,
    val fabricVersionRepository: FabricVersionRepository? = null,
    val optifineVersionRepository: OptiFineVersionRepository? = null,
    val announcementRepository: AnnouncementRepository? = null,
    val launcherConfigRepository: LauncherConfigRepository? = null,
    val featureFlagRepository: FeatureFlagRepository? = null,
    val localModScanner: io.ezz.launcher.core.minecraft.mods.LocalModScanner? = null,
    val skinManager: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager? = null,
    val processSessionTracker: io.ezz.launcher.core.runtime.process.ProcessSessionTracker? = null,
    val localInstanceManager: LocalInstanceManager? = null,
    val modrinthService: ModrinthService? = null,
    val platformBridge: PlatformBridge = DefaultPlatformBridge(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val currentLauncherVersion = "1.0.0"

    val instanceManager: LocalInstanceManager =
        localInstanceManager ?: LocalInstanceManager(pathProvider, instanceRepository)

    val modrinth: ModrinthService =
        modrinthService ?: ModrinthService()

    val skinService: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager =
        skinManager ?: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager(
            pathProvider = pathProvider,
            httpClient = io.ezz.launcher.core.network.client.HttpClientFactory.create()
        )

    val sessionTracker: io.ezz.launcher.core.runtime.process.ProcessSessionTracker =
        processSessionTracker ?: io.ezz.launcher.core.runtime.process.ProcessSessionTracker(pathProvider)

    private val _currentScreen = MutableStateFlow(NavigationScreen.HOME)
    val currentScreen: StateFlow<NavigationScreen> = _currentScreen.asStateFlow()

    private val _selectedInstance = MutableStateFlow<Instance?>(null)
    val selectedInstance: StateFlow<Instance?> = _selectedInstance.asStateFlow()

    private val _installedMods = MutableStateFlow<List<io.ezz.launcher.core.model.instance.ModMetadata>>(emptyList())
    val installedMods: StateFlow<List<io.ezz.launcher.core.model.instance.ModMetadata>> = _installedMods.asStateFlow()

    private val _processState = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    // Multi-instance real-time runtime tracking (instanceId -> InstanceRuntimeSession)
    private val _runningSessions = MutableStateFlow<Map<String, io.ezz.launcher.core.model.runtime.InstanceRuntimeSession>>(emptyMap())
    val runningSessions: StateFlow<Map<String, io.ezz.launcher.core.model.runtime.InstanceRuntimeSession>> = _runningSessions.asStateFlow()

    // High-performance 1-second UI clock ticker
    private val _tickerTime = MutableStateFlow(System.currentTimeMillis())
    val tickerTime: StateFlow<Long> = _tickerTime.asStateFlow()

    private val _logs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val logs: StateFlow<List<ConsoleLogEntry>> = _logs.asStateFlow()

    private val _availableVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val availableVersions: StateFlow<List<VersionSummary>> = _availableVersions.asStateFlow()

    private val _allVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val allVersions: StateFlow<List<VersionSummary>> = _allVersions.asStateFlow()

    private val _snapshotVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val snapshotVersions: StateFlow<List<VersionSummary>> = _snapshotVersions.asStateFlow()

    private val _oldVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val oldVersions: StateFlow<List<VersionSummary>> = _oldVersions.asStateFlow()

    private val _detectedJavaRuntimes = MutableStateFlow<List<JavaRuntime>>(emptyList())
    val detectedJavaRuntimes: StateFlow<List<JavaRuntime>> = _detectedJavaRuntimes.asStateFlow()

    private val _isSupabaseConnected = MutableStateFlow<Boolean?>(null)
    val isSupabaseConnected: StateFlow<Boolean?> = _isSupabaseConnected.asStateFlow()

    val isTestingSupabaseConnection = MutableStateFlow(false)
    val supabaseStatusMessage = MutableStateFlow<String?>(null)

    val currentProfile: StateFlow<EzzProfile?> = profileRepository?.currentProfile ?: MutableStateFlow(null)

    // Public Supabase Data Flows
    val announcements: StateFlow<List<SupabaseAnnouncementDto>> = announcementRepository?.announcements ?: MutableStateFlow(emptyList())
    
    private val _isMaintenanceMode = MutableStateFlow(false)
    val isMaintenanceMode: StateFlow<Boolean> = _isMaintenanceMode.asStateFlow()

    private val _maintenanceMessage = MutableStateFlow("")
    val maintenanceMessage: StateFlow<String> = _maintenanceMessage.asStateFlow()

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

    val featureFlags: StateFlow<Map<String, Boolean>> = featureFlagRepository?.flags ?: MutableStateFlow(emptyMap())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Server List Flow
    private val _savedServers = MutableStateFlow<List<io.ezz.launcher.core.model.instance.ServerEntry>>(emptyList())
    val savedServers: StateFlow<List<io.ezz.launcher.core.model.instance.ServerEntry>> = _savedServers.asStateFlow()

    // Download & Launch Progress HUD
    val activeDownloadState = MutableStateFlow<ActiveDownloadState?>(null)

    // Diagnostic Error Dialog State
    val launchErrorDialogData = MutableStateFlow<LaunchErrorData?>(null)

    // Dialog States
    val showCreateInstanceDialog = MutableStateFlow(false)
    val showEditInstanceDialog = MutableStateFlow<Instance?>(null)
    val showAddOfflineAccountDialog = MutableStateFlow(false)
    val showMicrosoftLoginDialog = MutableStateFlow(false)
    val showEzzAuthDialog = MutableStateFlow(false)
    val showAddServerDialog = MutableStateFlow(false)
    val showSearchDialog = MutableStateFlow(false)
    val microsoftLoginProgress = MutableStateFlow<MicrosoftLoginProgress?>(null)

    // Dedicated Instance Manager State
    val activeManageTab = MutableStateFlow(InstanceManagerTab.OVERVIEW)
    val manageStatistics = MutableStateFlow<InstanceStatistics?>(null)
    val manageMods = MutableStateFlow<List<LocalMod>>(emptyList())
    val manageResourcePacks = MutableStateFlow<List<LocalResourcePack>>(emptyList())
    val manageShaders = MutableStateFlow<List<LocalShaderPack>>(emptyList())
    val manageWorlds = MutableStateFlow<List<LocalWorld>>(emptyList())
    val manageScreenshots = MutableStateFlow<List<LocalScreenshot>>(emptyList())
    val manageLogs = MutableStateFlow<List<InstanceLogEntry>>(emptyList())
    val manageSelectedLogContent = MutableStateFlow<String?>(null)
    val manageRepairReport = MutableStateFlow<InstanceRepairReport?>(null)

    // Modrinth Image Caching Engine
    val imageLoader: ModrinthImageLoader = ModrinthImageLoader(pathProvider, modrinth, scope)

    // Isolated Modrinth Browse States per Content Type (Prevents State Leaks)
    val modsBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.MOD))
    val resourcePacksBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.RESOURCE_PACK))
    val shadersBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.SHADER))

    // Modrinth Global Actions
    val modrinthDownloadingProject = MutableStateFlow<String?>(null)
    val modrinthDownloadProgress = MutableStateFlow(0f)
    val modUpdateCandidates = MutableStateFlow<List<ModUpdateCandidate>>(emptyList())
    val isCheckingModUpdates = MutableStateFlow(false)

    private var searchModsJob: Job? = null
    private var searchResourcePacksJob: Job? = null
    private var searchShadersJob: Job? = null

    // Instance Manager Modals & Dialogs
    val selectedScreenshotForViewer = MutableStateFlow<LocalScreenshot?>(null)
    val showRepairDialog = MutableStateFlow(false)
    val showDuplicateInstanceDialog = MutableStateFlow<Instance?>(null)
    val showExportInstanceDialog = MutableStateFlow<Instance?>(null)
    val showWorldBackupRestoreDialog = MutableStateFlow<LocalWorld?>(null)
    val worldBackupsList = MutableStateFlow<List<LocalWorldBackup>>(emptyList())
    val selectedLogFile = MutableStateFlow<InstanceLogEntry?>(null)

    init {
        // Start 1-second live ticker
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _tickerTime.value = System.currentTimeMillis()
            }
        }

        // Recover alive Minecraft processes on launcher restart
        scope.launch(Dispatchers.IO) {
            try {
                val recovered = sessionTracker.recoverActiveSessions()
                if (recovered.isNotEmpty()) {
                    val map = _runningSessions.value.toMutableMap()
                    recovered.forEach { session ->
                        map[session.instanceId] = session
                        ProcessHandle.of(session.processId).ifPresent { handle ->
                            handle.onExit().thenAccept {
                                scope.launch {
                                    handleProcessExited(session.instanceId, 0)
                                }
                            }
                        }
                    }
                    _runningSessions.value = map

                    val currentSel = _selectedInstance.value
                    if (currentSel != null && map.containsKey(currentSel.id)) {
                        val sess = map[currentSel.id]!!
                        _processState.value = ProcessState.Running(sess.processId, sess.startedAt)
                    }
                }
            } catch (e: Throwable) {
                println("Note: process session recovery notice: ${e.message}")
            }
        }

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

                // Load Public Supabase Tables
                loadPublicData()

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
                    _selectedInstance.value?.let { refreshMods(it.id) }
                }
            } catch (e: Throwable) {
                println("Error collecting instances: ${e.message}")
            }
        }
    }

    fun isInstanceRunning(instanceId: String): Boolean {
        return _runningSessions.value.containsKey(instanceId)
    }

    fun getInstanceRuntimeSeconds(instanceId: String): Long? {
        val session = _runningSessions.value[instanceId] ?: return null
        val now = _tickerTime.value
        val elapsed = (now - session.startedAt) / 1000L
        return elapsed.coerceAtLeast(0L)
    }

    fun getInstanceRuntimeFormatted(instanceId: String): String? {
        val seconds = getInstanceRuntimeSeconds(instanceId) ?: return null
        return io.ezz.launcher.core.model.runtime.formatRuntime(seconds)
    }

    private fun handleProcessExited(instanceId: String, exitCode: Int) {
        _runningSessions.value = _runningSessions.value - instanceId
        scope.launch(Dispatchers.IO) {
            sessionTracker.unregisterSession(instanceId)
        }
        if (_selectedInstance.value?.id == instanceId) {
            _processState.value = ProcessState.Exited(exitCode)
        }
        _logs.value = _logs.value + ConsoleLogEntry(message = "=== Instance $instanceId Process Exited (Exit Code $exitCode) ===", isError = exitCode != 0)
    }

    fun stopInstance(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        val session = _runningSessions.value[targetId]
        if (session != null) {
            sessionTracker.stopProcess(session.processId)
            _runningSessions.value = _runningSessions.value - targetId
            scope.launch(Dispatchers.IO) {
                sessionTracker.unregisterSession(targetId)
            }
            if (_selectedInstance.value?.id == targetId) {
                _processState.value = ProcessState.Idle
            }
            _logs.value = _logs.value + ConsoleLogEntry(message = "=== Minecraft Process Terminated by User ===")
        }
    }

    fun selectInstance(instance: Instance) {
        _selectedInstance.value = instance
        val session = _runningSessions.value[instance.id]
        if (session != null) {
            _processState.value = ProcessState.Running(session.processId, session.startedAt)
        } else if (_processState.value is ProcessState.Running) {
            _processState.value = ProcessState.Idle
        }
        refreshMods(instance.id)
    }

    fun refreshMods(instanceId: String? = _selectedInstance.value?.id) {
        if (instanceId == null) {
            _installedMods.value = emptyList()
            return
        }
        scope.launch {
            try {
                val scanned = localModScanner?.scanMods(instanceId) ?: emptyList()
                _installedMods.value = scanned
            } catch (e: Throwable) {
                println("Note: could not scan local mods: ${e.message}")
            }
        }
    }

    fun toggleMod(instanceId: String, fileName: String, enable: Boolean) {
        scope.launch {
            try {
                localModScanner?.toggleMod(instanceId, fileName, enable)
                refreshMods(instanceId)
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to toggle mod: ${e.message}"
            }
        }
    }

    fun deleteMod(instanceId: String, fileName: String) {
        scope.launch {
            try {
                localModScanner?.deleteMod(instanceId, fileName)
                refreshMods(instanceId)
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to delete mod: ${e.message}"
            }
        }
    }

    fun openInstanceFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = pathProvider.getInstanceDirectory(targetId)
        platformBridge.openFolder(path)
    }

    fun openModsFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = pathProvider.getInstanceDirectory(targetId).resolve(".minecraft").resolve("mods")
        platformBridge.openFolder(path)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun navigateTo(screen: NavigationScreen) {
        _currentScreen.value = screen
    }

    fun updateCloseOnLaunch(close: Boolean) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(closeLauncherOnLaunch = close) }
            } catch (e: Exception) {
                println("Failed to update setting: ${e.message}")
            }
        }
    }

    fun updateMemorySettings(minMb: Int, maxMb: Int) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(defaultMinMemoryMb = minMb, defaultMaxMemoryMb = maxMb) }
            } catch (e: Exception) {
                println("Failed to update memory: ${e.message}")
            }
        }
    }

    fun updateGlobalJvmArgs(args: List<String>) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(globalJvmArgs = args) }
            } catch (e: Exception) {
                println("Failed to update JVM args: ${e.message}")
            }
        }
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

    fun deleteAccount(accountId: String) {
        scope.launch {
            try {
                accountRepository.removeAccount(accountId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete account: ${e.message}"
            }
        }
    }

    fun cancelMicrosoftLogin() {
        showMicrosoftLoginDialog.value = false
    }

    fun stopInstance() {
        _processState.value = ProcessState.Idle
    }

    fun refreshAvailableVersions() {
        scope.launch {
            try {
                val manifest = versionManifestService.getVersionManifest()
                val releases = manifest.versions.filter { it.type == "release" }
                val snapshots = manifest.versions.filter { it.type == "snapshot" }
                val olds = manifest.versions.filter { it.type == "old_beta" || it.type == "old_alpha" }
                
                _allVersions.value = manifest.versions
                _availableVersions.value = if (releases.isNotEmpty()) releases else manifest.versions
                _snapshotVersions.value = snapshots
                _oldVersions.value = olds
            } catch (e: Exception) {
                println("Warning: failed to refresh versions: ${e.message}")
            }
        }
    }

    fun updateSupabaseCredentials(url: String, anonKey: String) {
        scope.launch {
            try {
                isTestingSupabaseConnection.value = true
                supabaseStatusMessage.value = "Connecting to Supabase..."
                val newConfig = io.ezz.launcher.core.storage.supabase.SupabaseConfig(
                    supabaseUrl = url.trim(),
                    anonKey = anonKey.trim()
                )
                supabaseClient?.updateConfig(newConfig)
                val connected = supabaseClient?.checkConnection() == true
                _isSupabaseConnected.value = connected
                supabaseStatusMessage.value = if (connected) "Successfully connected to Supabase PostgreSQL!" else "Connection failed. Please check URL and Anon Key."
                if (connected) {
                    loadPublicData()
                }
            } catch (e: Exception) {
                _isSupabaseConnected.value = false
                supabaseStatusMessage.value = "Connection error: ${e.message}"
            } finally {
                isTestingSupabaseConnection.value = false
            }
        }
    }

    fun retrySupabaseConnection() {
        scope.launch {
            try {
                isTestingSupabaseConnection.value = true
                val connected = supabaseClient?.checkConnection() == true
                _isSupabaseConnected.value = connected
                if (connected) {
                    loadPublicData()
                }
            } catch (e: Exception) {
                _isSupabaseConnected.value = false
            } finally {
                isTestingSupabaseConnection.value = false
            }
        }
    }

    fun loadPublicData() {
        scope.launch {
            try {
                // 1. Announcements
                announcementRepository?.getActiveAnnouncements()

                // 2. Maintenance Mode & Config
                launcherConfigRepository?.let {
                    val (maintenance, message) = it.isMaintenanceMode()
                    _isMaintenanceMode.value = maintenance
                    _maintenanceMessage.value = message
                }

                // 3. Update Check
                releaseRepository?.let {
                    val updateResult = it.checkForUpdates(currentLauncherVersion, platform = "windows")
                    _updateCheckResult.value = updateResult
                }

                // 4. Feature Flags
                featureFlagRepository?.loadFlags(platform = "windows")

                // 5. Supported Minecraft Versions from Supabase
                minecraftVersionRepository?.getSupportedVersions()
            } catch (e: Throwable) {
                println("Note: Public table sync completed with notice: ${e.message}")
            }
        }
    }

    fun checkForUpdates() {
        scope.launch {
            releaseRepository?.let {
                _updateCheckResult.value = it.checkForUpdates(currentLauncherVersion, platform = "windows")
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

    fun addServer(name: String, address: String) {
        val entry = io.ezz.launcher.core.model.instance.ServerEntry(
            id = "server_${System.currentTimeMillis()}",
            name = name.ifBlank { "Minecraft Server" },
            address = address.trim(),
            motd = "Custom Server",
            isFeatured = false
        )
        _savedServers.value = _savedServers.value + entry
    }

    fun removeServer(id: String) {
        _savedServers.value = _savedServers.value.filterNot { it.id == id }
    }

    fun repairInstance(instance: Instance? = _selectedInstance.value) {
        val target = instance ?: return
        scope.launch {
            _logs.value = _logs.value + ConsoleLogEntry(message = "=== Validating & Repairing ${target.name} files ===")
            launchInstance(target)
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
            activeDownloadState.value = ActiveDownloadState(
                stage = "PREPARING",
                currentFile = "Checking dependencies...",
                progress = 0.05f
            )

            try {
                launchEngine.launch(targetInstance, launchAccount).collect { event ->
                    when (event) {
                        is LaunchEvent.StateChanged -> {
                            val state = event.state
                            _processState.value = state
                            when (state) {
                                is ProcessState.Preparing -> {
                                    activeDownloadState.value = ActiveDownloadState(
                                        stage = state.stage.uppercase(),
                                        currentFile = state.stage,
                                        progress = state.progress ?: 0.2f
                                    )
                                }
                                is ProcessState.Running -> {
                                    activeDownloadState.value = null
                                    val startedAt = if (state.startedAt > 0L) state.startedAt else System.currentTimeMillis()
                                    val session = io.ezz.launcher.core.model.runtime.InstanceRuntimeSession(
                                        instanceId = targetInstance.id,
                                        processId = state.processId ?: 0L,
                                        startedAt = startedAt
                                    )
                                    _runningSessions.value = _runningSessions.value + (targetInstance.id to session)
                                    scope.launch(Dispatchers.IO) {
                                        sessionTracker.registerSession(targetInstance.id, state.processId ?: 0L, startedAt)
                                    }
                                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Process started (PID: ${state.processId}) ===")
                                }
                                is ProcessState.Exited -> {
                                    activeDownloadState.value = null
                                    _runningSessions.value = _runningSessions.value - targetInstance.id
                                    scope.launch(Dispatchers.IO) {
                                        sessionTracker.unregisterSession(targetInstance.id)
                                    }
                                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Process exited with code ${state.exitCode} ===")
                                }
                                is ProcessState.Failed -> {
                                    activeDownloadState.value = null
                                    _runningSessions.value = _runningSessions.value - targetInstance.id
                                    scope.launch(Dispatchers.IO) {
                                        sessionTracker.unregisterSession(targetInstance.id)
                                    }
                                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Launch Failed: ${state.error.message} ===", isError = true)
                                    launchErrorDialogData.value = LaunchErrorData(
                                        instanceName = targetInstance.name,
                                        minecraftVersion = targetInstance.minecraftVersion,
                                        javaVersion = targetInstance.javaPath ?: "System Default Runtime",
                                        errorSummary = state.error.message,
                                        details = (state.error as? io.ezz.launcher.core.model.runtime.LaunchError.ExecutionFailed)?.cause?.stackTraceToString()
                                    )
                                }
                                else -> {
                                    activeDownloadState.value = null
                                }
                            }
                        }
                        is LaunchEvent.ProgressUpdate -> {
                            val dl = event.progress
                            activeDownloadState.value = ActiveDownloadState(
                                stage = "DOWNLOADING",
                                currentFile = dl.currentItemName,
                                progress = dl.percentage,
                                downloadedBytes = dl.bytesDownloaded,
                                totalBytes = dl.totalBytes,
                                speedText = if (dl.totalBytes > 0) "${(dl.bytesDownloaded / 1024 / 1024)} MB / ${(dl.totalBytes / 1024 / 1024)} MB" else "${(dl.bytesDownloaded / 1024)} KB"
                            )
                        }
                        is LaunchEvent.LogReceived -> {
                            _logs.value = _logs.value + ConsoleLogEntry(message = event.line, isError = event.isError)
                        }
                    }
                }
            } catch (e: Exception) {
                _processState.value = ProcessState.Failed(io.ezz.launcher.core.model.runtime.LaunchError.ExecutionFailed(e.message ?: "Launch Failed", e))
                activeDownloadState.value = null
                launchErrorDialogData.value = LaunchErrorData(
                    instanceName = targetInstance.name,
                    minecraftVersion = targetInstance.minecraftVersion,
                    javaVersion = targetInstance.javaPath ?: "System Default Runtime",
                    errorSummary = e.message ?: "Unknown launch failure",
                    details = e.stackTraceToString()
                )
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

    fun updateSettings(settings: LauncherSettings) {
        scope.launch {
            try {
                settingsRepository.updateSettings { settings }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save settings: ${e.message}"
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // ==========================================================
    // INSTANCE MANAGER ACTIONS
    // ==========================================================

    fun openInstanceManager(instance: Instance, initialTab: InstanceManagerTab = InstanceManagerTab.OVERVIEW) {
        selectInstance(instance)
        activeManageTab.value = initialTab
        _currentScreen.value = NavigationScreen.INSTANCE_MANAGER
        refreshManageData()
    }

    fun setManageTab(tab: InstanceManagerTab) {
        activeManageTab.value = tab
        if (tab == InstanceManagerTab.MODS && modsBrowseState.value.items.isEmpty()) {
            searchMods()
        } else if (tab == InstanceManagerTab.RESOURCE_PACKS && resourcePacksBrowseState.value.items.isEmpty()) {
            searchResourcePacks()
        } else if (tab == InstanceManagerTab.SHADERS && shadersBrowseState.value.items.isEmpty()) {
            searchShaders()
        }
    }

    fun refreshManageData() {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            try {
                manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
                manageMods.value = instanceManager.getMods(instance.id)
                manageResourcePacks.value = instanceManager.getResourcePacks(instance.id)
                manageShaders.value = instanceManager.getShaderPacks(instance.id)
                manageWorlds.value = instanceManager.getWorlds(instance.id)
                manageScreenshots.value = instanceManager.getScreenshots(instance.id)
                manageLogs.value = instanceManager.getLogs(instance.id)
            } catch (e: Throwable) {
                println("Error refreshing manage data: ${e.message}")
            }
        }
    }

    // MODS
    fun toggleManageMod(fileName: String, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.toggleMod(instance.id, fileName, enable)
            manageMods.value = instanceManager.getMods(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshMods(instance.id)
        }
    }

    fun deleteManageMod(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteMod(instance.id, fileName)
            manageMods.value = instanceManager.getMods(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshMods(instance.id)
        }
    }

    // RESOURCE PACKS
    fun toggleManageResourcePack(fileName: String, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.toggleResourcePack(instance.id, fileName, enable)
            manageResourcePacks.value = instanceManager.getResourcePacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    fun deleteManageResourcePack(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteResourcePack(instance.id, fileName)
            manageResourcePacks.value = instanceManager.getResourcePacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    // SHADERS
    fun toggleManageShader(fileName: String, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.toggleShaderPack(instance.id, fileName, enable)
            manageShaders.value = instanceManager.getShaderPacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    fun deleteManageShader(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteShaderPack(instance.id, fileName)
            manageShaders.value = instanceManager.getShaderPacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    // WORLDS
    fun backupWorld(worldFolderName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            val backup = instanceManager.backupWorld(instance.id, instance.name, worldFolderName)
            if (backup != null) {
                showWorldBackupRestoreDialog.value?.let { openWorldBackups(it) }
            }
        }
    }

    fun openWorldBackups(world: LocalWorld) {
        val instance = _selectedInstance.value ?: return
        showWorldBackupRestoreDialog.value = world
        scope.launch {
            worldBackupsList.value = instanceManager.getWorldBackups(instance.name, world.folderName)
        }
    }

    fun restoreWorldBackup(backupFilePath: String, targetFolderName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.restoreWorldBackup(instance.id, backupFilePath, targetFolderName)
            showWorldBackupRestoreDialog.value = null
            manageWorlds.value = instanceManager.getWorlds(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    fun duplicateWorld(worldFolderName: String, newName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.duplicateWorld(instance.id, worldFolderName, newName)
            manageWorlds.value = instanceManager.getWorlds(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    fun renameWorld(worldFolderName: String, newName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.renameWorld(instance.id, worldFolderName, newName)
            manageWorlds.value = instanceManager.getWorlds(instance.id)
        }
    }

    fun deleteWorld(worldFolderName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteWorld(instance.id, worldFolderName)
            manageWorlds.value = instanceManager.getWorlds(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    fun exportWorld(worldFolderName: String, destinationZip: java.io.File) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.exportWorld(instance.id, worldFolderName, destinationZip)
        }
    }

    fun importWorld(sourceFile: java.io.File) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.importWorld(instance.id, sourceFile)
            manageWorlds.value = instanceManager.getWorlds(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
        }
    }

    // SCREENSHOTS
    fun deleteScreenshot(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteScreenshot(instance.id, fileName)
            manageScreenshots.value = instanceManager.getScreenshots(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            if (selectedScreenshotForViewer.value?.fileName == fileName) {
                selectedScreenshotForViewer.value = null
            }
        }
    }

    // LOGS
    fun loadLogContent(logEntry: InstanceLogEntry) {
        selectedLogFile.value = logEntry
        scope.launch {
            manageSelectedLogContent.value = instanceManager.readLogContent(logEntry.filePath)
        }
    }

    // REPAIR
    fun runInstanceRepair() {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            manageRepairReport.value = instanceManager.repairInstance(instance)
            showRepairDialog.value = true
        }
    }

    // DUPLICATE & EXPORT
    fun duplicateInstanceWithOption(source: Instance, newName: String, includeWorlds: Boolean) {
        scope.launch {
            try {
                val duplicated = instanceManager.duplicateInstance(source, newName, includeWorlds)
                showDuplicateInstanceDialog.value = null
                instanceRepository.loadAll()
                _selectedInstance.value = duplicated
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to duplicate: ${e.message}"
            }
        }
    }

    fun exportInstanceWithOption(source: Instance, targetZip: java.io.File, includeWorlds: Boolean) {
        scope.launch {
            try {
                instanceManager.exportInstance(source, targetZip, includeWorlds)
                showExportInstanceDialog.value = null
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to export: ${e.message}"
            }
        }
    }

    // CUSTOM ICONS
    fun changeInstanceCustomIcon(instanceId: String, file: java.io.File) {
        scope.launch {
            try {
                val updated = instanceManager.setCustomIcon(instanceId, file)
                _selectedInstance.value = updated
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to update icon: ${e.message}"
            }
        }
    }

    fun removeInstanceCustomIcon(instanceId: String) {
        scope.launch {
            try {
                val updated = instanceManager.removeCustomIcon(instanceId)
                _selectedInstance.value = updated
            } catch (e: Throwable) {
                _errorMessage.value = "Failed to remove icon: ${e.message}"
            }
        }
    }

    // ==========================================================
    // MODRINTH SEARCH & PAGINATION (ISOLATED SERVICES)
    // ==========================================================

    fun searchMods(
        query: String? = null,
        page: Int? = null,
        loader: String? = null,
        version: String? = null,
        category: String? = null,
        sort: String? = null,
        debounceMs: Long = 0L
    ) {
        val instance = _selectedInstance.value ?: return
        val current = modsBrowseState.value
        val newQuery = query ?: current.searchQuery
        val newPage = page ?: (if (query != null || loader != null || version != null || category != null || sort != null) 1 else current.page)
        val newLoader = loader ?: current.selectedLoader ?: if (instance.loaderType != LoaderType.VANILLA) instance.loaderType.name.lowercase() else null
        val newVersion = version ?: current.selectedGameVersion ?: instance.minecraftVersion
        val newCategory = if (category == "ALL") null else (category ?: current.selectedCategory)
        val newSort = sort ?: current.selectedSort

        modsBrowseState.value = current.copy(
            searchQuery = newQuery,
            page = newPage,
            selectedLoader = newLoader,
            selectedGameVersion = newVersion,
            selectedCategory = newCategory,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        searchModsJob?.cancel()
        searchModsJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            try {
                val loaders = if (!newLoader.isNullOrBlank()) listOf(newLoader) else null
                val versions = if (!newVersion.isNullOrBlank()) listOf(newVersion) else null
                val categories = if (!newCategory.isNullOrBlank()) listOf(newCategory) else null
                val offset = (newPage - 1) * current.pageSize

                val res = modrinth.searchMods(
                    query = newQuery,
                    loaders = loaders,
                    gameVersions = versions,
                    categories = categories,
                    index = newSort,
                    offset = offset,
                    limit = current.pageSize
                )

                val validHits = res.hits.filter { it.projectType.equals("mod", ignoreCase = true) }
                val totalPages = maxOf(1, kotlin.math.ceil(res.totalHits.toDouble() / current.pageSize).toInt())

                modsBrowseState.value = modsBrowseState.value.copy(
                    items = validHits,
                    totalHits = res.totalHits,
                    totalPages = totalPages,
                    isLoading = false,
                    error = null
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                modsBrowseState.value = modsBrowseState.value.copy(
                    isLoading = false,
                    error = "Failed to load mods from Modrinth: ${e.message}"
                )
            }
        }
    }

    fun setModsPage(page: Int) {
        if (page < 1 || page > modsBrowseState.value.totalPages) return
        searchMods(page = page)
    }

    fun searchResourcePacks(
        query: String? = null,
        page: Int? = null,
        version: String? = null,
        resolution: String? = null,
        category: String? = null,
        sort: String? = null,
        debounceMs: Long = 0L
    ) {
        val instance = _selectedInstance.value ?: return
        val current = resourcePacksBrowseState.value
        val newQuery = query ?: current.searchQuery
        val newPage = page ?: (if (query != null || version != null || resolution != null || category != null || sort != null) 1 else current.page)
        val newVersion = version ?: current.selectedGameVersion ?: instance.minecraftVersion
        val newResolution = if (resolution == "ALL") null else (resolution ?: current.selectedResolution)
        val newCategory = if (category == "ALL") null else (category ?: current.selectedCategory)
        val newSort = sort ?: current.selectedSort

        resourcePacksBrowseState.value = current.copy(
            searchQuery = newQuery,
            page = newPage,
            selectedGameVersion = newVersion,
            selectedResolution = newResolution,
            selectedCategory = newCategory,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        searchResourcePacksJob?.cancel()
        searchResourcePacksJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            try {
                val versions = if (!newVersion.isNullOrBlank()) listOf(newVersion) else null
                val catList = mutableListOf<String>()
                if (!newCategory.isNullOrBlank()) catList.add(newCategory)
                if (!newResolution.isNullOrBlank()) catList.add(newResolution)
                val categories = if (catList.isNotEmpty()) catList else null
                val offset = (newPage - 1) * current.pageSize

                val res = modrinth.searchResourcePacks(
                    query = newQuery,
                    gameVersions = versions,
                    categories = categories,
                    index = newSort,
                    offset = offset,
                    limit = current.pageSize
                )

                val validHits = res.hits.filter { it.projectType.equals("resourcepack", ignoreCase = true) }
                val totalPages = maxOf(1, kotlin.math.ceil(res.totalHits.toDouble() / current.pageSize).toInt())

                resourcePacksBrowseState.value = resourcePacksBrowseState.value.copy(
                    items = validHits,
                    totalHits = res.totalHits,
                    totalPages = totalPages,
                    isLoading = false,
                    error = null
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                resourcePacksBrowseState.value = resourcePacksBrowseState.value.copy(
                    isLoading = false,
                    error = "Failed to load resource packs from Modrinth: ${e.message}"
                )
            }
        }
    }

    fun setResourcePacksPage(page: Int) {
        if (page < 1 || page > resourcePacksBrowseState.value.totalPages) return
        searchResourcePacks(page = page)
    }

    fun searchShaders(
        query: String? = null,
        page: Int? = null,
        version: String? = null,
        category: String? = null,
        sort: String? = null,
        debounceMs: Long = 0L
    ) {
        val instance = _selectedInstance.value ?: return
        val current = shadersBrowseState.value
        val newQuery = query ?: current.searchQuery
        val newPage = page ?: (if (query != null || version != null || category != null || sort != null) 1 else current.page)
        val newVersion = version ?: current.selectedGameVersion ?: instance.minecraftVersion
        val newCategory = if (category == "ALL") null else (category ?: current.selectedCategory)
        val newSort = sort ?: current.selectedSort

        shadersBrowseState.value = current.copy(
            searchQuery = newQuery,
            page = newPage,
            selectedGameVersion = newVersion,
            selectedCategory = newCategory,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        searchShadersJob?.cancel()
        searchShadersJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            try {
                val versions = if (!newVersion.isNullOrBlank()) listOf(newVersion) else null
                val categories = if (!newCategory.isNullOrBlank()) listOf(newCategory) else null
                val offset = (newPage - 1) * current.pageSize

                val res = modrinth.searchShaders(
                    query = newQuery,
                    gameVersions = versions,
                    categories = categories,
                    index = newSort,
                    offset = offset,
                    limit = current.pageSize
                )

                val validHits = res.hits.filter { it.projectType.equals("shader", ignoreCase = true) }
                val totalPages = maxOf(1, kotlin.math.ceil(res.totalHits.toDouble() / current.pageSize).toInt())

                shadersBrowseState.value = shadersBrowseState.value.copy(
                    items = validHits,
                    totalHits = res.totalHits,
                    totalPages = totalPages,
                    isLoading = false,
                    error = null
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                shadersBrowseState.value = shadersBrowseState.value.copy(
                    isLoading = false,
                    error = "Failed to load shaders from Modrinth: ${e.message}"
                )
            }
        }
    }

    fun setShadersPage(page: Int) {
        if (page < 1 || page > shadersBrowseState.value.totalPages) return
        searchShaders(page = page)
    }

    // ==========================================================
    // INSTALLATION & VERIFICATION
    // ==========================================================

    fun installModrinthProject(hit: ModrinthProjectHit) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            modrinthDownloadingProject.value = hit.title
            modrinthDownloadProgress.value = 0f
            try {
                val loaders = if (hit.projectType.equals("mod", ignoreCase = true) && instance.loaderType != LoaderType.VANILLA) {
                    listOf(instance.loaderType.name.lowercase())
                } else null
                val versions = listOf(instance.minecraftVersion)
                val projectVersions = modrinth.getProjectVersions(hit.projectId, loaders, versions)
                val latest = projectVersions.firstOrNull() ?: modrinth.getProjectVersions(hit.projectId).firstOrNull()

                if (latest != null && latest.files.isNotEmpty()) {
                    val primaryFile = latest.files.firstOrNull { it.primary } ?: latest.files.first()
                    val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
                    
                    // Strict directory destination checking
                    val targetDir = when {
                        hit.projectType.equals("resourcepack", ignoreCase = true) -> java.io.File(gameDir, "resourcepacks")
                        hit.projectType.equals("shader", ignoreCase = true) -> java.io.File(gameDir, "shaderpacks")
                        else -> java.io.File(gameDir, "mods")
                    }
                    targetDir.mkdirs()
                    val targetFile = java.io.File(targetDir, primaryFile.filename)

                    val ok = modrinth.downloadContent(
                        url = primaryFile.url,
                        targetFile = targetFile,
                        onProgress = { downloaded, total ->
                            if (total > 0) {
                                modrinthDownloadProgress.value = downloaded.toFloat() / total.toFloat()
                            }
                        }
                    )

                    // Also download required dependencies for mods
                    if (ok && hit.projectType.equals("mod", ignoreCase = true) && latest.dependencies.isNotEmpty()) {
                        for (dep in latest.dependencies) {
                            val depProjId = dep.projectId
                            if (dep.dependencyType == "required" && depProjId != null) {
                                val depVersions = modrinth.getProjectVersions(depProjId, loaders, versions)
                                val depLatest = depVersions.firstOrNull()
                                if (depLatest != null && depLatest.files.isNotEmpty()) {
                                    val depFile = depLatest.files.firstOrNull { it.primary } ?: depLatest.files.first()
                                    val depTarget = java.io.File(targetDir, depFile.filename)
                                    if (!depTarget.exists()) {
                                        modrinth.downloadContent(depFile.url, depTarget) { _, _ -> }
                                    }
                                }
                            }
                        }
                    }

                    refreshManageData()
                    if (hit.projectType.equals("mod", ignoreCase = true)) refreshMods(instance.id)
                }
            } catch (e: Throwable) {
                println("Error installing Modrinth item: ${e.message}")
            } finally {
                modrinthDownloadingProject.value = null
                modrinthDownloadProgress.value = 0f
            }
        }
    }

    fun isModInstalled(hit: ModrinthProjectHit): Boolean {
        val mods = manageMods.value
        return mods.any { local ->
            local.fileName.contains(hit.slug, ignoreCase = true) ||
            local.fileName.contains(hit.projectId, ignoreCase = true) ||
            local.name.equals(hit.title, ignoreCase = true) ||
            local.id.equals(hit.slug, ignoreCase = true)
        }
    }

    fun isResourcePackInstalled(hit: ModrinthProjectHit): Boolean {
        val packs = manageResourcePacks.value
        return packs.any { local ->
            local.fileName.contains(hit.slug, ignoreCase = true) ||
            local.name.contains(hit.title, ignoreCase = true)
        }
    }

    fun isShaderInstalled(hit: ModrinthProjectHit): Boolean {
        val shaders = manageShaders.value
        return shaders.any { local ->
            local.fileName.contains(hit.slug, ignoreCase = true) ||
            local.name.contains(hit.title, ignoreCase = true)
        }
    }

    fun checkForModUpdates() {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            isCheckingModUpdates.value = true
            try {
                val mods = instanceManager.getMods(instance.id)
                val candidates = modrinth.checkForUpdates(
                    installedMods = mods,
                    gameVersion = instance.minecraftVersion,
                    loader = instance.loaderType.name.lowercase()
                )
                modUpdateCandidates.value = candidates
            } catch (_: Throwable) {
            } finally {
                isCheckingModUpdates.value = false
            }
        }
    }

    fun updateModFromCandidate(candidate: ModUpdateCandidate) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            try {
                val file = candidate.latestVersion.files.firstOrNull { it.primary } ?: candidate.latestVersion.files.firstOrNull()
                if (file != null) {
                    val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
                    val modsDir = java.io.File(gameDir, "mods")
                    // Delete old mod
                    instanceManager.deleteMod(instance.id, candidate.localMod.fileName)
                    // Download new mod
                    val targetFile = java.io.File(modsDir, file.filename)
                    modrinth.downloadContent(file.url, targetFile) { _, _ -> }
                    refreshManageData()
                    refreshMods(instance.id)
                    // Remove candidate from list
                    modUpdateCandidates.value = modUpdateCandidates.value.filter { it.localMod.id != candidate.localMod.id }
                }
            } catch (e: Throwable) {
                println("Update error: ${e.message}")
            }
        }
    }
}
