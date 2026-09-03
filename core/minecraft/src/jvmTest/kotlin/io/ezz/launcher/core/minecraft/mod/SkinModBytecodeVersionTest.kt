package io.ezz.launcher.core.minecraft.mod

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.assertTrue

class SkinModBytecodeVersionTest {

    @Test
    fun verifyAndRepackageAllSkinModJars() {
        val resourcesDir = File("src/commonMain/resources")
        val classesDir = File("../../ezz-skin-mod/build/classes")

        assertTrue(classesDir.exists(), "Classes directory must exist")
        val jarFiles = resourcesDir.listFiles { _, name -> name.startsWith("ezz-skin-mod") && name.endsWith(".jar") } ?: return
        assertTrue(jarFiles.isNotEmpty(), "Skin mod JAR resources must exist")

        for (jar in jarFiles) {
            val updatedBytes = updateJarWithCompiledClasses(jar, classesDir)
            FileOutputStream(jar).use { it.write(updatedBytes) }
            println("Repackaged and verified ${jar.name} (${updatedBytes.size} bytes)")
        }

        // Verify all entries in all JARs are <= major version 65 (Java 21)
        for (jar in jarFiles) {
            JarFile(jar).use { jf ->
                val entries = jf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".class")) {
                        val classBytes = jf.getInputStream(entry).readBytes()
                        val majorVersion = getMajorVersion(classBytes)
                        println("[Bytecode Check] ${jar.name} -> ${entry.name}: major version $majorVersion (Java ${majorVersion - 44})")
                        assertTrue(
                            majorVersion <= 65,
                            "Class ${entry.name} in ${jar.name} has major version $majorVersion which is higher than Java 21 (65)!"
                        )
                    }
                }
            }
        }
    }

    private fun getMajorVersion(classBytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(classBytes)
        val magic = buffer.int
        if (magic != 0xCAFEBABE.toInt()) {
            throw IllegalArgumentException("Invalid class file magic: $magic")
        }
        val minor = buffer.short
        val major = buffer.short.toInt() and 0xFFFF
        return major
    }

    private fun updateJarWithCompiledClasses(originalJar: File, classesDir: File): ByteArray {
        val existingEntries = mutableMapOf<String, ByteArray>()

        JarFile(originalJar).use { jf ->
            val entries = jf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    existingEntries[entry.name] = jf.getInputStream(entry).readBytes()
                }
            }
        }

        classesDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relPath = file.relativeTo(classesDir).path.replace('\\', '/')
            existingEntries[relPath] = file.readBytes()
        }

        val baos = ByteArrayOutputStream()
        JarOutputStream(baos).use { jos ->
            for ((name, data) in existingEntries) {
                jos.putNextEntry(JarEntry(name))
                jos.write(data)
                jos.closeEntry()
            }
        }

        return baos.toByteArray()
    }
}
