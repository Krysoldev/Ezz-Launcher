package io.ezz.launcher.core.runtime.diagnostics

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.runtime.JavaRuntime
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashDiagnosticAnalyzerTest {

    private lateinit var tempGameDir: File
    private lateinit var testInstance: Instance

    @BeforeTest
    fun setUp() {
        tempGameDir = File.createTempFile("ezz_crash_test", "").apply {
            delete()
            mkdirs()
        }
        testInstance = Instance(
            id = "test-instance",
            name = "Test Modrinth Pack",
            minecraftVersion = "1.21.11",
            loaderType = LoaderType.FABRIC
        )
    }

    @AfterTest
    fun tearDown() {
        tempGameDir.deleteRecursively()
    }

    @Test
    fun testAnalyzeNativeLwjglCrashWithJava26() {
        val hsErr = File(tempGameDir, "hs_err_pid1234.log")
        hsErr.writeText(
            """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ff812345678, pid=1234, tid=5678
            #
            # JRE version: Java(TM) SE Runtime Environment (26.0.2) (build 26.0.2+1)
            # Java VM: Java HotSpot(TM) 64-Bit Server VM (build 26.0.2+1, mixed mode, sharing, tiered, compressed oops, g1 gc, windows-amd64)
            # Problematic frame:
            # C  [lwjgl.dll+0x12345]  Java_org_lwjgl_system_JNI_invokePV+0x15
            #
            """.trimIndent()
        )

        val java26 = JavaRuntime(
            path = "C:\\Program Files\\Java\\jdk-26\\bin\\java.exe",
            majorVersion = 26,
            fullVersion = "26.0.2",
            vendor = "Oracle",
            is64Bit = true
        )

        val diagnosis = CrashDiagnosticAnalyzer.analyzeCrash(
            gameDir = tempGameDir,
            instance = testInstance,
            runtime = java26,
            exitCode = -1073741819 // 0xc0000005
        )

        assertEquals(CrashCategory.NATIVE_LWJGL, diagnosis.category)
        assertTrue(diagnosis.recommendation.contains("Java 21", ignoreCase = true))
        assertTrue(diagnosis.summary.contains("LWJGL", ignoreCase = true))
        assertEquals("C  [lwjgl.dll+0x12345]  Java_org_lwjgl_system_JNI_invokePV+0x15", diagnosis.problematicFrame)
    }

    @Test
    fun testAnalyzeOutOfMemoryError() {
        val logDir = File(tempGameDir, "logs").apply { mkdirs() }
        val latestLog = File(logDir, "latest.log")
        latestLog.writeText(
            """
            [12:00:00] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader...
            [12:00:05] [main/ERROR]: java.lang.OutOfMemoryError: Java heap space
            """.trimIndent()
        )

        val java21 = JavaRuntime(
            path = "C:\\jdk21\\java.exe",
            majorVersion = 21,
            fullVersion = "21.0.6",
            vendor = "Temurin",
            is64Bit = true
        )

        val diagnosis = CrashDiagnosticAnalyzer.analyzeCrash(
            gameDir = tempGameDir,
            instance = testInstance,
            runtime = java21,
            exitCode = 1
        )

        assertEquals(CrashCategory.OUT_OF_MEMORY, diagnosis.category)
        assertTrue(diagnosis.recommendation.contains("RAM", ignoreCase = true))
    }
}
