package io.ezz.launcher.ui.viewmodel

import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthState
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
import io.ezz.launcher.core.auth.admin.AdminAuthorizationService
import io.ezz.launcher.core.auth.admin.AdminStatus
import io.ezz.launcher.core.storage.github.GitHubReleaseService
import io.ezz.launcher.core.storage.github.GitHubConnectionStatus
import io.ezz.launcher.core.storage.github.ReleasePublishState
import io.ezz.launcher.core.runtime.discord.DiscordRpcService
import io.ezz.launcher.core.storage.vault.SecureVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
import io.ezz.launcher.core.model.instance.LogLine
import io.ezz.launcher.core.model.instance.LogReadResult
import io.ezz.launcher.core.model.instance.LogSeverityLevel
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
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultManifest
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.repository.VaultSkinRepository
import io.ezz.launcher.core.storage.repository.LocalVaultSkinRepository
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ktor.client.request.get
import io.ktor.client.call.body

data class VaultScreenState(
    val currentAccount: Account? = null,
    val activeSkin: VaultSkin? = null,
    val selectedSkin: VaultSkin? = null,
    val allSkins: List<VaultSkin> = emptyList(),
    val selectedSkinBytes: ByteArray? = null,
    val isSelectedSkinActive: Boolean = false,
    val stateVersion: Long = 0L
)

enum class NavigationScreen {
    HOME,
    INSTANCES,
    VAULT,
    ACCOUNTS,
    MODS,
    RESOURCE_PACKS,
    SHADERS,
    WORLDS,
    SCREENSHOTS,
    SETTINGS,
    SERVERS,
    PROFILES,
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

sealed class JavaValidationResult {
    object Empty : JavaValidationResult()
    object Valid : JavaValidationResult()
    object NotFound : JavaValidationResult()
    object IsDirectory : JavaValidationResult()
    object NotJavaExecutable : JavaValidationResult()
}

sealed class ReleasePublishStep {
    object Idle : ReleasePublishStep()
    object Preparing : ReleasePublishStep()
    object Uploading : ReleasePublishStep()
    object Publishing : ReleasePublishStep()
    object SyncingSupabase : ReleasePublishStep()
    data class Success(val releaseUrl: String) : ReleasePublishStep()
    data class PartialSuccess(val message: String) : ReleasePublishStep()
    data class Failed(val error: String) : ReleasePublishStep()
}

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
    val curseForgeService: io.ezz.launcher.core.network.curseforge.CurseForgeService? = null,
    val vaultSkinRepository: VaultSkinRepository? = null,
    val platformBridge: PlatformBridge = DefaultPlatformBridge(),
    val adminAuthorizationService: AdminAuthorizationService? = null,
    val gitHubReleaseService: GitHubReleaseService? = null,
    val discordRpcService: DiscordRpcService? = null,
    val secureVault: SecureVault? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val currentLauncherVersion = "1.0.0"

    val vaultRepository: VaultSkinRepository =
        vaultSkinRepository ?: LocalVaultSkinRepository(pathProvider)

    val instanceManager: LocalInstanceManager =
        localInstanceManager ?: LocalInstanceManager(pathProvider, instanceRepository)

    val modrinth: ModrinthService =
        modrinthService ?: ModrinthService()

    val curseForge: io.ezz.launcher.core.network.curseforge.CurseForgeService =
        curseForgeService ?: io.ezz.launcher.core.network.curseforge.CurseForgeService()

    val skinService: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager =
        skinManager ?: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager(
            pathProvider = pathProvider,
            httpClient = io.ezz.launcher.core.network.client.HttpClientFactory.create(),
            vaultSkinRepository = vaultRepository
        )

    val sessionTracker: io.ezz.launcher.core.runtime.process.ProcessSessionTracker =
        processSessionTracker ?: io.ezz.launcher.core.runtime.process.ProcessSessionTracker(pathProvider)

    private val _currentScreen = MutableStateFlow(NavigationScreen.HOME)
    val currentScreen: StateFlow<NavigationScreen> = _currentScreen.asStateFlow()

    val instances: StateFlow<List<Instance>> = instanceRepository.instances

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

    private val _betaVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val betaVersions: StateFlow<List<VersionSummary>> = _betaVersions.asStateFlow()

    private val _alphaVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val alphaVersions: StateFlow<List<VersionSummary>> = _alphaVersions.asStateFlow()

    private val _oldVersions = MutableStateFlow<List<VersionSummary>>(emptyList())
    val oldVersions: StateFlow<List<VersionSummary>> = _oldVersions.asStateFlow()

    val isVersionManifestLoading = MutableStateFlow(false)
    val versionManifestError = MutableStateFlow<String?>(null)
    val latestReleaseVersion = MutableStateFlow("1.21.4")
    val latestSnapshotVersion = MutableStateFlow("24w46a")

    private val _detectedJavaRuntimes = MutableStateFlow<List<JavaRuntime>>(emptyList())
    val detectedJavaRuntimes: StateFlow<List<JavaRuntime>> = _detectedJavaRuntimes.asStateFlow()
    val isDetectingJava = MutableStateFlow(false)

    private val _detectedGpus = MutableStateFlow<List<io.ezz.launcher.core.runtime.detector.DetectedGpu>>(emptyList())
    val detectedGpus: StateFlow<List<io.ezz.launcher.core.runtime.detector.DetectedGpu>> = _detectedGpus.asStateFlow()

    private val _systemMemoryInfo = MutableStateFlow(io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.getSystemMemoryInfo())
    val systemMemoryInfo: StateFlow<io.ezz.launcher.core.runtime.detector.SystemMemoryInfo> = _systemMemoryInfo.asStateFlow()

    fun appendConsoleLog(message: String, isError: Boolean = false) {
        val entry = ConsoleLogEntry(message = message, isError = isError)
        val current = _logs.value
        if (current.size >= 2000) {
            _logs.value = current.drop(current.size - 1999) + entry
        } else {
            _logs.value = current + entry
        }
    }

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

    val isCheckingForUpdates = MutableStateFlow(false)
    val updateCheckError = MutableStateFlow<String?>(null)

    // Admin & Release System State
    private val _adminStatus = MutableStateFlow<AdminStatus>(AdminStatus.NormalUser())
    val adminStatus: StateFlow<AdminStatus> = _adminStatus.asStateFlow()

    val githubConnectionStatus: StateFlow<GitHubConnectionStatus> =
        gitHubReleaseService?.connectionStatus ?: MutableStateFlow(GitHubConnectionStatus.Disconnected).asStateFlow()

    private val _isCheckingAdmin = MutableStateFlow(false)
    val isCheckingAdmin: StateFlow<Boolean> = _isCheckingAdmin.asStateFlow()

    private val _releasePublishStep = MutableStateFlow<ReleasePublishStep>(ReleasePublishStep.Idle)
    val releasePublishStep: StateFlow<ReleasePublishStep> = _releasePublishStep.asStateFlow()

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

    // Authoritative Unified Vault Skin State (Single Source of Truth)
    private val _selectedVaultSkinId = MutableStateFlow<String?>(null)
    val selectedVaultSkinId: StateFlow<String?> = _selectedVaultSkinId.asStateFlow()

    private val _vaultVersion = MutableStateFlow(0L)
    val vaultVersion: StateFlow<Long> = _vaultVersion.asStateFlow()

