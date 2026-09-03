package io.ezz.launcher.core.runtime.diagnostics

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.runtime.JavaRuntime
import io.ezz.launcher.core.runtime.detector.GpuDetector
import io.ezz.launcher.core.runtime.detector.HardwareDetector
import io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector
import java.io.File

data class LiveDiagnosticReport(
    val minecraftVersion: String,
    val loaderType: String,
    val javaVersion: String,
    val javaPath: String,
    val is64BitJava: Boolean,
    val cpuModel: String,
    val cpuCores: Int,
    val cpuUsagePct: Int?,
    val gpuName: String,
    val actualMinecraftGpu: String,
    val gpuUtilizationPct: Int?,
    val vramUsedMb: Int?,
    val vramTotalMb: Int?,
    val gpuTempC: Int?,
    val systemTotalRamMb: Int,
    val systemAvailableRamMb: Int,
    val allocatedMinecraftRamMb: Int,
    val resolution: String,
    val refreshRateHz: Int,
    val vsync: Boolean,
    val maxFps: String,
    val renderDistance: Int,
    val simulationDistance: Int,
    val entityDistanceScaling: Double,
    val isSodiumInstalled: Boolean,
    val isIrisInstalled: Boolean,
    val isShadersActive: Boolean,
    val fpsCapSource: String,
    val sodiumLimiterStatus: String,
    val irisLimiterStatus: String,
    val nvidiaLimiterStatus: String,
    val powerSource: String,
    val windowsPowerScheme: String,
    val bottleneck: BottleneckType,
    val bottleneckExplanation: String,
    val recommendedFix: String,
    val isFpsCapped: Boolean,
    val formattedReport: String
)

object PerformanceDiagnosticService {

