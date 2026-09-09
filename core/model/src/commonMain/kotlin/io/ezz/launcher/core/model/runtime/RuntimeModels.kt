package io.ezz.launcher.core.model.runtime

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class JavaRuntime(
    val path: String,
    val majorVersion: Int,
    val fullVersion: String,
    val vendor: String = "",
    val is64Bit: Boolean = true
)

@Serializable
data class InstanceRuntimeSession(
    val instanceId: String,
    val processId: Long,
    val startedAt: Long
)

/**
 * Formats a duration in seconds into HH:MM:SS format (e.g. 00:00:05, 00:23:41, 01:14:09).
 */
fun formatRuntime(seconds: Long): String {
    val totalSecs = seconds.coerceAtLeast(0L)
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

/**
 * Authoritative single source of truth for Minecraft launch progress.
 */
@Serializable
data class LaunchProgressState(
    val operationId: String,
    val instanceId: String,
    val stage: String,
    val status: String,
    val progress: Float = 0f,
    val displayProgress: Float = progress,
    val percentage: Int = 0,
    val completedWork: Long = 0L,
    val totalWork: Long = 0L,
    val isIndeterminate: Boolean = false,
    val isCancellable: Boolean = true,
    val error: String? = null,
    val startedAt: Long = 0L
)

/**
 * Computes display percentage from display progress (0.0f..1.0f).
 * Real operation safety: 100% is strictly reserved for when the launch
 * operation has finished (isFinished is true) or display progress has reached 1.0f.
 */
fun computeDisplayPercentage(displayProgress: Float, isFinished: Boolean): Int {
    val raw = (displayProgress.coerceIn(0f, 1f) * 100f).roundToInt()
    return if (!isFinished && displayProgress < 0.999f) {
        raw.coerceIn(0, 99)
    } else {
        raw.coerceIn(0, 100)
    }
}

/**
 * Dynamic interpolation duration in milliseconds based on the size of the progress jump.
 * Smooth, deliberate, premium progression:
 * - Small jumps (1-2%) catch up smoothly (~120ms)
 * - Medium jumps (3-5%) take ~240ms
 * - Moderate jumps (6-15%) take ~550ms
 * - Noticeable jumps (16-35%, e.g. 16% -> 32%) take ~1000ms (~1 second)
 * - Large jumps (35-65%) take ~1600ms
 * - Massive jumps (65-100%, e.g. 32% -> 100%) take ~2100ms (~2.1 seconds)
 *
 * This allows Chibi Steve to visibly and naturally walk across the progress bar,
 * smoothly visiting intermediate integers (16..32, 32..100) without instant skipping.
 */
fun computeDisplayInterpolationDuration(delta: Float, @Suppress("UNUSED_PARAMETER") isTargetFinal: Boolean = false): Int {
    val d = abs(delta)
    return when {
        d <= 0.001f -> 0
        d <= 0.02f -> 120
        d <= 0.05f -> 240
        d <= 0.15f -> 550
        d <= 0.35f -> 1000
        d <= 0.65f -> 1600
        else -> 2100
    }
}

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Preparing(val stage: String, val progress: Float? = null) : ProcessState
    data class Running(
        val processId: Long? = null,
        val startedAt: Long = 0L
    ) : ProcessState
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
    val hideLauncherWhileRunning: Boolean = true,
    val darkTheme: Boolean = true,
    val selectedInstanceId: String? = null,
    val selectedAccountId: String? = null,
    val autoCheckUpdates: Boolean = true,
    val enableDiscordRpc: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val defaultWindowWidth: Int = 1280,
    val defaultWindowHeight: Int = 720,
    val defaultFullscreen: Boolean = false,
    val maxConcurrentDownloads: Int = 8,
    val downloadRetryAttempts: Int = 3,
    val soundEffectsEnabled: Boolean = false,
    val soundVolume: Float = 0.5f
)
