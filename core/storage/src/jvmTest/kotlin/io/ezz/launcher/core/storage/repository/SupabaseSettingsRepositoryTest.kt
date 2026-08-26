package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.supabase.SupabaseUserSettingsDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SupabaseSettingsRepositoryTest {

    private lateinit var repository: SupabaseSettingsRepository
    private val json = Json { ignoreUnknownKeys = true }
    private val databaseRows = mutableListOf<SupabaseUserSettingsDto>()

    @BeforeTest
    fun setUp() {
        databaseRows.clear()
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")

            when {
                path.contains("/rest/v1/user_settings") && method == "GET" -> {
                    respond(json.encodeToString(databaseRows), HttpStatusCode.OK, responseHeaders)
                }
                path.contains("/rest/v1/user_settings") && method == "POST" -> {
                    val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    val dto = json.decodeFromString<SupabaseUserSettingsDto>(bodyString)
                    databaseRows.add(dto)
                    respond(json.encodeToString(listOf(dto)), HttpStatusCode.Created, responseHeaders)
                }
                path.contains("/rest/v1/user_settings") && method == "PATCH" -> {
                    val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    val dto = json.decodeFromString<SupabaseUserSettingsDto>(bodyString)
                    databaseRows.removeIf { it.userId == dto.userId }
                    databaseRows.add(dto)
                    respond(json.encodeToString(listOf(dto)), HttpStatusCode.OK, responseHeaders)
                }
                else -> {
                    respond("[]", HttpStatusCode.OK, responseHeaders)
                }
            }
        }

        val httpClient = HttpClient(mockEngine)
        val supabaseConfig = SupabaseConfig("https://mock.supabase.co", "mock-anon-key")
        val supabaseClient = SupabaseClient(supabaseConfig, httpClient)
        repository = SupabaseSettingsRepository(supabaseClient)
    }

    @Test
    fun testLoadAndUpdateSettingsInSupabase() = runBlocking {
        val initial = repository.loadSettings()
        assertNotNull(initial)

        val updated = repository.updateSettings {
            it.copy(defaultMaxMemoryMb = 8192, darkTheme = true)
        }

        assertEquals(8192, updated.defaultMaxMemoryMb)
        assertEquals(1, databaseRows.size, "Settings must be persisted in Supabase PostgreSQL")
        assertEquals(8192, databaseRows.first().defaultMaxMemoryMb)
    }
}
