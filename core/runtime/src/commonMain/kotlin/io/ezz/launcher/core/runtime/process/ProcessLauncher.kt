package io.ezz.launcher.core.runtime.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed interface ProcessEvent {
    data class Started(val pid: Long) : ProcessEvent
    data class LogOutput(val line: String, val isError: Boolean = false) : ProcessEvent
    data class Terminated(val exitCode: Int) : ProcessEvent
    data class Error(val message: String, val cause: Throwable? = null) : ProcessEvent
}

interface ProcessLauncher {
    fun launch(
        command: List<String>,
        workingDirectory: File,
        environment: Map<String, String> = emptyMap()
    ): Flow<ProcessEvent>
}

class DesktopProcessLauncher(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProcessLauncher {

    override fun launch(
        command: List<String>,
        workingDirectory: File,
        environment: Map<String, String>
    ): Flow<ProcessEvent> = channelFlow {
        var process: Process? = null
        try {
            val processBuilder = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(false)

            processBuilder.environment().putAll(environment)
            process = processBuilder.start()

            val pid = try {
                process.pid()
            } catch (e: Throwable) {
                0L
            }
            send(ProcessEvent.Started(pid))

            // Launch concurrent stdout reader
            val stdoutJob = launch(dispatcher) {
                try {
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            send(ProcessEvent.LogOutput(line!!, isError = false))
                        }
                    }
                } catch (e: Throwable) {
                    // Stream closed
                }
            }

            // Launch concurrent stderr reader
            val stderrJob = launch(dispatcher) {
                try {
                    BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            send(ProcessEvent.LogOutput(line!!, isError = true))
                        }
                    }
                } catch (e: Throwable) {
                    // Stream closed
                }
            }

            val exitCode = withContext(dispatcher) {
                process.waitFor()
            }

            stdoutJob.join()
            stderrJob.join()

            send(ProcessEvent.Terminated(exitCode))
        } catch (e: Exception) {
            send(ProcessEvent.Error("Process execution failed: ${e.message}", e))
        } finally {
            process?.destroy()
        }
    }.flowOn(dispatcher)
}
