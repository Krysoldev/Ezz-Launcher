package io.ezz.launcher.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthService
import io.ezz.launcher.core.minecraft.loader.fabric.FabricInstaller
import io.ezz.launcher.core.minecraft.loader.fabric.FabricMetaClient
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineInstaller
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.minecraft.resolver.AssetResolver
import io.ezz.launcher.core.minecraft.resolver.LibraryResolver
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ezz.launcher.core.network.downloader.DownloadManager
import io.ezz.launcher.core.runtime.LaunchEngine
import io.ezz.launcher.core.runtime.process.DesktopProcessLauncher
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.SupabaseAccountRepository
import io.ezz.launcher.core.storage.repository.SupabaseInstanceRepository
import io.ezz.launcher.core.storage.repository.SupabaseModRepository
import io.ezz.launcher.core.storage.repository.SupabaseProfileRepository
import io.ezz.launcher.core.storage.repository.SupabaseSettingsRepository
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.vault.EncryptedFileVault
import io.ezz.launcher.ui.MainScreen
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.awt.Desktop
import java.net.URI

fun main() = application {
    val pathProvider = DefaultPathProvider.createDefault()
    val secureVault = EncryptedFileVault(pathProvider.rootDirectory.resolve("vault.dat"))
    val httpClient = HttpClientFactory.create()
    val downloadManager = DownloadManager(httpClient)

    val supabaseConfig = io.ezz.launcher.core.storage.supabase.SupabaseConfig.fromEnvironment()
    val supabaseClient = io.ezz.launcher.core.storage.supabase.SupabaseClient(supabaseConfig, httpClient)

    val instanceRepository = io.ezz.launcher.core.storage.repository.SupabaseInstanceRepository(supabaseClient, pathProvider)
    val accountRepository = io.ezz.launcher.core.storage.repository.SupabaseAccountRepository(supabaseClient, secureVault)
    val settingsRepository = io.ezz.launcher.core.storage.repository.SupabaseSettingsRepository(supabaseClient)
    val profileRepository = io.ezz.launcher.core.storage.repository.SupabaseProfileRepository(supabaseClient)
    val modRepository = io.ezz.launcher.core.storage.repository.SupabaseModRepository(supabaseClient)

    // Public / Global Repositories
    val releaseRepository = io.ezz.launcher.core.storage.repository.SupabaseLauncherReleaseRepository(supabaseClient)
    val minecraftVersionRepository = io.ezz.launcher.core.storage.repository.SupabaseMinecraftVersionRepository(supabaseClient)
    val fabricVersionRepository = io.ezz.launcher.core.storage.repository.SupabaseFabricVersionRepository(supabaseClient)
    val optifineVersionRepository = io.ezz.launcher.core.storage.repository.SupabaseOptiFineVersionRepository(supabaseClient)
    val announcementRepository = io.ezz.launcher.core.storage.repository.SupabaseAnnouncementRepository(supabaseClient)
    val launcherConfigRepository = io.ezz.launcher.core.storage.repository.SupabaseLauncherConfigRepository(supabaseClient)
    val featureFlagRepository = io.ezz.launcher.core.storage.repository.SupabaseFeatureFlagRepository(supabaseClient)

    val versionManifestService = VersionManifestService(httpClient, pathProvider)
    val libraryResolver = LibraryResolver(pathProvider)
    val assetResolver = AssetResolver(httpClient, pathProvider)
    val fabricMetaClient = FabricMetaClient(httpClient)
    val fabricInstaller = FabricInstaller(fabricMetaClient, pathProvider)
    val optiFineInstaller = OptiFineInstaller(pathProvider)

    val microsoftAuthService = MicrosoftAuthService(httpClient)
    val authManager = AuthManager(accountRepository, microsoftAuthService)
    val processLauncher = DesktopProcessLauncher()

    val launchEngine = LaunchEngine(
        pathProvider = pathProvider,
        versionManifestService = versionManifestService,
        libraryResolver = libraryResolver,
        assetResolver = assetResolver,
        fabricInstaller = fabricInstaller,
        optiFineInstaller = optiFineInstaller,
        downloadManager = downloadManager,
        authManager = authManager,
        instanceRepository = instanceRepository,
        processLauncher = processLauncher
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

    val viewModel = AppViewModel(
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
        platformBridge = platformBridge
    )

    val windowState = rememberWindowState(width = 1180.dp, height = 760.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Ezz Launcher — Cross-Platform Minecraft Java Edition Launcher",
        icon = painterResource("icon.png"),
        state = windowState
    ) {
        MainScreen(viewModel = viewModel)
    }
}
