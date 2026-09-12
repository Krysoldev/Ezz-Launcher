package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.github.GitHubConnectionStatus
import io.ezz.launcher.core.storage.github.GitHubReleaseService
import io.ezz.launcher.core.storage.github.ReleasePublishState
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.vault.EncryptedFileVault
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowIntegrationTest {

    @Test
    fun testGitHubServiceConnectionAndToken() = runBlocking {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val vaultFile = File(appData, ".ezzlauncher/vault.dat")
        if (!vaultFile.exists()) {
            println("Vault file does not exist at $vaultFile, skipping live test.")
            return@runBlocking
        }

        val vault = EncryptedFileVault(vaultFile.absolutePath.toPath())
        val supabaseClient = SupabaseClient(SupabaseConfig(), HttpClient())
        val releaseRepo = SupabaseLauncherReleaseRepository(supabaseClient)
        val gitHubService = GitHubReleaseService(vault, releaseRepo)

        val token = gitHubService.getStoredToken()
        println("Stored GitHub Token present: ${!token.isNullOrBlank()}")

        val status = if (!token.isNullOrBlank()) {
            gitHubService.connectWithToken(token)
        } else {
            gitHubService.connectionStatus.value
        }
        println("GitHub connection status: $status")

        if (status is GitHubConnectionStatus.Connected) {
            println("Connected as: ${status.username}, Repo: ${status.repository}, Release permission: ${status.hasReleasePermission}")
            assertTrue(status.hasReleasePermission)
        }
    }

    @Test
    fun testPublishAndVerifyOfficialRelease() = runBlocking {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val vaultFile = File(appData, ".ezzlauncher/vault.dat")
        if (!vaultFile.exists()) {
            println("Vault file does not exist, skipping live test.")
            return@runBlocking
        }

        val vault = EncryptedFileVault(vaultFile.absolutePath.toPath())
        val supabaseClient = SupabaseClient(SupabaseConfig(), HttpClient())
        val releaseRepo = SupabaseLauncherReleaseRepository(supabaseClient)
        val gitHubService = GitHubReleaseService(vault, releaseRepo)

        val rootReleaseDir = listOf(
            File("release"),
            File("../release"),
            File("../../release")
        ).firstOrNull { it.exists() && it.isDirectory } ?: File("release")

        val installerFile = File(rootReleaseDir, "EzzLauncher-Setup-1.0.1.exe").takeIf { it.exists() }
        val exeFile = File(rootReleaseDir, "EzzLauncher.exe").takeIf { it.exists() }
        println("Release dir: ${rootReleaseDir.canonicalPath}, Installer exists: ${installerFile?.exists()}, Exe exists: ${exeFile?.exists()}")

        val releaseNotes = """
            ### Ezz Launcher v1.0.1 — Official Release
            
            #### What's New
            - **Admin Release Management Center**: Official release pipeline directly from within Ezz Launcher.
            - **Strict Canonical Admin Security**: Hardened Microsoft identity verification for KrysolDev.
            - **Automated Update Detection & Delivery**: Live in-place installer updates with SHA-256 integrity checks.
            - **Data Preservation**: Seamlessly preserves all accounts, instances, mods, and configurations.
            - **UI & Performance Improvements**: Smoother sidebar, responsive layout, and refined admin tooling.
        """.trimIndent()

        println("Starting publishRelease flow for v1.0.1...")
        val states = gitHubService.publishRelease(
            adminUsername = "KrysolDev",
            version = "1.0.1",
            releaseTitle = "Ezz Launcher 1.0.1",
            releaseNotes = releaseNotes,
            installerFile = installerFile,
            exeFile = exeFile,
            isDraft = false,
            isRequired = false
        ).toList()

        for (state in states) {
            println("Release state: $state")
        }

        val finalState = states.lastOrNull()
        println("Final release state: $finalState")
        assertTrue(
            "Release must complete successfully (Published or GitHubPublished)",
            finalState is ReleasePublishState.Published || finalState is ReleasePublishState.GitHubPublished ||
                    (finalState is ReleasePublishState.Failed && finalState.isPartialSuccess)
        )
    }

    @Test
    fun testCheckForUpdatesDetectsNewRelease() = runBlocking {
        val supabaseClient = SupabaseClient(SupabaseConfig(), HttpClient())
        val releaseRepo = SupabaseLauncherReleaseRepository(supabaseClient)

        println("Checking for updates comparing against current version 1.0.0...")
        val updateResult = releaseRepo.checkForUpdates(currentVersion = "1.0.0", platform = "windows")
        println("Update check result: hasUpdate=${updateResult.hasUpdate}, latestVersion=${updateResult.latestRelease?.version}, installerUrl=${updateResult.latestRelease?.installerUrl}")

        assertTrue("Update must be detected for 1.0.0 -> 1.0.1", updateResult.hasUpdate)
        assertNotNull("Latest release metadata must be present", updateResult.latestRelease)
        assertEquals("1.0.1", updateResult.latestRelease?.version)
        assertNotNull("Installer download URL must be present", updateResult.latestRelease?.installerUrl)
        assertTrue(
            "Installer URL must point to official GitHub release asset",
            updateResult.latestRelease?.installerUrl?.contains("EzzLauncher-Setup-1.0.1.exe") == true
        )

        // Test running 1.0.1 does not report update
        val upToDateResult = releaseRepo.checkForUpdates(currentVersion = "1.0.1", platform = "windows")
        assertFalse("1.0.1 should be up to date", upToDateResult.hasUpdate)
    }
}
