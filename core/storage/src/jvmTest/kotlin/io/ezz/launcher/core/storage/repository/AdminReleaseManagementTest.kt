package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.github.GitHubAssetDto
import io.ezz.launcher.core.storage.github.GitHubReleaseDto
import io.ezz.launcher.core.storage.github.GitHubReleaseService
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdminReleaseManagementTest {

    @Test
    fun testSemanticVersionComparison() {
        val mockClient = SupabaseClient(SupabaseConfig(), HttpClient())
        val repo = SupabaseLauncherReleaseRepository(mockClient)

        // Basic version increments
        assertTrue("1.0.1 should be newer than 1.0.0", repo.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue("1.1.0 should be newer than 1.0.9", repo.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue("2.0.0 should be newer than 1.99.99", repo.isNewerVersion("2.0.0", "1.99.99"))

        // Multi-digit version components (e.g. 1.0.10 > 1.0.9)
        assertTrue("1.0.10 should be newer than 1.0.9", repo.isNewerVersion("1.0.10", "1.0.9"))
        assertTrue("1.10.0 should be newer than 1.9.0", repo.isNewerVersion("1.10.0", "1.9.0"))

        // Equal versions
        assertFalse("1.0.0 should not be newer than 1.0.0", repo.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse("1.0.1 should not be newer than 1.0.1", repo.isNewerVersion("1.0.1", "1.0.1"))

        // Older versions
        assertFalse("1.0.0 should not be newer than 1.0.1", repo.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse("1.0.9 should not be newer than 1.0.10", repo.isNewerVersion("1.0.9", "1.0.10"))

        // With 'v' prefixes
        assertTrue("v1.0.1 should be newer than 1.0.0", repo.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue("1.0.1 should be newer than v1.0.0", repo.isNewerVersion("1.0.1", "v1.0.0"))
        assertTrue("v1.0.1 should be newer than v1.0.0", repo.isNewerVersion("v1.0.1", "v1.0.0"))

        // Pre-release tag handling
        assertTrue("1.0.1-release should be newer than 1.0.0", repo.isNewerVersion("1.0.1-release", "1.0.0"))
    }

    @Test
    fun testGitHubReleaseDtoSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val rawJson = """
            {
                "id": 123456,
                "tag_name": "v1.0.1",
                "name": "Ezz Launcher 1.0.1",
                "body": "Official Release 1.0.1",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/Krysoldev/Ezz-Launcher/releases/tag/v1.0.1",
                "upload_url": "https://uploads.github.com/repos/Krysoldev/Ezz-Launcher/releases/123456/assets{?name,label}",
                "published_at": "2026-09-12T11:47:00Z",
                "assets": [
                    {
                        "id": 98765,
                        "name": "EzzLauncher-Setup-1.0.1.exe",
                        "size": 111620192,
                        "browser_download_url": "https://github.com/Krysoldev/Ezz-Launcher/releases/download/v1.0.1/EzzLauncher-Setup-1.0.1.exe",
                        "content_type": "application/octet-stream"
                    }
                ]
            }
        """.trimIndent()

        val release = json.decodeFromString<GitHubReleaseDto>(rawJson)
        assertEquals(123456L, release.id)
        assertEquals("v1.0.1", release.tagName)
        assertEquals("Ezz Launcher 1.0.1", release.name)
        assertFalse(release.draft)
        assertEquals(1, release.assets.size)
        assertEquals("EzzLauncher-Setup-1.0.1.exe", release.assets[0].name)
        assertEquals(111620192L, release.assets[0].size)
    }

    @Test
    fun testProductionReleaseArtifactsIntegrity() {
        val releaseDir = File("release")
        if (releaseDir.exists()) {
            val exe = File(releaseDir, "EzzLauncher.exe")
            val setup = File(releaseDir, "EzzLauncher-Setup-1.0.1.exe")

            if (exe.exists() && setup.exists()) {
                assertTrue("EzzLauncher.exe must not be empty", exe.length() > 100_000)
                assertTrue("EzzLauncher-Setup-1.0.1.exe must not be empty", setup.length() > 50_000_000)

                val exeHash = GitHubReleaseService.computeSha256(exe)
                val setupHash = GitHubReleaseService.computeSha256(setup)

                assertEquals(64, exeHash.length)
                assertEquals(64, setupHash.length)
                assertTrue("Hash must only contain hex characters", exeHash.all { it in '0'..'9' || it in 'a'..'f' })
                assertTrue("Hash must only contain hex characters", setupHash.all { it in '0'..'9' || it in 'a'..'f' })
            }
        }
    }
}
