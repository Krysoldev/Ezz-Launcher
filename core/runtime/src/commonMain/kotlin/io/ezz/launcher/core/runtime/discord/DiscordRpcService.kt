package io.ezz.launcher.core.runtime.discord

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class DiscordRpcStatus {
    DISABLED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed class PresenceState {
    data class Launcher(
        val username: String?,
        val uuid: String?,
        val avatarUrl: String?
    ) : PresenceState()

    data class Minecraft(
        val username: String,
        val minecraftVersion: String,
        val instanceName: String? = null,
        val uuid: String? = null,
        val avatarUrl: String? = null,
        val startedAtMs: Long = System.currentTimeMillis(),
        val processId: Long = 0L
    ) : PresenceState()
}

class DiscordRpcService(
    val clientId: String = "1533440955116556339",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _status = MutableStateFlow(DiscordRpcStatus.DISCONNECTED)
    val status: StateFlow<DiscordRpcStatus> = _status.asStateFlow()

    @Volatile
    private var activePipe: RandomAccessFile? = null
    @Volatile
    private var isHandshakeDone = false

    // Single mutex guarding all pipe transactions: ensures strictly sequential request-response (no deadlocks)
    private val ioMutex = Mutex()
    private val stateLock = Any()

    var isEnabled: Boolean = true
        private set

    private var activeLauncherAccount: Account? = null
    private var currentPresenceState: PresenceState = PresenceState.Launcher(null, null, null)
    private var autoConnectJob: Job? = null

    /**
     * Initializes the Discord RPC lifecycle upon launcher startup.
     * Idempotent: safe to call multiple times without creating duplicate connections.
     */
    fun initialize(account: Account? = null, enabled: Boolean = true) {
        println("[DiscordRPC] initialization started")
        println("[DiscordRPC] applicationId = $clientId")
        println("[DiscordRPC] enabled = $enabled")
        this.isEnabled = enabled
        this.activeLauncherAccount = account

        val avatar = resolveAccountAvatarUrl(account)
        synchronized(stateLock) {
            currentPresenceState = PresenceState.Launcher(
                username = account?.username,
                uuid = account?.uuid,
                avatarUrl = avatar
            )
        }

        println("[DiscordRPC] Discord client created")

        if (enabled) {
            _status.value = DiscordRpcStatus.DISCONNECTED
            startAutoConnectLoop()
        } else {
            _status.value = DiscordRpcStatus.DISABLED
        }
    }

    /**
     * Sets or updates the Launcher presence (when Minecraft is not running, or upon account switch).
     */
    fun setLauncherPresence(account: Account?, enabled: Boolean = isEnabled) {
        this.activeLauncherAccount = account
        this.isEnabled = enabled

        val username = account?.username
        val uuid = account?.uuid
        val avatar = resolveAccountAvatarUrl(account)

        var shouldPublish = false
        synchronized(stateLock) {
            if (currentPresenceState !is PresenceState.Minecraft) {
                currentPresenceState = PresenceState.Launcher(
                    username = username,
                    uuid = uuid,
                    avatarUrl = avatar
                )
                shouldPublish = true
            }
        }

        if (enabled && shouldPublish) {
            publishCurrentPresence()
        }
    }

    /**
     * Sets Minecraft running presence.
     */
    fun setMinecraftPresence(
        playerUsername: String,
        minecraftVersion: String,
        instanceName: String? = null,
        playerUuid: String? = null,
        avatarUrl: String? = null,
        startedAtMs: Long = System.currentTimeMillis(),
        processId: Long = 0L,
        enabled: Boolean = isEnabled
    ) {
        this.isEnabled = enabled

        synchronized(stateLock) {
            currentPresenceState = PresenceState.Minecraft(
                username = playerUsername,
                minecraftVersion = minecraftVersion,
                instanceName = instanceName,
                uuid = playerUuid,
                avatarUrl = avatarUrl,
                startedAtMs = startedAtMs,
                processId = processId
            )
        }

        if (enabled) {
            publishCurrentPresence()
        }
    }

    /**
     * Legacy / interoperability alias for [setMinecraftPresence].
     */
    fun updateActivity(
        playerUsername: String,
        minecraftVersion: String,
        instanceName: String? = null,
        playerUuid: String? = null,
        avatarUrl: String? = null,
        startedAtMs: Long = System.currentTimeMillis(),
        processId: Long = 0L,
        enabled: Boolean = true
    ) {
        setMinecraftPresence(
            playerUsername = playerUsername,
            minecraftVersion = minecraftVersion,
            instanceName = instanceName,
            playerUuid = playerUuid,
            avatarUrl = avatarUrl,
            startedAtMs = startedAtMs,
            processId = processId,
            enabled = enabled
        )
    }

    /**
     * Called when Minecraft process terminates.
     * Restores launcher presence immediately over existing pipe.
     */
    fun onMinecraftExited(processId: Long = 0L) {
        val account = activeLauncherAccount
        val username = account?.username
        val uuid = account?.uuid
        val avatar = resolveAccountAvatarUrl(account)

        synchronized(stateLock) {
            currentPresenceState = PresenceState.Launcher(
                username = username,
                uuid = uuid,
                avatarUrl = avatar
            )
        }

        if (isEnabled) {
            publishCurrentPresence()
        }
    }

    /**
     * Enables or disables Discord RPC dynamically without requiring launcher restart.
     */
    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled == enabled) return
        this.isEnabled = enabled

        if (enabled) {
            println("[DiscordRPC] enabled = true")
            _status.value = DiscordRpcStatus.DISCONNECTED
            startAutoConnectLoop()
            publishCurrentPresence()
        } else {
            println("[DiscordRPC] enabled = false")
            _status.value = DiscordRpcStatus.DISABLED
            autoConnectJob?.cancel()
            autoConnectJob = null
            clearActivity(disconnect = true)
        }
    }

    /**
     * Clears presence from Discord.
     */
    fun clearActivity(processId: Long = 0L, disconnect: Boolean = false) {
        scope.launch {
            try {
                ioMutex.withLock {
                    val pipe = activePipe
                    if (pipe != null && isHandshakeDone) {
                        val nonce = UUID.randomUUID().toString()
                        val payload = buildJsonObject {
                            put("cmd", "SET_ACTIVITY")
                            putJsonObject("args") {
                                put("pid", if (processId > 0) processId else ProcessHandle.current().pid())
                                put("activity", null as String?)
                            }
                            put("nonce", nonce)
                        }.toString()

                        transactFrame(pipe, 1, payload)
                        println("[DiscordRPC] activity cleared")
                    }
                }
            } catch (e: Throwable) {
                println("[DiscordRPC] activity update failure: clearActivity failed: ${e.message}")
            } finally {
                if (disconnect) {
                    disconnect()
                }
            }
        }
    }

    /**
     * Publishes whichever presence is active (Minecraft or Launcher) over the pipe.
     */
    fun publishCurrentPresence() {
        if (!isEnabled) return
        scope.launch {
            try {
                val current = synchronized(stateLock) { currentPresenceState }
                val summary = when (current) {
                    is PresenceState.Minecraft -> "Minecraft ${current.minecraftVersion} as ${current.username}"
                    is PresenceState.Launcher -> "Launcher (${current.username ?: "Guest"})"
                }

                println("[DiscordRPC] activity update started: $summary")

                val payload = when (current) {
                    is PresenceState.Minecraft -> {
                        buildMinecraftPayload(
                            playerUsername = current.username,
                            minecraftVersion = current.minecraftVersion,
                            instanceName = current.instanceName,
                            playerUuid = current.uuid,
                            avatarUrl = current.avatarUrl,
                            startedAtMs = current.startedAtMs,
                            processId = current.processId
                        )
                    }
                    is PresenceState.Launcher -> {
                        buildLauncherPayload(
                            username = current.username,
                            avatarUrl = current.avatarUrl,
                            uuid = current.uuid
                        )
                    }
                }

                val success = executeActivityTransaction(payload)
                if (success) {
                    println("[DiscordRPC] activity update success: $summary")
                } else {
                    println("[DiscordRPC] activity update failure: Discord rejected activity or pipe unavailable")
                }
            } catch (e: Throwable) {
                println("[DiscordRPC] activity update failure: ${e.message}")
                disconnect()
            }
        }
    }

    private suspend fun executeActivityTransaction(payload: String): Boolean = ioMutex.withLock {
        val pipe = ensureConnectedLocked() ?: return false
        return try {
            val response = transactFrame(pipe, 1, payload)
            if (response != null) {
                val evt = response.second["evt"]?.jsonPrimitive?.content
                if (evt == "ERROR") {
                    val msg = response.second["data"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                    println("[DiscordRPC] activity update failure: Discord error: $msg")
                    false
                } else {
                    true
                }
            } else {
                false
            }
        } catch (e: Throwable) {
            println("[DiscordRPC] connection failure: ${e.message}")
            disconnectLocked()
            false
        }
    }

    private fun startAutoConnectLoop() {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            var backoffMs = 4000L
            val maxBackoffMs = 15000L

            while (isActive && isEnabled) {
                val isConnected = ioMutex.withLock {
                    if (activePipe == null || !isHandshakeDone) {
                        val pipe = ensureConnectedLocked()
                        if (pipe != null) {
                            println("[DiscordRPC] reconnect success")
                            backoffMs = 4000L
                            true
                        } else {
                            println("[DiscordRPC] reconnect scheduled: retry in ${backoffMs}ms")
                            backoffMs = (backoffMs * 3 / 2).coerceAtMost(maxBackoffMs)
                            false
                        }
                    } else {
                        // Heartbeat / health check via PING (Opcode 3)
                        checkPipeHealthLocked()
                    }
                }

                if (isConnected) {
                    publishCurrentPresence()
                }

                delay(backoffMs)
            }
        }
    }

    private fun checkPipeHealthLocked(): Boolean {
        val pipe = activePipe ?: return false
        return try {
            val pingPayload = "{\"nonce\":\"health-check\"}"
            val pong = transactFrame(pipe, 3, pingPayload)
            if (pong != null && pong.first == 4) {
                true
            } else {
                println("[DiscordRPC] connection failure: Unexpected ping response")
                disconnectLocked()
                false
            }
        } catch (e: Throwable) {
            println("[DiscordRPC] connection failure: Pipe health check failed (${e.message})")
            disconnectLocked()
            false
        }
    }

    private fun ensureConnectedLocked(): RandomAccessFile? {
        if (activePipe != null && isHandshakeDone) return activePipe

        _status.value = DiscordRpcStatus.CONNECTING
        println("[DiscordRPC] connection attempt")

        val (pipe, pipeIndex) = openDiscordPipeWithIndex() ?: run {
            _status.value = DiscordRpcStatus.DISCONNECTED
            println("[DiscordRPC] connection failure: Discord desktop IPC pipe not found (discord-ipc-0..9)")
            return null
        }

        return try {
            val handshake = buildJsonObject {
                put("v", 1)
                put("client_id", clientId)
            }.toString()

            val response = transactFrame(pipe, 0, handshake)
            if (response == null) {
                println("[DiscordRPC] connection failure: Handshake failed (no response from Discord)")
                try { pipe.close() } catch (_: Throwable) {}
                _status.value = DiscordRpcStatus.DISCONNECTED
                return null
            }

            activePipe = pipe
            isHandshakeDone = true
            _status.value = DiscordRpcStatus.CONNECTED
            println("[DiscordRPC] connection success (connected to discord-ipc-$pipeIndex)")
            pipe
        } catch (e: Throwable) {
            println("[DiscordRPC] connection failure: ${e.message}")
            try { pipe.close() } catch (_: Throwable) {}
            activePipe = null
            isHandshakeDone = false
            _status.value = DiscordRpcStatus.DISCONNECTED
            null
        }
    }

    private fun transactFrame(pipe: RandomAccessFile, opcode: Int, jsonPayload: String): Pair<Int, kotlinx.serialization.json.JsonObject>? {
        // Write frame
        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(opcode)
        header.putInt(bytes.size)
        pipe.write(header.array())
        if (bytes.isNotEmpty()) {
            pipe.write(bytes)
        }

        // Read response
        val respHeader = ByteArray(8)
        pipe.readFully(respHeader)
        val respBuf = ByteBuffer.wrap(respHeader).order(ByteOrder.LITTLE_ENDIAN)
        val respOpcode = respBuf.int
        val respLength = respBuf.int

        if (respLength < 0 || respLength > 1_000_000) {
            throw java.io.IOException("Invalid Discord frame length: $respLength")
        }

        val respBody = ByteArray(respLength)
        if (respLength > 0) {
            pipe.readFully(respBody)
        }
        val respString = String(respBody, Charsets.UTF_8)
        val json = try {
            Json.parseToJsonElement(respString).jsonObject
        } catch (e: Throwable) {
            buildJsonObject {}
        }

        return respOpcode to json
    }

    private fun openDiscordPipeWithIndex(): Pair<RandomAccessFile, Int>? {
        val isWindows = System.getProperty("os.name")?.contains("win", ignoreCase = true) == true
        for (i in 0..9) {
            val pipePath = if (isWindows) {
                "\\\\.\\pipe\\discord-ipc-$i"
            } else {
                val tempDir = System.getenv("XDG_RUNTIME_DIR")
                    ?: System.getenv("TMPDIR")
                    ?: System.getenv("TMP")
                    ?: System.getenv("TEMP")
                    ?: "/tmp"
                "$tempDir/discord-ipc-$i"
            }

            try {
                val file = File(pipePath)
                if (isWindows || file.exists()) {
                    val raf = RandomAccessFile(pipePath, "rw")
                    return raf to i
                }
            } catch (_: Throwable) {
                // Try next index
            }
        }
        return null
    }

    private fun disconnectLocked() {
        try {
            activePipe?.close()
        } catch (_: Throwable) {}
        activePipe = null
        isHandshakeDone = false
        if (isEnabled) {
            _status.value = DiscordRpcStatus.DISCONNECTED
        }
        println("[DiscordRPC] disconnected")
    }

    private fun disconnect() {
        scope.launch {
            ioMutex.withLock {
                disconnectLocked()
            }
        }
    }

    fun resolveAccountAvatarUrl(account: Account?): String? {
        if (account == null) return null
        return when (account.type) {
            AccountType.MICROSOFT -> {
                account.avatarUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: if (account.uuid.isNotBlank()) "https://minotar.net/helm/${account.uuid.replace("-", "")}/128.png"
                    else if (account.username.isNotBlank()) "https://minotar.net/helm/${account.username}/128.png"
                    else "https://minotar.net/helm/Steve/128.png"
            }
            AccountType.OFFLINE -> {
                account.avatarUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: if (account.username.isNotBlank()) "https://minotar.net/helm/${account.username}/128.png"
                    else "https://minotar.net/helm/Steve/128.png"
            }
        }
    }

    internal fun resolveAvatarUrl(
        avatarUrl: String?,
        playerUuid: String?,
        playerUsername: String
    ): String {
        return when {
            !avatarUrl.isNullOrBlank() && avatarUrl.startsWith("http", ignoreCase = true) -> avatarUrl
            !playerUuid.isNullOrBlank() -> "https://minotar.net/helm/${playerUuid.replace("-", "")}/128.png"
            playerUsername.isNotBlank() && !playerUsername.equals("Ezz Launcher", ignoreCase = true) -> "https://minotar.net/helm/$playerUsername/128.png"
            else -> "https://minotar.net/helm/Steve/128.png"
        }
    }

    internal fun buildLauncherPayload(
        username: String?,
        avatarUrl: String?,
        uuid: String? = null,
        processId: Long = 0L,
        nonce: String = UUID.randomUUID().toString()
    ): String {
        val hasAccount = !username.isNullOrBlank()
        val visibleName = if (hasAccount) username!! else "Player"
        val effectiveAvatarUrl = resolveAvatarUrl(avatarUrl, uuid, visibleName)

        return buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", if (processId > 0) processId else ProcessHandle.current().pid())
                putJsonObject("activity") {
                    put("name", "Ezz Launcher")
                    put("type", 0)
                    if (hasAccount) {
                        put("details", username!!)
                        put("state", "Ready to play")
                    } else {
                        put("details", "Ready to play")
                    }
                    putJsonObject("assets") {
                        put("large_image", "ezzlauncher")
                        put("large_text", "Ezz Launcher")
                        if (hasAccount) {
                            put("small_image", effectiveAvatarUrl)
                            put("small_text", visibleName)
                        }
                    }
                }
            }
            put("nonce", nonce)
        }.toString()
    }

    internal fun buildMinecraftPayload(
        playerUsername: String,
        minecraftVersion: String,
        instanceName: String? = null,
        playerUuid: String? = null,
        avatarUrl: String? = null,
        startedAtMs: Long = System.currentTimeMillis(),
        processId: Long = 0L,
        nonce: String = UUID.randomUUID().toString()
    ): String {
        val startEpochSeconds = startedAtMs / 1000L
        val cleanVersion = if (minecraftVersion.startsWith("Minecraft", ignoreCase = true)) {
            minecraftVersion
        } else {
            "Minecraft $minecraftVersion"
        }
        val effectiveAvatarUrl = resolveAvatarUrl(avatarUrl, playerUuid, playerUsername)
        val visibleName = playerUsername.ifBlank { "Player" }

        val stateText = if (!instanceName.isNullOrBlank() && !instanceName.equals("Default", ignoreCase = true) && !instanceName.equals(cleanVersion, ignoreCase = true)) {
            "$instanceName ($cleanVersion)"
        } else {
            cleanVersion
        }

        return buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", if (processId > 0) processId else ProcessHandle.current().pid())
                putJsonObject("activity") {
                    put("name", "Ezz Launcher")
                    put("type", 0)
                    put("details", "Playing Minecraft")
                    put("state", stateText)
                    putJsonObject("timestamps") {
                        put("start", startEpochSeconds)
                    }
                    putJsonObject("assets") {
                        put("large_image", "ezzlauncher")
                        put("large_text", "Ezz Launcher")
                        put("small_image", effectiveAvatarUrl)
                        put("small_text", visibleName)
                    }
                }
            }
            put("nonce", nonce)
        }.toString()
    }

    internal fun buildActivityPayload(
        playerUsername: String,
        minecraftVersion: String,
        instanceName: String? = null,
        playerUuid: String? = null,
        avatarUrl: String? = null,
        startedAtMs: Long = System.currentTimeMillis(),
        processId: Long = 0L,
        nonce: String = UUID.randomUUID().toString()
    ): String {
        return buildMinecraftPayload(
            playerUsername = playerUsername,
            minecraftVersion = minecraftVersion,
            instanceName = instanceName,
            playerUuid = playerUuid,
            avatarUrl = avatarUrl,
            startedAtMs = startedAtMs,
            processId = processId,
            nonce = nonce
        )
    }
}
