package io.ezz.launcher.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthService
import io.ezz.launcher.core.auth.microsoft.WindowsHwndResolver
import io.ezz.launcher.core.minecraft.loader.fabric.FabricInstaller
import io.ezz.launcher.core.minecraft.loader.fabric.FabricMetaClient
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineInstaller
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.minecraft.resolver.AssetResolver
import io.ezz.launcher.core.minecraft.resolver.LibraryResolver
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.network.curseforge.CurseForgeService
import io.ezz.launcher.core.network.downloader.DownloadManager
import io.ezz.launcher.core.runtime.LaunchEngine
import io.ezz.launcher.core.runtime.process.DesktopProcessLauncher
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
import io.ezz.launcher.core.storage.repository.LocalMinecraftAccountRepository
import io.ezz.launcher.core.storage.repository.LocalSettingsRepository
import io.ezz.launcher.core.storage.repository.LocalVaultSkinRepository
import io.ezz.launcher.core.storage.repository.SupabaseAnnouncementRepository
import io.ezz.launcher.core.storage.repository.SupabaseFabricVersionRepository
import io.ezz.launcher.core.storage.repository.SupabaseFeatureFlagRepository
import io.ezz.launcher.core.storage.repository.SupabaseLauncherConfigRepository
import io.ezz.launcher.core.storage.repository.SupabaseLauncherReleaseRepository
import io.ezz.launcher.core.storage.repository.SupabaseMinecraftVersionRepository
import io.ezz.launcher.core.storage.repository.SupabaseModRepository
import io.ezz.launcher.core.storage.repository.SupabaseOptiFineVersionRepository
import io.ezz.launcher.core.storage.repository.SupabaseProfileRepository
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.vault.EncryptedFileVault
import io.ezz.launcher.ui.MainScreen
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun setupProductionLogging() {
    try {
        val appData = System.getenv("APPDATA")
            ?: (System.getProperty("user.home") + "/AppData/Roaming")
        val logDir = File(appData, ".ezzlauncher/logs")
        logDir.mkdirs()
        val logFile = File(logDir, "launcher.log")
        if (logFile.exists() && logFile.length() > 10 * 1024 * 1024) {
            val oldFile = File(logDir, "launcher.old.log")
            if (oldFile.exists()) oldFile.delete()
            logFile.renameTo(oldFile)
        }

        val fos = FileOutputStream(logFile, true)
        val originalOut = System.out
        val originalErr = System.err

        val teeOut = object : OutputStream() {
            override fun write(b: Int) {
                originalOut.write(b)
                fos.write(b)
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                originalOut.write(b, off, len)
                fos.write(b, off, len)
            }
            override fun flush() {
                originalOut.flush()
                fos.flush()
            }
            override fun close() {
                originalOut.close()
                fos.close()
            }
        }

        val teeErr = object : OutputStream() {
            override fun write(b: Int) {
                originalErr.write(b)
                fos.write(b)
            }
            override fun write(b: ByteArray, off: Int, len: Int) {
                originalErr.write(b, off, len)
                fos.write(b, off, len)
            }
            override fun flush() {
                originalErr.flush()
                fos.flush()
            }
            override fun close() {
                originalErr.close()
                fos.close()
            }
        }

        System.setOut(PrintStream(teeOut, true, "UTF-8"))
        System.setErr(PrintStream(teeErr, true, "UTF-8"))
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        println("=== EZZ LAUNCHER PRODUCTION LOG [$now] ===")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        println("Java: ${System.getProperty("java.version")} by ${System.getProperty("java.vendor")}")
        println("VM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
        println("User Dir: ${System.getProperty("user.dir")}")
        println("App Data: $appData")
    } catch (e: Throwable) {
        System.err.println("Failed to initialize production log file: ${e.message}")
    }
}

fun main() {
    setupProductionLogging()
    println("STARTING EZZ LAUNCHER")
    println("-> Creating application")

    // Global Uncaught Exception Handler
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        System.err.println("[EZZ STARTUP ERROR] Uncaught exception on thread '${thread.name}': ${throwable.message}")
        throwable.printStackTrace()
    }

    application {
        println("-> Creating main window")
        val windowState = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 1180.dp,
            height = 760.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "Ezz Launcher",
            icon = painterResource("icon.png"),
            state = windowState,
            visible = true
        ) {
            val viewModelState = remember { mutableStateOf<AppViewModel?>(null) }
            val startupErrorState = remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                println("-> Showing main window")
                try {
                    window.toFront()
                    window.requestFocus()
                } catch (_: Throwable) {}

                withContext(Dispatchers.IO) {
                    try {
                        println("-> Loading configuration")
                        val pathProvider = DefaultPathProvider.createDefault()
                        pathProvider.initializeDirectories()
                        val settingsRepository = LocalSettingsRepository(pathProvider)
                        settingsRepository.loadSettings()
                        val secureVault = EncryptedFileVault(pathProvider.rootDirectory.resolve("vault.dat"))
                        val httpClient = HttpClientFactory.create()
                        val downloadManager = DownloadManager(httpClient)
                        val supabaseConfig = SupabaseConfig.fromEnvironment()
                        val supabaseClient = SupabaseClient(supabaseConfig, httpClient)

                        println("-> Loading accounts")
                        LocalMinecraftAccountRepository.isStartupPhase = true
                        val accountRepository = LocalMinecraftAccountRepository(pathProvider, secureVault)
                        val loadedAccounts = accountRepository.loadAll()
                        println("-> Loaded ${loadedAccounts.size} account(s)")
                        val msalCacheDir = pathProvider.rootDirectory.resolve("cache").resolve("msal").toFile()
                        // Lazy MicrosoftAuthService: WAM broker is NOT initialized during startup
                        val microsoftAuthService = MicrosoftAuthService(httpClient, cacheDir = msalCacheDir)
                        val authManager = AuthManager(accountRepository, microsoftAuthService)

                        // Startup must NEVER automatically create an account.
                        // Accounts are ONLY created by explicit user actions (Add Offline Account or Microsoft Sign-In).

                        println("-> Initializing services")
                        val instanceRepository = LocalInstanceRepository(pathProvider)
                        instanceRepository.loadAll()

                        val profileRepository = SupabaseProfileRepository(supabaseClient)
                        val modRepository = SupabaseModRepository(supabaseClient)
                        val releaseRepository = SupabaseLauncherReleaseRepository(supabaseClient)
                        val minecraftVersionRepository = SupabaseMinecraftVersionRepository(supabaseClient)
                        val fabricVersionRepository = SupabaseFabricVersionRepository(supabaseClient)
                        val optifineVersionRepository = SupabaseOptiFineVersionRepository(supabaseClient)
                        val announcementRepository = SupabaseAnnouncementRepository(supabaseClient)
                        val launcherConfigRepository = SupabaseLauncherConfigRepository(supabaseClient)
                        val featureFlagRepository = SupabaseFeatureFlagRepository(supabaseClient)

                        val versionManifestService = VersionManifestService(httpClient, pathProvider)
                        val libraryResolver = LibraryResolver(pathProvider)
                        val assetResolver = AssetResolver(httpClient, pathProvider)
                        val fabricMetaClient = FabricMetaClient(httpClient)
                        val fabricInstaller = FabricInstaller(fabricMetaClient, pathProvider)
                        val optiFineInstaller = OptiFineInstaller(pathProvider)
                        val processLauncher = DesktopProcessLauncher()
                        val vaultSkinRepository = LocalVaultSkinRepository(pathProvider)
                        val discordRpcService = io.ezz.launcher.core.runtime.discord.DiscordRpcService()

                        val launchEngine = LaunchEngine(
                            pathProvider = pathProvider,
                            versionManifestService = versionManifestService,
                            libraryResolver = libraryResolver,
                            assetResolver = assetResolver,
                            fabricInstaller = fabricInstaller,
                            optiFineInstaller = optiFineInstaller,
                            downloadManager = downloadManager,
                            instanceRepository = instanceRepository,
                            processLauncher = processLauncher,
                            vaultSkinRepository = vaultSkinRepository,
                            settingsRepository = settingsRepository,
                            discordRpcService = discordRpcService
                        )

                        val platformBridge = DefaultPlatformBridge(
                            onOpenFolder = { path ->
                                try {
                                    val file = path.toFile()
                                    file.mkdirs()
                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                        Desktop.getDesktop().open(file)
                                    }
                                } catch (e: Throwable) {
                                    println("Failed to open folder: ${e.message}")
                                }
                            },
                            onOpenUrl = { url ->
                                try {
                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                        Desktop.getDesktop().browse(URI(url))
                                    }
                                } catch (e: Throwable) {
                                    println("Failed to open URL: ${e.message}")
                                }
                            }
                        )

                        val localModScanner = io.ezz.launcher.core.minecraft.mods.LocalModScanner(pathProvider)
                        val skinManager = io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager(pathProvider, httpClient, vaultSkinRepository)
                        val processSessionTracker = io.ezz.launcher.core.runtime.process.ProcessSessionTracker(pathProvider)
                        val localInstanceManager = io.ezz.launcher.core.storage.instance.LocalInstanceManager(pathProvider, instanceRepository)
                        val modrinthService = io.ezz.launcher.core.network.modrinth.ModrinthService(httpClient)
                        val curseForgeService = CurseForgeService()

                        val adminAuthorizationService = io.ezz.launcher.core.auth.admin.AdminAuthorizationService(
                            httpClient = httpClient,
                            releaseRepository = releaseRepository,
                            supabaseClient = supabaseClient,
                            microsoftAuthService = microsoftAuthService
                        )
                        val gitHubReleaseService = io.ezz.launcher.core.storage.github.GitHubReleaseService(
                            vault = secureVault,
                            releaseRepository = releaseRepository,
                            httpClient = httpClient
                        )

                        val vm = AppViewModel(
                            instanceRepository = instanceRepository,
                            accountRepository = accountRepository,
                            settingsRepository = settingsRepository,
                            versionManifestService = versionManifestService,
                            fabricMetaClient = fabricMetaClient,
                            authManager = authManager,
                            launchEngine = launchEngine,
                            pathProvider = pathProvider,
                            supabaseClient = supabaseClient,
                            profileRepository = profileRepository,
                            modRepository = modRepository,
                            releaseRepository = releaseRepository,
                            minecraftVersionRepository = minecraftVersionRepository,
                            fabricVersionRepository = fabricVersionRepository,
                            optifineVersionRepository = optifineVersionRepository,
                            announcementRepository = announcementRepository,
                            launcherConfigRepository = launcherConfigRepository,
                            featureFlagRepository = featureFlagRepository,
                            localModScanner = localModScanner,
                            skinManager = skinManager,
                            processSessionTracker = processSessionTracker,
                            localInstanceManager = localInstanceManager,
                            modrinthService = modrinthService,
                            curseForgeService = curseForgeService,
                            vaultSkinRepository = vaultSkinRepository,
                            platformBridge = platformBridge,
                            adminAuthorizationService = adminAuthorizationService,
                            gitHubReleaseService = gitHubReleaseService,
                            discordRpcService = discordRpcService,
                            secureVault = secureVault
                        )

                        // Attach lazy HWND provider: only called when user clicks Microsoft login!
                        vm.nativeWindowProvider = {
                            WindowsHwndResolver.resolve(window, "Ezz Launcher")
                        }

                        LocalMinecraftAccountRepository.isStartupPhase = false
                        println("-> Startup complete")
                        viewModelState.value = vm
                    } catch (e: Throwable) {
                        println("[STARTUP ERROR] Initialization failed: ${e.message}")
                        e.printStackTrace()
                        startupErrorState.value = e.message ?: "Startup failed"
                    }
                }
            }

            val currentVm = viewModelState.value
            if (currentVm != null) {
                MainScreen(viewModel = currentVm)
            } else {
                LauncherStartupSplash(
                    errorMessage = startupErrorState.value
                )
            }
        }
    }
}

@Composable
private fun LauncherStartupSplash(
    errorMessage: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource("icon.png"),
                contentDescription = "Ezz Launcher",
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (errorMessage == null) {
                CircularProgressIndicator(
                    color = Color(0xFF6366F1),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Starting Ezz Launcher...",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "Startup Notice",
                    color = Color(0xFFF87171),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
