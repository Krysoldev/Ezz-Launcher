package io.ezz.launcher.core.minecraft.options

import io.ezz.launcher.core.model.instance.PerformanceProfile
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinecraftOptionsManagerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_options_test", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testReadAndPreserveExistingOptions() {
        val optionsFile = File(tempDir, "options.txt")
        optionsFile.writeText(
            """
            version:3465
            lang:en_us
            soundCategory_master:0.8
            key_key.forward:key.keyboard.w
            renderDistance:16
            """.trimIndent()
        )

        val options = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("3465", options["version"])
        assertEquals("en_us", options["lang"])
        assertEquals("0.8", options["soundCategory_master"])
        assertEquals("key.keyboard.w", options["key_key.forward"])
        assertEquals("16", options["renderDistance"])
    }

    @Test
    fun testApplyBalancedProfilePreservesCustomKeys() {
        val optionsFile = File(tempDir, "options.txt")
        optionsFile.writeText(
            """
            lang:en_us
            key_key.forward:key.keyboard.w
            soundCategory_music:0.5
            """.trimIndent()
        )

        MinecraftOptionsManager.applyPerformanceProfile(tempDir, PerformanceProfile.BALANCED)

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("en_us", updated["lang"])
        assertEquals("key.keyboard.w", updated["key_key.forward"])
        assertEquals("0.5", updated["soundCategory_music"])

        assertEquals("12", updated["renderDistance"])
        assertEquals("10", updated["simulationDistance"])
        assertEquals("2", updated["graphicsMode"])
        assertEquals("260", updated["maxFps"])
        assertEquals("false", updated["enableVsync"])
    }

    @Test
    fun testApplyDisplayLimitFpsMode() {
        MinecraftOptionsManager.applyPerformanceProfile(
            gameDir = tempDir,
            profile = PerformanceProfile.PERFORMANCE,
            fpsMode = io.ezz.launcher.core.model.instance.FpsMode.DISPLAY_LIMIT,
            displayRefreshRate = 144
        )

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("144", updated["maxFps"])
        assertEquals("false", updated["enableVsync"])
        assertEquals("10", updated["renderDistance"])
    }

    @Test
    fun testApplyCustomFpsMode() {
        MinecraftOptionsManager.applyPerformanceProfile(
            gameDir = tempDir,
            profile = PerformanceProfile.MAX_FPS,
            fpsMode = io.ezz.launcher.core.model.instance.FpsMode.CUSTOM,
            customFpsLimit = 360
        )

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("360", updated["maxFps"])
        assertEquals("false", updated["enableVsync"])
    }

    @Test
    fun testApplyMaxFpsProfile() {
        MinecraftOptionsManager.applyPerformanceProfile(tempDir, PerformanceProfile.MAX_FPS)

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("8", updated["renderDistance"])
        assertEquals("6", updated["simulationDistance"])
        assertEquals("1", updated["graphicsMode"])
        assertEquals("260", updated["maxFps"])
        assertEquals("false", updated["enableVsync"])
        assertEquals("0", updated["ao"])
        assertEquals("false", updated["entityShadows"])
        assertEquals("0.5", updated["entityDistanceScaling"])
    }

    @Test
    fun testApplyExtremeFpsProfile() {
        MinecraftOptionsManager.applyPerformanceProfile(tempDir, PerformanceProfile.EXTREME_FPS)

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("8", updated["renderDistance"])
        assertEquals("5", updated["simulationDistance"])
        assertEquals("1", updated["graphicsMode"])
        assertEquals("260", updated["maxFps"])
        assertEquals("false", updated["enableVsync"])
        assertEquals("0", updated["ao"])
        assertEquals("false", updated["entityShadows"])
        assertEquals("0.5", updated["entityDistanceScaling"])
        assertEquals("0", updated["biomeBlendRadius"])
        assertEquals("0", updated["mipmapLevels"])
    }

    @Test
    fun testDefaultProfileLeavesOptionsUntouched() {
        val optionsFile = File(tempDir, "options.txt")
        optionsFile.writeText("renderDistance:32\nenableVsync:true\n")

        MinecraftOptionsManager.applyPerformanceProfile(
            gameDir = tempDir,
            profile = PerformanceProfile.DEFAULT,
            fpsMode = io.ezz.launcher.core.model.instance.FpsMode.DEFAULT
        )

        val updated = MinecraftOptionsManager.readOptions(tempDir)
        assertEquals("32", updated["renderDistance"])
        assertEquals("true", updated["enableVsync"])
    }

    @Test
    fun testHasActiveShaders() {
        val optionsFile = File(tempDir, "options.txt")
        optionsFile.writeText("shaderPack:ComplementaryReimagined.zip\n")
        assertTrue(MinecraftOptionsManager.hasActiveShaders(tempDir))

        optionsFile.writeText("shaderPack:OFF\n")
        assertFalse(MinecraftOptionsManager.hasActiveShaders(tempDir))
    }
}
