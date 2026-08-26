package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.supabase.SupabaseInstanceDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstanceRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: SupabaseInstanceRepository
    private val json = Json { ignoreUnknownKeys = true }
    private val databaseRows = mutableListOf<SupabaseInstanceDto>()

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_test_storage", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        databaseRows.clear()

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")

            when {
                path.contains("/rest/v1/instances") && method == "GET" -> {
                    respond(json.encodeToString(databaseRows), HttpStatusCode.OK, responseHeaders)
                }
                path.contains("/rest/v1/instances") && method == "POST" -> {
                    val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    val dto = json.decodeFromString<SupabaseInstanceDto>(bodyString)
                    databaseRows.add(dto)
                    respond(json.encodeToString(listOf(dto)), HttpStatusCode.Created, responseHeaders)
                }
                path.contains("/rest/v1/instances") && method == "PATCH" -> {
                    val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    val dto = json.decodeFromString<SupabaseInstanceDto>(bodyString)
                    databaseRows.removeIf { it.id == dto.id }
                    databaseRows.add(dto)
                    respond(json.encodeToString(listOf(dto)), HttpStatusCode.OK, responseHeaders)
                }
                path.contains("/rest/v1/instances") && method == "DELETE" -> {
                    respond("{}", HttpStatusCode.OK, responseHeaders)
                }
                else -> {
                    respond("[]", HttpStatusCode.OK, responseHeaders)
                }
            }
        }

        val httpClient = HttpClient(mockEngine)
        val supabaseConfig = SupabaseConfig("https://mock.supabase.co", "mock-anon-key")
        val supabaseClient = SupabaseClient(supabaseConfig, httpClient)
        repository = SupabaseInstanceRepository(supabaseClient, pathProvider)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCreateInstanceWithIsolatedDirectories() = runBlocking {
        val instance = repository.createInstance(
            name = "Test Survival",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.VANILLA,
            loaderVersion = null,
            minMemoryMb = 2048,
            maxMemoryMb = 4096,
            customJvmArgs = emptyList()
        )

        assertNotNull(instance.id)
        assertEquals("Test Survival", instance.name)
        assertEquals("1.21.4", instance.minecraftVersion)
        assertEquals(1, databaseRows.size, "Supabase PostgreSQL must contain the newly inserted instance")

        // Verify isolated subdirectories exist
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id).toFile()
        assertTrue(gameDir.exists(), "Game directory must exist")
        assertTrue(File(gameDir, "mods").exists(), "mods directory must exist")
        assertTrue(File(gameDir, "config").exists(), "config directory must exist")
        assertTrue(File(gameDir, "saves").exists(), "saves directory must exist")
        assertTrue(File(gameDir, "resourcepacks").exists(), "resourcepacks directory must exist")
        assertTrue(File(gameDir, "shaderpacks").exists(), "shaderpacks directory must exist")
        assertTrue(File(gameDir, "logs").exists(), "logs directory must exist")
    }

    @Test
    fun testDuplicateAndIsolation() = runBlocking {
        val original = repository.createInstance(
            name = "Original",
            minecraftVersion = "1.20.4",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9",
            minMemoryMb = 1024,
            maxMemoryMb = 2048,
            customJvmArgs = emptyList()
        )

        val duplicated = repository.duplicateInstance(original.id, "Duplicated")

        assertNotNull(duplicated)
        assertTrue(duplicated.id != original.id)
        assertEquals("Duplicated", duplicated.name)
        assertEquals(2, databaseRows.size, "Supabase PostgreSQL must contain both original and duplicated instances")

        val origDir = pathProvider.getInstanceDirectory(original.id).toFile()
        val dupDir = pathProvider.getInstanceDirectory(duplicated.id).toFile()

        assertTrue(origDir.exists())
        assertTrue(dupDir.exists())
        assertTrue(origDir.absolutePath != dupDir.absolutePath)
    }

    @Test
    fun testDeleteInstanceRemovesDirectory() = runBlocking {
        val instance = repository.createInstance(
            name = "To Delete",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.VANILLA,
            loaderVersion = null,
            minMemoryMb = 1024,
            maxMemoryMb = 2048,
            customJvmArgs = emptyList()
        )

        val instanceDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        assertTrue(instanceDir.exists())

        repository.deleteInstance(instance.id)

        // Verify local directory removed
        assertTrue(!instanceDir.exists(), "Instance directory should be deleted from filesystem")
    }
}
