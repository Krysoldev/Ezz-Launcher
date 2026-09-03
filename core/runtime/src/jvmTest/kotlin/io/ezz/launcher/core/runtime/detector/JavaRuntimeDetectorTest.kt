package io.ezz.launcher.core.runtime.detector

import io.ezz.launcher.core.model.runtime.JavaRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaRuntimeDetectorTest {

    @Test
    fun testRequiredJavaVersions() {
        assertEquals(26, JavaRuntimeDetector.getRequiredJavaMajorVersion("26.0"))
        assertEquals(25, JavaRuntimeDetector.getRequiredJavaMajorVersion("25.0"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21.11"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21.4"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21.1"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.20.6"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.20.5"))
        assertEquals(17, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.20.4"))
        assertEquals(17, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.20.1"))
        assertEquals(17, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.18.2"))
        assertEquals(16, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.17.1"))
        assertEquals(8, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.16.5"))
        assertEquals(8, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.12.2"))
        assertEquals(8, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.8.9"))
    }

    @Test
    fun testRuntimeCompatibilityCheck() {
        val java21 = JavaRuntime(path = "C:\\jdk21\\java.exe", majorVersion = 21, fullVersion = "21.0.6", vendor = "Temurin", is64Bit = true)
        val java26 = JavaRuntime(path = "C:\\jdk26\\java.exe", majorVersion = 26, fullVersion = "26.0.2", vendor = "Oracle", is64Bit = true)
        val java17 = JavaRuntime(path = "C:\\jdk17\\java.exe", majorVersion = 17, fullVersion = "17.0.9", vendor = "Microsoft", is64Bit = true)

        val (compat21, _) = JavaRuntimeDetector.checkRuntimeCompatibility(java21, "1.21.11")
        assertTrue(compat21, "Java 21 should be compatible with Minecraft 1.21.11")

        val (compat26, reason26) = JavaRuntimeDetector.checkRuntimeCompatibility(java26, "1.21.11")
        assertFalse(compat26, "Java 26 should be flagged as incompatible with Minecraft 1.21.11")
        assertTrue(reason26.contains("LWJGL", ignoreCase = true))

        val (compat17, _) = JavaRuntimeDetector.checkRuntimeCompatibility(java17, "1.20.1")
        assertTrue(compat17, "Java 17 should be compatible with Minecraft 1.20.1")
    }

    @Test
    fun testParse64BitVersionOutput() {
        val output64 = """
            openjdk version "21.0.6" 2025-01-21
            OpenJDK Runtime Environment Temurin-21.0.6+7 (build 21.0.6+7)
            OpenJDK 64-Bit Server VM Temurin-21.0.6+7 (build 21.0.6+7, mixed mode, sharing)
        """.trimIndent()

        val parsed = JavaRuntimeDetector.parseJavaVersionOutput("C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe", output64)
        assertEquals(21, parsed.majorVersion)
        assertEquals("21.0.6", parsed.fullVersion)
        assertEquals("Eclipse Adoptium (Temurin)", parsed.vendor)
        assertTrue(parsed.is64Bit, "Expected 64-Bit runtime detection")
    }

    @Test
    fun testParse32BitVersionOutput() {
        val output32 = """
            java version "1.8.0_351"
            Java(TM) SE Runtime Environment (build 1.8.0_351-b10)
            Java HotSpot(TM) Client VM (build 25.351-b10, mixed mode, sharing)
        """.trimIndent()

        val parsed = JavaRuntimeDetector.parseJavaVersionOutput("C:\\Program Files (x86)\\Java\\jre1.8.0_351\\bin\\java.exe", output32)
        assertEquals(8, parsed.majorVersion)
        assertFalse(parsed.is64Bit, "Expected 32-Bit runtime detection")
    }

    @Test
    fun testFindBestRuntimePrefers64Bit() {
        val runtimes = listOf(
            JavaRuntime(path = "C:\\jdk-21-x86\\bin\\java.exe", majorVersion = 21, fullVersion = "21.0.1", vendor = "Oracle", is64Bit = false),
            JavaRuntime(path = "C:\\jdk-21-x64\\bin\\java.exe", majorVersion = 21, fullVersion = "21.0.6", vendor = "Eclipse Adoptium", is64Bit = true),
            JavaRuntime(path = "C:\\jdk-17-x64\\bin\\java.exe", majorVersion = 17, fullVersion = "17.0.9", vendor = "Microsoft", is64Bit = true),
            JavaRuntime(path = "C:\\jdk-8-x64\\bin\\java.exe", majorVersion = 8, fullVersion = "1.8.0_351", vendor = "Oracle", is64Bit = true)
        )

        val best21 = JavaRuntimeDetector.findBestRuntime("1.21.4", runtimes)
        assertEquals(21, best21.majorVersion)
        assertTrue(best21.is64Bit, "Must select 64-bit runtime")
        assertEquals("Eclipse Adoptium", best21.vendor)
    }

    @Test
    fun testSystemMemoryInfoCalculation() {
        val memoryInfo = JavaRuntimeDetector.getSystemMemoryInfo()
        assertTrue(memoryInfo.totalRamMb >= 1024, "Total RAM should be at least 1GB")
        assertTrue(memoryInfo.recommendedMinMb >= 1024, "Recommended min RAM should be at least 1GB")
        assertTrue(memoryInfo.recommendedMaxMb >= memoryInfo.recommendedMinMb, "Max RAM should be >= Min RAM")
    }
}
