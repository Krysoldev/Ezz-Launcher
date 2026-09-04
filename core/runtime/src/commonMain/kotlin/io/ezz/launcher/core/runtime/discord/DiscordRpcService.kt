package io.ezz.launcher.core.runtime.discord

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class DiscordRpcService(
    private val clientId: String = "1533440955116556339",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _status = MutableStateFlow(DiscordRpcStatus.DISCONNECTED)
    val status: StateFlow<DiscordRpcStatus> = _status.asStateFlow()

    private var activePipe: RandomAccessFile? = null
    private var isHandshakeDone = false

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
        if (!enabled) {
            clearActivity(processId)
            return
        }

        scope.launch {
            try {
                if (!ensureConnected()) return@launch

                val payload = buildActivityPayload(
                    playerUsername = playerUsername,
                    minecraftVersion = minecraftVersion,
                    instanceName = instanceName,
                    playerUuid = playerUuid,
                    avatarUrl = avatarUrl,
                    startedAtMs = startedAtMs,
                    processId = processId
                )

                sendFrame(1, payload)
            } catch (e: Throwable) {
                disconnect()
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
            playerUsername.isNotBlank() -> "https://minotar.net/helm/$playerUsername/128.png"
            else -> "https://minotar.net/helm/Steve/128.png"
        }
    }

    @Suppress("UNUSED_PARAMETER")
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
        val startEpochSeconds = startedAtMs / 1000L
        val cleanVersion = if (minecraftVersion.startsWith("Minecraft", ignoreCase = true)) {
            minecraftVersion
        } else {
            "Minecraft $minecraftVersion"
        }
        val effectiveAvatarUrl = resolveAvatarUrl(avatarUrl, playerUuid, playerUsername)
        val visibleName = playerUsername.ifBlank { "Player" }

        return buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", if (processId > 0) processId else ProcessHandle.current().pid())
                putJsonObject("activity") {
                    put("name", visibleName)
                    put("type", 0)
                    put("details", "Playing Minecraft")
                    put("state", cleanVersion)
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

    fun clearActivity(processId: Long = 0L) {
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
                }
            } catch (e: Throwable) {
                // Ignore clear errors
            } finally {
                disconnect()
            }
        }
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
            readFrame()

            isHandshakeDone = true
            _status.value = DiscordRpcStatus.CONNECTED
            true
        } catch (e: Throwable) {
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
        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(opcode)
        header.putInt(bytes.size)

        synchronized(pipe) {
            pipe.write(header.array())
            pipe.write(bytes)
        }
    }

    private fun readFrame(): Pair<Int, String>? {
        val pipe = activePipe ?: return null
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
    }

    private fun disconnect() {
        try {
            activePipe?.close()
        } catch (e: Throwable) {
            // Ignore close errors
        } finally {
            activePipe = null
            isHandshakeDone = false
            _status.value = DiscordRpcStatus.DISCONNECTED
        }
    }
}
