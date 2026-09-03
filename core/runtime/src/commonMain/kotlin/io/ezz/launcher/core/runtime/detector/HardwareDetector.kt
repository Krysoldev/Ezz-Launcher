package io.ezz.launcher.core.runtime.detector

import io.ezz.launcher.core.model.instance.GpuPreference
import io.ezz.launcher.core.model.instance.PerformanceProfile
import java.awt.GraphicsEnvironment
import java.io.File

data class DetailedHardwareProfile(
    val cpuModel: String,
    val cpuCores: Int,
    val totalRamMb: Int,
    val availableRamMb: Int,
    val primaryGpu: String,
    val hasDedicatedGpu: Boolean,
    val allGpus: List<String>,
    val displayResolution: String,
    val displayRefreshRateHz: Int,
    val recommendedMinRamMb: Int,
    val recommendedMaxRamMb: Int,
    val recommendedProfile: PerformanceProfile,
    val recommendedGpuPreference: GpuPreference
)

object HardwareDetector {

    fun detectHardware(): DetailedHardwareProfile {
        val cpuModel = detectCpuModel()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val memInfo = JavaRuntimeDetector.getSystemMemoryInfo()
        val detectedGpus = GpuDetector.detectGpus()
        val gpuNames = detectedGpus.map { it.name }
        val dedicatedGpu = detectedGpus.firstOrNull { it.isDedicated }
        val primaryGpuName = dedicatedGpu?.name ?: detectedGpus.firstOrNull()?.name ?: "Standard Graphics"
        val hasDedicated = dedicatedGpu != null

        val (resString, refreshRate) = detectDisplayInfo()

        // Tailor memory bounds: For 8 GB total RAM, allocate 2048 MB min / 4096 MB max
        val recommendedMin = 2048
        val recommendedMax = when {
            memInfo.totalRamMb >= 32768 -> 8192
            memInfo.totalRamMb >= 16384 -> 6144
            memInfo.totalRamMb >= 12288 -> 4096
            memInfo.totalRamMb >= 8192 -> 4096 // Safe 4GB cap on 8GB machines leaving 4GB for OS/GPU
            else -> (memInfo.totalRamMb - 2048).coerceIn(1536, 3072)
        }

        val recommendedProfile = if (hasDedicated && memInfo.totalRamMb >= 8192) {
            PerformanceProfile.PERFORMANCE
        } else {
            PerformanceProfile.BALANCED
        }

        val recommendedGpuPref = if (hasDedicated) {
            GpuPreference.HIGH_PERFORMANCE
        } else {
            GpuPreference.AUTO
        }

        return DetailedHardwareProfile(
            cpuModel = cpuModel,
            cpuCores = cpuCores,
            totalRamMb = memInfo.totalRamMb,
            availableRamMb = memInfo.availableRamMb,
            primaryGpu = primaryGpuName,
            hasDedicatedGpu = hasDedicated,
            allGpus = gpuNames,
            displayResolution = resString,
            displayRefreshRateHz = refreshRate,
            recommendedMinRamMb = recommendedMin,
            recommendedMaxRamMb = recommendedMax,
            recommendedProfile = recommendedProfile,
            recommendedGpuPreference = recommendedGpuPref
        )
    }

    private fun detectCpuModel(): String {
        // Try reading Windows registry or wmic if on Windows
        if (JavaRuntimeDetector.isWindows()) {
            try {
                val proc = ProcessBuilder("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString")
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                val line = out.lines().firstOrNull { it.contains("ProcessorNameString", ignoreCase = true) }
                if (line != null) {
                    val name = line.substringAfter("REG_SZ").trim()
                    if (name.isNotBlank()) return name
                }
            } catch (_: Exception) {}

            val envIdentifier = System.getenv("PROCESSOR_IDENTIFIER")
            if (!envIdentifier.isNullOrBlank()) return envIdentifier
        }

        // Try reading /proc/cpuinfo on Linux
        try {
            val cpuinfo = File("/proc/cpuinfo")
            if (cpuinfo.exists()) {
                val line = cpuinfo.readLines().firstOrNull { it.startsWith("model name", ignoreCase = true) }
                if (line != null) {
                    val name = line.substringAfter(":").trim()
                    if (name.isNotBlank()) return name
                }
            }
        } catch (_: Exception) {}

        return System.getProperty("os.arch") + " Processor (${Runtime.getRuntime().availableProcessors()} Cores)"
    }

    private fun detectDisplayInfo(): Pair<String, Int> {
        return try {
            if (!GraphicsEnvironment.isHeadless()) {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screen = ge.defaultScreenDevice
                val dm = screen.displayMode
                val w = dm.width
                val h = dm.height
                val rate = if (dm.refreshRate > 0) dm.refreshRate else 60
                "${w}x${h}" to rate
            } else {
                "1920x1080" to 60
            }
        } catch (_: Throwable) {
            "1920x1080" to 60
        }
    }
}
