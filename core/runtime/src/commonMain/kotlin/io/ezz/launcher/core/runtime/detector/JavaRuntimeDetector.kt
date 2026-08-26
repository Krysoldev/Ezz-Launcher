package io.ezz.launcher.core.runtime.detector

import io.ezz.launcher.core.model.runtime.JavaRuntime
import java.io.File

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

        // 2. JAVA_HOME environment variable
        val envJavaHome = System.getenv("JAVA_HOME")
        if (!envJavaHome.isNullOrBlank()) {
            candidates.add(envJavaHome)
        }

        // 3. PATH discovery
        val pathEnv = System.getenv("PATH") ?: ""
        val pathDirs = pathEnv.split(File.pathSeparator)
        for (dir in pathDirs) {
            val javaExe = File(dir, if (isWindows()) "java.exe" else "java")
            if (javaExe.exists() && javaExe.canExecute()) {
                val home = javaExe.parentFile?.parentFile?.absolutePath
                if (home != null) candidates.add(home)
            }
        }

        // 4. Standard OS Directories
        val userHome = System.getProperty("user.home") ?: "."
        val standardRoots = mutableListOf(
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft",
            "C:\\Program Files\\BellSoft",
            "C:\\Program Files\\Zulu",
            "C:\\Program Files\\Amazon Corretto",
            "$userHome\\.jdks",
            "/usr/lib/jvm",
            "/Library/Java/JavaVirtualMachines"
        )

        for (rootPath in standardRoots) {
            val rootDir = File(rootPath)
            if (rootDir.exists() && rootDir.isDirectory) {
                rootDir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory) {
                        candidates.add(subDir.absolutePath)
                    }
                }
            }
        }

        for (candidate in candidates) {
            val runtime = inspectJavaHome(candidate)
            if (runtime != null && found.none { it.path == runtime.path }) {
                found.add(runtime)
            }
        }

        return found.sortedByDescending { it.majorVersion }
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

    private fun parseJavaVersionOutput(binaryPath: String, output: String): JavaRuntime {
        var major = 8
        var full = "Unknown"
        var is64 = output.contains("64-Bit", ignoreCase = true) || output.contains("x86_64", ignoreCase = true) || output.contains("amd64", ignoreCase = true)
        var vendor = "Unknown"

        val versionRegex = Regex("""(?:java|openjdk) version "([^"]+)"""")
        val match = versionRegex.find(output)
        if (match != null) {
            full = match.groupValues[1]
            major = parseMajorVersion(full)
        } else {
            val line = output.lines().firstOrNull() ?: ""
            full = line
            major = parseMajorVersion(line)
        }

        when {
            output.contains("HotSpot", ignoreCase = true) -> vendor = "Oracle/OpenJDK"
            output.contains("Temurin", ignoreCase = true) || output.contains("Adoptium", ignoreCase = true) -> vendor = "Eclipse Adoptium"
            output.contains("Microsoft", ignoreCase = true) -> vendor = "Microsoft"
            output.contains("Zulu", ignoreCase = true) -> vendor = "Azul Zulu"
            output.contains("Corretto", ignoreCase = true) -> vendor = "Amazon Corretto"
            output.contains("GraalVM", ignoreCase = true) -> vendor = "GraalVM"
        }

        return JavaRuntime(
            path = binaryPath,
            majorVersion = major,
            fullVersion = full,
            vendor = vendor,
            is64Bit = is64
        )
    }

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

        // 1. Exact match (e.g. Java 21 for 1.21.x, Java 17 for 1.20.x, Java 8 for 1.12.x)
        val exactMatch = detected.firstOrNull { it.majorVersion == requiredMajor }
        if (exactMatch != null) return exactMatch

        // 2. Compatible stable release (between requiredMajor and 22, avoiding preview/experimental versions > 22)
        val stableCompatible = detected
            .filter { it.majorVersion >= requiredMajor && it.majorVersion <= 22 }
            .minByOrNull { it.majorVersion }
        if (stableCompatible != null) return stableCompatible

        // 3. Any compatible release (closest to requiredMajor)
        val anyCompatible = detected
            .filter { it.majorVersion >= requiredMajor }
            .minByOrNull { it.majorVersion }
        if (anyCompatible != null) return anyCompatible

        // 4. Closest overall
        val closest = detected.minByOrNull { kotlin.math.abs(it.majorVersion - requiredMajor) }
        if (closest != null) return closest

        // 5. Fallback PATH command
        val defaultCmd = if (isWindows()) "java.exe" else "java"
        return JavaRuntime(
            path = defaultCmd,
            majorVersion = requiredMajor,
            fullVersion = "System PATH",
            vendor = "System"
        )
    }

    fun parseMajorVersion(versionStr: String): Int {
        val cleaned = versionStr.trim().removePrefix("1.")
        val firstNum = cleaned.takeWhile { it.isDigit() }
        return firstNum.toIntOrNull() ?: 8
    }

    fun getRequiredJavaMajorVersion(minecraftVersion: String): Int {
        return when {
            isAtLeastVersion(minecraftVersion, "1.20.5") -> 21
            isAtLeastVersion(minecraftVersion, "1.18") -> 17
            isAtLeastVersion(minecraftVersion, "1.17") -> 16
            else -> 8
        }
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

    private fun isWindows(): Boolean {
        return System.getProperty("os.name")?.lowercase()?.contains("win") ?: false
    }
}
