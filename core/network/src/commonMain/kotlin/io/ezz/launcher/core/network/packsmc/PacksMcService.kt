package io.ezz.launcher.core.network.packsmc

import io.ezz.launcher.core.model.packsmc.PacksMcDownloadResponse
import io.ezz.launcher.core.model.packsmc.PacksMcErrorResponse
import io.ezz.launcher.core.model.packsmc.PacksMcIdentityResponse
import io.ezz.launcher.core.model.packsmc.PacksMcListResponse
import io.ezz.launcher.core.model.packsmc.PacksMcPack
import io.ezz.launcher.core.model.packsmc.PacksMcStatus
import io.ezz.launcher.core.network.client.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom exception representing errors returned by the PacksMC API.
 */
class PacksMcException(
    val code: String,
    override val message: String,
    val statusCode: Int,
    val retryAfterSeconds: Int? = null
) : Exception(message)

/**
 * Official client service for the PacksMC API (v1).
 * Adheres strictly to the PacksMC API contract (https://packsmc.com/docs).
 *
 * Features:
 * - Bearer token authentication from dynamic provider (SecureVault / env)
 * - Rate limiting (HTTP 429) detection and Retry-After tracking
 * - Automatic exponential backoff retry for transient 500 DB errors
 * - In-memory response caching for pack details and search results (5-min TTL)
 * - Observable service status flow
 */
