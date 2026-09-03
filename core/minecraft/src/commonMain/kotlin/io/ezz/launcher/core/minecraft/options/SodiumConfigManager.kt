package io.ezz.launcher.core.minecraft.options

import io.ezz.launcher.core.model.instance.PerformanceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Optimizes Sodium rendering configuration (`config/sodium-options.json`) for high-throughput frame rates.
 */
object SodiumConfigManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Optimizes Sodium configuration if Sodium is installed and an aggressive profile is active.
     */
    fun optimizeSodiumConfig(gameDir: File, profile: PerformanceProfile) {
        if (profile == PerformanceProfile.DEFAULT || profile == PerformanceProfile.BALANCED) return

        val configDir = File(gameDir, "config")
        val sodiumConfigFile = File(configDir, "sodium-options.json")

        try {
            configDir.mkdirs()
            val existingObj: JsonObject = if (sodiumConfigFile.exists() && sodiumConfigFile.length() > 0L) {
                try {
                    json.parseToJsonElement(sodiumConfigFile.readText()).jsonObject
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }
            } else {
                JsonObject(emptyMap())
            }

            val qualityObj = existingObj["quality"]?.let {
                try { it.jsonObject } catch (_: Exception) { null }
            } ?: JsonObject(emptyMap())

            val performanceObj = existingObj["performance"]?.let {
                try { it.jsonObject } catch (_: Exception) { null }
            } ?: JsonObject(emptyMap())

            val updatedQuality = buildJsonObject {
                qualityObj.forEach { (k, v) -> put(k, v) }
                put("weather_quality", "FAST")
                put("leaves_quality", "FAST")
                put("particle_quality", "LOW")
                put("smooth_lighting", "OFF")
                put("enable_vignette", false)
            }

            val updatedPerformance = buildJsonObject {
                performanceObj.forEach { (k, v) -> put(k, v) }
                put("animate_only_visible_textures", true)
                put("always_defer_chunk_updates", true)
            }

            val finalConfig = buildJsonObject {
                existingObj.forEach { (k, v) -> put(k, v) }
                put("quality", updatedQuality)
                put("performance", updatedPerformance)
            }

            sodiumConfigFile.writeText(json.encodeToString(JsonObject.serializer(), finalConfig))
        } catch (_: Exception) {
            // Ignore write errors to preserve stability
        }
    }

    /**
     * Inspects Sodium configuration to check if an internal limiter or VSync is active.
     */
    fun getSodiumLimiterStatus(gameDir: File): String {
        val sodiumConfigFile = File(File(gameDir, "config"), "sodium-options.json")
        if (!sodiumConfigFile.exists()) return "OFF (Synced with options.txt)"
        try {
            val rootObj = json.parseToJsonElement(sodiumConfigFile.readText()).jsonObject
            val fpsLimit = rootObj["fps_limit"]?.let {
                try { it.toString().toIntOrNull() } catch (_: Exception) { null }
            }
            if (fpsLimit != null && fpsLimit > 0) {
                return "ON ($fpsLimit FPS)"
            }
            val vsync = rootObj["vsync"]?.let {
                try { it.toString().toBooleanStrictOrNull() } catch (_: Exception) { null }
            }
            if (vsync == true) {
                return "ON (VSync Enabled)"
            }
        } catch (_: Exception) {}
        return "OFF"
    }
}

