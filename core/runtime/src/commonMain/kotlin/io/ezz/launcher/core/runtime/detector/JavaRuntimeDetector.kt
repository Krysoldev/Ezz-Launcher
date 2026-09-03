package io.ezz.launcher.core.runtime.detector

import io.ezz.launcher.core.model.runtime.JavaRuntime
import java.io.File
import java.lang.management.ManagementFactory

data class SystemMemoryInfo(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val recommendedMinMb: Int,
    val recommendedMaxMb: Int
)

object JavaRuntimeDetector {

    fun detectInstalledRuntimes(): List<JavaRuntime> {
        if (isAndroid()) {
            return listOf(
                JavaRuntime(
                    path = "android_runtime",
                    majorVersion = 21,
                    fullVersion = "Android Runtime (OpenJDK 21 ART)",
                    vendor = "Android OpenJDK",
                    is64Bit = true
                )
            )
        }

        val found = mutableListOf<JavaRuntime>()
        val candidates = mutableSetOf<String>()

        // 1. Current JVM running launcher
        val currentJavaHome = System.getProperty("java.home")
        if (!currentJavaHome.isNullOrBlank()) {
            candidates.add(currentJavaHome)
        }

        // 2. JAVA_HOME & JDK_HOME environment variables
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        System.getenv("JDK_HOME")?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }

        // 3. PATH discovery
        val pathEnv = System.getenv("PATH") ?: ""
        val pathDirs = pathEnv.split(File.pathSeparator)
        for (dir in pathDirs) {
            val javaExe = File(dir, if (isWindows()) "java.exe" else "java")
            if (javaExe.exists() && javaExe.canExecute()) {
                val home = javaExe.parentFile?.parentFile?.absolutePath
                if (home != null) candidates.add(home)
                candidates.add(javaExe.absolutePath)
            }
        }

        // 4. Standard OS Directories
        val userHome = System.getProperty("user.home") ?: "."
        val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome\\AppData\\Local"
        val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"

        val standardRoots = mutableListOf(
            "$programFiles\\Java",
            "$programFiles\\Eclipse Adoptium",
            "$programFiles\\Microsoft",
            "$programFiles\\BellSoft",
            "$programFiles\\Zulu",
            "$programFiles\\Amazon Corretto",
            "$programFiles\\Semeru",
            "$programFilesX86\\Java",
            "$userHome\\.jdks",
            "$userHome\\.sdkman\\candidates\\java",
            "$appData\\.minecraft\\runtime",
            "$localAppData\\Packages\\Microsoft.4297127D64C94_8wekyb3d8bbwe\\LocalCache\\Local\\runtime",
            "/usr/lib/jvm",
            "/Library/Java/JavaVirtualMachines",
            "/opt/jdk"
        )

