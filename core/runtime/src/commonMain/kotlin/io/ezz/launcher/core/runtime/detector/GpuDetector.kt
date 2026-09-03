package io.ezz.launcher.core.runtime.detector

import io.ezz.launcher.core.model.instance.GpuPreference
import java.io.File

data class DetectedGpu(
    val name: String,
    val isDedicated: Boolean,
    val vendor: String
)

object GpuDetector {

    private var cachedGpus: List<DetectedGpu>? = null

    fun detectGpus(): List<DetectedGpu> {
        cachedGpus?.let { return it }

        val gpus = mutableListOf<DetectedGpu>()
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") ?: false

        if (isWindows) {
            try {
                val process = ProcessBuilder("wmic", "path", "win32_VideoController", "get", "name")
                    .redirectErrorStream(true)
                    .start()
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor()

                lines.drop(1).map { it.trim() }.filter { it.isNotBlank() }.forEach { name ->
                    val lower = name.lowercase()
                    val isDedicated = lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") ||
                            lower.contains("gtx") || lower.contains("radeon rx") || lower.contains("arc") ||
                            (lower.contains("amd") && !lower.contains("radeon graphics") && !lower.contains("vega"))

                    val vendor = when {
                        lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") || lower.contains("gtx") -> "NVIDIA"
                        lower.contains("amd") || lower.contains("radeon") -> "AMD"
                        lower.contains("intel") -> "Intel"
                        else -> "Unknown"
                    }

                    gpus.add(DetectedGpu(name = name, isDedicated = isDedicated, vendor = vendor))
                }
            } catch (_: Throwable) {
                // Fallback
            }
        }

        if (gpus.isEmpty()) {
            gpus.add(DetectedGpu(name = "System Default GPU", isDedicated = false, vendor = "Auto"))
        }

        cachedGpus = gpus
        return gpus
    }

    /**
     * Builds process environment variables according to the user's GPU preference.
     */
    fun buildGpuEnvironment(preference: GpuPreference): Map<String, String> {
        val env = mutableMapOf<String, String>()

        when (preference) {
            GpuPreference.HIGH_PERFORMANCE -> {
                // Windows DirectX / OpenGL Application Compatibility Shim for dedicated GPU
                env["SHIM_MCCOMPAT"] = "0x800000001"

                // NVIDIA Prime offload (Cross-platform / Windows OpenGL)
                env["__NV_PRIME_RENDER_OFFLOAD"] = "1"
                env["__GLX_VENDOR_LIBRARY_NAME"] = "nvidia"
                env["__VK_LAYER_NV_optimus"] = "1"

                // AMD Radeon environment
                env["DRI_PRIME"] = "1"
                env["GPU_FORCE_64BIT_PTR"] = "1"
                env["GPU_MAX_HEAP_SIZE"] = "100"
                env["GPU_USE_SYNC_OBJECTS"] = "1"
                env["GPU_MAX_ALLOC_PERCENT"] = "100"
                env["GPU_SINGLE_ALLOC_PERCENT"] = "100"
            }
            GpuPreference.POWER_SAVING -> {
                env["DRI_PRIME"] = "0"
                env["__NV_PRIME_RENDER_OFFLOAD"] = "0"
            }
            GpuPreference.AUTO -> {
                // Let OS decide
            }
        }

        return env
    }

    /**
     * Registers the Java binary in Windows User GPU Preferences (HKCU\Software\Microsoft\DirectX\UserGpuPreferences).
     */
    fun ensureWindowsGpuPreference(javaPath: String, preference: GpuPreference) {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
        if (!isWindows || javaPath.isBlank()) return

        try {
            val prefValue = when (preference) {
                GpuPreference.HIGH_PERFORMANCE -> "GpuPreference=2;"
                GpuPreference.POWER_SAVING -> "GpuPreference=1;"
                GpuPreference.AUTO -> "GpuPreference=0;"
            }
            val cleanPath = File(javaPath).absolutePath
            val proc = ProcessBuilder("reg", "add", "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences", "/v", cleanPath, "/t", "REG_SZ", "/d", prefValue, "/f")
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
        } catch (_: Exception) {}
    }

    /**
     * Queries nvidia-smi for active compute/render processes to verify if javaw.exe is active on dedicated GPU.
     */
    fun getActiveNvidiaGpuProcessInfo(pid: Long? = null): String? {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
        if (!isWindows) return null

        return try {
            val proc = ProcessBuilder("nvidia-smi", "--query-compute-apps=pid,process_name,used_memory", "--format=csv,noheader")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (pid != null) {
                output.lines().firstOrNull { it.contains(pid.toString()) }
            } else {
                output.lines().firstOrNull { it.contains("java", ignoreCase = true) }
            }
        } catch (_: Exception) {
            null
        }
    }
}
