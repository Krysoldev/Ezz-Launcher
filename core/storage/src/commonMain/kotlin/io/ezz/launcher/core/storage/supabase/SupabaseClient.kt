package io.ezz.launcher.core.storage.supabase

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseException(message: String, val code: String? = null, cause: Throwable? = null) : Exception(message, cause)

class SupabaseClient(
    var config: SupabaseConfig,
    @PublishedApi internal val httpClient: HttpClient,
    @PublishedApi internal val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _currentSession = MutableStateFlow<SupabaseAuthSessionDto?>(null)
    val currentSession: StateFlow<SupabaseAuthSessionDto?> = _currentSession.asStateFlow()

    private val _isConnected = MutableStateFlow<Boolean?>(null)
    val isConnected: StateFlow<Boolean?> = _isConnected.asStateFlow()

    val currentUserId: String? get() = _currentSession.value?.user?.id

    fun updateConfig(newConfig: SupabaseConfig) {
        config = newConfig
    }

    fun setSession(session: SupabaseAuthSessionDto?) {
        _currentSession.value = session
    }

    @PublishedApi
    internal fun getAuthToken(): String {
        return _currentSession.value?.accessToken ?: config.anonKey
    }

    suspend fun checkConnection(): Boolean = withContext(dispatcher) {
        try {
            if (!config.isConfigured) {
                _isConnected.value = false
                return@withContext false
            }
            val response = httpClient.get("${config.restUrl}/") {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
            }
            val connected = response.status.isSuccess()
            _isConnected.value = connected
            connected
        } catch (e: Throwable) {
            _isConnected.value = false
            false
        }
    }

    // =========================================================================
    // PostgREST Generic CRUD Operations
    // =========================================================================

    suspend inline fun <reified T> select(
        table: String,
        params: Map<String, String> = emptyMap()
    ): List<T> = withContext(dispatcher) {
        val url = "${config.restUrl}/$table"
        try {
            val response: HttpResponse = httpClient.get(url) {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
                header("Accept", "application/json")
                params.forEach { (k, v) -> parameter(k, v) }
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(body, response.status.value)
            }

            json.decodeFromString<List<T>>(body)
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Supabase query failed on table '$table': ${e.message}", cause = e)
        }
    }

    suspend inline fun <reified B, reified R> insert(
        table: String,
        bodyData: B
    ): List<R> = withContext(dispatcher) {
        val url = "${config.restUrl}/$table"
        try {
            val jsonPayload = json.encodeToString(bodyData)
            val response: HttpResponse = httpClient.post(url) {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(jsonPayload)
            }

            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(responseBody, response.status.value)
            }

            json.decodeFromString<List<R>>(responseBody)
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Supabase insert failed on table '$table': ${e.message}", cause = e)
        }
    }

    suspend inline fun <reified B, reified R> update(
        table: String,
        filterParams: Map<String, String>,
        bodyData: B
    ): List<R> = withContext(dispatcher) {
        val url = "${config.restUrl}/$table"
        try {
            val jsonPayload = json.encodeToString(bodyData)
            val response: HttpResponse = httpClient.patch(url) {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                filterParams.forEach { (k, v) -> parameter(k, v) }
                setBody(jsonPayload)
            }

            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(responseBody, response.status.value)
            }

            json.decodeFromString<List<R>>(responseBody)
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Supabase update failed on table '$table': ${e.message}", cause = e)
        }
    }

    suspend fun delete(
        table: String,
        filterParams: Map<String, String>
    ): Unit = withContext(dispatcher) {
        val url = "${config.restUrl}/$table"
        try {
            val response: HttpResponse = httpClient.delete(url) {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
                filterParams.forEach { (k, v) -> parameter(k, v) }
            }

            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(responseBody, response.status.value)
            }
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Supabase delete failed on table '$table': ${e.message}", cause = e)
        }
    }

    // =========================================================================
    // Supabase GoTrue Auth Operations
    // =========================================================================

    suspend fun signUp(email: String, password: String, displayName: String = "Ezz Player"): SupabaseAuthSessionDto = withContext(dispatcher) {
        val url = "${config.authUrl}/signup"
        try {
            val payload = buildJsonObject {
                put("email", email)
                put("password", password)
                put("data", buildJsonObject { put("display_name", displayName) })
            }
            val response: HttpResponse = httpClient.post(url) {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(body, response.status.value)
            }
            val session = json.decodeFromString<SupabaseAuthSessionDto>(body)
            setSession(session)
            session
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Sign up failed: ${e.message}", cause = e)
        }
    }

    suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSessionDto = withContext(dispatcher) {
        val url = "${config.authUrl}/token?grant_type=password"
        try {
            val payload = buildJsonObject {
                put("email", email)
                put("password", password)
            }
            val response: HttpResponse = httpClient.post(url) {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(body, response.status.value)
            }
            val session = json.decodeFromString<SupabaseAuthSessionDto>(body)
            setSession(session)
            session
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Sign in failed: ${e.message}", cause = e)
        }
    }

    suspend fun refreshSession(refreshToken: String): SupabaseAuthSessionDto = withContext(dispatcher) {
        val url = "${config.authUrl}/token?grant_type=refresh_token"
        try {
            val payload = buildJsonObject {
                put("refresh_token", refreshToken)
            }
            val response: HttpResponse = httpClient.post(url) {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                handleError(body, response.status.value)
            }
            val session = json.decodeFromString<SupabaseAuthSessionDto>(body)
            setSession(session)
            session
        } catch (e: SupabaseException) {
            throw e
        } catch (e: Exception) {
            throw SupabaseException("Session refresh failed: ${e.message}", cause = e)
        }
    }

    suspend fun signOut(): Unit = withContext(dispatcher) {
        val url = "${config.authUrl}/logout"
        try {
            httpClient.post(url) {
                header("apikey", config.anonKey)
                header(HttpHeaders.Authorization, "Bearer ${getAuthToken()}")
            }
        } catch (e: Exception) {
            // Ignore network errors on logout
        } finally {
            setSession(null)
        }
    }

    fun handleError(responseBody: String, statusCode: Int): Nothing {
        val errorDto = try {
            json.decodeFromString<SupabaseErrorDto>(responseBody)
        } catch (e: Exception) {
            null
        }
        val safeMessage = errorDto?.formatSafeMessage() ?: "HTTP $statusCode request error"
        throw SupabaseException(safeMessage, code = errorDto?.code)
    }
}
