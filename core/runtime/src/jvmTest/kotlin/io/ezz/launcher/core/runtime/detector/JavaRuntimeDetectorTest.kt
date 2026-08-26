package io.ezz.launcher.core.runtime.detector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaRuntimeDetectorTest {

    @Test
    fun testRequiredJavaVersions() {
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21.4"))
        assertEquals(21, JavaRuntimeDetector.getRequiredJavaMajorVersion("1.21"))
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
    fun testDetectRuntimesNotNull() {
        val detected = JavaRuntimeDetector.detectInstalledRuntimes()
        assertTrue(detected is List<*>)
    }

    @Test
    fun testFindBestRuntimePrefersExactLTSOverFutureJDK() {
        val runtimes = listOf(
            io.ezz.launcher.core.model.runtime.JavaRuntime(path = "C:\\jdk-26\\bin\\java.exe", majorVersion = 26, fullVersion = "26.0.2", vendor = "Oracle"),
            io.ezz.launcher.core.model.runtime.JavaRuntime(path = "C:\\jdk-21\\bin\\java.exe", majorVersion = 21, fullVersion = "21.0.11", vendor = "Eclipse Adoptium"),
            io.ezz.launcher.core.model.runtime.JavaRuntime(path = "C:\\jdk-17\\bin\\java.exe", majorVersion = 17, fullVersion = "17.0.9", vendor = "Microsoft"),
            io.ezz.launcher.core.model.runtime.JavaRuntime(path = "C:\\jdk-8\\bin\\java.exe", majorVersion = 8, fullVersion = "1.8.0_351", vendor = "Oracle")
        )

        val best21 = JavaRuntimeDetector.findBestRuntime("1.21.4", runtimes)
        assertEquals(21, best21.majorVersion)
        assertEquals("Eclipse Adoptium", best21.vendor)

        val best17 = JavaRuntimeDetector.findBestRuntime("1.20.1", runtimes)
        assertEquals(17, best17.majorVersion)

        val best8 = JavaRuntimeDetector.findBestRuntime("1.12.2", runtimes)
        assertEquals(8, best8.majorVersion)
    }
}
