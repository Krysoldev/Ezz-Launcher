package io.ezz.launcher.core.runtime.diagnostics

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.PerformanceProfile
import io.ezz.launcher.core.model.runtime.JavaRuntime
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PerformanceDiagnosticServiceTest {

    @Test
    fun testGenerateDiagnosticReport() {
        val tempDir = File.createTempFile("ezz_test_game_dir", "").apply {
            delete()
            mkdirs()
        }
        try {
            val optionsFile = File(tempDir, "options.txt")
            optionsFile.writeText("renderDistance:12\nsimulationDistance:8\nenableVsync:false\nmaxFps:260\nentityDistanceScaling:0.75\n")

            val instance = Instance(
                id = "test-instance",
                name = "Fabric 1.21.11 Modded",
                minecraftVersion = "1.21.11",
                loaderType = LoaderType.FABRIC,
                loaderVersion = "0.16.9",
                minMemoryMb = 2048,
                maxMemoryMb = 4096,
                performanceProfile = PerformanceProfile.PERFORMANCE,
                windowWidth = 1280,
                windowHeight = 720
            )

            val runtime = JavaRuntime(
                path = "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.3.9-hotspot\\bin\\javaw.exe",
                majorVersion = 21,
                fullVersion = "21.0.3",
                is64Bit = true,
                vendor = "Eclipse Adoptium"
            )

            val report = PerformanceDiagnosticService.generateDiagnosticReport(
                instance = instance,
                gameDir = tempDir,
                javaRuntime = runtime
            )

            assertNotNull(report.cpuModel)
            assertNotNull(report.gpuName)
            assertNotNull(report.actualMinecraftGpu)
            assertNotNull(report.fpsCapSource)
            assertNotNull(report.sodiumLimiterStatus)
            assertNotNull(report.irisLimiterStatus)
            assertNotNull(report.nvidiaLimiterStatus)
            assertTrue(report.systemTotalRamMb >= 1024)
            assertTrue(report.allocatedMinecraftRamMb == 4096)
            assertTrue(report.formattedReport.contains("EZZ PERFORMANCE REPORT"))
            assertTrue(report.formattedReport.contains("Minecraft Active GPU"))
            assertTrue(report.formattedReport.contains("FPS CAP SOURCE"))
            assertTrue(report.formattedReport.contains("Sodium limiter"))
            assertTrue(report.formattedReport.contains("Iris limiter"))
            assertTrue(report.formattedReport.contains("NVIDIA limiter"))
            assertTrue(report.formattedReport.contains("Actual GPU"))
            assertTrue(report.formattedReport.contains("Display refresh"))
            assertTrue(report.formattedReport.contains("Java Executable"))
            assertTrue(report.formattedReport.contains("Detected Bottleneck"))
            assertTrue(report.formattedReport.contains("Recommended Fix"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