        for (rootPath in standardRoots) {
            val rootDir = File(rootPath)
            if (rootDir.exists() && rootDir.isDirectory) {
                rootDir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory) {
                        candidates.add(subDir.absolutePath)
                        // Also check 1 level deeper (e.g. runtime/java-runtime-gamma/windows-x64/java-runtime-gamma)
                        subDir.listFiles()?.filter { it.isDirectory }?.forEach { deepSub ->
                            candidates.add(deepSub.absolutePath)
                        }
                    }
                }
            }
        }

        for (candidate in candidates) {
            val runtime = inspectJavaHome(candidate)
            if (runtime != null && found.none { it.path.equals(runtime.path, ignoreCase = true) }) {
                found.add(runtime)
            }
        }

        // Sort prioritizing 64-Bit and then highest major version
        return found.sortedWith(
            compareByDescending<JavaRuntime> { it.is64Bit }
                .thenByDescending { it.majorVersion }
        )
    }

    fun inspectJavaHome(dirOrBinaryPath: String): JavaRuntime? {
        val file = File(dirOrBinaryPath)
        if (!file.exists()) return null

        val javaBinary = when {
            file.isFile && (file.name.equals("java.exe", ignoreCase = true) || file.name.equals("java", ignoreCase = true)) -> {
                file
            }
            isWindows() -> {
                val binJava = File(file, "bin\\java.exe")
                if (binJava.exists()) binJava else File(file, "java.exe")
            }
            else -> {
                val binJava = File(file, "bin/java")
                if (binJava.exists()) binJava else File(file, "java")
            }
        }

        if (!javaBinary.exists() || !javaBinary.canExecute()) return null

        return try {
            val process = ProcessBuilder(javaBinary.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            parseJavaVersionOutput(javaBinary.absolutePath, output)
        } catch (e: Exception) {
            null
        }
    }

    fun parseJavaVersionOutput(binaryPath: String, output: String): JavaRuntime {
        var major = 8
        var full = "Unknown"
        var is64 = output.contains("64-Bit", ignoreCase = true) ||
                output.contains("x86_64", ignoreCase = true) ||
                output.contains("amd64", ignoreCase = true) ||
                output.contains("aarch64", ignoreCase = true) ||
                output.contains("arm64", ignoreCase = true)

        if (output.contains("32-Bit", ignoreCase = true) || output.contains("i386", ignoreCase = true) || output.contains("i686", ignoreCase = true)) {
            is64 = false
        }

        val versionRegex = Regex("""(?:java|openjdk) version "([^"]+)"""")
        val match = versionRegex.find(output)
        if (match != null) {
            full = match.groupValues[1]
            major = parseMajorVersion(full)
        } else {
            val line = output.lines().firstOrNull() ?: ""
            full = line.trim()
            major = parseMajorVersion(line)
        }

        var vendor = "Unknown"
        when {
            output.contains("Temurin", ignoreCase = true) || output.contains("Adoptium", ignoreCase = true) -> vendor = "Eclipse Adoptium (Temurin)"
            output.contains("Microsoft", ignoreCase = true) -> vendor = "Microsoft OpenJDK"
            output.contains("Zulu", ignoreCase = true) -> vendor = "Azul Zulu"
            output.contains("Corretto", ignoreCase = true) -> vendor = "Amazon Corretto"
            output.contains("Liberica", ignoreCase = true) || output.contains("BellSoft", ignoreCase = true) -> vendor = "BellSoft Liberica"
            output.contains("GraalVM", ignoreCase = true) -> vendor = "GraalVM"
            output.contains("HotSpot", ignoreCase = true) -> vendor = "Oracle / OpenJDK"
            output.contains("Semeru", ignoreCase = true) || output.contains("IBM", ignoreCase = true) -> vendor = "IBM Semeru"
        }

        return JavaRuntime(
            path = binaryPath,
            majorVersion = major,
            fullVersion = full,
            vendor = vendor,
            is64Bit = is64
        )
    }

    /**
     * Intelligently selects the best available 64-bit Java runtime matching Minecraft's requirements.
     * Order of preference:
     * 1. 64-Bit exact major version match (e.g. Java 21 for MC 1.21, Java 17 for MC 1.20).
     * 2. 64-Bit closest compatible version (version >= requiredMajor).
     * 3. 64-Bit closest overall.
     * 4. 32-Bit exact/compatible match (with fallback).
     * 5. System PATH fallback.
     */
    fun findBestRuntime(minecraftVersion: String, detected: List<JavaRuntime> = detectInstalledRuntimes()): JavaRuntime {
        val requiredMajor = getRequiredJavaMajorVersion(minecraftVersion)

        if (isAndroid()) {
            return JavaRuntime(
                path = "android_runtime",
                majorVersion = requiredMajor,
                fullVersion = "Android Runtime (OpenJDK $requiredMajor ART)",
                vendor = "Android OpenJDK",
                is64Bit = true
            )
        }

        val runtimes64 = detected.filter { it.is64Bit }

        // 1. 64-bit Exact match
        val exactMatch64 = runtimes64.firstOrNull { it.majorVersion == requiredMajor }
        if (exactMatch64 != null) return exactMatch64

        // 2. Safe tested compatible releases
        val safeCompatible64 = when (requiredMajor) {
            21 -> runtimes64.filter { it.majorVersion in 21..22 }
            17 -> runtimes64.filter { it.majorVersion in 17..21 }
            16 -> runtimes64.filter { it.majorVersion in 16..21 }
            8 -> runtimes64.filter { it.majorVersion in 8..11 }
            else -> runtimes64.filter { it.majorVersion >= requiredMajor }
        }

        val bestSafe64 = safeCompatible64.minByOrNull { kotlin.math.abs(it.majorVersion - requiredMajor) }
        if (bestSafe64 != null) return bestSafe64

        // 3. 64-bit closest overall
        val closest64 = runtimes64.minByOrNull { kotlin.math.abs(it.majorVersion - requiredMajor) }
        if (closest64 != null) return closest64

        // 4. Any detected match (including 32-bit if no 64-bit found)
        val exactAny = detected.firstOrNull { it.majorVersion == requiredMajor }
        if (exactAny != null) return exactAny

        val safeAny = detected.filter {
            when (requiredMajor) {
                21 -> it.majorVersion in 21..22
                17 -> it.majorVersion in 17..21
                16 -> it.majorVersion in 16..21
                8 -> it.majorVersion in 8..11
                else -> it.majorVersion >= requiredMajor
            }
        }.minByOrNull { kotlin.math.abs(it.majorVersion - requiredMajor) }
        if (safeAny != null) return safeAny

        // 5. Fallback PATH command
        val defaultCmd = if (isWindows()) "java.exe" else "java"
        return JavaRuntime(
            path = defaultCmd,
            majorVersion = requiredMajor,
            fullVersion = "System PATH",
            vendor = "System",
            is64Bit = true
        )
    }

    fun parseMajorVersion(versionStr: String): Int {
        val cleaned = versionStr.trim().removePrefix("1.")
        val firstNum = cleaned.takeWhile { it.isDigit() }
        return firstNum.toIntOrNull() ?: 8
    }

    fun getRequiredJavaMajorVersion(minecraftVersion: String): Int {
        return when {
            isAtLeastVersion(minecraftVersion, "26.0") -> 26
            isAtLeastVersion(minecraftVersion, "25.0") -> 25
            isAtLeastVersion(minecraftVersion, "1.20.5") -> 21
            isAtLeastVersion(minecraftVersion, "1.18") -> 17
            isAtLeastVersion(minecraftVersion, "1.17") -> 16
            else -> 8
        }
    }

    fun checkRuntimeCompatibility(runtime: JavaRuntime, minecraftVersion: String): Pair<Boolean, String> {
        val requiredMajor = getRequiredJavaMajorVersion(minecraftVersion)
        return when {
            runtime.majorVersion == requiredMajor -> {
                true to "Compatible ($requiredMajor Recommended)"
            }
            requiredMajor == 21 && runtime.majorVersion == 22 -> {
                true to "Compatible (Java 22)"
            }
            requiredMajor == 21 && runtime.majorVersion >= 23 -> {
                false to "Incompatible: Java ${runtime.majorVersion} causes native LWJGL 3.3.3 JNI crashes. Java 21 is required."
            }
            requiredMajor == 17 && runtime.majorVersion in 17..21 -> {
                true to "Compatible (Java ${runtime.majorVersion})"
            }
            runtime.majorVersion < requiredMajor -> {
                false to "Incompatible: Java $requiredMajor required, but Java ${runtime.majorVersion} detected."
            }
            !runtime.is64Bit -> {
                false to "Warning: 32-bit Java detected. 64-bit Java $requiredMajor required."
            }
            else -> {
                true to "Compatible (Java ${runtime.majorVersion})"
            }
        }
    }

    fun getSystemMemoryInfo(): SystemMemoryInfo {
        var totalMb = 8192
        var freeMb = 4096
        try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val totalPhysicalMemMethod = osBean.javaClass.methods.firstOrNull { it.name == "getTotalPhysicalMemorySize" || it.name == "getTotalMemorySize" }
            val freePhysicalMemMethod = osBean.javaClass.methods.firstOrNull { it.name == "getFreePhysicalMemorySize" || it.name == "getFreeMemorySize" }

            if (totalPhysicalMemMethod != null) {
                val bytes = totalPhysicalMemMethod.invoke(osBean) as? Long ?: 0L
                if (bytes > 0L) totalMb = (bytes / (1024 * 1024)).toInt()
            }
            if (freePhysicalMemMethod != null) {
                val bytes = freePhysicalMemMethod.invoke(osBean) as? Long ?: 0L
                if (bytes > 0L) freeMb = (bytes / (1024 * 1024)).toInt()
            }
        } catch (_: Throwable) {
            // Fallback estimation
        }

        // Calculate safe allocation ceiling: leave at least 2.5 GB for OS/GPU/Background
        val maxSafeAllocation = (totalMb - 2560).coerceAtLeast(2048)
        val recommendedMin = 2048
        val recommendedMax = when {
            totalMb >= 32768 -> 8192
            totalMb >= 16384 -> 6144
            totalMb >= 12288 -> 4096
            totalMb >= 8192 -> 4096
            else -> maxSafeAllocation.coerceAtMost(3072)
        }

        return SystemMemoryInfo(
            totalRamMb = totalMb,
            availableRamMb = freeMb,
            recommendedMinMb = recommendedMin,
            recommendedMaxMb = recommendedMax
        )
    }

    private fun isAtLeastVersion(version: String, target: String): Boolean {
        val vParts = version.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val tParts = target.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

        for (i in 0 until maxOf(vParts.size, tParts.size)) {
            val v = vParts.getOrElse(i) { 0 }
            val t = tParts.getOrElse(i) { 0 }
            if (v > t) return true
            if (v < t) return false
        }
        return true
    }

    fun isAndroid(): Boolean {
        return try {
            Class.forName("android.os.Build")
            true
        } catch (e: Throwable) {
            val vendor = System.getProperty("java.vendor") ?: ""
            val vmVendor = System.getProperty("java.vm.vendor") ?: ""
            vendor.contains("Android", ignoreCase = true) || vmVendor.contains("Android", ignoreCase = true)
        }
    }

    fun isWindows(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
    }
}
