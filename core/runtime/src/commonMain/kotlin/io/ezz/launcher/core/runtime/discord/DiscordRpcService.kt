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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
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
    private val clientId: String = "1533440955116556339",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _status = MutableStateFlow(DiscordRpcStatus.DISCONNECTED)
    val status: StateFlow<DiscordRpcStatus> = _status.asStateFlow()

    private var activePipe: RandomAccessFile? = null
    private var isHandshakeDone = false

    var isEnabled: Boolean = true
        private set

    private var activeLauncherAccount: Account? = null
    private var currentPresenceState: PresenceState = PresenceState.Launcher(null, null, null)
    private var autoConnectJob: Job? = null

    /**
     * Initializes the Discord RPC lifecycle upon launcher startup.
     * Starts background auto-connection and sets launcher presence immediately.
     */
    fun initialize(account: Account? = null, enabled: Boolean = true) {
        println("[DiscordRPC] RPC initialization started (Application ID: $clientId)")
        this.isEnabled = enabled
        this.activeLauncherAccount = account

        val avatar = resolveAccountAvatarUrl(account)
        currentPresenceState = PresenceState.Launcher(
            username = account?.username,
            uuid = account?.uuid,
            avatarUrl = avatar
        )

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

        println("[DiscordRPC] Launcher presence set for '${username ?: "Guest"}'")

        // Only switch visible presence if Minecraft is not currently running
        if (currentPresenceState !is PresenceState.Minecraft) {
            currentPresenceState = PresenceState.Launcher(
                username = username,
                uuid = uuid,
                avatarUrl = avatar
            )
            if (enabled) {
                publishCurrentPresence()
            }
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

        println("[DiscordRPC] Minecraft process detected (PID: $processId)")
        println("[DiscordRPC] Minecraft presence set for '$playerUsername' ($minecraftVersion)")

        currentPresenceState = PresenceState.Minecraft(
            username = playerUsername,
            minecraftVersion = minecraftVersion,
            instanceName = instanceName,
            uuid = playerUuid,
            avatarUrl = avatarUrl,
            startedAtMs = startedAtMs,
            processId = processId
        )

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
     * Restores launcher presence immediately without disconnecting the RPC pipe.
     */
    fun onMinecraftExited(processId: Long = 0L) {
        println("[DiscordRPC] Minecraft process exited (PID: $processId)")

        val account = activeLauncherAccount
        val username = account?.username
        val uuid = account?.uuid
        val avatar = resolveAccountAvatarUrl(account)

        currentPresenceState = PresenceState.Launcher(
            username = username,
            uuid = uuid,
            avatarUrl = avatar
        )

        println("[DiscordRPC] Launcher presence set for '${username ?: "Guest"}'")

        if (isEnabled) {
            publishCurrentPresence()
        }
    }

    /**
     * Enables or disables Discord RPC according to user settings.
     */
    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled == enabled) return
        this.isEnabled = enabled

        if (enabled) {
            println("[DiscordRPC] Discord RPC enabled in settings")
            _status.value = DiscordRpcStatus.DISCONNECTED
            startAutoConnectLoop()
            publishCurrentPresence()
        } else {
            println("[DiscordRPC] Discord RPC disabled in settings")
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
                if (isHandshakeDone && activePipe != null) {
                    val nonce = UUID.randomUUID().toString()
                    val payload = buildJsonObject {
                        put("cmd", "SET_ACTIVITY")
                        putJsonObject("args") {
                            put("pid", if (processId > 0) processId else ProcessHandle.current().pid())
                            put("activity", null as String?)
                        }
                        put("nonce", nonce)
                    }.toString()
                    sendFrame(1, payload)
                    println("[DiscordRPC] RPC presence cleared")
                }
            } catch (e: Throwable) {
                println("[DiscordRPC] RPC errors: clearActivity failed: ${e.message}")
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
                if (!ensureConnected()) return@launch

                val payload = when (val presence = currentPresenceState) {
                    is PresenceState.Minecraft -> {
                        buildMinecraftPayload(
                            playerUsername = presence.username,
                            minecraftVersion = presence.minecraftVersion,
                            instanceName = presence.instanceName,
                            playerUuid = presence.uuid,
                            avatarUrl = presence.avatarUrl,
                            startedAtMs = presence.startedAtMs,
                            processId = presence.processId
                        )
                    }
                    is PresenceState.Launcher -> {
                        buildLauncherPayload(
                            username = presence.username,
                            avatarUrl = presence.avatarUrl,
                            uuid = presence.uuid
                        )
                    }
                }

                sendFrame(1, payload)
            } catch (e: Throwable) {
                println("[DiscordRPC] RPC errors: publishCurrentPresence failed: ${e.message}")
                disconnect()
            }
        }
    }

    private fun startAutoConnectLoop() {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            while (isActive && isEnabled) {
                if (activePipe == null || !isHandshakeDone) {
                    println("[DiscordRPC] Discord availability: Checking Discord IPC pipe...")
                    val connected = ensureConnected()
                    if (connected) {
                        println("[DiscordRPC] RPC connection result: CONNECTED")
                        publishCurrentPresence()
                    } else {
                        println("[DiscordRPC] Discord availability: Not running or IPC pipe unavailable (will retry gracefully).")
                    }
                }
                delay(3000L)
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

    private suspend fun ensureConnected(): Boolean = withContext(dispatcher) {
        if (activePipe != null && isHandshakeDone) return@withContext true

        _status.value = DiscordRpcStatus.CONNECTING
        val pipe = openDiscordPipe() ?: run {
            _status.value = DiscordRpcStatus.DISCONNECTED
            return@withContext false
        }

        activePipe = pipe
        try {
            // Handshake (opcode 0)
            val handshake = buildJsonObject {
                put("v", 1)
                put("client_id", clientId)
            }.toString()

            sendFrame(0, handshake)

            // Read response
            val response = readFrame()
            if (response == null) {
                disconnect()
                return@withContext false
            }

            isHandshakeDone = true
            _status.value = DiscordRpcStatus.CONNECTED
            true
        } catch (e: Throwable) {
            println("[DiscordRPC] RPC errors: Handshake failed: ${e.message}")
            disconnect()
            false
        }
    }

    private fun openDiscordPipe(): RandomAccessFile? {
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
                    return raf
                }
            } catch (e: Throwable) {
                // Try next index
            }
        }
        return null
    }

    private fun sendFrame(opcode: Int, jsonPayload: String) {
        val pipe = activePipe ?: return
        try {
            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(opcode)
            header.putInt(bytes.size)

            synchronized(pipe) {
                pipe.write(header.array())
                pipe.write(bytes)
            }
        } catch (e: Throwable) {
            println("[DiscordRPC] RPC errors: Failed to send frame: ${e.message}")
            disconnect()
        }
    }

    private fun readFrame(): Pair<Int, String>? {
        val pipe = activePipe ?: return null
        try {
            val headerBytes = ByteArray(8)
            synchronized(pipe) {
                pipe.readFully(headerBytes)
                val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val opcode = buf.int
                val length = buf.int
                if (length < 0 || length > 65536) return null
                val bodyBytes = ByteArray(length)
                pipe.readFully(bodyBytes)
                return opcode to String(bodyBytes, Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            println("[DiscordRPC] RPC errors: Failed to read frame: ${e.message}")
            disconnect()
            return null
        }
    }

    private fun disconnect() {
        try {
            activePipe?.close()
        } catch (e: Throwable) {
            // Ignore close errors
        } finally {
            activePipe = null
            isHandshakeDone = false
            if (isEnabled) {
                _status.value = DiscordRpcStatus.DISCONNECTED
            }
        }
    }
}
