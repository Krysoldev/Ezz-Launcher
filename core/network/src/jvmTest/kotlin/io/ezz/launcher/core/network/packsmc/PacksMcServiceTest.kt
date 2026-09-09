package io.ezz.launcher.core.network.packsmc

import io.ezz.launcher.core.model.packsmc.PacksMcStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksMcServiceTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testNoApiKeyReturnsFailure() = runTest {
        val service = PacksMcService(apiKeyProvider = { null })
        val result = service.getPacks(query = "pvp")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PacksMcException)
        assertEquals("no_api_key", ex.code)
        assertEquals(401, ex.statusCode)
        assertEquals(PacksMcStatus.NO_API_KEY, service.status.value)
    }

    @Test
    fun testLiveApiUnauthorizedWithInvalidKey() = runTest {
        val service = PacksMcService(apiKeyProvider = { "pmc_invalid_key_for_test" })
        val result = service.getIdentity()
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is PacksMcException)
        assertEquals(401, ex.statusCode)
        assertEquals("unauthenticated", ex.code)
        assertEquals(PacksMcStatus.INVALID_KEY, service.status.value)
    }

    @Test
    fun testLivePacksEndpointUnauthorizedWithInvalidKey() = runTest {
        val service = PacksMcService(apiKeyProvider = { "pmc_invalid_key_for_test" })
        val result = service.getPacks(query = "blood", sort = "downloads")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is PacksMcException)
        assertEquals(401, ex.statusCode)
        assertEquals("unauthenticated", ex.code)
        assertEquals(PacksMcStatus.INVALID_KEY, service.status.value)
    }

    @Test
    fun testMockGetPacksSuccessWithQueryParamsAndBearerAuth() = runTest {
        var recordedAuthHeader: String? = null
        var recordedUrl: String? = null

        val mockEngine = MockEngine { request ->
            recordedAuthHeader = request.headers[HttpHeaders.Authorization]
            recordedUrl = request.url.toString()
            respond(
                content = """
                    {
                        "data": [
                            {
                                "id": "pack_123",
                                "slug": "bare-bones",
                                "name": "Bare Bones",
                                "description": "A simplistic resource pack",
                                "resolution": "16x",
                                "mc_versions": ["1.21.4", "1.21"],
                                "downloads": 542000,
                                "likes": 32000,
                                "views": 1500000,
                                "author": {
                                    "username": "RobotPantaloons",
                                    "verified": true
                                },
                                "download_url": "https://packsmc.com/pack/bare-bones",
                                "is_exclusive": false
                            }
                        ],
                        "next_cursor": "cursor_abc123"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_valid_test_token" })
        val result = service.getPacks(query = "bare bones", sort = "recent", resolution = "16x")

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(1, response.data.size)
        assertEquals("pack_123", response.data[0].id)
        assertEquals("Bare Bones", response.data[0].name)
        assertEquals("16x", response.data[0].resolution)
        assertEquals("RobotPantaloons", response.data[0].author?.username)
        assertTrue(response.data[0].author?.verified == true)
        assertEquals("cursor_abc123", response.nextCursor)
        assertEquals("Bearer pmc_valid_test_token", recordedAuthHeader)
        assertTrue(recordedUrl?.contains("q=bare+bones") == true || recordedUrl?.contains("q=bare%20bones") == true)
        assertTrue(recordedUrl?.contains("sort=recent") == true)
        assertTrue(recordedUrl?.contains("resolution=16x") == true)
    }

    @Test
    fun testMockGetPackDetailsAndCacheHit() = runTest {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """
                    {
                        "id": "pack_456",
                        "slug": "faithful-32x",
                        "name": "Faithful 32x",
                        "description": "High definition textures",
                        "resolution": "32x",
                        "mc_versions": ["1.21.4"],
                        "downloads": 1200000,
                        "likes": 85000,
                        "license": {
                            "code": "custom",
                            "label": "Faithful License",
                            "allows_redistribution": false,
                            "requires_credit": true
                        },
                        "credits": [
                            {"username": "Vattic", "role": "Original Creator"},
                            {"username": "FaithfulTeam", "role": "Maintainer"}
                        ],
                        "features": ["Connected Textures", "Custom GUI"],
                        "tags": ["faithful", "vanilla-plus"]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_key" })

        // First call triggers network
        val res1 = service.getPackDetails("faithful-32x")
        assertTrue(res1.isSuccess)
        val pack1 = res1.getOrNull()
        assertNotNull(pack1)
        assertEquals("Faithful 32x", pack1.name)
        assertEquals("32x", pack1.resolution)
        assertEquals(2, pack1.credits.size)
        assertEquals("Vattic", pack1.credits[0].username)
        assertFalse(pack1.license?.allowsRedistribution == true)
        assertTrue(pack1.license?.requiresCredit == true)
        assertEquals(1, callCount)

        // Second call hits in-memory cache without making network request
        val res2 = service.getPackDetails("faithful-32x")
        assertTrue(res2.isSuccess)
        assertEquals(1, callCount) // callCount stays 1 due to cache!
    }

    @Test
    fun testMockDownloadPageUrlEndpoint() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                        "pack_id": "pack_789",
                        "download_url": "https://packsmc.com/pack/pack_789/download",
                        "web_url": "https://packsmc.com/pack/pack_789",
                        "requires_packs_plus": false,
                        "instructions": "Download directly through the PacksMC page"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_key" })
        val result = service.getPackDownloadPage("pack_789")
        assertTrue(result.isSuccess)
        val dl = result.getOrNull()
        assertNotNull(dl)
        assertEquals("https://packsmc.com/pack/pack_789/download", dl.downloadUrl)
        assertFalse(dl.requiresPacksPlus)
    }

    @Test
    fun testMockRateLimit429WithRetryAfterHeader() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                        "error": {
                            "code": "rate_limit_exceeded",
                            "message": "Too many requests. Please slow down."
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("15")
                )
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_key" })
        val result = service.getPacks(query = "test")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as PacksMcException
        assertEquals(429, ex.statusCode)
        assertEquals("rate_limit_exceeded", ex.code)
        assertEquals(15, ex.retryAfterSeconds)
        assertEquals(PacksMcStatus.RATE_LIMITED, service.status.value)
        assertNotNull(service.rateLimitCooldownUntilMs.value)
    }

    @Test
    fun testMockNotFound404() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                        "error": {
                            "code": "not_found",
                            "message": "Resource pack does not exist."
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_key" })
        val result = service.getPackDetails("non_existent_pack")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as PacksMcException
        assertEquals(404, ex.statusCode)
        assertEquals("not_found", ex.code)
    }

    @Test
    fun testMockForbidden403ExclusiveContent() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                        "error": {
                            "code": "forbidden",
                            "message": "This pack requires a Packs+ subscription."
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(testJson) }
        }

        val service = PacksMcService(client = client, apiKeyProvider = { "pmc_key" })
        val result = service.getPackDownloadPage("exclusive_pack")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as PacksMcException
        assertEquals(403, ex.statusCode)
        assertEquals("forbidden", ex.code)
    }

    @Test
    fun testClearCacheResetsState() = runTest {
        val service = PacksMcService(apiKeyProvider = { null })
        service.getIdentity()
        assertEquals(PacksMcStatus.NO_API_KEY, service.status.value)

        service.clearCache()
        assertEquals(PacksMcStatus.READY, service.status.value)
        assertNull(service.rateLimitCooldownUntilMs.value)
    }
}