    fun generateDiagnosticReport(
        instance: Instance,
        gameDir: File,
        javaRuntime: JavaRuntime,
        runningPid: Long? = null
    ): LiveDiagnosticReport {
        val hw = HardwareDetector.detectHardware()
        val mem = JavaRuntimeDetector.getSystemMemoryInfo()

        // 1. GPU Telemetry from nvidia-smi
        var gpuUtil: Int? = null
        var vramUsed: Int? = null
        var vramTotal: Int? = null
        var gpuTemp: Int? = null
        var actualGpu = hw.primaryGpu

        try {
            val proc = ProcessBuilder("nvidia-smi", "--query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu", "--format=csv,noheader")
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (out.isNotBlank()) {
                val parts = out.split(",").map { it.trim() }
                if (parts.size >= 5) {
                    actualGpu = parts[0]
                    gpuUtil = parts[1].removeSuffix("%").trim().toIntOrNull()
                    vramUsed = parts[2].replace("MiB", "").replace("MB", "").trim().toIntOrNull()
                    vramTotal = parts[3].replace("MiB", "").replace("MB", "").trim().toIntOrNull()
                    gpuTemp = parts[4].toIntOrNull()
                }
            }
        } catch (_: Exception) {}

        // Check if Java PID is actively on NVIDIA GPU
        val nvidiaProcessLine = GpuDetector.getActiveNvidiaGpuProcessInfo(runningPid)
        if (nvidiaProcessLine != null) {
            actualGpu = "$actualGpu (Active & Verified via PID ${runningPid ?: "Java"})"
        }

        // 2. Power Source & Windows Scheme
        val powerSource = detectPowerSource()
        val powerScheme = detectPowerScheme()

        // 3. Read In-Game Options
        val optionsFile = File(gameDir, "options.txt")
        val optionsMap = mutableMapOf<String, String>()
        if (optionsFile.exists()) {
            optionsFile.forEachLine { line ->
                val split = line.indexOf(':')
                if (split != -1) {
                    optionsMap[line.substring(0, split).trim()] = line.substring(split + 1).trim()
                }
            }
        }

        val vsync = optionsMap["enableVsync"]?.toBooleanStrictOrNull() ?: false
        val maxFpsVal = optionsMap["maxFps"]?.toIntOrNull() ?: 260
        val maxFpsStr = if (maxFpsVal >= 260 || maxFpsVal <= 0) "Unlimited ($maxFpsVal)" else "$maxFpsVal FPS"
        val renderDist = optionsMap["renderDistance"]?.toIntOrNull() ?: 12
        val simDist = optionsMap["simulationDistance"]?.toIntOrNull() ?: 8
        val entityScaling = optionsMap["entityDistanceScaling"]?.toDoubleOrNull() ?: 1.0

        // 4. Mod & Shader Detection
        val modsDir = File(gameDir, "mods")
        val modFiles = if (modsDir.exists()) modsDir.listFiles()?.map { it.name.lowercase() } ?: emptyList() else emptyList()
        val isSodiumInstalled = modFiles.any { it.contains("sodium") }
        val isIrisInstalled = modFiles.any { it.contains("iris") }
        val isShadersActive = io.ezz.launcher.core.minecraft.options.MinecraftOptionsManager.hasActiveShaders(gameDir)

        // 5. FPS Limiters & Cap Analysis
        val sodiumLimiter = if (isSodiumInstalled) io.ezz.launcher.core.minecraft.options.SodiumConfigManager.getSodiumLimiterStatus(gameDir) else "OFF (Not Installed)"
        val irisLimiter = if (!isIrisInstalled) "OFF (Not Installed)" else if (isShadersActive) "ON (Active Shaders - GPU Overhead)" else "OFF (Shaders Disabled)"
        val nvidiaLimiter = if (powerSource.contains("Battery", ignoreCase = true)) "ON (BatteryBoost Power Throttling)" else "OFF / UNKNOWN"

        val fpsCapSource = when {
            vsync -> "VSync (Synchronized to ${hw.displayRefreshRateHz} Hz monitor refresh rate)"
            maxFpsVal in 1..259 -> "Minecraft options.txt (maxFps: $maxFpsVal)"
            powerSource.contains("Battery", ignoreCase = true) -> "Battery Mode / Windows Power Throttling"
            sodiumLimiter.startsWith("ON") -> "Sodium config ($sodiumLimiter)"
            else -> "None (Uncapped / Unlimited)"
        }

        val isFpsCapped = vsync || (maxFpsVal in 1..259) || (powerSource.contains("Battery", ignoreCase = true))
        val bottleneck: BottleneckType
        val explanation: String
        val recommendation: String

        when {
            isFpsCapped && vsync -> {
                bottleneck = BottleneckType.BALANCED
                explanation = "FPS is synchronized to display refresh rate (${hw.displayRefreshRateHz} Hz) due to VSync."
                recommendation = "Disable VSync in Video Settings to unlock unconstrained rendering."
            }
            isFpsCapped && powerSource.contains("Battery", ignoreCase = true) -> {
                bottleneck = BottleneckType.BALANCED
                explanation = "Laptop is running on Battery mode. Windows and GPU driver enforce power-saving clock throttling."
                recommendation = "Connect AC Power adapter for maximum GPU clocks and performance."
            }
            isShadersActive -> {
                bottleneck = BottleneckType.GPU_BOUND
                explanation = "Iris shaders are active. Post-processing and shadow geometry pass is heavily GPU-bound."
                recommendation = "Disable shaders or use Fast lighting profile if higher frame rates are needed."
            }
            entityScaling > 0.8 && modFiles.size > 50 -> {
                bottleneck = BottleneckType.ENTITY_BOUND
                explanation = "100+ mods / entity tick overhead is limiting single-thread CPU render throughput."
                recommendation = "Enable Ezz Performance Profile to scale Entity Distance to 50%-75% and Simulation Distance to 6."
            }
            gpuUtil != null && gpuUtil >= 95 -> {
                bottleneck = BottleneckType.GPU_BOUND
                explanation = "Dedicated GPU utilization is near 100% capacity."
                recommendation = "Lower in-game resolution or render distance."
            }
            else -> {
                bottleneck = BottleneckType.BALANCED
                explanation = "Render pipeline is operating smoothly across CPU and Dedicated GPU without FPS caps."
                recommendation = "Keep Ezz Performance Mode active for optimal 1% low frame consistency."
            }
        }

        // 6. Format Full Ezz Performance Report
        val report = buildString {
            appendLine("--------------------------------")
            appendLine("EZZ PERFORMANCE REPORT")
            appendLine("--------------------------------")
            appendLine("Minecraft           : ${instance.minecraftVersion}")
            appendLine("Fabric Loader       : ${instance.loaderType.name}")
            appendLine("Java Runtime        : ${javaRuntime.majorVersion} (${if (javaRuntime.is64Bit) "64-Bit" else "32-Bit"})")
            appendLine("Java Executable     : ${javaRuntime.path}")
            appendLine("Sodium Installed    : $isSodiumInstalled")
            appendLine("Iris / Shaders      : ${if (isIrisInstalled) (if (isShadersActive) "Active Shaders" else "Installed (Shaders OFF)") else "Not Installed"}")
            appendLine("")
            appendLine("CPU Model           : ${hw.cpuModel} (${hw.cpuCores} Threads)")
            appendLine("Detected GPU        : ${hw.primaryGpu}")
            appendLine("Minecraft Active GPU: $actualGpu")
            appendLine("System RAM          : ${mem.totalRamMb} MB (${mem.totalRamMb / 1024} GB)")
            appendLine("Allocated MC RAM    : ${instance.minMemoryMb} MB Min / ${instance.maxMemoryMb} MB Max")
            appendLine("")
            appendLine("=== FPS CAP & SYNCHRONIZATION BREAKDOWN ===")
            appendLine("FPS CAP SOURCE      : $fpsCapSource")
            appendLine("VSync               : ${if (vsync) "ON" else "OFF"}")
            appendLine("Minecraft Max FPS   : $maxFpsStr")
            appendLine("Sodium limiter      : $sodiumLimiter")
            appendLine("Iris limiter        : $irisLimiter")
            appendLine("NVIDIA limiter      : $nvidiaLimiter")
            appendLine("Actual GPU          : $actualGpu")
            appendLine("Display refresh     : ${hw.displayRefreshRateHz} Hz")
            appendLine("Resolution          : ${instance.windowWidth}x${instance.windowHeight} (Display: ${hw.displayResolution} @ ${hw.displayRefreshRateHz} Hz)")
            appendLine("")
            appendLine("Render Distance     : $renderDist chunks")
            appendLine("Simulation Distance : $simDist chunks")
            appendLine("Entity Distance     : ${(entityScaling * 100).toInt()}%")
            appendLine("Power Source        : $powerSource ($powerScheme)")
            if (gpuUtil != null) appendLine("GPU Utilization     : $gpuUtil%")
            if (vramUsed != null && vramTotal != null) appendLine("GPU VRAM            : $vramUsed / $vramTotal MB")
            if (gpuTemp != null) appendLine("GPU Temperature     : $gpuTemp°C")
            appendLine("")
            appendLine("Detected Bottleneck : ${bottleneck.title}")
            appendLine("Explanation         : $explanation")
            appendLine("Recommended Fix     : $recommendation")
            appendLine("--------------------------------")
        }

        return LiveDiagnosticReport(
            minecraftVersion = instance.minecraftVersion,
            loaderType = instance.loaderType.name,
            javaVersion = "Java ${javaRuntime.majorVersion} (${javaRuntime.fullVersion})",
            javaPath = javaRuntime.path,
            is64BitJava = javaRuntime.is64Bit,
            cpuModel = hw.cpuModel,
            cpuCores = hw.cpuCores,
            cpuUsagePct = null,
            gpuName = hw.primaryGpu,
            actualMinecraftGpu = actualGpu,
            gpuUtilizationPct = gpuUtil,
            vramUsedMb = vramUsed,
            vramTotalMb = vramTotal,
            gpuTempC = gpuTemp,
            systemTotalRamMb = mem.totalRamMb,
            systemAvailableRamMb = mem.availableRamMb,
            allocatedMinecraftRamMb = instance.maxMemoryMb,
            resolution = "${instance.windowWidth}x${instance.windowHeight}",
            refreshRateHz = hw.displayRefreshRateHz,
            vsync = vsync,
            maxFps = maxFpsStr,
            renderDistance = renderDist,
            simulationDistance = simDist,
            entityDistanceScaling = entityScaling,
            isSodiumInstalled = isSodiumInstalled,
            isIrisInstalled = isIrisInstalled,
            isShadersActive = isShadersActive,
            fpsCapSource = fpsCapSource,
            sodiumLimiterStatus = sodiumLimiter,
            irisLimiterStatus = irisLimiter,
            nvidiaLimiterStatus = nvidiaLimiter,
            powerSource = powerSource,
            windowsPowerScheme = powerScheme,
            bottleneck = bottleneck,
            bottleneckExplanation = explanation,
            recommendedFix = recommendation,
            isFpsCapped = isFpsCapped,
            formattedReport = report
        )
    }

    private fun detectPowerSource(): String {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
        if (!isWindows) return "AC Power"

        return try {
            val proc = ProcessBuilder("powershell", "-Command", "Get-CimInstance -ClassName Win32_Battery | Select-Object -ExpandProperty BatteryStatus")
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            when (out) {
                "1" -> "Discharging (Battery)"
                "2" -> "AC Power (Connected)"
                "3" -> "Fully Charged (AC Power)"
                else -> "AC Power"
            }
        } catch (_: Exception) {
            "AC Power"
        }
    }

    private fun detectPowerScheme(): String {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
        if (!isWindows) return "Standard"

        return try {
            val proc = ProcessBuilder("powercfg", "/getactivescheme")
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            val match = Regex("\\((.*?)\\)").find(out)
            match?.groupValues?.get(1) ?: "Balanced"
        } catch (_: Exception) {
            "Balanced"
        }
    }
}
