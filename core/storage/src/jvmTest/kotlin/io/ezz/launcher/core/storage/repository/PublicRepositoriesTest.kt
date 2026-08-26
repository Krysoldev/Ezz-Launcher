package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.supabase.SupabaseFabricVersionDto
import io.ezz.launcher.core.storage.supabase.SupabaseFeatureFlagDto
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherConfigDto
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftVersionDto
import io.ezz.launcher.core.storage.supabase.SupabaseOptiFineVersionDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicRepositoriesTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private fun createMockClient(): SupabaseClient {
        val releases = listOf(
            SupabaseLauncherReleaseDto(
                id = "rel-1",
                version = "1.1.0",
                platform = "windows",
                downloadUrl = "https://example.com/download.exe",
                releaseNotes = "New features",
                isLatest = true,
                isRequired = false,
                isActive = true
            )
        )

        val mcVersions = listOf(
            SupabaseMinecraftVersionDto(id = "mc-1", version = "1.21.4", isSupported = true, isAvailable = true),
            SupabaseMinecraftVersionDto(id = "mc-2", version = "1.20.4", isSupported = true, isAvailable = true)
        )

        val fabricVersions = listOf(
            SupabaseFabricVersionDto(id = "fab-1", minecraftVersion = "1.21.4", loaderVersion = "0.16.10"),
            SupabaseFabricVersionDto(id = "fab-2", minecraftVersion = "1.20.4", loaderVersion = "0.15.11")
        )

        val optifineVersions = listOf(
            SupabaseOptiFineVersionDto(id = "opt-1", minecraftVersion = "1.20.4", optifineVersion = "HD_U_I7")
        )

        val announcements = listOf(
            SupabaseAnnouncementDto(id = "ann-1", title = "Maintenance", message = "System update", priority = 10)
        )

        val configs = listOf(
            SupabaseLauncherConfigDto(key = "maintenance_mode", value = "false"),
            SupabaseLauncherConfigDto(key = "maintenance_message", value = "All systems operational")
        )

        val featureFlags = listOf(
            SupabaseFeatureFlagDto(id = "flag-1", featureKey = "fabric_support", enabled = true, platform = "windows"),
            SupabaseFeatureFlagDto(id = "flag-2", featureKey = "beta_features", enabled = false, platform = "windows")
        )

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path.contains("/rest/v1/launcher_releases") -> respond(json.encodeToString(releases), HttpStatusCode.OK, headers)
                path.contains("/rest/v1/minecraft_versions") -> respond(json.encodeToString(mcVersions), HttpStatusCode.OK, headers)
                path.contains("/rest/v1/fabric_versions") -> {
                    val mcParam = request.url.parameters["minecraft_version"]?.removePrefix("eq.")
                    val filtered = if (mcParam != null) fabricVersions.filter { it.minecraftVersion == mcParam } else fabricVersions
                    respond(json.encodeToString(filtered), HttpStatusCode.OK, headers)
                }
                path.contains("/rest/v1/optifine_versions") -> {
                    val mcParam = request.url.parameters["minecraft_version"]?.removePrefix("eq.")
                    val filtered = if (mcParam != null) optifineVersions.filter { it.minecraftVersion == mcParam } else optifineVersions
                    respond(json.encodeToString(filtered), HttpStatusCode.OK, headers)
                }
                path.contains("/rest/v1/launcher_announcements") -> respond(json.encodeToString(announcements), HttpStatusCode.OK, headers)
                path.contains("/rest/v1/launcher_config") -> respond(json.encodeToString(configs), HttpStatusCode.OK, headers)
                path.contains("/rest/v1/feature_flags") -> respond(json.encodeToString(featureFlags), HttpStatusCode.OK, headers)
                else -> respond("[]", HttpStatusCode.OK, headers)
            }
        }

        return SupabaseClient(
            config = SupabaseConfig(supabaseUrl = "https://mock.supabase.co"),
            httpClient = HttpClient(mockEngine)
        )
    }

    @Test
    fun testLauncherReleaseRepository() = runBlocking {
        val client = createMockClient()
        val repo = SupabaseLauncherReleaseRepository(client)

        val latest = repo.getLatestRelease("windows")
        assertNotNull(latest)
        assertEquals("1.1.0", latest.version)

        val updateResult = repo.checkForUpdates("1.0.0", "windows")
        assertTrue(updateResult.hasUpdate)
        assertEquals("1.1.0", updateResult.latestRelease?.version)

        val noUpdateResult = repo.checkForUpdates("1.1.0", "windows")
        assertFalse(noUpdateResult.hasUpdate)
    }

    @Test
    fun testMinecraftVersionRepository() = runBlocking {
        val client = createMockClient()
        val repo = SupabaseMinecraftVersionRepository(client)

        val versions = repo.getSupportedVersions()
        assertEquals(2, versions.size)
        assertEquals("1.21.4", versions[0].version)
    }

    @Test
    fun testFabricAndOptiFineRepositories() = runBlocking {
        val client = createMockClient()
        val fabricRepo = SupabaseFabricVersionRepository(client)
        val optifineRepo = SupabaseOptiFineVersionRepository(client)

        val fabricList = fabricRepo.getFabricVersions("1.21.4")
        assertEquals(1, fabricList.size)
        assertEquals("0.16.10", fabricList[0].loaderVersion)

        val optifine = optifineRepo.getOptiFineVersion("1.20.4")
        assertNotNull(optifine)
        assertEquals("HD_U_I7", optifine.optifineVersion)
    }

    @Test
    fun testAnnouncementAndConfigRepositories() = runBlocking {
        val client = createMockClient()
        val announceRepo = SupabaseAnnouncementRepository(client)
        val configRepo = SupabaseLauncherConfigRepository(client)
        val flagRepo = SupabaseFeatureFlagRepository(client)

        val announcements = announceRepo.getActiveAnnouncements()
        assertEquals(1, announcements.size)
        assertEquals("Maintenance", announcements[0].title)

        val (maintenance, _) = configRepo.isMaintenanceMode()
        assertFalse(maintenance)

        val fabricFlag = flagRepo.isFeatureEnabled("fabric_support", "windows")
        assertTrue(fabricFlag)

        val betaFlag = flagRepo.isFeatureEnabled("beta_features", "windows")
        assertFalse(betaFlag)
    }
}
