package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseConfig
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftAccountDto
import io.ezz.launcher.core.storage.vault.InMemorySecureVault
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

class SupabaseAccountRepositoryTest {

    private lateinit var repository: SupabaseAccountRepository
    private val json = Json { ignoreUnknownKeys = true }
    private val databaseRows = mutableListOf<SupabaseMinecraftAccountDto>()

    @BeforeTest
    fun setUp() {
        databaseRows.clear()
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")

            when {
                path.contains("/rest/v1/minecraft_accounts") && method == "GET" -> {
                    respond(json.encodeToString(databaseRows), HttpStatusCode.OK, responseHeaders)
                }
                path.contains("/rest/v1/minecraft_accounts") && method == "POST" -> {
                    val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                    val dto = json.decodeFromString<SupabaseMinecraftAccountDto>(bodyString)
                    databaseRows.add(dto)
                    respond(json.encodeToString(listOf(dto)), HttpStatusCode.Created, responseHeaders)
                }
                path.contains("/rest/v1/minecraft_accounts") && method == "PATCH" -> {
                    respond("[]", HttpStatusCode.OK, responseHeaders)
                }
                path.contains("/rest/v1/minecraft_accounts") && method == "DELETE" -> {
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
        val secureVault = InMemorySecureVault()
        repository = SupabaseAccountRepository(supabaseClient, secureVault)
    }

    @Test
    fun testSaveAndReadMinecraftAccountInSupabase() = runBlocking {
        val account = OfflineAccount(
            id = "acc-uuid-1",
            username = "Steve",
            uuid = "00000000-0000-0000-0000-000000000001"
        )

        repository.saveAccount(account)

        assertEquals(1, databaseRows.size, "Account must be inserted into Supabase PostgreSQL")
        assertEquals("Steve", databaseRows.first().username)
        assertEquals("acc-uuid-1", databaseRows.first().id)

        val loaded = repository.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("Steve", loaded.first().username)
        assertEquals(AccountType.OFFLINE, loaded.first().type)
    }
}
