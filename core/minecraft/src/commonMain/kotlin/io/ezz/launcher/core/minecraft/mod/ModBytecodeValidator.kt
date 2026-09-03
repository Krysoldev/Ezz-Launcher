package io.ezz.launcher.core.minecraft.mod

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

sealed class ModCompatibilityResult {
    data object Compatible : ModCompatibilityResult()
    data class Incompatible(
        val modName: String,
        val maxMajorVersionFound: Int,
        val requiredJavaVersion: Int,
        val currentJavaVersion: Int
    ) : ModCompatibilityResult() {
        val errorMessage: String
            get() = """
                INCOMPATIBLE MOD DETECTED
                
                $modName requires a newer Java runtime.
                
                Selected Java:
                Java $currentJavaVersion
                
                Required:
                Java $requiredJavaVersion / newer (Class file version $maxMajorVersionFound)
                
                Recommended action:
                Install a Java-$currentJavaVersion-compatible build of this mod or configure a compatible Java runtime.
            """.trimIndent()
    }
}

object ModBytecodeValidator {

    /**
     * Converts Java major class file version to Java release version.
     * e.g. 52 -> 8, 61 -> 17, 65 -> 21, 70 -> 26
     */
    fun classVersionToJavaRelease(majorVersion: Int): Int {
        return if (majorVersion >= 45) {
            majorVersion - 44
        } else {
            majorVersion
        }
    }

    /**
     * Converts Java release version to maximum supported class file major version.
     * e.g. 8 -> 52, 17 -> 61, 21 -> 65, 26 -> 70
     */
    fun javaReleaseToClassVersion(javaVersion: Int): Int {
        return javaVersion + 44
    }

    /**
     * Inspects a JAR stream and determines the maximum class file major version present.
     */
    fun getMaxClassMajorVersion(jarInputStream: InputStream): Int {
        var maxMajor = 0
        ZipInputStream(jarInputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class") && !entry.name.startsWith("META-INF/versions/")) {
                    val header = ByteArray(8)
                    var bytesRead = 0
                    while (bytesRead < 8) {
                        val r = zis.read(header, bytesRead, 8 - bytesRead)
                        if (r == -1) break
                        bytesRead += r
                    }
                    if (bytesRead >= 8) {
                        val buffer = ByteBuffer.wrap(header)
                        val magic = buffer.int
                        if (magic == 0xCAFEBABE.toInt()) {
                            val minor = buffer.short
                            val major = buffer.short.toInt() and 0xFFFF
                            if (major > maxMajor) {
                                maxMajor = major
                            }
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
        return maxMajor
    }

    /**
     * Validates if the given JAR bytes are compatible with the target Java runtime version.
     */
    fun validateJarBytes(modName: String, jarBytes: ByteArray, javaMajorVersion: Int): ModCompatibilityResult {
        if (jarBytes.isEmpty()) return ModCompatibilityResult.Compatible
        val maxClassVersion = getMaxClassMajorVersion(ByteArrayInputStream(jarBytes))
        if (maxClassVersion == 0) return ModCompatibilityResult.Compatible

        val currentMaxSupportedClassVersion = javaReleaseToClassVersion(javaMajorVersion)
        if (maxClassVersion > currentMaxSupportedClassVersion) {
            val requiredJava = classVersionToJavaRelease(maxClassVersion)
            return ModCompatibilityResult.Incompatible(
                modName = modName,
                maxMajorVersionFound = maxClassVersion,
                requiredJavaVersion = requiredJava,
                currentJavaVersion = javaMajorVersion
            )
        }

        return ModCompatibilityResult.Compatible
    }

    /**
     * Validates if a JAR file is compatible with the target Java runtime version.
     */
    fun validateJarFile(jarFile: File, javaMajorVersion: Int): ModCompatibilityResult {
        if (!jarFile.exists() || !jarFile.isFile) return ModCompatibilityResult.Compatible
        return jarFile.inputStream().use { stream ->
            val maxClassVersion = getMaxClassMajorVersion(stream)
            if (maxClassVersion == 0) return@use ModCompatibilityResult.Compatible

            val currentMaxSupportedClassVersion = javaReleaseToClassVersion(javaMajorVersion)
            if (maxClassVersion > currentMaxSupportedClassVersion) {
                val requiredJava = classVersionToJavaRelease(maxClassVersion)
                ModCompatibilityResult.Incompatible(
                    modName = jarFile.name,
                    maxMajorVersionFound = maxClassVersion,
                    requiredJavaVersion = requiredJava,
                    currentJavaVersion = javaMajorVersion
                )
            } else {
                ModCompatibilityResult.Compatible
            }
        }
    }
}
