package io.ezz.launcher.core.runtime.diagnostics

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.runtime.JavaRuntime
import java.io.File

enum class CrashCategory(val title: String) {
    NATIVE_LWJGL("Native LWJGL / JNI Incompatibility"),
    JAVA_RUNTIME_MISMATCH("Java Runtime Version Mismatch"),
    MOD_CONFLICT("Mod Conflict / Incompatible Mixin"),
    OUT_OF_MEMORY("Memory / Heap Exhaustion"),
    GPU_DRIVER("GPU Driver / OpenGL Crash"),
    MISSING_DEPENDENCY("Missing Mod Dependency"),
    CORRUPTED_FILE("Corrupted Installation File"),
    UNKNOWN("Minecraft Game Crash")
}

data class CrashDiagnosis(
    val category: CrashCategory,
    val summary: String,
    val recommendation: String,
    val details: List<String> = emptyList(),
    val problematicFrame: String? = null,
    val reportFilePath: String? = null
)

object CrashDiagnosticAnalyzer {

    fun analyzeCrash(
        gameDir: File,
        instance: Instance,
        runtime: JavaRuntime,
        exitCode: Int
    ): CrashDiagnosis {
        val rootDir = gameDir.parentFile ?: gameDir

        // 1. Check for JVM HotSpot native crash report (hs_err_pid*.log)
        val hsErrFiles = (gameDir.listFiles() ?: emptyArray())
            .plus(rootDir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.startsWith("hs_err_pid") && it.name.endsWith(".log") }
            .sortedByDescending { it.lastModified() }

        val latestHsErr = hsErrFiles.firstOrNull()
        if (latestHsErr != null && (System.currentTimeMillis() - latestHsErr.lastModified()) < 120_000L) {
            val content = try { latestHsErr.readText() } catch (_: Exception) { "" }
            if (content.contains("EXCEPTION_ACCESS_VIOLATION", ignoreCase = true) ||
                content.contains("org.lwjgl", ignoreCase = true) ||
                content.contains("Unsupported JNI version", ignoreCase = true) ||
                content.contains("jvm.dll", ignoreCase = true) ||
                content.contains("lwjgl", ignoreCase = true)
            ) {
                val lines = content.lines()
                var problematicFrame: String? = null
                val probFrameIdx = lines.indexOfFirst { it.contains("Problematic frame", ignoreCase = true) }
                if (probFrameIdx != -1) {
                    val candidateLine = lines[probFrameIdx].substringAfter(":", "").trim()
                    if (candidateLine.isNotBlank()) {
                        problematicFrame = candidateLine
                    } else if (probFrameIdx + 1 < lines.size) {
                        problematicFrame = lines[probFrameIdx + 1].trim().removePrefix("#").trim()
                    }
                }

                if (runtime.majorVersion >= 23) {
                    return CrashDiagnosis(
                        category = CrashCategory.NATIVE_LWJGL,
                        summary = "Native crash in LWJGL / JNI subsystem caused by running Minecraft on Java ${runtime.majorVersion} (${runtime.fullVersion}).",
                        recommendation = "Minecraft ${instance.minecraftVersion} officially requires Java 21. Java ${runtime.majorVersion} is incompatible with LWJGL 3.3.3 natives. Switch to a 64-bit Java 21 runtime (such as Eclipse Temurin 21 or Microsoft OpenJDK 21) in Instance Settings.",
                        details = listOfNotNull(
                            "Crash report: ${latestHsErr.absolutePath}",
                            problematicFrame?.let { "Problematic frame: $it" },
                            "Exit code: $exitCode"
                        ),
                        problematicFrame = problematicFrame,
                        reportFilePath = latestHsErr.absolutePath
                    )
                }

                return CrashDiagnosis(
                    category = CrashCategory.NATIVE_LWJGL,
                    summary = "Native memory access violation (0xc0000005) in graphics/LWJGL subsystem.",
                    recommendation = "Verify GPU drivers and ensure 64-bit Java 21 is selected for Minecraft 1.20.5+ / 1.21.x.",
                    details = listOfNotNull(
                        "Crash report: ${latestHsErr.absolutePath}",
                        problematicFrame?.let { "Problematic frame: $it" }
                    ),
                    problematicFrame = problematicFrame,
                    reportFilePath = latestHsErr.absolutePath
                )
            }
        }

        // 2. Check crash-reports/ directory
        val crashReportsDir = File(gameDir, "crash-reports")
        if (crashReportsDir.exists()) {
            val latestReport = crashReportsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.maxByOrNull { it.lastModified() }

            if (latestReport != null && (System.currentTimeMillis() - latestReport.lastModified()) < 120_000L) {
                val content = try { latestReport.readText() } catch (_: Exception) { "" }
                return parseCrashReport(content, latestReport.absolutePath, instance, runtime)
            }
        }

        // 3. Check logs/latest.log
        val latestLogFile = File(File(gameDir, "logs"), "latest.log")
        if (latestLogFile.exists()) {
            val lines = try { latestLogFile.readLines().takeLast(100) } catch (_: Exception) { emptyList() }
            val logContent = lines.joinToString("\n")

            if (logContent.contains("Unsupported JNI version detected", ignoreCase = true) ||
                logContent.contains("UnsupportedClassVersionError", ignoreCase = true)
            ) {
                return CrashDiagnosis(
                    category = CrashCategory.JAVA_RUNTIME_MISMATCH,
                    summary = "Java version incompatibility detected during game initialization.",
                    recommendation = "Use Java 21 for Minecraft 1.21.x instances. Higher unreleased versions (such as Java 26) cause native JNI incompatibilities.",
                    details = listOf("Detected Java: ${runtime.fullVersion}"),
                    reportFilePath = latestLogFile.absolutePath
                )
            }

            if (logContent.contains("OutOfMemoryError", ignoreCase = true) ||
                logContent.contains("Java heap space", ignoreCase = true)
            ) {
                return CrashDiagnosis(
                    category = CrashCategory.OUT_OF_MEMORY,
                    summary = "Minecraft ran out of allocated Java heap memory.",
                    recommendation = "Increase allocated RAM in Instance Settings (4096 MB - 6144 MB recommended).",
                    details = listOf("Allocated RAM: ${instance.maxMemoryMb} MB"),
                    reportFilePath = latestLogFile.absolutePath
                )
            }

            if (logContent.contains("MixinApplyError", ignoreCase = true) ||
                logContent.contains("IncompatibleModException", ignoreCase = true) ||
                logContent.contains("Could not find required mod", ignoreCase = true)
            ) {
                return CrashDiagnosis(
                    category = CrashCategory.MOD_CONFLICT,
                    summary = "Mod compatibility or dependency issue detected.",
                    recommendation = "Check your mods folder for duplicate, incompatible, or missing dependency mods.",
                    details = lines.filter { it.contains("Exception", ignoreCase = true) || it.contains("Error", ignoreCase = true) }.take(5),
                    reportFilePath = latestLogFile.absolutePath
                )
            }
        }

        // Default diagnosis
        return CrashDiagnosis(
            category = CrashCategory.UNKNOWN,
            summary = "Minecraft exited unexpectedly with exit code $exitCode.",
            recommendation = "Verify that compatible mods and Java 21 are selected for this instance.",
            details = listOf("Exit code: $exitCode", "Java: ${runtime.fullVersion}")
        )
    }

