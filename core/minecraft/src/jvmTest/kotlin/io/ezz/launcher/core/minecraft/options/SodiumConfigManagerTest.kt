package io.ezz.launcher.core.minecraft.options

import io.ezz.launcher.core.model.instance.PerformanceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SodiumConfigManagerTest {

    private lateinit var tempGameDir: File

    @BeforeTest
    fun setUp() {
        tempGameDir = File.createTempFile("ezz_sodium_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        tempGameDir.deleteRecursively()
    }

    @Test
    fun testOptimizeSodiumConfigForExtremeFps() {
        SodiumConfigManager.optimizeSodiumConfig(tempGameDir, PerformanceProfile.EXTREME_FPS)

        val configFile = File(File(tempGameDir, "config"), "sodium-options.json")
        assertTrue(configFile.exists(), "Sodium config file should be created")

        val rootObj = Json.parseToJsonElement(configFile.readText()).jsonObject
        val quality = rootObj["quality"]?.jsonObject
        val performance = rootObj["performance"]?.jsonObject

        assertEquals("FAST", quality?.get("weather_quality")?.jsonPrimitive?.content)
        assertEquals("FAST", quality?.get("leaves_quality")?.jsonPrimitive?.content)
        assertEquals("LOW", quality?.get("particle_quality")?.jsonPrimitive?.content)
        assertEquals("OFF", quality?.get("smooth_lighting")?.jsonPrimitive?.content)
        assertEquals("true", performance?.get("animate_only_visible_textures")?.jsonPrimitive?.content)
        assertEquals("true", performance?.get("always_defer_chunk_updates")?.jsonPrimitive?.content)
    }

    @Test
    fun testDefaultAndBalancedDoNotModifySodiumConfig() {
        SodiumConfigManager.optimizeSodiumConfig(tempGameDir, PerformanceProfile.DEFAULT)
        val configFile = File(File(tempGameDir, "config"), "sodium-options.json")
        assertFalse(configFile.exists(), "Sodium config should not be created for Default profile")

        SodiumConfigManager.optimizeSodiumConfig(tempGameDir, PerformanceProfile.BALANCED)
        assertFalse(configFile.exists(), "Sodium config should not be created for Balanced profile")
    }
}
