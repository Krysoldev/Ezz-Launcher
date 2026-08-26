package io.ezz.launcher.core.model.runtime

import kotlinx.serialization.Serializable

@Serializable
data class JavaRuntime(
    val path: String,
    val majorVersion: Int,
    val fullVersion: String,
    val vendor: String = "",
    val is64Bit: Boolean = true
)

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Preparing(val stage: String, val progress: Float? = null) : ProcessState
    data class Running(val processId: Long? = null) : ProcessState
    data class Exited(val exitCode: Int, val crashReport: String? = null) : ProcessState
    data class Failed(val error: LaunchError) : ProcessState
}

sealed interface LaunchError {
    val message: String

    data class MissingVersion(val versionId: String) : LaunchError {
        override val message: String get() = "Minecraft version $versionId not found"
    }
    data class MissingJavaRuntime(override val message: String) : LaunchError
    data class IncompatibleJava(val required: Int, val found: Int) : LaunchError {
        override val message: String get() = "Requires Java $required (found Java $found)"
    }
    data class DownloadFailed(override val message: String) : LaunchError
    data class ExecutionFailed(override val message: String, val cause: Throwable? = null) : LaunchError
    data class AccountInvalid(override val message: String) : LaunchError
}

@Serializable
data class LauncherSettings(
    val defaultMinMemoryMb: Int = 1024,
    val defaultMaxMemoryMb: Int = 4096,
    val defaultJavaPath: String? = null,
    val globalJvmArgs: List<String> = listOf(
        "-XX:+UseG1GC",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=20",
        "-XX:G1ReservePercent=20",
        "-XX:MaxGCPauseMillis=50",
        "-XX:G1HeapRegionSize=32M"
    ),
    val closeLauncherOnLaunch: Boolean = false,
    val darkTheme: Boolean = true,
    val selectedInstanceId: String? = null,
    val selectedAccountId: String? = null
)
