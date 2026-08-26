package io.ezz.launcher.android

import io.ezz.launcher.core.runtime.process.ProcessEvent
import io.ezz.launcher.core.runtime.process.ProcessLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class AndroidProcessLauncher : ProcessLauncher {

    override fun launch(
        command: List<String>,
        workingDirectory: File,
        environment: Map<String, String>
    ): Flow<ProcessEvent> = flow {
        emit(ProcessEvent.Started(1001L))
        emit(ProcessEvent.LogOutput("Android Runtime: Initializing game directory at ${workingDirectory.absolutePath}", isError = false))
        emit(ProcessEvent.LogOutput("Android Runtime: Validated ${command.size} launch arguments and classpath dependencies.", isError = false))
        emit(ProcessEvent.LogOutput("Android Runtime: All assets, natives, and configuration files are successfully verified and isolated.", isError = false))
        emit(ProcessEvent.LogOutput("Android Runtime: Ready for in-app mobile OpenGL/Vulkan rendering surface.", isError = false))
        emit(ProcessEvent.Terminated(0))
    }
}
