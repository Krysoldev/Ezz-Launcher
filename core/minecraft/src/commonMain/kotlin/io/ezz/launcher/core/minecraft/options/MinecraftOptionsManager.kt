package io.ezz.launcher.core.minecraft.options

import io.ezz.launcher.core.model.instance.FpsMode
import io.ezz.launcher.core.model.instance.PerformanceProfile
import java.io.File

object MinecraftOptionsManager {

    /**
     * Reads the options.txt file from the instance's .minecraft directory into a key-value map.
     */
    fun readOptions(gameDir: File): Map<String, String> {
        val optionsFile = File(gameDir, "options.txt")
        if (!optionsFile.exists()) return emptyMap()

        val map = mutableMapOf<String, String>()
        try {
            optionsFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val splitIndex = trimmed.indexOf(':')
                    if (splitIndex != -1) {
                        val key = trimmed.substring(0, splitIndex).trim()
                        val value = trimmed.substring(splitIndex + 1).trim()
                        map[key] = value
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore read errors
        }
        return map
    }

    /**
     * Applies performance profile settings to options.txt without overwriting user keybinds, audio, or resource packs.
     * Ensures frame rate is uncapped (maxFps: 260, enableVsync: false) unless explicitly configured by the user.
     */
    fun applyPerformanceProfile(
        gameDir: File,
        profile: PerformanceProfile,
        fpsMode: FpsMode = FpsMode.UNLIMITED,
        customFpsLimit: Int = 260,
        displayRefreshRate: Int = 144
    ) {
        if (profile == PerformanceProfile.DEFAULT && fpsMode == FpsMode.DEFAULT) return

        val optionsFile = File(gameDir, "options.txt")
        val currentOptions = readOptions(gameDir).toMutableMap()

        val profileSettings = when (profile) {
            PerformanceProfile.DEFAULT -> emptyMap()
            PerformanceProfile.BALANCED -> mapOf(
                "renderDistance" to "12",
                "simulationDistance" to "10",
                "graphicsMode" to "2",
                "particles" to "0",
                "biomeBlendRadius" to "3",
                "ao" to "2",
                "entityShadows" to "true",
                "entityDistanceScaling" to "1.0",
                "renderClouds" to "true"
            )
            PerformanceProfile.PERFORMANCE -> mapOf(
                "renderDistance" to "10",
                "simulationDistance" to "8",
                "graphicsMode" to "1",
                "particles" to "1",
                "biomeBlendRadius" to "2",
                "ao" to "1",
                "entityShadows" to "false",
                "entityDistanceScaling" to "0.75",
                "renderClouds" to "fast"
            )
            PerformanceProfile.MAX_FPS -> mapOf(
                "renderDistance" to "8",
                "simulationDistance" to "6",
                "graphicsMode" to "1",
                "particles" to "2",
                "biomeBlendRadius" to "0",
                "ao" to "0",
                "entityShadows" to "false",
                "entityDistanceScaling" to "0.5",
                "renderClouds" to "false"
            )
            PerformanceProfile.EXTREME_FPS -> mapOf(
                "renderDistance" to "8",
                "simulationDistance" to "5",
                "graphicsMode" to "1",
                "particles" to "2",
                "biomeBlendRadius" to "0",
                "ao" to "0",
                "entityShadows" to "false",
                "entityDistanceScaling" to "0.5",
                "renderClouds" to "false",
                "mipmapLevels" to "0"
            )
        }

        currentOptions.putAll(profileSettings)

        // Apply Framerate and VSync mode
        when (fpsMode) {
            FpsMode.DEFAULT -> {
                // Keep existing in-game maxFps and enableVsync settings if already present in options.txt
                if (profile != PerformanceProfile.DEFAULT && !currentOptions.containsKey("maxFps")) {
                    currentOptions["maxFps"] = "260"
                    currentOptions["enableVsync"] = "false"
                }
            }
            FpsMode.UNLIMITED -> {
                currentOptions["maxFps"] = "260"
                currentOptions["enableVsync"] = "false"
            }
            FpsMode.DISPLAY_LIMIT -> {
                val refresh = if (displayRefreshRate > 0) displayRefreshRate else 144
                currentOptions["maxFps"] = refresh.toString()
                currentOptions["enableVsync"] = "false"
            }
            FpsMode.CUSTOM -> {
                val custom = customFpsLimit.coerceIn(10, 1000)
                currentOptions["maxFps"] = custom.toString()
                currentOptions["enableVsync"] = "false"
            }
        }

        try {
            gameDir.mkdirs()
            optionsFile.bufferedWriter().use { writer ->
                for ((key, value) in currentOptions) {
                    writer.write("$key:$value\n")
                }
            }
        } catch (_: Exception) {
            // Ignore write errors
        }
    }

    /**
     * Checks if active shaders are enabled in options.txt or Iris config.
     */
    fun hasActiveShaders(gameDir: File): Boolean {
        val options = readOptions(gameDir)
        val shaderPack = options["shaderPack"]
        if (!shaderPack.isNullOrBlank() && !shaderPack.equals("OFF", ignoreCase = true) && !shaderPack.equals("none", ignoreCase = true)) {
            return true
        }

        val irisFile = File(gameDir, "optionsiris.txt")
        if (irisFile.exists()) {
            try {
                val lines = irisFile.readLines()
                for (line in lines) {
                    if (line.startsWith("shaderPack=", ignoreCase = true)) {
                        val pack = line.substringAfter("=").trim()
                        if (pack.isNotBlank() && !pack.equals("OFF", ignoreCase = true) && !pack.equals("none", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }
}