    private fun parseCrashReport(
        content: String,
        reportPath: String,
        instance: Instance,
        runtime: JavaRuntime
    ): CrashDiagnosis {
        return when {
            content.contains("OutOfMemoryError", ignoreCase = true) -> CrashDiagnosis(
                category = CrashCategory.OUT_OF_MEMORY,
                summary = "Out of Memory: Minecraft exceeded its maximum allocated heap.",
                recommendation = "Increase memory limit in Instance Settings (4 GB - 6 GB recommended).",
                reportFilePath = reportPath
            )
            content.contains("UnsupportedClassVersionError", ignoreCase = true) ||
            content.contains("has been compiled by a more recent version of the Java Runtime", ignoreCase = true) -> CrashDiagnosis(
                category = CrashCategory.JAVA_RUNTIME_MISMATCH,
                summary = "Java Runtime mismatch: Mod or game class was compiled for a different Java version.",
                recommendation = "Minecraft 1.21.x requires Java 21. Select Java 21 in Instance Settings.",
                reportFilePath = reportPath
            )
            content.contains("net.fabricmc.loader.impl.FormattedException", ignoreCase = true) ||
            content.contains("IncompatibleModException", ignoreCase = true) ||
            content.contains("DuplicateModsException", ignoreCase = true) -> CrashDiagnosis(
                category = CrashCategory.MOD_CONFLICT,
                summary = "Fabric Loader detected mod conflicts or missing dependencies.",
                recommendation = "Review installed mods in the Mods tab and remove conflicting or duplicate versions.",
                reportFilePath = reportPath
            )
            content.contains("nvoglv64", ignoreCase = true) ||
            content.contains("amdocl64", ignoreCase = true) ||
            content.contains("igc64", ignoreCase = true) -> CrashDiagnosis(
                category = CrashCategory.GPU_DRIVER,
                summary = "GPU Graphics Driver crash encountered during rendering.",
                recommendation = "Update your GPU graphics drivers (NVIDIA / AMD / Intel) and ensure dedicated GPU is selected.",
                reportFilePath = reportPath
            )
            else -> CrashDiagnosis(
                category = CrashCategory.UNKNOWN,
                summary = "Minecraft crash report generated: " + content.lines().firstOrNull { it.isNotBlank() },
                recommendation = "Review the crash report details for specific mod or environment errors.",
                reportFilePath = reportPath
            )
        }
    }
}
