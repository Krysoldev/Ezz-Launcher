package io.ezz.launcher.core.runtime.process

import io.ezz.launcher.core.model.runtime.InstanceRuntimeSession
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks running Minecraft Java processes locally.
 * - Stores active running sessions in `running_sessions.json`.
 * - Survives launcher restart: checks OS ProcessHandle to detect live Minecraft sessions,
 *   recovers actual process start time from OS metadata, and restores live runtime state.
 * - Manages process termination / graceful stop.
 */
class ProcessSessionTracker(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val sessionsFile: Path get() = pathProvider.rootDirectory.resolve("running_sessions.json")
    private val activeSessions = ConcurrentHashMap<String, InstanceRuntimeSession>()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun registerSession(instanceId: String, processId: Long, startedAt: Long) {
        val session = InstanceRuntimeSession(
            instanceId = instanceId,
            processId = processId,
            startedAt = startedAt
        )
        activeSessions[instanceId] = session
        persistSessions()
    }

    suspend fun unregisterSession(instanceId: String) {
        activeSessions.remove(instanceId)
        persistSessions()
    }

    fun getActiveSession(instanceId: String): InstanceRuntimeSession? {
        return activeSessions[instanceId]
    }

    fun getAllActiveSessions(): Map<String, InstanceRuntimeSession> {
        return activeSessions.toMap()
    }

    suspend fun recoverActiveSessions(): List<InstanceRuntimeSession> = withContext(dispatcher) {
        val recovered = mutableListOf<InstanceRuntimeSession>()
        if (!fileSystem.exists(sessionsFile)) {
            return@withContext recovered
        }

        try {
            val content = fileSystem.read(sessionsFile) { readUtf8() }
            if (content.isNotBlank()) {
                val savedList = json.decodeFromString<List<InstanceRuntimeSession>>(content)
                for (saved in savedList) {
                    val handleOpt = ProcessHandle.of(saved.processId)
                    if (handleOpt.isPresent && handleOpt.get().isAlive) {
                        val handle = handleOpt.get()
                        val osStartTime = handle.info().startInstant()
                            .map { it.toEpochMilli() }
                            .orElse(saved.startedAt)

                        val activeSession = saved.copy(startedAt = osStartTime)
                        activeSessions[saved.instanceId] = activeSession
                        recovered.add(activeSession)
                    }
                }
            }
        } catch (e: Exception) {
            println("Note: could not recover saved running sessions: ${e.message}")
        }

        // Clean up dead sessions
        persistSessions()
        return@withContext recovered
    }

    fun stopProcess(processId: Long): Boolean {
        return try {
            val handleOpt = ProcessHandle.of(processId)
            if (handleOpt.isPresent) {
                val handle = handleOpt.get()
                handle.destroy()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun persistSessions() = withContext(dispatcher) {
        try {
            val list = activeSessions.values.toList()
            val content = json.encodeToString(list)
            val parent = sessionsFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(sessionsFile) {
                writeUtf8(content)
            }
        } catch (e: Exception) {
            println("Note: could not persist running sessions: ${e.message}")
        }
    }
}
