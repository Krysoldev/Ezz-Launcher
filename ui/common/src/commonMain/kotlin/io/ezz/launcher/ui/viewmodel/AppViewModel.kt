package io.ezz.launcher.ui.viewmodel

import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.ui.instance.content.model.ContentLoadState
import io.ezz.launcher.ui.instance.content.model.InstanceContentState
import io.ezz.launcher.ui.instance.content.service.InstanceContentHydrator
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthState
import io.ezz.launcher.core.minecraft.loader.fabric.FabricMetaClient
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
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
import io.ezz.launcher.core.model.instance.InstanceContentType
import io.ezz.launcher.core.storage.instance.InstanceContentDestinationResolver
import io.ezz.launcher.core.storage.instance.LocalInstanceManager
import io.ezz.launcher.core.network.modrinth.ModrinthService
import io.ezz.launcher.ui.image.ModrinthImageLoader
import io.ezz.launcher.ui.platform.PlatformBridge
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import io.ezz.launcher.ui.platform.WindowsModernFilePicker
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultManifest
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.repository.VaultSkinRepository
import io.ezz.launcher.core.storage.repository.LocalVaultSkinRepository
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.platform.FileSelectionMode
import io.ezz.launcher.ui.components.ToastType
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ezz.launcher.core.model.curseforge.CurseForgeBrowseState
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeSortField
import io.ezz.launcher.core.model.curseforge.CurseForgeModLoaderType
import io.ezz.launcher.core.minecraft.mods.CurseForgeDependencyResolver
import io.ezz.launcher.core.minecraft.mods.ModInstallationTransaction
import io.ezz.launcher.core.minecraft.mods.GlobalModDependencySolver
import io.ezz.launcher.core.model.instance.InstallationPlan
import io.ezz.launcher.core.model.instance.PlanActionType
import io.ezz.launcher.core.model.instance.PlanItem
import io.ezz.launcher.core.model.instance.ResolvedEnvironment

