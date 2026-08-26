package io.ezz.launcher.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import io.ezz.launcher.core.storage.repository.DefaultAccountRepository
import io.ezz.launcher.core.storage.repository.DefaultInstanceRepository
import io.ezz.launcher.core.storage.repository.DefaultSettingsRepository
import io.ezz.launcher.core.storage.vault.EncryptedFileVault
import io.ezz.launcher.ui.MainScreen
import io.ezz.launcher.ui.platform.DefaultPlatformBridge
import io.ezz.launcher.ui.viewmodel.AppViewModel
import okio.Path.Companion.toPath

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val rootDir = (getExternalFilesDir(null) ?: filesDir).absolutePath.toPath().resolve(".ezzlauncher")
            val pathProvider = DefaultPathProvider(rootDir)
            try {
                pathProvider.initializeDirectories()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            val secureVault = EncryptedFileVault(pathProvider.rootDirectory.resolve("vault.dat"))
            val httpClient = HttpClientFactory.create()
            val downloadManager = DownloadManager(httpClient)

            val instanceRepository = DefaultInstanceRepository(pathProvider)
            val accountRepository = DefaultAccountRepository(pathProvider, secureVault)
            val settingsRepository = DefaultSettingsRepository(pathProvider)

            val versionManifestService = VersionManifestService(httpClient, pathProvider)
            val libraryResolver = LibraryResolver(pathProvider)
            val assetResolver = AssetResolver(httpClient, pathProvider)
            val fabricMetaClient = FabricMetaClient(httpClient)
            val fabricInstaller = FabricInstaller(fabricMetaClient, pathProvider)
            val optiFineInstaller = OptiFineInstaller(pathProvider)

            val microsoftAuthService = MicrosoftAuthService(httpClient)
            val authManager = AuthManager(accountRepository, microsoftAuthService)
            val processLauncher = AndroidProcessLauncher()

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
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Files stored at: $path", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenUrl = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: Throwable) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
                platformBridge = platformBridge
            )

            setContent {
                MainScreen(viewModel = viewModel)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0F19))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ezz Launcher Startup Error:\n${e.message ?: e.toString()}",
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}