class PacksMcService(
    private val client: HttpClient = HttpClientFactory.create(),
    private val apiKeyProvider: suspend () -> String?
) {
    private val baseUrl = "https://packsmc.com"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val userAgent = "Krysoldev/Ezz-Launcher/1.0.0 (admin@ezzlauncher.com)"

    private val _status = MutableStateFlow(PacksMcStatus.READY)
    val status: StateFlow<PacksMcStatus> = _status.asStateFlow()

    private val _rateLimitCooldownUntilMs = MutableStateFlow<Long?>(null)
    val rateLimitCooldownUntilMs: StateFlow<Long?> = _rateLimitCooldownUntilMs.asStateFlow()

    // 5-minute In-Memory Cache for pack details and catalog searches
    private data class CacheEntry<T>(val data: T, val expiresAtMs: Long)
    private val packDetailsCache = ConcurrentHashMap<String, CacheEntry<PacksMcPack>>()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<PacksMcListResponse>>()
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    /**
     * Confirms the API key works and reports identity, tier, limits, and capabilities.
     * Useful for health checking and testing key validity.
     */
    suspend fun getIdentity(apiKeyOverride: String? = null): Result<PacksMcIdentityResponse> = withContext(Dispatchers.IO) {
        val key = apiKeyOverride ?: apiKeyProvider()
        if (key.isNullOrBlank()) {
            _status.value = PacksMcStatus.NO_API_KEY
            return@withContext Result.failure(
                PacksMcException("no_api_key", "PacksMC API key is required", 401)
            )
        }

        executeWithRetry<PacksMcIdentityResponse> {
            val response = client.get("$baseUrl/api/v1/me") {
                header("User-Agent", userAgent)
                header("Authorization", "Bearer ${key.trim()}")
            }
            handleResponse(response)
        }.onSuccess {
            _status.value = PacksMcStatus.READY
        }.onFailure { err ->
            if (err is PacksMcException && err.statusCode == 401) {
                _status.value = PacksMcStatus.INVALID_KEY
            }
        }
    }

    /**
     * List / search public packs from the PacksMC catalog.
     *
     * @param query Full-text search over name + description + category (min 2 chars)
     * @param resolution Exact match (e.g. 16x)
     * @param author Filter to one creator by Minecraft username
     * @param sort "recent" (default), "downloads", or "likes"
     * @param limit 1-100 (default 25)
     * @param cursor next_cursor from previous page (sort=recent only)
     */
    suspend fun getPacks(
        query: String? = null,
        resolution: String? = null,
        author: String? = null,
        sort: String = "recent",
        limit: Int = 25,
        cursor: String? = null
    ): Result<PacksMcListResponse> = withContext(Dispatchers.IO) {
        // Cache lookup for first page queries
        val cacheKey = "packs:${query.orEmpty()}:${resolution.orEmpty()}:${author.orEmpty()}:$sort:$limit:${cursor.orEmpty()}"
        val now = System.currentTimeMillis()
        val cached = searchCache[cacheKey]
        if (cached != null && cached.expiresAtMs > now) {
            return@withContext Result.success(cached.data)
        }

        checkCooldown()?.let { return@withContext Result.failure(it) }

        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            _status.value = PacksMcStatus.NO_API_KEY
            return@withContext Result.failure(
                PacksMcException("no_api_key", "PacksMC API key is required", 401)
            )
        }

        executeWithRetry<PacksMcListResponse> {
            val response = client.get("$baseUrl/api/v1/packs") {
                header("User-Agent", userAgent)
                header("Authorization", "Bearer ${key.trim()}")
                val cleanQuery = query?.trim()
                if (!cleanQuery.isNullOrBlank() && cleanQuery.length >= 2) {
                    parameter("q", cleanQuery)
                }
                if (!resolution.isNullOrBlank() && resolution != "ALL") {
                    parameter("resolution", resolution.trim())
                }
                if (!author.isNullOrBlank()) {
                    parameter("author", author.trim())
                }
                if (sort.isNotBlank()) {
                    parameter("sort", sort)
                }
                parameter("limit", limit.coerceIn(1, 100))
                if (!cursor.isNullOrBlank() && sort == "recent") {
                    parameter("cursor", cursor)
                }
            }
            handleResponse(response)
        }.onSuccess { res ->
            _status.value = PacksMcStatus.READY
            searchCache[cacheKey] = CacheEntry(res, now + cacheTtlMs)
        }.onFailure { err ->
            updateStatusOnError(err)
        }
    }

    /**
     * Retrieve full pack details by ID or slug.
     */
    suspend fun getPackDetails(idOrSlug: String): Result<PacksMcPack> = withContext(Dispatchers.IO) {
        val cleanId = idOrSlug.trim()
        val now = System.currentTimeMillis()
        val cached = packDetailsCache[cleanId]
        if (cached != null && cached.expiresAtMs > now) {
            return@withContext Result.success(cached.data)
        }

        checkCooldown()?.let { return@withContext Result.failure(it) }

        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            _status.value = PacksMcStatus.NO_API_KEY
            return@withContext Result.failure(
                PacksMcException("no_api_key", "PacksMC API key is required", 401)
            )
        }

        executeWithRetry<PacksMcPack> {
            val response = client.get("$baseUrl/api/v1/packs/$cleanId") {
                header("User-Agent", userAgent)
                header("Authorization", "Bearer ${key.trim()}")
            }
            handleResponse(response)
        }.onSuccess { pack ->
            _status.value = PacksMcStatus.READY
            packDetailsCache[cleanId] = CacheEntry(pack, now + cacheTtlMs)
            packDetailsCache[pack.id] = CacheEntry(pack, now + cacheTtlMs)
            packDetailsCache[pack.slug] = CacheEntry(pack, now + cacheTtlMs)
        }.onFailure { err ->
            updateStatusOnError(err)
        }
    }

    /**
     * Retrieve canonical download page URL for a pack.
     * Note: PacksMC does NOT provide raw file ZIP URLs; download_url points to the PacksMC pack page.
     */
    suspend fun getPackDownloadPage(idOrSlug: String): Result<PacksMcDownloadResponse> = withContext(Dispatchers.IO) {
        checkCooldown()?.let { return@withContext Result.failure(it) }

        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            _status.value = PacksMcStatus.NO_API_KEY
            return@withContext Result.failure(
                PacksMcException("no_api_key", "PacksMC API key is required", 401)
            )
        }

        val cleanId = idOrSlug.trim()
        executeWithRetry<PacksMcDownloadResponse> {
            val response = client.get("$baseUrl/api/v1/packs/$cleanId/download") {
                header("User-Agent", userAgent)
                header("Authorization", "Bearer ${key.trim()}")
            }
            handleResponse(response)
        }.onSuccess {
            _status.value = PacksMcStatus.READY
        }.onFailure { err ->
            updateStatusOnError(err)
        }
    }

    /**
     * Helper to check if currently rate-limited and still in cooldown.
     */
    private fun checkCooldown(): PacksMcException? {
        val cooldownUntil = _rateLimitCooldownUntilMs.value ?: return null
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) {
            val remainingSec = ((cooldownUntil - now) / 1000).toInt().coerceAtLeast(1)
            return PacksMcException(
                code = "rate_limited",
                message = "PacksMC rate limit exceeded. Please wait $remainingSec seconds.",
                statusCode = 429,
                retryAfterSeconds = remainingSec
            )
        } else {
            _rateLimitCooldownUntilMs.value = null
            if (_status.value == PacksMcStatus.RATE_LIMITED) {
                _status.value = PacksMcStatus.READY
            }
            return null
        }
    }

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        val statusCode = response.status.value
        val rawBody = response.bodyAsText()

        if (response.status.isSuccess()) {
            return json.decodeFromString(rawBody)
        }

        // Parse structured error payload if available
        var errorCode = "unknown_error"
        var errorMessage = "HTTP $statusCode: ${response.status.description}"

        try {
            val errorResponse = json.decodeFromString<PacksMcErrorResponse>(rawBody)
            val errDetail = errorResponse.error
            if (errDetail != null) {
                errorCode = errDetail.code
                errorMessage = errDetail.message
            }
        } catch (_: Throwable) {}

        var retryAfterSec: Int? = null
        if (statusCode == 429) {
            val retryAfterHeader = response.headers["Retry-After"]
            retryAfterSec = retryAfterHeader?.toIntOrNull() ?: 60
            val cooldownUntil = System.currentTimeMillis() + (retryAfterSec * 1000L)
            _rateLimitCooldownUntilMs.value = cooldownUntil
            _status.value = PacksMcStatus.RATE_LIMITED
        }

        throw PacksMcException(
            code = errorCode,
            message = errorMessage,
            statusCode = statusCode,
            retryAfterSeconds = retryAfterSec
        )
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelayMs
        for (attempt in 0..maxRetries) {
            try {
                return Result.success(block())
            } catch (e: PacksMcException) {
                // Only retry transient 500 DB errors
                if (e.statusCode == 500 && attempt < maxRetries) {
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }
                return Result.failure(e)
            } catch (e: Throwable) {
                if (attempt < maxRetries) {
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }
                return Result.failure(e)
            }
        }
        return Result.failure(PacksMcException("unknown", "Max retries exceeded", 500))
    }

    private fun updateStatusOnError(err: Throwable) {
        when {
            err is PacksMcException && err.statusCode == 401 -> _status.value = PacksMcStatus.INVALID_KEY
            err is PacksMcException && err.statusCode == 429 -> _status.value = PacksMcStatus.RATE_LIMITED
            err !is PacksMcException -> _status.value = PacksMcStatus.OFFLINE
            else -> _status.value = PacksMcStatus.ERROR
        }
    }

    /**
     * Clear caches when key is updated or on demand.
     */
    fun clearCache() {
        packDetailsCache.clear()
        searchCache.clear()
        _rateLimitCooldownUntilMs.value = null
        _status.value = PacksMcStatus.READY
    }
}