data class FileConflictState(
    val title: String,
    val message: String,
    val onConfirmReplace: () -> Unit,
    val onCancel: () -> Unit
)

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

    val destinationResolver: InstanceContentDestinationResolver =
        InstanceContentDestinationResolver(pathProvider)

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
    val javaDetectionError = MutableStateFlow<String?>(null)

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

    // Authoritative Single Source of Truth for Minecraft Launch Progress
    val launchProgressState = MutableStateFlow<io.ezz.launcher.core.model.runtime.LaunchProgressState?>(null)
    private var activeLaunchJob: kotlinx.coroutines.Job? = null
    private var activeLaunchOperationId: String? = null

    // Diagnostic Error Dialog State
    val launchErrorDialogData = MutableStateFlow<LaunchErrorData?>(null)

    // Unified Content Installation & Queue Management (V2 Rebuild)
    val contentInstallationManager: io.ezz.launcher.ui.instance.installation.service.ContentInstallationManager by lazy {
        io.ezz.launcher.ui.instance.installation.service.ContentInstallationManager(
            curseForgeService = curseForge,
            getInstanceDir = { id -> pathProvider.getInstanceDirectory(id).toFile() },
            getInstalledMods = { _ -> manageMods.value },
            onContentChanged = { id ->
                refreshManageData()
                refreshMods(id)
            },
            scope = scope
        )
    }

    val activeLocalImportRequest = MutableStateFlow<io.ezz.launcher.ui.instance.installation.model.LocalImportRequest?>(null)
    val selectedCurseForgeModForDetails = MutableStateFlow<io.ezz.launcher.core.model.curseforge.CurseForgeMod?>(null)

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

    // Centralized Content Hydration States (3-State UI Pipeline)
    val isContentHydrating = MutableStateFlow(false)
    val isModsLoading = MutableStateFlow(false)
    val isResourcePacksLoading = MutableStateFlow(false)
    val isShadersLoading = MutableStateFlow(false)
    val isWorldsLoading = MutableStateFlow(false)
    val isScreenshotsLoading = MutableStateFlow(false)
    val isStatisticsLoading = MutableStateFlow(false)

    val contentHydrator: InstanceContentHydrator by lazy {
        InstanceContentHydrator(
            instanceManager = instanceManager,
            getInstanceDir = { id -> pathProvider.getInstanceDirectory(id).toFile() },
            getInstance = { id -> instances.value.firstOrNull { it.id == id } },
            scope = scope,
            dispatcher = Dispatchers.IO,
            mainDispatcher = Dispatchers.Main,
            onContentUpdated = { state ->
                isContentHydrating.value = state.isHydrating
                isModsLoading.value = state.modsState.isLoading
                isResourcePacksLoading.value = state.resourcePacksState.isLoading
                isShadersLoading.value = state.shadersState.isLoading
                isWorldsLoading.value = state.worldsState.isLoading
                isScreenshotsLoading.value = state.screenshotsState.isLoading
                isLogLoading.value = state.logsState.isLoading
                isStatisticsLoading.value = state.statisticsState.isLoading

                if (state.modsState is ContentLoadState.Success) {
                    manageMods.value = state.modsState.data
                    _installedMods.value = state.modsState.data.map { mod ->
                        io.ezz.launcher.core.model.instance.ModMetadata(
                            id = mod.id,
                            instanceId = state.instanceId,
                            name = mod.name,
                            version = mod.version,
                            fileName = mod.fileName,
                            fileHash = null,
                            loader = mod.loader,
                            description = mod.description,
                            authors = mod.author?.let { listOf(it) } ?: emptyList(),
                            fileSize = mod.fileSize,
                            enabled = mod.enabled,
                            dependencies = mod.dependencies,
                            breaks = mod.breaks,
                            conflicts = mod.conflicts
                        )
                    }
                }
                if (state.resourcePacksState is ContentLoadState.Success) {
                    manageResourcePacks.value = state.resourcePacksState.data
                }
                if (state.shadersState is ContentLoadState.Success) {
                    manageShaders.value = state.shadersState.data
                }
                if (state.worldsState is ContentLoadState.Success) {
                    manageWorlds.value = state.worldsState.data
                }
                if (state.screenshotsState is ContentLoadState.Success) {
                    manageScreenshots.value = state.screenshotsState.data
                }
                if (state.logsState is ContentLoadState.Success) {
                    manageLogs.value = state.logsState.data
                    val logsList = state.logsState.data
                    val currentSel = selectedLogFile.value
                    if (currentSel == null || logsList.none { it.filePath == currentSel.filePath }) {
                        val latest = logsList.firstOrNull { it.fileName == "latest.log" } ?: logsList.firstOrNull()
                        loadLogContent(latest)
                    }
                }
                if (state.statisticsState is ContentLoadState.Success) {
                    manageStatistics.value = state.statisticsState.data
                }
                missingDependencies.value = state.missingDependencies
                compatibilityConflicts.value = state.compatibilityConflicts
            }
        )
    }

    // Modrinth Image Caching Engine
    val imageLoader: ModrinthImageLoader = ModrinthImageLoader(pathProvider, modrinth, scope)

    // Isolated Modrinth Browse States per Content Type (Prevents State Leaks)
    val modsBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.MOD))
    val resourcePacksBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.RESOURCE_PACK))
    val shadersBrowseState = MutableStateFlow(ModrinthBrowseState(contentType = ModrinthContentType.SHADER))

    // CurseForge Mod Browsing & Installation (CurseForge-only mod source)
    val curseForgeModsBrowseState = MutableStateFlow(CurseForgeBrowseState())
    val curseForgeDownloadingMod = MutableStateFlow<String?>(null)
    val curseForgeDownloadProgress = MutableStateFlow(0f)
    private var searchCurseForgeModsJob: Job? = null
    private var curseForgeSearchSeq = 0L

    // Safe File Conflict Prompt State
    val fileConflictState = MutableStateFlow<FileConflictState?>(null)

    // Modrinth Global Actions
    val modrinthDownloadingProject = MutableStateFlow<String?>(null)
    val modrinthDownloadProgress = MutableStateFlow(0f)
    val modUpdateCandidates = MutableStateFlow<List<ModUpdateCandidate>>(emptyList())
    val isCheckingModUpdates = MutableStateFlow(false)

    private var searchModsJob: Job? = null
    private var modsSearchSeq = 0L
    private var searchResourcePacksJob: Job? = null
    private var resourcePacksSearchSeq = 0L
    private var searchShadersJob: Job? = null
    private var shadersSearchSeq = 0L
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
                    _selectedInstance.value?.let { contentHydrator.hydrateInstance(it.id, forceRefresh = false) }
                }
            } catch (e: Throwable) {
                println("Error collecting instances: ${e.message}")
            }
        }

        // Active filesystem watcher for external modifications while in Instance Manager
        scope.launch(Dispatchers.IO) {
            var lastModsMod = 0L
            var lastRpMod = 0L
            var lastSpMod = 0L
            var lastSavesMod = 0L
            var lastSsMod = 0L
            var trackedInstanceId: String? = null

            while (isActive) {
                delay(2000L)
                val currentScreen = _currentScreen.value
                val currentInst = _selectedInstance.value

                if (currentScreen == NavigationScreen.INSTANCE_MANAGER && currentInst != null) {
                    val instId = currentInst.id
                    if (trackedInstanceId != instId) {
                        trackedInstanceId = instId
                        lastModsMod = 0L
                        lastRpMod = 0L
                        lastSpMod = 0L
                        lastSavesMod = 0L
                        lastSsMod = 0L
                    }

                    try {
                        val baseDir = pathProvider.getInstanceDirectory(instId).resolve(".minecraft").toFile()
                        val modsDir = java.io.File(baseDir, "mods")
                        val rpDir = java.io.File(baseDir, "resourcepacks")
                        val spDir = java.io.File(baseDir, "shaderpacks")
                        val savesDir = java.io.File(baseDir, "saves")
                        val ssDir = java.io.File(baseDir, "screenshots")

                        val curModsMod = if (modsDir.exists()) modsDir.lastModified() else 0L
                        val curRpMod = if (rpDir.exists()) rpDir.lastModified() else 0L
                        val curSpMod = if (spDir.exists()) spDir.lastModified() else 0L
                        val curSavesMod = if (savesDir.exists()) savesDir.lastModified() else 0L
                        val curSsMod = if (ssDir.exists()) ssDir.lastModified() else 0L

                        var hasChanged = false
                        if (lastModsMod != 0L && curModsMod != lastModsMod) hasChanged = true
                        if (lastRpMod != 0L && curRpMod != lastRpMod) hasChanged = true
                        if (lastSpMod != 0L && curSpMod != lastSpMod) hasChanged = true
                        if (lastSavesMod != 0L && curSavesMod != lastSavesMod) hasChanged = true
                        if (lastSsMod != 0L && curSsMod != lastSsMod) hasChanged = true

                        lastModsMod = curModsMod
                        lastRpMod = curRpMod
                        lastSpMod = curSpMod
                        lastSavesMod = curSavesMod
                        lastSsMod = curSsMod

                        if (hasChanged) {
                            println("[AppViewModel] External filesystem change detected for $instId. Hydrating content...")
                            contentHydrator.hydrateInstance(instId, forceRefresh = true)
                        }
                    } catch (_: Throwable) {
                    }
                }
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
                val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                println("[DiscordRPC] settings loaded: enableDiscordRpc = $rpcEnabled")
                val currentAccount = accountRepository.selectedAccount.value
                discordRpcService?.initialize(account = currentAccount, enabled = rpcEnabled)
            } catch (e: Throwable) {
                println("[DiscordRPC] connection failure: Startup initialization failed: ${e.message}")
            }
        }

        scope.launch {
            try {
                settingsRepository.settings.collect { s ->
                    io.ezz.launcher.ui.audio.EzzAudioService.updateSettings(s.soundEffectsEnabled, s.soundVolume)
                }
            } catch (e: Throwable) {
                println("Error syncing audio settings: ${e.message}")
            }
        }

        scope.launch {
            try {
                accountRepository.selectedAccount.collect { selAcc ->
                    // Reset ephemeral preview selection when switching accounts
                    _selectedVaultSkin.value = null
                    val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                    discordRpcService?.setLauncherPresence(selAcc, enabled = rpcEnabled)

                    if (selAcc == null || selAcc !is MicrosoftAccount || selAcc.type != AccountType.MICROSOFT) {
                        _adminStatus.value = AdminStatus.NormalUser(
                            minecraftUsername = selAcc?.username ?: "",
                            minecraftUuid = selAcc?.uuid ?: "",
                            microsoftConnected = false
                        )
                        if (selAcc != null) {
                            skinService.loadOrRefreshSkin(selAcc)
                        }
                    } else {
                        skinService.loadOrRefreshSkin(selAcc)
                        refreshAdminStatus(selAcc)
                    }
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
            fileConflictState.value = null
            modsBrowseState.value = ModrinthBrowseState(
                contentType = ModrinthContentType.MOD,
                selectedGameVersion = instance.minecraftVersion,
                selectedLoader = if (instance.loaderType != LoaderType.VANILLA) instance.loaderType.name.lowercase() else null
            )
            curseForgeModsBrowseState.value = CurseForgeBrowseState(
                selectedGameVersion = instance.minecraftVersion,
                selectedLoader = CurseForgeModLoaderType.fromLoaderName(instance.loaderType.name)
            )
            resourcePacksBrowseState.value = ModrinthBrowseState(
                selectedGameVersion = instance.minecraftVersion
            )
            shadersBrowseState.value = ModrinthBrowseState(
                selectedGameVersion = instance.minecraftVersion
            )
        }
        _selectedInstance.value = instance
        val session = _runningSessions.value[instance.id]
        if (session != null) {
            _processState.value = ProcessState.Running(session.processId, session.startedAt)
        } else if (_processState.value is ProcessState.Running) {
            _processState.value = ProcessState.Idle
        }
        contentHydrator.hydrateInstance(instance.id, forceRefresh = false)
    }

    fun refreshMods(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, InstanceContentType.MOD)
    }

    fun refreshResourcePacks(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, InstanceContentType.RESOURCE_PACK)
    }

    fun refreshShaders(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, InstanceContentType.SHADER)
    }

    fun refreshWorlds(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, InstanceContentType.WORLD)
    }

    fun refreshScreenshots(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, InstanceContentType.SCREENSHOT)
    }

    fun refreshContent(instanceId: String? = _selectedInstance.value?.id, contentType: InstanceContentType) {
        val targetId = instanceId ?: _selectedInstance.value?.id ?: return
        contentHydrator.hydrateCategory(targetId, contentType)
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
        val path = destinationResolver.resolveContentPath(targetId, InstanceContentType.MOD)
        platformBridge.openFolder(path)
    }

    fun openResourcePacksFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = destinationResolver.resolveContentPath(targetId, InstanceContentType.RESOURCE_PACK)
        platformBridge.openFolder(path)
    }

    fun openShadersFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = destinationResolver.resolveContentPath(targetId, InstanceContentType.SHADER)
        platformBridge.openFolder(path)
    }

    fun openSavesFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = destinationResolver.resolveContentPath(targetId, InstanceContentType.WORLD)
        platformBridge.openFolder(path)
    }

    fun openScreenshotsFolder(instanceId: String? = _selectedInstance.value?.id) {
        val targetId = instanceId ?: return
        val path = destinationResolver.resolveContentPath(targetId, InstanceContentType.SCREENSHOT)
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
        // Immediately reset admin status so stale state never leaks during account transition
        _adminStatus.value = AdminStatus.NormalUser(
            minecraftUsername = account.username,
            minecraftUuid = account.uuid,
            microsoftConnected = account is MicrosoftAccount && account.type == AccountType.MICROSOFT
        )
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

        val inspected = JavaRuntimeDetector.inspectJavaHome(file.absolutePath)
        if (inspected == null) {
            return JavaValidationResult.NotJavaExecutable
        }
        return JavaValidationResult.Valid
    }

    fun inspectCustomJava(path: String): JavaRuntime? {
        if (path.isBlank()) return null
        return JavaRuntimeDetector.inspectJavaHome(path.trim())
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
                discordRpcService?.setEnabled(enabled)
            } catch (e: Exception) {
                println("Failed to update Discord RPC setting: ${e.message}")
            }
        }
    }

    fun updateSoundSettings(enabled: Boolean, volume: Float) {
        scope.launch {
            try {
                settingsRepository.updateSettings { it.copy(soundEffectsEnabled = enabled, soundVolume = volume) }
                io.ezz.launcher.ui.audio.EzzAudioService.updateSettings(enabled, volume)
            } catch (e: Exception) {
                println("Failed to update sound settings: ${e.message}")
            }
        }
    }

    fun refreshAdminStatus(account: Account? = accountRepository.selectedAccount.value) {
        scope.launch {
            val targetAccount = account ?: accountRepository.selectedAccount.value
            if (targetAccount == null || targetAccount !is MicrosoftAccount || targetAccount.type != AccountType.MICROSOFT) {
                _adminStatus.value = AdminStatus.NormalUser(
                    minecraftUsername = targetAccount?.username ?: "",
                    minecraftUuid = targetAccount?.uuid ?: "",
                    microsoftConnected = false
                )
                return@launch
            }

            _isCheckingAdmin.value = true
            try {
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
                        microsoftConnected = true
                    )
                }
            } catch (e: Throwable) {
                println("Admin verification notice: ${e.message}")
                _adminStatus.value = AdminStatus.NormalUser(
                    minecraftUsername = targetAccount.username,
                    minecraftUuid = targetAccount.uuid,
                    microsoftConnected = true
                )
            } finally {
                _isCheckingAdmin.value = false
            }
        }
    }

    fun checkGitHubStatus() {
        gitHubReleaseService?.checkExistingToken()
        println("[AUTH_DIAGNOSTIC] 8. GitHub authorization state: ${githubConnectionStatus.value}")
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
            if (currentAccount == null || currentAccount !is MicrosoftAccount || currentAccount.type != AccountType.MICROSOFT || adminStatus.value !is AdminStatus.VerifiedAdmin) {
                _releasePublishStep.value = ReleasePublishStep.Failed("Unauthorized: verified Microsoft admin account required.")
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
            javaDetectionError.value = null
            try {
                val detected = JavaRuntimeDetector.detectInstalledRuntimes()
                _detectedJavaRuntimes.value = detected
            } catch (e: Exception) {
                javaDetectionError.value = e.message ?: "Could not detect Java runtimes."
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
        val playClickTime = System.currentTimeMillis()
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

        activeLaunchJob?.cancel()
        val operationId = java.util.UUID.randomUUID().toString()
        activeLaunchOperationId = operationId

        val initialProgressState = io.ezz.launcher.core.model.runtime.LaunchProgressState(
            operationId = operationId,
            instanceId = targetInstance.id,
            stage = "PREPARING LAUNCH",
            status = "Preparing launch environment...",
            progress = 0.01f,
            percentage = 1,
            startedAt = System.currentTimeMillis()
        )
        launchProgressState.value = initialProgressState
        activeDownloadState.value = ActiveDownloadState(
            stage = "PREPARING LAUNCH",
            currentFile = "Preparing launch environment...",
            progress = 0.01f
        )

        activeLaunchJob = scope.launch {
            val launchAccount = try {
                withContext(Dispatchers.IO) {
                    authManager.getValidSession(rawAccount)
                }
            } catch (e: Exception) {
                _logs.value = listOf(ConsoleLogEntry(message = "[Auth Notice] ${e.message}", isError = false))
                rawAccount
            }

            _logs.value = listOf(ConsoleLogEntry(message = "=== Launching ${targetInstance.name} (${targetInstance.minecraftVersion}) as ${launchAccount.username} ==="))

            try {
                launchEngine.launch(targetInstance, launchAccount, operationId, playClickTime).collect { event ->
                    if (activeLaunchOperationId != operationId) return@collect

                    when (event) {
                        is LaunchEvent.ProgressUpdate -> {
                            if (event.operationId != activeLaunchOperationId) return@collect
                            val current = launchProgressState.value
                            val newProgress = event.progress.coerceIn(0f, 1f)
                            val newDisplayProgress = event.displayProgress.coerceIn(0f, 1f)
                            // Monotonic progress protection: progress cannot move backward within the same launch attempt
                            val safeProgress = if (current != null && current.operationId == event.operationId) {
                                maxOf(current.progress, newProgress)
                            } else {
                                newProgress
                            }
                            val safeDisplayProgress = if (current != null && current.operationId == event.operationId) {
                                maxOf(current.displayProgress, newDisplayProgress)
                            } else {
                                newDisplayProgress
                            }
                            val percentage = io.ezz.launcher.core.model.runtime.computeDisplayPercentage(
                                displayProgress = safeDisplayProgress,
                                isFinished = safeProgress >= 1.0f && safeDisplayProgress >= 1.0f
                            )

                            // High-frequency coalescing: only skip if percentage, displayProgress, status, stage, and isIndeterminate are identical
                            if (current != null &&
                                current.percentage == percentage &&
                                kotlin.math.abs(current.displayProgress - safeDisplayProgress) < 0.001f &&
                                current.status == event.status &&
                                current.stage == event.stage &&
                                current.isIndeterminate == event.isIndeterminate
                            ) {
                                return@collect
                            }

                            val updatedState = io.ezz.launcher.core.model.runtime.LaunchProgressState(
                                operationId = event.operationId,
                                instanceId = targetInstance.id,
                                stage = event.stage,
                                status = event.status,
                                progress = safeProgress,
                                displayProgress = safeDisplayProgress,
                                percentage = percentage,
                                completedWork = event.completedWork,
                                totalWork = event.totalWork,
                                isIndeterminate = event.isIndeterminate,
                                startedAt = current?.startedAt ?: System.currentTimeMillis()
                            )
                            launchProgressState.value = updatedState

                            activeDownloadState.value = ActiveDownloadState(
                                stage = event.stage,
                                currentFile = event.status,
                                progress = safeProgress,
                                downloadedBytes = event.completedWork,
                                totalBytes = event.totalWork,
                                speedText = if (event.totalWork > 0) "${(event.completedWork / 1024 / 1024)} MB / ${(event.totalWork / 1024 / 1024)} MB" else ""
                            )
                        }
                        is LaunchEvent.DownloadProgressUpdate -> {
                            // legacy support
                        }
                        is LaunchEvent.StateChanged -> {
                            val state = event.state
                            _processState.value = state
                            when (state) {
                                is ProcessState.Preparing -> {
                                    val current = launchProgressState.value
                                    val safeProgress = state.progress?.coerceIn(0f, 1f) ?: (current?.progress ?: 0.05f)
                                    val safeDisplay = state.progress?.coerceIn(0f, 1f) ?: (current?.displayProgress ?: 0.05f)
                                    val percentage = io.ezz.launcher.core.model.runtime.computeDisplayPercentage(
                                        displayProgress = safeDisplay,
                                        isFinished = safeProgress >= 1.0f && safeDisplay >= 1.0f
                                    )
                                    launchProgressState.value = (current ?: initialProgressState).copy(
                                        stage = "PREPARING",
                                        status = state.stage,
                                        progress = safeProgress,
                                        displayProgress = safeDisplay,
                                        percentage = percentage
                                    )
                                    activeDownloadState.value = ActiveDownloadState(
                                        stage = "PREPARING",
                                        currentFile = state.stage,
                                        progress = safeProgress
                                    )
                                }
                                is ProcessState.Running -> {
                                    val current = launchProgressState.value
                                    launchProgressState.value = (current ?: initialProgressState).copy(
                                        stage = "MINECRAFT STARTED",
                                        status = "Minecraft running (PID: ${state.processId})",
                                        progress = 1.0f,
                                        displayProgress = 1.0f,
                                        percentage = 100,
                                        isIndeterminate = false
                                    )
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

                                    // Asynchronously update Discord RPC without blocking or delaying launch sequence
                                    val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                                    val currentAccount = accountRepository.selectedAccount.value
                                    val rpcUsername = currentAccount?.username ?: "Player"
                                    scope.launch(Dispatchers.IO) {
                                        discordRpcService?.setMinecraftPresence(
                                            playerUsername = rpcUsername,
                                            minecraftVersion = targetInstance.minecraftVersion,
                                            instanceName = targetInstance.name,
                                            playerUuid = currentAccount?.uuid,
                                            startedAtMs = startedAt,
                                            processId = state.processId ?: 0L,
                                            enabled = rpcEnabled
                                        )
                                    }

                                    // Keep 100% visible for 1.8 seconds for smooth display progress arrival & victory celebration
                                    scope.launch {
                                        kotlinx.coroutines.delay(1800)
                                        if (activeLaunchOperationId == operationId) {
                                            launchProgressState.value = null
                                        }
                                    }
                                }
                                is ProcessState.Exited -> {
                                    launchProgressState.value = null
                                    activeDownloadState.value = null
                                    _runningSessions.value = _runningSessions.value - targetInstance.id
                                    scope.launch(Dispatchers.IO) {
                                        sessionTracker.unregisterSession(targetInstance.id)
                                    }
                                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Process exited with code ${state.exitCode} ===")
                                    val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                                    val currentAccount = accountRepository.selectedAccount.value
                                    discordRpcService?.onMinecraftExited()
                                    discordRpcService?.setLauncherPresence(currentAccount, enabled = rpcEnabled)
                                }
                                is ProcessState.Failed -> {
                                    val current = launchProgressState.value
                                    launchProgressState.value = current?.copy(
                                        stage = "LAUNCH FAILED",
                                        status = state.error.message,
                                        error = state.error.message,
                                        isIndeterminate = false
                                    )
                                    activeDownloadState.value = null
                                    _runningSessions.value = _runningSessions.value - targetInstance.id
                                    scope.launch(Dispatchers.IO) {
                                        sessionTracker.unregisterSession(targetInstance.id)
                                    }
                                    val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                                    val currentAccount = accountRepository.selectedAccount.value
                                    discordRpcService?.onMinecraftExited()
                                    discordRpcService?.setLauncherPresence(currentAccount, enabled = rpcEnabled)
                                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Launch Failed: ${state.error.message} ===", isError = true)
                                    launchErrorDialogData.value = LaunchErrorData(
                                        instanceName = targetInstance.name,
                                        minecraftVersion = targetInstance.minecraftVersion,
                                        javaVersion = targetInstance.javaPath ?: "System Default Runtime",
                                        errorSummary = state.error.message,
                                        details = (state.error as? io.ezz.launcher.core.model.runtime.LaunchError.ExecutionFailed)?.cause?.stackTraceToString()
                                    )
                                    scope.launch {
                                        kotlinx.coroutines.delay(1800)
                                        if (activeLaunchOperationId == operationId) {
                                            launchProgressState.value = null
                                        }
                                    }
                                }
                                else -> {
                                    // Idle
                                }
                            }
                        }
                        is LaunchEvent.LogReceived -> {
                            appendConsoleLog(event.line, isError = event.isError)
                        }
                    }
                }
            } catch (e: Exception) {
                val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
                val currentAccount = accountRepository.selectedAccount.value
                discordRpcService?.onMinecraftExited()
                discordRpcService?.setLauncherPresence(currentAccount, enabled = rpcEnabled)
                if (e is kotlinx.coroutines.CancellationException) {
                    _logs.value = _logs.value + ConsoleLogEntry(message = "=== Launch cancelled by user ===")
                } else {
                    _processState.value = ProcessState.Failed(io.ezz.launcher.core.model.runtime.LaunchError.ExecutionFailed(e.message ?: "Launch Failed", e))
                    launchProgressState.value = null
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
    }

    fun cancelLaunch() {
        activeLaunchJob?.cancel()
        activeLaunchJob = null
        activeLaunchOperationId = null
        launchProgressState.value = null
        activeDownloadState.value = null
        _processState.value = ProcessState.Idle
        _logs.value = _logs.value + ConsoleLogEntry(message = "=== Launch cancelled by user ===")
        val rpcEnabled = settingsRepository.settings.value.enableDiscordRpc
        val currentAccount = accountRepository.selectedAccount.value
        discordRpcService?.onMinecraftExited()
        discordRpcService?.setLauncherPresence(currentAccount, enabled = rpcEnabled)
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

    fun toggleFavoriteInstance(instance: Instance) {
        val updated = instance.copy(isFavorite = !instance.isFavorite)
        updateInstance(updated)
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

    private var lastUsedPickerDirectory: java.io.File? = null

    fun openFilePicker(
        title: String,
        description: String = "",
        allowedExtensions: Set<String> = emptySet(),
        initialDirectory: java.io.File? = null,
        selectionMode: FileSelectionMode = FileSelectionMode.FILES_ONLY,
        isMultiSelect: Boolean = false,
        isSaveMode: Boolean = false,
        defaultSaveName: String? = null,
        onFileSelected: (java.io.File?) -> Unit
    ) {
        val resolvedInitial = initialDirectory ?: lastUsedPickerDirectory ?: run {
            val userHome = System.getProperty("user.home", ".")
            if (title.contains("Modpack", ignoreCase = true)) {
                val downloads = java.io.File(userHome, "Downloads")
                if (downloads.exists() && downloads.isDirectory) downloads else java.io.File(userHome)
            } else {
                java.io.File(userHome)
            }
        }

        scope.launch(Dispatchers.IO) {
            val filterSpecs = WindowsModernFilePicker.extensionsToFilterSpecs(title, allowedExtensions)
            val selected = if (isSaveMode) {
                val cleanExt = allowedExtensions.firstOrNull()?.removePrefix(".")
                WindowsModernFilePicker.saveFileDialog(
                    title = title,
                    filterSpecs = filterSpecs,
                    initialDir = resolvedInitial,
                    defaultName = defaultSaveName,
                    defaultExtension = cleanExt
                )
            } else {
                val isFolder = selectionMode == FileSelectionMode.DIRECTORIES_ONLY
                val cleanExt = allowedExtensions.firstOrNull()?.removePrefix(".")
                WindowsModernFilePicker.openFileDialog(
                    title = title,
                    filterSpecs = filterSpecs,
                    initialDir = resolvedInitial,
                    defaultExtension = cleanExt,
                    isFolderPicker = isFolder
                )
            }

            if (selected != null) {
                lastUsedPickerDirectory = if (selected.isDirectory) selected else selected.parentFile
            }

            withContext(Dispatchers.Main) {
                onFileSelected(selected)
            }
        }
    }

    fun closeFilePicker() {
        // No-op with modern native picker
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
                        title = "Instance Imported",
                        description = "'${imported?.name ?: "Instance"}' imported successfully.",
                        type = ToastType.SUCCESS
                    )
                } else {
                    val rawError = result.exceptionOrNull()?.message ?: "Could not import modpack."
                    val cleanError = if (rawError.contains("manifest", ignoreCase = true) || rawError.contains("zip", ignoreCase = true) || rawError.contains("corrupt", ignoreCase = true)) {
                        "Invalid Modrinth modpack."
                    } else {
                        rawError
                    }
                    ToastManager.show("Could not import modpack.", cleanError, ToastType.ERROR)
                }
                onComplete?.invoke(result)
            } catch (e: Exception) {
                if (session != importSessionId) return@launch
                isImportingMrpack.value = false
                mrpackImportProgress.value = null
                ToastManager.show("Could not import modpack.", "Invalid Modrinth modpack.", ToastType.ERROR)
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

    val previousScreenBeforeManager = MutableStateFlow<NavigationScreen>(NavigationScreen.INSTANCES)

    fun openInstanceManager(instance: Instance, initialTab: InstanceManagerTab = InstanceManagerTab.OVERVIEW) {
        selectInstance(instance)
        activeManageTab.value = initialTab
        if (_currentScreen.value != NavigationScreen.INSTANCE_MANAGER) {
            previousScreenBeforeManager.value = _currentScreen.value
        }
        _currentScreen.value = NavigationScreen.INSTANCE_MANAGER
        refreshManageData()
    }

    fun navigateBackFromManager() {
        _currentScreen.value = previousScreenBeforeManager.value
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

    fun refreshManageData(forceRefresh: Boolean = true) {
        val instance = _selectedInstance.value ?: return
        contentHydrator.hydrateInstance(instance.id, forceRefresh = forceRefresh)
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
            refreshResourcePacks(instance.id)
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
            refreshResourcePacks(instance.id)
        }
    }

    fun deleteManageResourcePack(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteResourcePack(instance.id, fileName)
            manageResourcePacks.value = instanceManager.getResourcePacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshResourcePacks(instance.id)
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
            refreshResourcePacks(instance.id)
        }
    }

    // SHADERS
    fun toggleManageShader(fileName: String, enable: Boolean) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.toggleShaderPack(instance.id, fileName, enable)
            manageShaders.value = instanceManager.getShaderPacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshShaders(instance.id)
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
            refreshShaders(instance.id)
        }
    }

    fun deleteManageShader(fileName: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            instanceManager.deleteShaderPack(instance.id, fileName)
            manageShaders.value = instanceManager.getShaderPacks(instance.id)
            manageStatistics.value = instanceManager.getInstanceStatistics(instance.id)
            refreshShaders(instance.id)
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
            refreshShaders(instance.id)
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

        val reqId = ++modsSearchSeq
        searchModsJob?.cancel()
        searchModsJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (reqId != modsSearchSeq) return@launch
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
                if (reqId != modsSearchSeq) return@launch

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
                if (reqId != modsSearchSeq) return@launch
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

    // ==========================================================
    // CURSEFORGE MOD BROWSING & INSTALLATION
    // ==========================================================

    fun searchCurseForgeMods(
        query: String? = null,
        page: Int? = null,
        sort: CurseForgeSortField? = null,
        debounceMs: Long = 0L
    ) {
        val instance = _selectedInstance.value ?: return
        val current = curseForgeModsBrowseState.value
        val rawQuery = query ?: current.searchQuery
        val trimmedQuery = rawQuery.trim()
        val newPage = page ?: (if (query != null || sort != null) 1 else current.page)
        val newSort = sort ?: current.selectedSort

        val mcVersion = instance.minecraftVersion
        val loaderType = CurseForgeModLoaderType.fromLoaderName(instance.loaderType.name)

        curseForgeModsBrowseState.value = current.copy(
            searchQuery = rawQuery,
            page = newPage,
            selectedGameVersion = mcVersion,
            selectedLoader = loaderType,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        val reqId = ++curseForgeSearchSeq
        searchCurseForgeModsJob?.cancel()
        searchCurseForgeModsJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (reqId != curseForgeSearchSeq) return@launch
            try {
                val offset = (newPage - 1) * current.pageSize
                val res = curseForge.searchMods(
                    query = trimmedQuery,
                    gameVersion = mcVersion,
                    modLoaderType = loaderType,
                    sortField = newSort,
                    index = offset,
                    pageSize = current.pageSize
                )
                if (reqId != curseForgeSearchSeq) return@launch

                val totalCount = res.pagination?.totalCount ?: res.data.size.toLong()
                val totalPages = maxOf(1, kotlin.math.ceil(totalCount.toDouble() / current.pageSize).toInt())

                curseForgeModsBrowseState.value = curseForgeModsBrowseState.value.copy(
                    items = res.data,
                    totalHits = totalCount,
                    totalPages = totalPages,
                    isLoading = false,
                    error = null
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (reqId != curseForgeSearchSeq) return@launch
                curseForgeModsBrowseState.value = curseForgeModsBrowseState.value.copy(
                    isLoading = false,
                    error = "Couldn't search mods."
                )
            }
        }
    }

    fun setCurseForgeModsPage(page: Int) {
        if (page < 1 || page > curseForgeModsBrowseState.value.totalPages) return
        searchCurseForgeMods(page = page)
    }

    fun isCurseForgeModInstalled(mod: CurseForgeMod): Boolean {
        val querySlug = mod.slug.lowercase()
        val queryName = mod.name.lowercase()
        val queryId = mod.id.toString()
        return manageMods.value.any { local ->
            val fileName = local.fileName.lowercase()
            val name = local.name.lowercase()
            val id = local.id.lowercase()
            fileName.contains(querySlug) ||
            name.equals(queryName, ignoreCase = true) ||
            id.equals(querySlug, ignoreCase = true) ||
            id == queryId
        }
    }

    fun installCurseForgeMod(mod: CurseForgeMod, targetInstance: Instance? = null) {
        val instance = targetInstance ?: _selectedInstance.value ?: return
        scope.launch(Dispatchers.IO) {
            try {
                curseForgeDownloadingMod.value = mod.name
                curseForgeDownloadProgress.value = 0.05f

                val loaderName = instance.loaderType.name
                val loaderType = CurseForgeModLoaderType.fromLoaderName(loaderName)
                val mcVer = instance.minecraftVersion

                // Fetch files for this mod
                val files = curseForge.getModFiles(
                    modId = mod.id,
                    gameVersion = mcVer,
                    modLoaderType = loaderType,
                    pageSize = 30
                )

                val resolution = CurseForgeDependencyResolver.resolveCompatibility(
                    minecraftVersion = mcVer,
                    loader = loaderName,
                    installedMods = manageMods.value,
                    mod = mod,
                    candidateFiles = files
                )

                val chosenFile = resolution.recommendedFile ?: resolution.latestFile
                if (chosenFile == null) {
                    withContext(Dispatchers.Main) {
                        ToastManager.show(
                            "Incompatible Mod",
                            "No compatible version found for Minecraft $mcVer ($loaderName).",
                            ToastType.WARNING
                        )
                        curseForgeDownloadingMod.value = null
                        curseForgeDownloadProgress.value = 0f
                    }
                    return@launch
                }

                curseForgeDownloadProgress.value = 0.15f
                val resolvedDeps = CurseForgeDependencyResolver.resolveDependencies(
                    curseForgeService = curseForge,
                    file = chosenFile,
                    targetMc = mcVer,
                    targetLoader = loaderName,
                    installedMods = manageMods.value
                )

                val modsDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("mods").toFile()
                modsDir.mkdirs()

                val downloadUrl = chosenFile.downloadUrl ?: curseForge.getModFileDownloadUrl(mod.id, chosenFile.id)
                if (downloadUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        ToastManager.show("Download Unavailable", "Direct download URL is blocked or unavailable for this mod.", ToastType.ERROR)
                        curseForgeDownloadingMod.value = null
                        curseForgeDownloadProgress.value = 0f
                    }
                    return@launch
                }

                val mainDest = java.io.File(modsDir, chosenFile.fileName)
                val success = curseForge.downloadContent(downloadUrl, mainDest) { downloaded, total ->
                    if (total > 0) {
                        curseForgeDownloadProgress.value = 0.2f + (downloaded.toFloat() / total) * 0.6f
                    }
                }

                if (!success) {
                    withContext(Dispatchers.Main) {
                        ToastManager.show("Download Failed", "Could not download ${chosenFile.fileName}", ToastType.ERROR)
                        curseForgeDownloadingMod.value = null
                        curseForgeDownloadProgress.value = 0f
                    }
                    return@launch
                }

                // Download required dependencies
                val depsToDownload = resolvedDeps.filter { it.selectedToInstall && it.candidateFile != null && !it.isAlreadyInstalled }
                for (dep in depsToDownload) {
                    val depFile = dep.candidateFile!!
                    val depUrl = depFile.downloadUrl ?: curseForge.getModFileDownloadUrl(dep.depModId, depFile.id)
                    if (!depUrl.isNullOrBlank()) {
                        val depDest = java.io.File(modsDir, depFile.fileName)
                        curseForge.downloadContent(depUrl, depDest) { _, _ -> }
                    }
                }

                curseForgeDownloadProgress.value = 1.0f
                delay(250)

                withContext(Dispatchers.Main) {
                    curseForgeDownloadingMod.value = null
                    curseForgeDownloadProgress.value = 0f
                    refreshManageData()
                    ToastManager.show("Mod Installed", "Mod '${mod.name}' installed successfully.", ToastType.SUCCESS)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    curseForgeDownloadingMod.value = null
                    curseForgeDownloadProgress.value = 0f
                    ToastManager.show("Install Error", e.message ?: "Failed to install mod", ToastType.ERROR)
                }
            }
        }
    }

    // ==========================================================
    // CONTEXT-SPECIFIC LOCAL IMPORTS (V2 VALIDATED DIALOG)
    // ==========================================================

    fun installCurseForgeModV2(instance: Instance, mod: CurseForgeMod, chosenFile: CurseForgeFile? = null) {
        contentInstallationManager.startCurseForgeModInstall(instance, mod, chosenFile)
    }

    fun importLocalMod(instance: Instance) {
        openFilePicker(
            title = "Import Mod",
            description = "Select a .jar mod file",
            allowedExtensions = setOf("jar"),
            onFileSelected = { file ->
                if (file == null) return@openFilePicker
                val modsDir = destinationResolver.resolveContentDirectory(instance.id, InstanceContentType.MOD)
                activeLocalImportRequest.value = io.ezz.launcher.ui.instance.installation.model.LocalImportRequest(
                    file = file,
                    contentType = io.ezz.launcher.ui.instance.installation.model.ContentType.MOD,
                    instance = instance,
                    targetDirectory = modsDir
                )
            }
        )
    }

    fun importLocalResourcePack(instance: Instance) {
        openFilePicker(
            title = "Import Resource Pack",
            description = "Select a .zip resource pack",
            allowedExtensions = setOf("zip"),
            onFileSelected = { file ->
                if (file == null) return@openFilePicker
                val packsDir = destinationResolver.resolveContentDirectory(instance.id, InstanceContentType.RESOURCE_PACK)
                activeLocalImportRequest.value = io.ezz.launcher.ui.instance.installation.model.LocalImportRequest(
                    file = file,
                    contentType = io.ezz.launcher.ui.instance.installation.model.ContentType.RESOURCE_PACK,
                    instance = instance,
                    targetDirectory = packsDir
                )
            }
        )
    }

    fun importLocalShader(instance: Instance) {
        openFilePicker(
            title = "Import Shader",
            description = "Select a .zip shader pack",
            allowedExtensions = setOf("zip"),
            onFileSelected = { file ->
                if (file == null) return@openFilePicker
                val shadersDir = destinationResolver.resolveContentDirectory(instance.id, InstanceContentType.SHADER)
                activeLocalImportRequest.value = io.ezz.launcher.ui.instance.installation.model.LocalImportRequest(
                    file = file,
                    contentType = io.ezz.launcher.ui.instance.installation.model.ContentType.SHADER,
                    instance = instance,
                    targetDirectory = shadersDir
                )
            }
        )
    }

    fun importLocalWorld(instance: Instance) {
        openFilePicker(
            title = "Import World",
            description = "Select a Minecraft world archive (.zip)",
            allowedExtensions = setOf("zip"),
            onFileSelected = { file ->
                if (file == null) return@openFilePicker
                scope.launch(Dispatchers.IO) {
                    if (!file.name.endsWith(".zip", ignoreCase = true) || !file.exists() || !file.isFile || file.length() <= 0) {
                        withContext(Dispatchers.Main) {
                            ToastManager.show("Invalid World File", "The selected file is not a valid .zip archive.", ToastType.ERROR)
                        }
                        return@launch
                    }

                    var levelDatEntryPath: String? = null
                    try {
                        java.util.zip.ZipFile(file).use { zip ->
                            val entries = zip.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val norm = entry.name.replace('\\', '/')
                                if (norm.equals("level.dat", ignoreCase = true) || norm.endsWith("/level.dat", ignoreCase = true)) {
                                    levelDatEntryPath = norm
                                    break
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            ToastManager.show("Invalid World File", "The archive is corrupted or cannot be read.", ToastType.ERROR)
                        }
                        return@launch
                    }

                    if (levelDatEntryPath == null) {
                        withContext(Dispatchers.Main) {
                            ToastManager.show("Invalid World Archive", "Archive does not contain a valid Minecraft world (missing level.dat).", ToastType.ERROR)
                        }
                        return@launch
                    }

                    val savesDir = destinationResolver.resolveContentDirectory(instance.id, InstanceContentType.WORLD)

                    val parts = levelDatEntryPath!!.split('/')
                    val (prefixToStrip, worldFolderName) = if (parts.size > 1) {
                        val folderPrefix = parts.dropLast(1).joinToString("/") + "/"
                        folderPrefix to parts[parts.size - 2]
                    } else {
                        "" to file.nameWithoutExtension
                    }

                    val targetWorldDir = java.io.File(savesDir, worldFolderName)
                    destinationResolver.validateDestination(instance.id, InstanceContentType.WORLD, targetWorldDir)

                    fun extractWorld() {
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (targetWorldDir.exists()) {
                                    targetWorldDir.deleteRecursively()
                                }
                                targetWorldDir.mkdirs()

                                java.util.zip.ZipFile(file).use { zip ->
                                    val entries = zip.entries()
                                    while (entries.hasMoreElements()) {
                                        val entry = entries.nextElement()
                                        val entryName = entry.name.replace('\\', '/')
                                        if (entryName.contains("..")) continue // Prevent zip slip

                                        val relativePath = if (prefixToStrip.isNotEmpty() && entryName.startsWith(prefixToStrip)) {
                                             entryName.removePrefix(prefixToStrip)
                                        } else if (prefixToStrip.isEmpty()) {
                                            entryName
                                        } else {
                                            continue
                                        }

                                        if (relativePath.isBlank()) continue
                                        val destFile = java.io.File(targetWorldDir, relativePath)

                                        if (entry.isDirectory) {
                                            destFile.mkdirs()
                                        } else {
                                            destFile.parentFile?.mkdirs()
                                            zip.getInputStream(entry).use { input ->
                                                java.io.FileOutputStream(destFile).use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    refreshWorlds(instance.id)
                                    ToastManager.show("World Imported", "World '$worldFolderName' imported successfully.", ToastType.SUCCESS)
                                }
                            } catch (e: Throwable) {
                                withContext(Dispatchers.Main) {
                                    ToastManager.show("Import Failed", e.message ?: "Failed to extract world archive", ToastType.ERROR)
                                }
                            }
                        }
                    }

                    if (targetWorldDir.exists()) {
                        withContext(Dispatchers.Main) {
                            fileConflictState.value = FileConflictState(
                                title = "World Already Exists",
                                message = "World '$worldFolderName' already exists in this instance. Do you want to replace it?",
                                onConfirmReplace = {
                                    fileConflictState.value = null
                                    extractWorld()
                                },
                                onCancel = {
                                    fileConflictState.value = null
                                }
                            )
                        }
                    } else {
                        extractWorld()
                    }
                }
            }
        )
    }

    fun dismissLocalImport() {
        activeLocalImportRequest.value = null
    }

    fun onLocalImportFinished(request: io.ezz.launcher.ui.instance.installation.model.LocalImportRequest) {
        activeLocalImportRequest.value = null
        val contentType = when (request.contentType) {
            io.ezz.launcher.ui.instance.installation.model.ContentType.MOD -> InstanceContentType.MOD
            io.ezz.launcher.ui.instance.installation.model.ContentType.RESOURCE_PACK -> InstanceContentType.RESOURCE_PACK
            io.ezz.launcher.ui.instance.installation.model.ContentType.SHADER -> InstanceContentType.SHADER
            io.ezz.launcher.ui.instance.installation.model.ContentType.WORLD -> InstanceContentType.WORLD
        }
        refreshContent(request.instance.id, contentType)
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
        val rawQuery = query ?: current.searchQuery
        val trimmedQuery = rawQuery.trim()
        val newPage = page ?: (if (query != null || version != null || resolution != null || category != null || sort != null) 1 else current.page)
        val newVersion = version ?: current.selectedGameVersion ?: instance.minecraftVersion
        val newResolution = if (resolution == "ALL") null else (resolution ?: current.selectedResolution)
        val newCategory = if (category == "ALL") null else (category ?: current.selectedCategory)
        val newSort = sort ?: current.selectedSort

        resourcePacksBrowseState.value = current.copy(
            searchQuery = rawQuery,
            page = newPage,
            selectedGameVersion = newVersion,
            selectedResolution = newResolution,
            selectedCategory = newCategory,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        val reqId = ++resourcePacksSearchSeq
        searchResourcePacksJob?.cancel()
        searchResourcePacksJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (reqId != resourcePacksSearchSeq) return@launch
            try {
                val versions = if (!newVersion.isNullOrBlank()) listOf(newVersion) else null
                val catList = mutableListOf<String>()
                if (!newCategory.isNullOrBlank()) catList.add(newCategory)
                if (!newResolution.isNullOrBlank()) catList.add(newResolution)
                val categories = if (catList.isNotEmpty()) catList else null
                val offset = (newPage - 1) * current.pageSize

                val res = modrinth.searchResourcePacks(
                    query = trimmedQuery,
                    gameVersions = versions,
                    categories = categories,
                    index = newSort,
                    offset = offset,
                    limit = current.pageSize
                )
                if (reqId != resourcePacksSearchSeq) return@launch

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
                if (reqId != resourcePacksSearchSeq) return@launch
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
        val rawQuery = query ?: current.searchQuery
        val trimmedQuery = rawQuery.trim()
        val newPage = page ?: (if (query != null || version != null || category != null || sort != null) 1 else current.page)
        val newVersion = version ?: current.selectedGameVersion ?: instance.minecraftVersion
        val newCategory = if (category == "ALL") null else (category ?: current.selectedCategory)
        val newSort = sort ?: current.selectedSort

        shadersBrowseState.value = current.copy(
            searchQuery = rawQuery,
            page = newPage,
            selectedGameVersion = newVersion,
            selectedCategory = newCategory,
            selectedSort = newSort,
            isLoading = true,
            error = null
        )

        val reqId = ++shadersSearchSeq
        searchShadersJob?.cancel()
        searchShadersJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (reqId != shadersSearchSeq) return@launch
            try {
                val versions = if (!newVersion.isNullOrBlank()) listOf(newVersion) else null
                val categories = if (!newCategory.isNullOrBlank()) listOf(newCategory) else null
                val offset = (newPage - 1) * current.pageSize

                val res = modrinth.searchShaders(
                    query = trimmedQuery,
                    gameVersions = versions,
                    categories = categories,
                    index = newSort,
                    offset = offset,
                    limit = current.pageSize
                )
                if (reqId != shadersSearchSeq) return@launch

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
                if (reqId != shadersSearchSeq) return@launch
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

    fun getResolvedEnvironment(instanceId: String): ResolvedEnvironment? {
        val instance = instanceRepository.instances.value.find { it.id == instanceId }
            ?: _selectedInstance.value?.takeIf { it.id == instanceId }
            ?: return null
        val mcVer = instance.minecraftVersion.trim()
        val loaderName = instance.loaderType.name.trim()
        if (mcVer.isBlank() || loaderName.isBlank()) {
            return null
        }
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id).toFile()
        return try {
            ResolvedEnvironment(
                instanceId = instance.id,
                minecraftVersion = mcVer,
                loader = loaderName,
                loaderVersion = null,
                minecraftDirectoryPath = gameDir.absolutePath
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun installContentWithDependencies(
        instance: Instance,
        project: ModrinthProjectHit,
        mainVersion: ModrinthVersion,
        selectedDependencies: List<io.ezz.launcher.core.model.modrinth.ResolvedModDependency>,
        contentType: InstanceContentType = InstanceContentType.fromModrinthType(project.projectType),
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetDir = destinationResolver.resolveContentDirectory(instance.id, contentType)
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id).toFile()

        // 1. Transactional Mod Installation Flow (MODS)
        if (contentType == InstanceContentType.MOD) {
            val env = getResolvedEnvironment(instance.id)
                ?: throw IllegalStateException("Cannot install mod: Incomplete instance environment for '${instance.name}' (Minecraft: ${instance.minecraftVersion}, Loader: ${instance.loaderType})")

            val planItems = mutableListOf<PlanItem>()
            val initialMods = instanceManager.getMods(instance.id)

            // HARD SAFETY RULE: All currently installed mods are preserved as KEEP
            for (m in initialMods) {
                planItems.add(
                    PlanItem(
                        action = PlanActionType.KEEP,
                        modId = m.id,
                        modName = m.name,
                        currentVersion = m.version,
                        targetVersion = m.version,
                        fileName = m.fileName,
                        reason = "Existing installed mod preserved"
                    )
                )
            }

            val primaryFile = mainVersion.files.firstOrNull { it.primary } ?: mainVersion.files.firstOrNull()
                ?: throw IllegalStateException("No download files available for ${project.title} (v${mainVersion.versionNumber})")

            planItems.add(
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = project.projectId,
                    modName = project.title,
                    targetVersion = mainVersion.versionNumber,
                    fileName = primaryFile.filename,
                    downloadUrl = primaryFile.url,
                    fileSize = primaryFile.size,
                    isPrimary = true,
                    reason = "User requested mod installation"
                )
            )

            selectedDependencies.filter { it.selectedToInstall && it.version != null }.forEach { dep ->
                val depVer = dep.version!!
                val depFile = depVer.files.firstOrNull { it.primary } ?: depVer.files.firstOrNull() ?: return@forEach
                val depTitle = dep.project?.title ?: depVer.name
                planItems.add(
                    PlanItem(
                        action = PlanActionType.INSTALL,
                        modId = dep.project?.projectId ?: depVer.projectId,
                        modName = depTitle,
                        targetVersion = depVer.versionNumber,
                        fileName = depFile.filename,
                        downloadUrl = depFile.url,
                        fileSize = depFile.size,
                        isPrimary = false,
                        reason = "Required dependency for ${project.title}"
                    )
                )
            }

            val initialFiles = env.modsDirectory.listFiles { f ->
                f.isFile && (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".jar.disabled", ignoreCase = true))
            }?.map { it.name }?.toSet() ?: emptySet()

            val expectedFinal = initialFiles + planItems.filter { it.action == PlanActionType.INSTALL }.map { it.fileName }.toSet()

            val plan = InstallationPlan(
                environment = env,
                targetModId = project.projectId,
                targetModName = project.title,
                selectedVersionNumber = mainVersion.versionNumber,
                items = planItems,
                initialModFileNames = initialFiles,
                expectedFinalModFileNames = expectedFinal
            )

            val javaVer = io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)

            val txResult = ModInstallationTransaction.execute(
                plan = plan,
                javaMajorVersion = javaVer,
                downloader = { url, targetFile, progressCb ->
                    modrinth.downloadContent(url, targetFile, progressCb)
                },
                onProgress = onProgress
            )

            if (txResult.isFailure) {
                val err = txResult.exceptionOrNull() ?: IllegalStateException("Mod installation failed")
                return@withContext Result.failure(err)
            }

            // Save metadata sidecar
            try {
                val metaFile = java.io.File(targetDir, ".modrinth_${primaryFile.filename}.json")
                metaFile.writeText("""{"projectId":"${project.projectId}","slug":"${project.slug}","title":"${project.title.replace("\"", "\\\"")}"}""")
            } catch (_: Throwable) {}

            // Save sidecar icon if available
            val iconUrl = project.iconUrl
            if (!iconUrl.isNullOrBlank()) {
                try {
                    val iconSideFile = java.io.File(targetDir, ".icon_${primaryFile.filename}.png")
                    val cleanName = primaryFile.filename.removeSuffix(".jar").removeSuffix(".disabled")
                    val iconCacheDir = pathProvider.cacheDirectory.resolve("icons").resolve("mods").toFile().apply { mkdirs() }
                    val iconCacheFile = java.io.File(iconCacheDir, "${cleanName}.png")
                    modrinth.downloadContent(iconUrl, iconSideFile) { _, _ -> }
                    if (iconSideFile.exists() && iconSideFile.length() > 0) {
                        iconSideFile.copyTo(iconCacheFile, overwrite = true)
                    }
                } catch (_: Throwable) {}
            }

            refreshContent(instance.id, contentType)
            ToastManager.show(
                title = "Mod Installed",
                description = "${project.title} (v${mainVersion.versionNumber}) installed to ${instance.name}",
                type = ToastType.SUCCESS
            )
            return@withContext Result.success(Unit)
        }

        // 2. Safe Staged Installation for Non-Mod Content (Resource Packs & Shaders)
        val timeStamp = System.currentTimeMillis()
        val stagingDir = java.io.File(gameDir, ".install_staging_$timeStamp")
        stagingDir.mkdirs()

        val newlyAddedFiles = mutableListOf<java.io.File>()

        try {
            val primaryFile = mainVersion.files.firstOrNull { it.primary } ?: mainVersion.files.firstOrNull()
                ?: throw IllegalStateException("No download files available for ${project.title} (v${mainVersion.versionNumber})")
            val stagedFile = java.io.File(stagingDir, primaryFile.filename)

            onProgress("Downloading ${project.title} (v${mainVersion.versionNumber})...", 0.20f)
            val ok = modrinth.downloadContent(
                url = primaryFile.url,
                targetFile = stagedFile,
                onProgress = { downloaded, total ->
                    if (total > 0) {
                        val fileFraction = downloaded.toFloat() / total.toFloat()
                        onProgress("Downloading ${project.title} (${(fileFraction * 100).toInt()}%)...", 0.20f + fileFraction * 0.60f)
                    }
                }
            )

            if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                throw IllegalStateException("Failed to download ${project.title} file ${primaryFile.filename}")
            }

            onProgress("Installing ${contentType.displayName.lowercase()}...", 0.85f)
            val finalTarget = java.io.File(targetDir, primaryFile.filename)
            destinationResolver.validateDestination(instance.id, contentType, finalTarget)

            if (finalTarget.exists()) {
                finalTarget.delete()
            }
            if (!stagedFile.renameTo(finalTarget)) {
                stagedFile.copyTo(finalTarget, overwrite = true)
                stagedFile.delete()
            }

            if (!finalTarget.exists() || finalTarget.length() == 0L) {
                throw IllegalStateException("Installation verification failed: $finalTarget does not exist or is empty")
            }
            newlyAddedFiles.add(finalTarget)

            // Save sidecar icon if available
            val iconUrl = project.iconUrl
            if (!iconUrl.isNullOrBlank()) {
                try {
                    val iconSideFile = java.io.File(targetDir, ".icon_${primaryFile.filename}.png")
                    val cleanName = primaryFile.filename.removeSuffix(".jar").removeSuffix(".zip").removeSuffix(".disabled")
                    val iconCategory = when (contentType) {
                        InstanceContentType.RESOURCE_PACK -> "resourcepacks"
                        InstanceContentType.SHADER -> "shaders"
                        else -> "mods"
                    }
                    val iconCacheDir = pathProvider.cacheDirectory.resolve("icons").resolve(iconCategory).toFile().apply { mkdirs() }
                    val iconCacheFile = java.io.File(iconCacheDir, "${cleanName}.png")
                    modrinth.downloadContent(iconUrl, iconSideFile) { _, _ -> }
                    if (iconSideFile.exists() && iconSideFile.length() > 0) {
                        iconSideFile.copyTo(iconCacheFile, overwrite = true)
                    }
                } catch (_: Throwable) {}
            }

            try {
                val metaFile = java.io.File(targetDir, ".modrinth_${primaryFile.filename}.json")
                metaFile.writeText("""{"projectId":"${project.projectId}","slug":"${project.slug}","title":"${project.title.replace("\"", "\\\"")}"}""")
            } catch (_: Throwable) {}

            onProgress("Verifying instance ${contentType.displayName.lowercase()}...", 0.95f)
            refreshContent(instance.id, contentType)
            stagingDir.deleteRecursively()

            onProgress("Installed successfully", 1f)
            ToastManager.show(
                title = "${contentType.displayName} Installed",
                description = "${project.title} (v${mainVersion.versionNumber}) installed to ${instance.name}",
                type = ToastType.SUCCESS
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            println("[ContentInstaller] Non-mod installation failed, rolling back: ${e.message}")
            try {
                newlyAddedFiles.forEach { file ->
                    if (file.exists()) file.delete()
                }
                stagingDir.deleteRecursively()
                refreshContent(instance.id, contentType)
            } catch (_: Throwable) {}
            Result.failure(e)
        }
    }

    suspend fun installModWithDependencies(
        instance: Instance,
        project: ModrinthProjectHit,
        mainVersion: ModrinthVersion,
        selectedDependencies: List<io.ezz.launcher.core.model.modrinth.ResolvedModDependency>,
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<Unit> = installContentWithDependencies(
        instance = instance,
        project = project,
        mainVersion = mainVersion,
        selectedDependencies = selectedDependencies,
        contentType = InstanceContentType.fromModrinthType(project.projectType),
        onProgress = onProgress
    )

    fun installModrinthProject(hit: ModrinthProjectHit) {
        openModInstaller(hit)
    }

    fun installModrinthVersion(
        projectTitle: String,
        version: ModrinthVersion,
        contentType: InstanceContentType = InstanceContentType.MOD,
        iconUrl: String? = null,
        projectId: String? = null,
        slug: String? = null
    ) {
        val instance = _selectedInstance.value ?: return
        scope.launch {
            modrinthDownloadingProject.value = projectTitle
            modrinthDownloadProgress.value = 0f
            try {
                if (version.files.isNotEmpty()) {
                    val primaryFile = version.files.firstOrNull { it.primary } ?: version.files.first()
                    val targetDir = destinationResolver.resolveContentDirectory(instance.id, contentType)
                    val targetFile = java.io.File(targetDir, primaryFile.filename)
                    destinationResolver.validateDestination(instance.id, contentType, targetFile)

                    modrinth.downloadContent(
                        url = primaryFile.url,
                        targetFile = targetFile,
                        onProgress = { downloaded, total ->
                            if (total > 0) {
                                modrinthDownloadProgress.value = downloaded.toFloat() / total.toFloat()
                            }
                        }
                    )
                    if (!targetFile.exists() || targetFile.length() == 0L) {
                        throw IllegalStateException("Failed to verify downloaded file: ${targetFile.absolutePath}")
                    }

                    if (!iconUrl.isNullOrBlank()) {
                        try {
                            val iconSideFile = java.io.File(targetDir, ".icon_${primaryFile.filename}.png")
                            val cleanName = primaryFile.filename.removeSuffix(".jar").removeSuffix(".zip").removeSuffix(".disabled")
                            val iconCategory = when (contentType) {
                                InstanceContentType.RESOURCE_PACK -> "resourcepacks"
                                InstanceContentType.SHADER -> "shaders"
                                else -> "mods"
                            }
                            val iconCacheDir = pathProvider.cacheDirectory.resolve("icons").resolve(iconCategory).toFile().apply { mkdirs() }
                            val iconCacheFile = java.io.File(iconCacheDir, "${cleanName}.png")
                            modrinth.downloadContent(iconUrl, iconSideFile) { _, _ -> }
                            if (iconSideFile.exists() && iconSideFile.length() > 0) {
                                iconSideFile.copyTo(iconCacheFile, overwrite = true)
                            }
                        } catch (_: Throwable) {}
                    }

                    if (!projectId.isNullOrBlank() || !slug.isNullOrBlank()) {
                        try {
                            val metaFile = java.io.File(targetDir, ".modrinth_${primaryFile.filename}.json")
                            metaFile.writeText("""{"projectId":"${projectId ?: ""}","slug":"${slug ?: ""}","title":"${projectTitle.replace("\"", "\\\"")}"}""")
                        } catch (_: Throwable) {}
                    }

                    refreshContent(instance.id, contentType)
                }
            } catch (e: Throwable) {
                println("Error installing specific version: ${e.message}")
            } finally {
                modrinthDownloadingProject.value = null
                modrinthDownloadProgress.value = 0f
            }
        }
    }

    private fun isLocalContentMatch(
        localFileName: String,
        localName: String,
        hit: ModrinthProjectHit,
        targetDir: java.io.File?
    ): Boolean {
        // 1. Check sidecar metadata file .modrinth_<fileName>.json if targetDir is available
        if (targetDir != null) {
            val metaFile = java.io.File(targetDir, ".modrinth_${localFileName}.json")
            if (metaFile.exists()) {
                try {
                    val text = metaFile.readText()
                    if (text.contains("\"${hit.projectId}\"") || text.contains("\"${hit.slug}\"")) {
                        return true
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Direct string checks
        if (localFileName.contains(hit.slug, ignoreCase = true) ||
            localFileName.contains(hit.projectId, ignoreCase = true) ||
            localName.contains(hit.slug, ignoreCase = true) ||
            localName.contains(hit.title, ignoreCase = true) ||
            hit.title.contains(localName, ignoreCase = true)
        ) {
            return true
        }

        // 3. Normalized alphanumeric checks
        val normFileName = localFileName.lowercase().filter { it.isLetterOrDigit() }
        val normLocalName = localName.lowercase().filter { it.isLetterOrDigit() }
        val normSlug = hit.slug.lowercase().filter { it.isLetterOrDigit() }
        val normTitle = hit.title.lowercase().filter { it.isLetterOrDigit() }

        if (normFileName.contains(normSlug) || normLocalName.contains(normSlug) ||
            normFileName.contains(normTitle) || normLocalName.contains(normTitle) ||
            normSlug.contains(normLocalName) || normTitle.contains(normLocalName)
        ) {
            return true
        }

        // 4. Strip common content suffixes ("shaders", "shader", "resourcepack", "texturepack", "pack")
        val strippedTitle = normTitle
            .removeSuffix("shaders")
            .removeSuffix("shader")
            .removeSuffix("resourcepack")
            .removeSuffix("texturepack")
            .removeSuffix("pack")
        val strippedSlug = normSlug
            .removeSuffix("shaders")
            .removeSuffix("shader")
            .removeSuffix("resourcepack")
            .removeSuffix("texturepack")
            .removeSuffix("pack")

        if (strippedSlug.length >= 3 && (normFileName.contains(strippedSlug) || normLocalName.contains(strippedSlug) || strippedSlug.contains(normLocalName))) {
            return true
        }
        if (strippedTitle.length >= 3 && (normFileName.contains(strippedTitle) || normLocalName.contains(strippedTitle) || strippedTitle.contains(normLocalName))) {
            return true
        }

        return false
    }

    fun isModInstalled(hit: ModrinthProjectHit): Boolean {
        val instanceId = _selectedInstance.value?.id
        val dir = if (instanceId != null) try { destinationResolver.resolveContentDirectory(instanceId, InstanceContentType.MOD) } catch (_: Throwable) { null } else null
        val mods = manageMods.value
        return mods.any { local ->
            local.fileName.contains(hit.slug, ignoreCase = true) ||
            local.fileName.contains(hit.projectId, ignoreCase = true) ||
            local.name.equals(hit.title, ignoreCase = true) ||
            local.id.equals(hit.slug, ignoreCase = true) ||
            local.id.equals(hit.projectId, ignoreCase = true) ||
            isLocalContentMatch(local.fileName, local.name, hit, dir)
        }
    }

    fun isResourcePackInstalled(hit: ModrinthProjectHit): Boolean {
        val instanceId = _selectedInstance.value?.id
        val dir = if (instanceId != null) try { destinationResolver.resolveContentDirectory(instanceId, InstanceContentType.RESOURCE_PACK) } catch (_: Throwable) { null } else null
        val packs = manageResourcePacks.value
        return packs.any { local ->
            isLocalContentMatch(local.fileName, local.name, hit, dir)
        }
    }

    fun isShaderInstalled(hit: ModrinthProjectHit): Boolean {
        val instanceId = _selectedInstance.value?.id
        val dir = if (instanceId != null) try { destinationResolver.resolveContentDirectory(instanceId, InstanceContentType.SHADER) } catch (_: Throwable) { null } else null
        val shaders = manageShaders.value
        return shaders.any { local ->
            isLocalContentMatch(local.fileName, local.name, hit, dir)
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