    val vaultState: StateFlow<VaultScreenState> = combine(
        accountRepository.selectedAccount,
        vaultRepository.manifest,
        _selectedVaultSkinId,
        _vaultVersion
    ) { account, manifest, selectedId, version ->
        val currentAccountId = account?.id
        val activeSkinId = if (currentAccountId != null) {
            manifest.accountSkinMappings[currentAccountId] ?: manifest.activeSkinId
        } else {
            manifest.activeSkinId
        }
        val activeSkin = activeSkinId?.let { id -> manifest.skins.firstOrNull { it.id == id } }

        val selectedSkin = if (selectedId != null) {
            manifest.skins.firstOrNull { it.id == selectedId } ?: activeSkin ?: manifest.skins.firstOrNull()
        } else {
            activeSkin ?: manifest.skins.firstOrNull()
        }

        val isSelectedActive = if (selectedSkin != null) {
            selectedSkin.id == activeSkin?.id
        } else {
            activeSkin == null
        }

        val skinBytes = selectedSkin?.let { vaultRepository.getSkinBytes(it) }

        VaultScreenState(
            currentAccount = account,
            activeSkin = activeSkin,
            selectedSkin = selectedSkin,
            allSkins = manifest.skins,
            selectedSkinBytes = skinBytes,
            isSelectedSkinActive = isSelectedActive,
            stateVersion = version
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = VaultScreenState()
    )

    val vaultSkins: StateFlow<List<VaultSkin>> = vaultRepository.skins
    val activeVaultSkinId: StateFlow<String?> = vaultRepository.activeSkinId
    private val _selectedVaultSkin = MutableStateFlow<VaultSkin?>(null)
    val selectedVaultSkin: StateFlow<VaultSkin?> = _selectedVaultSkin.asStateFlow()

    // Dialog States
    val showCreateInstanceDialog = MutableStateFlow(false)
    val showEditInstanceDialog = MutableStateFlow<Instance?>(null)
    val showModpackBrowserDialog = MutableStateFlow(false)
    val showImportModpackDialog = MutableStateFlow(false)
    val showExportModpackDialog = MutableStateFlow<Instance?>(null)
    val pendingMrpackFile = MutableStateFlow<java.io.File?>(null)
    val mrpackImportProgress = MutableStateFlow<io.ezz.launcher.core.model.modrinth.MrpackImportProgress?>(null)
    val isImportingMrpack = MutableStateFlow(false)
    private var mrpackImportJob: Job? = null
    val showAddOfflineAccountDialog = MutableStateFlow(false)
    val showMicrosoftLoginDialog = MutableStateFlow(false)
    val showEzzAuthDialog = MutableStateFlow(false)
    val showAddServerDialog = MutableStateFlow(false)
    val showSearchDialog = MutableStateFlow(false)
    val microsoftAuthState = MutableStateFlow<MicrosoftAuthState>(MicrosoftAuthState.Idle)
    private var microsoftLoginJob: Job? = null
    var windowHandle: Long? = null
    var nativeWindowProvider: (() -> Long?)? = null

    // Dedicated Instance Manager State
    val activeManageTab = MutableStateFlow(InstanceManagerTab.OVERVIEW)
    val manageStatistics = MutableStateFlow<InstanceStatistics?>(null)
    val manageMods = MutableStateFlow<List<LocalMod>>(emptyList())
    val missingDependencies = MutableStateFlow<List<String>>(emptyList())
    val compatibilityConflicts = MutableStateFlow<List<io.ezz.launcher.core.model.modrinth.ModConflict>>(emptyList())
    val manageResourcePacks = MutableStateFlow<List<LocalResourcePack>>(emptyList())
    val manageShaders = MutableStateFlow<List<LocalShaderPack>>(emptyList())
    val manageWorlds = MutableStateFlow<List<LocalWorld>>(emptyList())
    val manageScreenshots = MutableStateFlow<List<LocalScreenshot>>(emptyList())
    val manageLogs = MutableStateFlow<List<InstanceLogEntry>>(emptyList())
    val manageSelectedLogContent = MutableStateFlow<String?>(null)
    val manageLogResult = MutableStateFlow<LogReadResult?>(null)
    val isLogLoading = MutableStateFlow(false)
    val logLoadError = MutableStateFlow<String?>(null)
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
    private var loadLogJob: Job? = null
    private var liveLogJob: Job? = null

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

        scope.launch(Dispatchers.IO) {
            try {
                supabaseClient?.let {
                    _isSupabaseConnected.value = it.checkConnection()
                }
                profileRepository?.loadProfile()
                refreshAvailableVersions()
                refreshJavaRuntimes()
                _detectedGpus.value = io.ezz.launcher.core.runtime.detector.GpuDetector.detectGpus()
                _systemMemoryInfo.value = io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.getSystemMemoryInfo()

                // Load Public Supabase Tables
                loadPublicData()
            } catch (e: Throwable) {
                _errorMessage.value = "Background service note: ${e.message}"
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

        scope.launch {
            try {
                accountRepository.accounts.collect { accounts ->
                    accounts.forEach { acc ->
                        skinService.loadOrRefreshSkin(acc)
                    }
                }
            } catch (e: Throwable) {
                println("Error collecting accounts for skins: ${e.message}")
            }
        }

        scope.launch {
            try {
                accountRepository.selectedAccount.collect { selAcc ->
                    // Reset ephemeral preview selection when switching accounts
                    _selectedVaultSkin.value = null
                    if (selAcc != null) {
                        skinService.loadOrRefreshSkin(selAcc)
                    }
                    refreshAdminStatus(selAcc)
                }
            } catch (e: Throwable) {
                println("Error collecting selectedAccount: ${e.message}")
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
        val previousId = _selectedInstance.value?.id
        if (previousId != instance.id) {
            stopLiveLogWatching()
            selectedLogFile.value = null
            manageSelectedLogContent.value = null
            manageLogResult.value = null
            logLoadError.value = null
        }
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
        if (fileName.startsWith("ezz-skin-mod", ignoreCase = true) || fileName.contains("ezzskin", ignoreCase = true)) {
            println("[AppViewModel] Cannot delete protected Ezz Skin Mod: $fileName")
            return
        }
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
                _selectedVaultSkinId.value = null
                _selectedVaultSkin.value = null
                _vaultVersion.value++
            } catch (e: Exception) {
                _errorMessage.value = "Failed to select account: ${e.message}"
            }
        }
    }



    fun stopInstance() {
        _processState.value = ProcessState.Idle
    }

    fun refreshAvailableVersions(forceRefresh: Boolean = false) {
        scope.launch {
            isVersionManifestLoading.value = true
            versionManifestError.value = null
            try {
                val manifest = versionManifestService.getVersionManifest(forceRefresh)
                val releases = manifest.versions.filter { it.type == "release" }
                val snapshots = manifest.versions.filter { it.type == "snapshot" }
                val beta = manifest.versions.filter { it.type == "old_beta" }
                val alpha = manifest.versions.filter { it.type == "old_alpha" }
                val olds = beta + alpha
                
                _allVersions.value = manifest.versions
                _availableVersions.value = releases
                _snapshotVersions.value = snapshots
                _betaVersions.value = beta
                _alphaVersions.value = alpha
                _oldVersions.value = olds

                latestReleaseVersion.value = manifest.latest.release
                latestSnapshotVersion.value = manifest.latest.snapshot
            } catch (e: Exception) {
                println("Warning: failed to refresh versions: ${e.message}")
                versionManifestError.value = e.message ?: "Unable to load Minecraft versions"
            } finally {
                isVersionManifestLoading.value = false
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
            isCheckingForUpdates.value = true
            updateCheckError.value = null
            try {
                if (releaseRepository != null) {
                    val result = withContext(Dispatchers.IO) {
                        releaseRepository.checkForUpdates(currentLauncherVersion, platform = "windows")
                    }
                    _updateCheckResult.value = result
                } else {
                    updateCheckError.value = "Could not check for updates. Try again later."
                }
            } catch (e: Throwable) {
                updateCheckError.value = "Could not check for updates. Try again later."
            } finally {
                isCheckingForUpdates.value = false
            }
        }
    }

    fun validateCustomJavaPath(path: String): JavaValidationResult {
        if (path.isBlank()) return JavaValidationResult.Empty
        val file = java.io.File(path.trim())
        if (!file.exists()) return JavaValidationResult.NotFound
        if (file.isDirectory) return JavaValidationResult.IsDirectory
        val name = file.name.lowercase()
        val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")
        val isExecutable = if (isWindows) {
            name == "java.exe" || name == "javaw.exe" || name.endsWith(".exe")
        } else {
            name == "java" || file.canExecute()
        }
        if (!isExecutable) return JavaValidationResult.NotJavaExecutable
        return JavaValidationResult.Valid
    }

    fun updateCustomJavaPath(path: String) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(defaultJavaPath = path.trim()) }
            } catch (e: Exception) {
                println("Failed to update custom Java path: ${e.message}")
            }
        }
    }

    fun updateWindowDefaults(width: Int, height: Int, fullscreen: Boolean) {
        scope.launch {
            try {
                settingsRepository.updateSettings {
                    it.copy(
                        defaultWindowWidth = width.coerceIn(320, 7680),
                        defaultWindowHeight = height.coerceIn(240, 4320),
                        defaultFullscreen = fullscreen
                    )
                }
            } catch (e: Exception) {
                println("Failed to update window defaults: ${e.message}")
            }
        }
    }

    fun updateDiscordRpc(enabled: Boolean) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(enableDiscordRpc = enabled) }
                if (!enabled) {
                    discordRpcService?.clearActivity()
                }
            } catch (e: Exception) {
                println("Failed to update Discord RPC setting: ${e.message}")
            }
        }
    }

    fun refreshAdminStatus(account: Account? = accountRepository.selectedAccount.value) {
        scope.launch {
            _isCheckingAdmin.value = true
            try {
                val targetAccount = account ?: accountRepository.selectedAccount.value
                if (targetAccount == null) {
                    _adminStatus.value = AdminStatus.NormalUser()
                    return@launch
                }
                if (adminAuthorizationService != null) {
                    val status = withContext(Dispatchers.IO) {
                        adminAuthorizationService.verifyAdminStatus(targetAccount)
                    }
                    _adminStatus.value = status
                    if (status is AdminStatus.VerifiedAdmin) {
                        checkGitHubStatus()
                    }
                } else {
                    _adminStatus.value = AdminStatus.NormalUser(
                        minecraftUsername = targetAccount.username,
                        minecraftUuid = targetAccount.uuid,
                        microsoftConnected = targetAccount is io.ezz.launcher.core.model.account.MicrosoftAccount
                    )
                }
            } catch (e: Throwable) {
                println("Admin verification notice: ${e.message}")
                _adminStatus.value = AdminStatus.NormalUser()
            } finally {
                _isCheckingAdmin.value = false
            }
        }
    }

    fun checkGitHubStatus() {
        gitHubReleaseService?.checkExistingToken()
    }

    fun connectGitHub(token: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            if (gitHubReleaseService != null) {
                val status = withContext(Dispatchers.IO) {
                    gitHubReleaseService.connectWithToken(token.trim())
                }
                if (status is GitHubConnectionStatus.Connected) {
                    onResult(true, null)
                } else if (status is GitHubConnectionStatus.Error) {
                    onResult(false, status.message)
                } else {
                    onResult(false, "Failed to connect to GitHub")
                }
            } else {
                onResult(false, "GitHub release service is unavailable")
            }
        }
    }

    fun disconnectGitHub() {
        scope.launch {
            withContext(Dispatchers.IO) {
                gitHubReleaseService?.disconnect()
            }
        }
    }

    fun publishAdminRelease(
        version: String,
        title: String,
        changelog: String,
        artifactFile: java.io.File?,
        isDraft: Boolean
    ) {
        scope.launch {
            val currentAccount = accountRepository.selectedAccount.value
            if (currentAccount == null || adminStatus.value !is AdminStatus.VerifiedAdmin) {
                _releasePublishStep.value = ReleasePublishStep.Failed("Unauthorized: verified admin account required.")
                return@launch
            }
            if (gitHubReleaseService == null) {
                _releasePublishStep.value = ReleasePublishStep.Failed("GitHub release service unavailable.")
                return@launch
            }

            gitHubReleaseService.publishRelease(
                adminUsername = currentAccount.username,
                version = version,
                releaseTitle = title,
                releaseNotes = changelog,
                artifactFile = artifactFile,
                isDraft = isDraft
            ).collect { state ->
                when (state) {
                    is ReleasePublishState.Idle -> _releasePublishStep.value = ReleasePublishStep.Idle
                    is ReleasePublishState.Preparing -> _releasePublishStep.value = ReleasePublishStep.Preparing
                    is ReleasePublishState.PublishingRelease -> _releasePublishStep.value = ReleasePublishStep.Publishing
                    is ReleasePublishState.UploadingArtifact -> _releasePublishStep.value = ReleasePublishStep.Uploading
                    is ReleasePublishState.SyncingSupabase -> _releasePublishStep.value = ReleasePublishStep.SyncingSupabase
                    is ReleasePublishState.Published -> {
                        _releasePublishStep.value = ReleasePublishStep.Success(state.gitHubUrl)
                        checkForUpdates()
                    }
                    is ReleasePublishState.Failed -> {
                        if (state.isPartialSuccess) {
                            _releasePublishStep.value = ReleasePublishStep.PartialSuccess(state.error)
                        } else {
                            _releasePublishStep.value = ReleasePublishStep.Failed(state.error)
                        }
                    }
                }
            }
        }
    }

    fun resetReleasePublishState() {
        _releasePublishStep.value = ReleasePublishStep.Idle
    }

    fun refreshJavaRuntimes() {
        scope.launch(Dispatchers.IO) {
            isDetectingJava.value = true
            try {
                val detected = JavaRuntimeDetector.detectInstalledRuntimes()
                _detectedJavaRuntimes.value = detected
            } catch (e: Exception) {
                println("Warning: failed to detect Java runtimes: ${e.message}")
            } finally {
                isDetectingJava.value = false
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

        val rawAccount = account

        scope.launch {
            val launchAccount = try {
                authManager.getValidSession(rawAccount)
            } catch (e: Exception) {
                _logs.value = listOf(ConsoleLogEntry(message = "[Auth Notice] ${e.message}", isError = false))
                rawAccount
            }

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
                            appendConsoleLog(event.line, isError = event.isError)
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
        minMemoryMb: Int = 1024,
        maxMemoryMb: Int = 4096,
        customJvmArgs: List<String> = emptyList(),
        javaPath: String? = null,
        windowWidth: Int = 1280,
        windowHeight: Int = 720,
        customIconFile: java.io.File? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        scope.launch {
            try {
                var newInstance = instanceRepository.createInstance(
                    name = name.trim().ifBlank { "Minecraft $minecraftVersion" },
                    minecraftVersion = minecraftVersion,
                    loaderType = loaderType,
                    loaderVersion = loaderVersion,
                    minMemoryMb = minMemoryMb,
                    maxMemoryMb = maxMemoryMb,
                    customJvmArgs = customJvmArgs,
                    javaPath = javaPath,
                    windowWidth = windowWidth,
                    windowHeight = windowHeight
                )

                if (customIconFile != null && customIconFile.exists()) {
                    newInstance = instanceManager.setCustomIcon(newInstance.id, customIconFile)
                }

                _selectedInstance.value = newInstance
                showCreateInstanceDialog.value = false
                onSuccess?.invoke()
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

    private var importSessionId: Long = 0L

    fun openImportModpack(file: java.io.File? = null) {
        resetImportState()
        pendingMrpackFile.value = file
        showImportModpackDialog.value = true
    }

    fun closeImportModpack() {
        showImportModpackDialog.value = false
        resetImportState()
    }

    fun resetImportState() {
        importSessionId++
        mrpackImportJob?.cancel()
        mrpackImportJob = null
        isImportingMrpack.value = false
        mrpackImportProgress.value = null
        pendingMrpackFile.value = null
    }

    fun openExportModpack(instance: Instance) {
        showExportModpackDialog.value = instance
    }

    fun cancelMrpackImport() {
        resetImportState()
        ToastManager.show("Import Cancelled", "Modpack import was aborted.", ToastType.INFO)
    }

    fun executeImportMrpack(
        file: java.io.File,
        instanceName: String? = null,
        onComplete: ((Result<Instance>) -> Unit)? = null
    ) {
        mrpackImportJob?.cancel()
        val session = ++importSessionId
        mrpackImportJob = scope.launch {
            isImportingMrpack.value = true
            mrpackImportProgress.value = io.ezz.launcher.core.model.modrinth.MrpackImportProgress(
                stage = io.ezz.launcher.core.model.modrinth.MrpackImportStage.READING_MANIFEST,
                message = "Validating and reading modpack...",
                progress = 0.05f
            )

            try {
                val result = instanceManager.mrpackManager.importMrpack(file, instanceName) { progress ->
                    if (session == importSessionId) {
                        mrpackImportProgress.value = progress
                    }
                }

                if (session != importSessionId) {
                    return@launch // Discard stale session
                }

                isImportingMrpack.value = false
                if (result.isSuccess) {
                    val imported = result.getOrNull()
                    if (imported != null) {
                        _selectedInstance.value = imported
                    }
                    instanceRepository.loadAll()
                    ToastManager.show(
                        title = "Modpack Imported",
                        description = "'${imported?.name ?: "Instance"}' is ready to play!",
                        type = ToastType.SUCCESS
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to import modpack"
                    ToastManager.show("Import Failed", error, ToastType.ERROR)
                }
                onComplete?.invoke(result)
            } catch (e: Exception) {
                if (session != importSessionId) return@launch
                isImportingMrpack.value = false
                mrpackImportProgress.value = null
                ToastManager.show("Import Error", e.message ?: "Unknown error", ToastType.ERROR)
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun executeExportMrpack(
        instance: Instance,
        targetFile: java.io.File,
        options: io.ezz.launcher.core.model.modrinth.MrpackExportOptions,
        onProgress: (String, Float) -> Unit = { _, _ -> },
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val result = instanceManager.mrpackManager.exportMrpack(instance, targetFile, options, onProgress)
                if (result.isSuccess) {
                    ToastManager.show(
                        title = "Modpack Exported",
                        description = "Saved '${options.customName ?: instance.name}' (.mrpack)",
                        type = ToastType.SUCCESS
                    )
                    onComplete?.invoke(true)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to create .mrpack"
                    ToastManager.show("Export Failed", error, ToastType.ERROR)
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                ToastManager.show("Export Error", e.message ?: "Failed to export", ToastType.ERROR)
                onComplete?.invoke(false)
            }
        }
    }

    fun importInstanceFromFile(
        file: java.io.File,
        preferredName: String? = null,
        onComplete: ((Result<Instance>) -> Unit)? = null
    ) {
        openImportModpack(file)
    }

    fun exportInstanceToFile(
        instance: Instance,
        targetFile: java.io.File,
        includeWorlds: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val options = io.ezz.launcher.core.model.modrinth.MrpackExportOptions(
            customName = instance.name,
            includeConfigs = true,
            includeMods = true,
            includeResourcePacks = true,
            includeShaderPacks = true
        )
        executeExportMrpack(instance, targetFile, options, onComplete = onComplete)
    }

    fun installModrinthModpack(
        hit: ModrinthProjectHit,
        version: ModrinthVersion?,
        customName: String? = null,
        onComplete: ((Result<Instance>) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val targetVersion = version ?: modrinth.getProjectVersions(hit.projectId).firstOrNull()
                if (targetVersion == null) {
                    val error = IllegalStateException("No compatible versions found for modpack '${hit.title}'.")
                    ToastManager.show("Installation Failed", error.message, ToastType.ERROR)
                    onComplete?.invoke(Result.failure(error))
                    return@launch
                }

                val primaryFile = targetVersion.files.firstOrNull { it.primary } ?: targetVersion.files.firstOrNull()
                if (primaryFile == null) {
                    val error = IllegalStateException("No downloadable file found for modpack version '${targetVersion.name}'.")
                    ToastManager.show("Installation Failed", error.message, ToastType.ERROR)
                    onComplete?.invoke(Result.failure(error))
                    return@launch
                }

                activeDownloadState.value = ActiveDownloadState(
                    stage = "DOWNLOADING",
                    currentFile = "${hit.title} modpack",
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = primaryFile.size
                )

                val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "ezz_modpack_dl")
                tempDir.mkdirs()
                val tempMrpack = java.io.File(tempDir, "${hit.slug}_${targetVersion.id}.mrpack")

                val downloaded = modrinth.downloadContent(primaryFile.url, tempMrpack) { bytes, total ->
                    val pct = if (total > 0) bytes.toFloat() / total.toFloat() else 0f
                    activeDownloadState.value = ActiveDownloadState(
                        stage = "DOWNLOADING",
                        currentFile = "${hit.title} (${primaryFile.filename})",
                        progress = pct,
                        downloadedBytes = bytes,
                        totalBytes = total
                    )
                }

                if (!downloaded || !tempMrpack.exists()) {
                    activeDownloadState.value = null
                    val error = IllegalStateException("Failed to download modpack archive from Modrinth.")
                    ToastManager.show("Download Failed", error.message, ToastType.ERROR)
                    onComplete?.invoke(Result.failure(error))
                    return@launch
                }

                val packName = customName?.takeIf { it.isNotBlank() } ?: hit.title
                val result = instanceManager.importInstanceFromMrpack(tempMrpack, packName) { step, progress ->
                    activeDownloadState.value = ActiveDownloadState(
                        stage = "INSTALLING",
                        currentFile = step,
                        progress = progress,
                        downloadedBytes = (progress * 100L).toLong(),
                        totalBytes = 100L
                    )
                }

                val iconUrl = hit.iconUrl
                if (result.isSuccess) {
                    val rawCreated = result.getOrNull()
                    if (rawCreated != null && !iconUrl.isNullOrBlank()) {
                        try {
                            val iconBytes = modrinth.downloadImageBytes(iconUrl)
                            if (iconBytes != null && iconBytes.isNotEmpty()) {
                                val iconFile = java.io.File(tempDir, "icon_${hit.projectId}.png")
                                iconFile.writeBytes(iconBytes)
                                instanceManager.setCustomIcon(rawCreated.id, iconFile)
                                iconFile.delete()
                            }
                        } catch (_: Exception) {}
                    }

                    instanceRepository.loadAll()
                    val created = rawCreated?.let { instanceRepository.getInstance(it.id) } ?: rawCreated
                    if (created != null) {
                        _selectedInstance.value = created
                    }
                    showModpackBrowserDialog.value = false
                    ToastManager.show(
                        title = "Modpack Installed",
                        description = "'${packName}' is ready to play!",
                        type = ToastType.SUCCESS
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Installation failed"
                    ToastManager.show("Modpack Error", error, ToastType.ERROR)
                }
                onComplete?.invoke(result)
            } catch (e: Exception) {
                activeDownloadState.value = null
                ToastManager.show("Modpack Error", e.message ?: "Failed to install modpack", ToastType.ERROR)
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun addOfflineAccount(username: String) {
        scope.launch {
            try {
                val cleanUsername = username.trim()
                if (cleanUsername.isBlank()) return@launch

                val created = authManager.createOfflineAccount(cleanUsername)
                showAddOfflineAccountDialog.value = false
                skinService.loadOrRefreshSkin(created)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add offline account: ${e.message}"
            }
        }
    }

    fun setWindowHandle(handle: Long) {
        this.windowHandle = handle
        authManager.microsoftAuthService.setWindowHandle(handle)
    }

    fun openMicrosoftLoginModal() {
        microsoftAuthState.value = MicrosoftAuthState.Idle
        val effectiveHandle = windowHandle ?: nativeWindowProvider?.invoke()
        if (effectiveHandle != null && effectiveHandle != 0L) {
            windowHandle = effectiveHandle
            authManager.microsoftAuthService.setWindowHandle(effectiveHandle)
        }
        showMicrosoftLoginDialog.value = true
    }

    fun startMicrosoftLogin() {
        showMicrosoftLoginDialog.value = true
        microsoftAuthState.value = MicrosoftAuthState.ConnectingToMicrosoft

        val effectiveHandle = windowHandle ?: nativeWindowProvider?.invoke()
        if (effectiveHandle != null && effectiveHandle != 0L) {
            windowHandle = effectiveHandle
            authManager.microsoftAuthService.setWindowHandle(effectiveHandle)
        }

        microsoftLoginJob?.cancel()
        microsoftLoginJob = scope.launch {
            try {
                authManager.startMicrosoftLogin(effectiveHandle).collect { state ->
                    if (state is MicrosoftAuthState.Cancelled) {
                        println("[AppViewModel] Microsoft authentication was cancelled by user. Returning cleanly to Accounts page.")
                        microsoftAuthState.value = MicrosoftAuthState.Idle
                        showMicrosoftLoginDialog.value = false
                        return@collect
                    }
                    microsoftAuthState.value = state
                    if (state is MicrosoftAuthState.Success) {
                        skinService.loadOrRefreshSkin(state.account)
                    }
                }
            } catch (e: Exception) {
                microsoftAuthState.value = MicrosoftAuthState.Failed(e.message ?: "Authentication failed")
            }
        }
    }

    fun cancelMicrosoftLogin() {
        microsoftLoginJob?.cancel()
        microsoftLoginJob = null
        authManager.microsoftAuthService.cancelActiveLogin()
        microsoftAuthState.value = MicrosoftAuthState.Idle
        showMicrosoftLoginDialog.value = false
    }

    fun deleteAccount(id: String) {
        scope.launch {
            try {
                authManager.removeAccount(id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to remove account: ${e.message}"
            }
        }
    }

    fun removeAccount(id: String) = deleteAccount(id)

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
        val prevTab = activeManageTab.value
        activeManageTab.value = tab
        if (prevTab == InstanceManagerTab.LOGS && tab != InstanceManagerTab.LOGS) {
            stopLiveLogWatching()
        }
        if (tab == InstanceManagerTab.MODS && modsBrowseState.value.items.isEmpty()) {
            searchMods()
        } else if (tab == InstanceManagerTab.RESOURCE_PACKS && resourcePacksBrowseState.value.items.isEmpty()) {
            searchResourcePacks()
        } else if (tab == InstanceManagerTab.SHADERS && shadersBrowseState.value.items.isEmpty()) {
            searchShaders()
        } else if (tab == InstanceManagerTab.LOGS) {
            val inst = _selectedInstance.value
            if (inst != null && _runningSessions.value.containsKey(inst.id)) {
                startLiveLogWatching(inst.id)
            }
        }
    }

    fun refreshManageData() {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            try {
                manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
                val mods = instanceManager.getMods(instance.id)
                manageMods.value = mods
                refreshMissingDependencies(instance, mods)
                manageResourcePacks.value = instanceManager.getResourcePacks(instance.id)
                manageShaders.value = instanceManager.getShaderPacks(instance.id)
                manageWorlds.value = instanceManager.getWorlds(instance.id)
                manageScreenshots.value = instanceManager.getScreenshots(instance.id)
                val logsList = instanceManager.getLogs(instance.id)
                manageLogs.value = logsList

                // Ensure selected log is consistent with the current instance
                val currentSel = selectedLogFile.value
                if (currentSel == null || logsList.none { it.filePath == currentSel.filePath }) {
                    val latest = logsList.firstOrNull { it.fileName == "latest.log" } ?: logsList.firstOrNull()
                    loadLogContent(latest)
                }
            } catch (e: Throwable) {
                println("Error refreshing manage data: ${e.message}")
            }
        }
    }

    fun refreshMissingDependencies(instance: Instance? = _selectedInstance.value, mods: List<LocalMod>? = null) {
        val inst = instance ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val currentMods = mods ?: manageMods.value.ifEmpty { instanceManager.getMods(inst.id) }
                val report = io.ezz.launcher.core.minecraft.mods.ModCompatibilityResolver.validateLaunchCompatibility(
                    minecraftVersion = inst.minecraftVersion,
                    loader = inst.loaderType.name,
                    installedMods = currentMods
                )
                missingDependencies.value = report.missingDependencies
                compatibilityConflicts.value = report.explicitConflicts
            } catch (e: Throwable) {
                println("[AppViewModel] Error refreshing missing dependencies: ${e.message}")
            }
        }
    }

    // MODS
    fun toggleManageMod(fileName: String, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        val isEzzSkinMod = fileName.startsWith("ezz-skin-mod", ignoreCase = true) || fileName.contains("ezzskin", ignoreCase = true)
        if (isEzzSkinMod) {
            val updated = instance.copy(ezzSkinEnabled = enable)
            updateInstance(updated)
        }
        scope.launch {
            instanceManager.toggleMod(instance.id, fileName, enable)
            manageMods.value = instanceManager.getMods(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshMods(instance.id)
        }
    }

    fun bulkToggleMods(fileNames: List<String>, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        val hasEzzSkinMod = fileNames.any { it.startsWith("ezz-skin-mod", ignoreCase = true) || it.contains("ezzskin", ignoreCase = true) }
        if (hasEzzSkinMod) {
            val updated = instance.copy(ezzSkinEnabled = enable)
            updateInstance(updated)
        }
        scope.launch {
            fileNames.forEach { fileName ->
                instanceManager.toggleMod(instance.id, fileName, enable)
            }
            manageMods.value = instanceManager.getMods(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshMods(instance.id)
        }
    }

    fun deleteManageMod(fileName: String) {
        val instance = _selectedInstance.value ?: return
        if (fileName.startsWith("ezz-skin-mod", ignoreCase = true) || fileName.contains("ezzskin", ignoreCase = true)) {
            println("[AppViewModel] Cannot delete protected Ezz Skin Mod: $fileName")
            return
        }
        scope.launch {
            instanceManager.deleteMod(instance.id, fileName)
            manageMods.value = instanceManager.getMods(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshMods(instance.id)
        }
    }

    fun bulkDeleteMods(fileNames: List<String>) {
        val instance = _selectedInstance.value ?: return
        val filtered = fileNames.filterNot { it.startsWith("ezz-skin-mod", ignoreCase = true) || it.contains("ezzskin", ignoreCase = true) }
        scope.launch {
            filtered.forEach { fileName ->
                instanceManager.deleteMod(instance.id, fileName)
            }
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

    fun bulkToggleResourcePacks(fileNames: List<String>, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            fileNames.forEach { fileName ->
                instanceManager.toggleResourcePack(instance.id, fileName, enable)
            }
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

    fun bulkDeleteResourcePacks(fileNames: List<String>) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            fileNames.forEach { fileName ->
                instanceManager.deleteResourcePack(instance.id, fileName)
            }
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

    fun bulkToggleShaders(fileNames: List<String>, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            fileNames.forEach { fileName ->
                instanceManager.toggleShaderPack(instance.id, fileName, enable)
            }
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

    fun bulkDeleteShaders(fileNames: List<String>) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            fileNames.forEach { fileName ->
                instanceManager.deleteShaderPack(instance.id, fileName)
            }
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
    fun loadLogContent(logEntry: InstanceLogEntry?, isLiveUpdate: Boolean = false) {
        if (logEntry == null) {
            selectedLogFile.value = null
            manageSelectedLogContent.value = null
            manageLogResult.value = null
            isLogLoading.value = false
            logLoadError.value = null
            return
        }

        selectedLogFile.value = logEntry
        if (!isLiveUpdate) {
            isLogLoading.value = true
        }
        logLoadError.value = null

        loadLogJob?.cancel()
        loadLogJob = scope.launch(Dispatchers.IO) {
            try {
                val result = instanceManager.readLogResult(logEntry.filePath, maxLines = 5000)
                manageLogResult.value = result
                manageSelectedLogContent.value = if (result.lines.isEmpty()) "" else result.lines.joinToString("\n") { it.text }
                logLoadError.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation
            } catch (e: Throwable) {
                logLoadError.value = "Failed to load log: ${e.message}"
            } finally {
                isLogLoading.value = false
            }
        }
    }

    fun startLiveLogWatching(instanceId: String) {
        liveLogJob?.cancel()
        liveLogJob = scope.launch(Dispatchers.IO) {
            var lastModTime = 0L
            var lastSize = 0L
            while (isActive) {
                delay(1500L)
                val currentSelected = _selectedInstance.value
                val isRunning = _runningSessions.value.containsKey(instanceId)
                if (currentSelected?.id != instanceId || !isRunning || activeManageTab.value != InstanceManagerTab.LOGS) {
                    break
                }

                val currentLog = selectedLogFile.value ?: manageLogs.value.firstOrNull { it.fileName == "latest.log" }
                if (currentLog != null) {
                    val file = java.io.File(currentLog.filePath)
                    if (file.exists()) {
                        val currentMod = file.lastModified()
                        val currentSize = file.length()
                        if (currentMod != lastModTime || currentSize != lastSize) {
                            lastModTime = currentMod
                            lastSize = currentSize
                            loadLogContent(currentLog, isLiveUpdate = true)
                        }
                    }
                }
            }
        }
    }

    fun stopLiveLogWatching() {
        liveLogJob?.cancel()
        liveLogJob = null
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

    // Active Mod Install Modal state
    val activeModInstallProject = MutableStateFlow<ModrinthProjectHit?>(null)

    fun openModInstaller(hit: ModrinthProjectHit) {
        activeModInstallProject.value = hit
    }

    fun closeModInstaller() {
        activeModInstallProject.value = null
    }

    fun getInstalledMod(hit: ModrinthProjectHit, instanceId: String? = _selectedInstance.value?.id): LocalMod? {
        val querySlug = hit.slug.lowercase()
        val queryTitle = hit.title.lowercase()
        val queryId = hit.projectId.lowercase()

        val foundLocal = manageMods.value.firstOrNull { local ->
            val fileName = local.fileName.lowercase()
            val name = local.name.lowercase()
            val id = local.id.lowercase()
            fileName.contains(querySlug) ||
            fileName.contains(queryId) ||
            name.equals(queryTitle, ignoreCase = true) ||
            id.equals(querySlug, ignoreCase = true) ||
            id.equals(queryId, ignoreCase = true)
        }
        if (foundLocal != null) return foundLocal

        val meta = _installedMods.value.firstOrNull { m ->
            val fileName = m.fileName.lowercase()
            val name = m.name.lowercase()
            val id = m.id.lowercase()
            fileName.contains(querySlug) ||
            fileName.contains(queryId) ||
            name.equals(queryTitle, ignoreCase = true) ||
            id.equals(querySlug, ignoreCase = true) ||
            id.equals(queryId, ignoreCase = true)
        }
        return meta?.let {
            LocalMod(
                id = it.id,
                name = it.name,
                version = it.version,
                description = it.description,
                fileName = it.fileName,
                fileSize = it.fileSize,
                enabled = it.enabled,
                loader = it.loader
            )
        }
    }

    suspend fun installModWithDependencies(
        instance: Instance,
        project: ModrinthProjectHit,
        mainVersion: ModrinthVersion,
        selectedDependencies: List<io.ezz.launcher.core.model.modrinth.ResolvedModDependency>,
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
        val modsDir = java.io.File(gameDir, "mods")
        modsDir.mkdirs()

        val timeStamp = System.currentTimeMillis()
        val stagingDir = java.io.File(gameDir, ".install_staging_$timeStamp")
        val backupDir = java.io.File(gameDir, ".install_backup_$timeStamp")
        stagingDir.mkdirs()

        val newlyAddedModFiles = mutableListOf<java.io.File>()
        val backedUpOldFiles = mutableListOf<Pair<java.io.File, java.io.File>>() // original -> backup

        try {
            val filesToDownload = mutableListOf<Pair<String, ModrinthVersion>>()
            filesToDownload.add(project.title to mainVersion)
            selectedDependencies.filter { it.selectedToInstall && it.version != null }.forEach { dep ->
                val depTitle = dep.project?.title ?: dep.version!!.name
                filesToDownload.add(depTitle to dep.version!!)
            }

            val totalCount = filesToDownload.size
            for ((index, item) in filesToDownload.withIndex()) {
                val (title, ver) = item
                val primaryFile = ver.files.firstOrNull { it.primary } ?: ver.files.firstOrNull()
                    ?: throw IllegalStateException("No download files available for $title (v${ver.versionNumber})")
                val stagedFile = java.io.File(stagingDir, primaryFile.filename)

                onProgress("Downloading $title (v${ver.versionNumber})...", index.toFloat() / totalCount.toFloat())
                val ok = modrinth.downloadContent(
                    url = primaryFile.url,
                    targetFile = stagedFile,
                    onProgress = { downloaded, total ->
                        if (total > 0) {
                            val fileFraction = downloaded.toFloat() / total.toFloat()
                            val overallProgress = (index.toFloat() + fileFraction) / totalCount.toFloat()
                            onProgress("Downloading $title (${(fileFraction * 100).toInt()}%)...", overallProgress)
                        }
                    }
                )

                if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                    throw IllegalStateException("Failed to download $title file ${primaryFile.filename}")
                }

                // 2. Validate Bytecode & Integrity
                onProgress("Validating bytecode: $title...", (index.toFloat() + 0.8f) / totalCount.toFloat())
                val javaVer = io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)
                val validation = io.ezz.launcher.core.minecraft.mod.ModBytecodeValidator.validateJarFile(stagedFile, javaVer)
                if (validation is io.ezz.launcher.core.minecraft.mod.ModCompatibilityResult.Incompatible) {
                    throw IllegalStateException("Bytecode incompatibility: ${validation.errorMessage}")
                }
            }

            // 3. Stage old version backup (Duplicate mod protection - never leave 2 versions of same mod)
            onProgress("Preparing file installation...", 0.90f)
            backupDir.mkdirs()
            val existingMods = modsDir.listFiles { _, name -> name.endsWith(".jar") || name.endsWith(".jar.disabled") } ?: emptyArray()

            for ((title, ver) in filesToDownload) {
                val primaryFile = ver.files.firstOrNull { it.primary } ?: ver.files.firstOrNull() ?: continue
                val cleanPrefix = primaryFile.filename.substringBefore('-').lowercase()
                if (cleanPrefix.isNotBlank() && !cleanPrefix.startsWith("ezz-skin-mod")) {
                    existingMods.forEach { oldJar ->
                        val oldName = oldJar.name.lowercase()
                        if ((oldName.startsWith(cleanPrefix) || oldName.contains(cleanPrefix)) && oldJar.name != primaryFile.filename) {
                            val backupTarget = java.io.File(backupDir, oldJar.name)
                            if (oldJar.renameTo(backupTarget)) {
                                backedUpOldFiles.add(oldJar to backupTarget)
                            }
                        }
                    }
                }
            }

            // 4. Atomically move staged files into mods/
            for ((_, ver) in filesToDownload) {
                val primaryFile = ver.files.firstOrNull { it.primary } ?: ver.files.firstOrNull() ?: continue
                val stagedFile = java.io.File(stagingDir, primaryFile.filename)
                val finalTarget = java.io.File(modsDir, primaryFile.filename)
                if (finalTarget.exists()) {
                    finalTarget.delete()
                }
                if (!stagedFile.renameTo(finalTarget)) {
                    // Fallback copy
                    stagedFile.copyTo(finalTarget, overwrite = true)
                    stagedFile.delete()
                }
                newlyAddedModFiles.add(finalTarget)
            }

            // 5. Post-installation Verification
            onProgress("Verifying instance mods...", 0.95f)
            refreshManageData()
            refreshMods(instance.id)

            // Clean up temporary directories
            stagingDir.deleteRecursively()
            backupDir.deleteRecursively()

            onProgress("Installed successfully", 1f)
            ToastManager.show(
                title = "Mod Installed",
                description = "${project.title} (v${mainVersion.versionNumber}) installed to ${instance.name}",
                type = ToastType.SUCCESS
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            // ROLLBACK: Undo changes on failure
            println("[ModInstaller] Installation failed, rolling back: ${e.message}")
            try {
                // Delete newly added jars
                newlyAddedModFiles.forEach { file ->
                    if (file.exists()) file.delete()
                }
                // Restore old jars from backup
                backedUpOldFiles.forEach { (originalFile, backupFile) ->
                    if (backupFile.exists()) {
                        backupFile.renameTo(originalFile)
                    }
                }
                stagingDir.deleteRecursively()
                backupDir.deleteRecursively()
                refreshMods(instance.id)
            } catch (rollbackEx: Throwable) {
                println("[ModInstaller] Rollback encountered error: ${rollbackEx.message}")
            }
            Result.failure(e)
        }
    }

    fun installModrinthProject(hit: ModrinthProjectHit) {
        openModInstaller(hit)
    }

    fun installModrinthVersion(projectTitle: String, version: ModrinthVersion) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            modrinthDownloadingProject.value = projectTitle
            modrinthDownloadProgress.value = 0f
            try {
                if (version.files.isNotEmpty()) {
                    val primaryFile = version.files.firstOrNull { it.primary } ?: version.files.first()
                    val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
                    val targetDir = java.io.File(gameDir, "mods")
                    targetDir.mkdirs()
                    val targetFile = java.io.File(targetDir, primaryFile.filename)

                    modrinth.downloadContent(
                        url = primaryFile.url,
                        targetFile = targetFile,
                        onProgress = { downloaded, total ->
                            if (total > 0) {
                                modrinthDownloadProgress.value = downloaded.toFloat() / total.toFloat()
                            }
                        }
                    )
                    refreshManageData()
                    refreshMods(instance.id)
                }
            } catch (e: Throwable) {
                println("Error installing specific version: ${e.message}")
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
            local.id.equals(hit.slug, ignoreCase = true) ||
            local.id.equals(hit.projectId, ignoreCase = true)
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

    // ==========================================
    // VAULT SKIN SYSTEM ACTIONS
    // ==========================================

    fun selectVaultSkin(skin: VaultSkin?) {
        _selectedVaultSkin.value = skin
        _selectedVaultSkinId.value = skin?.id
    }

    fun selectVaultSkinById(skinId: String?) {
        _selectedVaultSkinId.value = skinId
        _selectedVaultSkin.value = skinId?.let { vaultRepository.getSkin(it) }
    }

    fun importVaultSkin(
        bytes: ByteArray,
        preferredName: String?,
        explicitModel: SkinModelType? = null,
        onResult: (Result<VaultSkin>) -> Unit
    ) {
        scope.launch {
            val result = vaultRepository.importSkin(bytes, preferredName, explicitModel)
            if (result.isSuccess) {
                val imported = result.getOrNull()
                _selectedVaultSkin.value = imported
                _selectedVaultSkinId.value = imported?.id
                _vaultVersion.value++
                ToastManager.show(
                    title = "Skin Imported",
                    description = "'${imported?.name ?: "Skin"}' added to Vault.",
                    type = ToastType.SUCCESS
                )
            }
            onResult(result)
        }
    }

    fun importSkinFromUsername(
        username: String,
        explicitModel: SkinModelType? = null,
        onResult: (Result<VaultSkin>) -> Unit
    ) {
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Player username cannot be empty.")))
            return
        }
        scope.launch {
            try {
                val client = io.ezz.launcher.core.network.client.HttpClientFactory.create()
                val urls = listOf(
                    "https://minotar.net/skin/$trimmed",
                    "https://mc-heads.net/download/$trimmed"
                )
                var fetchedBytes: ByteArray? = null
                for (url in urls) {
                    try {
                        val response = client.get(url)
                        if (response.status.value in 200..299) {
                            val bytes: ByteArray = response.body()
                            if (bytes.size >= 500 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                                fetchedBytes = bytes
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (fetchedBytes == null || fetchedBytes.isEmpty()) {
                    val error = IllegalStateException("Could not find Minecraft skin for player '$trimmed'.")
                    ToastManager.show("Skin Lookup Failed", error.message, ToastType.ERROR)
                    onResult(Result.failure(error))
                    return@launch
                }

                val result = vaultRepository.importSkin(fetchedBytes, preferredName = trimmed, explicitModel)
                if (result.isSuccess) {
                    val imported = result.getOrNull()
                    _selectedVaultSkin.value = imported
                    _selectedVaultSkinId.value = imported?.id
                    _vaultVersion.value++
                    ToastManager.show(
                        title = "Skin Imported",
                        description = "Imported skin from player '$trimmed'.",
                        type = ToastType.SUCCESS
                    )
                }
                onResult(result)
            } catch (e: Exception) {
                ToastManager.show("Import Failed", e.message ?: "Network error", ToastType.ERROR)
                onResult(Result.failure(e))
            }
        }
    }

    fun importSkinFromUrl(
        url: String,
        preferredName: String? = null,
        explicitModel: SkinModelType? = null,
        onResult: (Result<VaultSkin>) -> Unit
    ) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("URL cannot be empty.")))
            return
        }
        scope.launch {
            try {
                val client = io.ezz.launcher.core.network.client.HttpClientFactory.create()
                val response = client.get(trimmed)
                if (response.status.value !in 200..299) {
                    val error = IllegalStateException("Failed to download skin (HTTP ${response.status.value}).")
                    ToastManager.show("Import Failed", error.message, ToastType.ERROR)
                    onResult(Result.failure(error))
                    return@launch
                }
                val bytes: ByteArray = response.body()
                if (bytes.isEmpty() || bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte()) {
                    val error = IllegalArgumentException("The URL does not point to a valid PNG image.")
                    ToastManager.show("Invalid Image", error.message, ToastType.ERROR)
                    onResult(Result.failure(error))
                    return@launch
                }
                val defaultName = preferredName?.takeIf { it.isNotBlank() } ?: "Web Skin"
                val result = vaultRepository.importSkin(bytes, preferredName = defaultName, explicitModel)
                if (result.isSuccess) {
                    val imported = result.getOrNull()
                    _selectedVaultSkin.value = imported
                    _selectedVaultSkinId.value = imported?.id
                    _vaultVersion.value++
                    ToastManager.show(
                        title = "Skin Imported",
                        description = "Imported '${imported?.name ?: defaultName}'.",
                        type = ToastType.SUCCESS
                    )
                }
                onResult(result)
            } catch (e: Exception) {
                ToastManager.show("Import Failed", e.message ?: "Download error", ToastType.ERROR)
                onResult(Result.failure(e))
            }
        }
    }

    fun exportSkinToFile(skin: VaultSkin, targetFile: java.io.File, onResult: ((Result<Unit>) -> Unit)? = null) {
        scope.launch {
            try {
                val bytes = vaultRepository.getSkinBytes(skin)
                if (bytes == null || bytes.isEmpty()) {
                    val error = IllegalStateException("Skin file bytes not found.")
                    ToastManager.show("Export Failed", error.message, ToastType.ERROR)
                    onResult?.invoke(Result.failure(error))
                    return@launch
                }
                targetFile.writeBytes(bytes)
                ToastManager.show(
                    title = "Skin Exported",
                    description = "Saved to ${targetFile.name}",
                    type = ToastType.SUCCESS
                )
                onResult?.invoke(Result.success(Unit))
            } catch (e: Exception) {
                ToastManager.show("Export Error", e.message ?: "Failed to write file", ToastType.ERROR)
                onResult?.invoke(Result.failure(e))
            }
        }
    }

    fun setActiveVaultSkin(skinId: String?, accountId: String? = null, onComplete: (() -> Unit)? = null) {
        scope.launch {
            val targetAccount = accountId?.let { id -> accountRepository.accounts.value.find { it.id == id } }
                ?: accountRepository.selectedAccount.value
            val effectiveAccountId = targetAccount?.id

            vaultRepository.setActiveSkin(skinId, effectiveAccountId)
            val skin = skinId?.let { vaultRepository.getSkin(it) }
            val skinBytes = skin?.let { vaultRepository.getSkinBytes(it) }

            if (targetAccount != null) {
                skinService.onSkinChanged(targetAccount, skinBytes)
            }

            _selectedVaultSkinId.value = skinId
            _selectedVaultSkin.value = skin
            _vaultVersion.value++

            ToastManager.show(
                title = "Skin Applied",
                description = if (skin != null) "'${skin.name}' applied to ${targetAccount?.username ?: "account"}." else "Reset to default Steve skin.",
                type = ToastType.SUCCESS
            )
            onComplete?.invoke()
        }
    }

    fun renameVaultSkin(skinId: String, newName: String, onResult: ((Result<VaultSkin>) -> Unit)? = null) {
        scope.launch {
            val result = vaultRepository.renameSkin(skinId, newName)
            if (result.isSuccess && _selectedVaultSkin.value?.id == skinId) {
                _selectedVaultSkin.value = result.getOrNull()
            }
            _vaultVersion.value++
            onResult?.invoke(result)
        }
    }

    fun updateVaultSkinModel(skinId: String, modelType: SkinModelType) {
        scope.launch {
            val result = vaultRepository.updateSkinModel(skinId, modelType)
            if (result.isSuccess && _selectedVaultSkin.value?.id == skinId) {
                _selectedVaultSkin.value = result.getOrNull()
            }
            val skin = vaultRepository.getSkin(skinId)
            val skinBytes = skin?.let { vaultRepository.getSkinBytes(it) }
            val targetAccount = accountRepository.selectedAccount.value
            if (targetAccount != null && (targetAccount.type == io.ezz.launcher.core.model.account.AccountType.OFFLINE)) {
                skinService.onSkinChanged(targetAccount, skinBytes)
            }
            _vaultVersion.value++
        }
    }

    fun deleteVaultSkin(skinId: String) {
        scope.launch {
            val isCurrentSelected = _selectedVaultSkinId.value == skinId || _selectedVaultSkin.value?.id == skinId
            vaultRepository.deleteSkin(skinId)
            if (isCurrentSelected) {
                _selectedVaultSkinId.value = null
                _selectedVaultSkin.value = vaultRepository.skins.value.firstOrNull()
            }
            val targetAccount = accountRepository.selectedAccount.value
            if (targetAccount != null && targetAccount.type == io.ezz.launcher.core.model.account.AccountType.OFFLINE) {
                val activeSkin = vaultRepository.getActiveSkin(targetAccount.id)
                val skinBytes = activeSkin?.let { vaultRepository.getSkinBytes(it) }
                skinService.onSkinChanged(targetAccount, skinBytes)
            }
            _vaultVersion.value++
            ToastManager.show(
                title = "Skin Deleted",
                description = "Skin removed from Vault.",
                type = ToastType.INFO
            )
        }
    }

    fun getVaultSkinBytes(skin: VaultSkin): ByteArray? {
        return vaultRepository.getSkinBytes(skin)
    }
}

