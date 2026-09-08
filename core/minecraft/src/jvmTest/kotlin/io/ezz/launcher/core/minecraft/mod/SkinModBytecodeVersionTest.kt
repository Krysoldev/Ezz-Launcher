package io.ezz.launcher.core.minecraft.mod

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.jar.JarFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkinModBytecodeVersionTest {

    @Test
    fun verifyAllSkinModJarsBytecodeAndStructure() {
        val resourcesDir = File("src/commonMain/resources")
        assertTrue(resourcesDir.exists(), "Resources directory must exist: ${resourcesDir.absolutePath}")

        val expectedVersions = mapOf(
            "ezz-skin-mod-1.16.jar" to 52, // Java 8
            "ezz-skin-mod-1.17.jar" to 61, // Java 17
            "ezz-skin-mod-1.18.jar" to 61, // Java 17
            "ezz-skin-mod-1.19.jar" to 61, // Java 17
            "ezz-skin-mod-1.20.jar" to 61, // Java 17
            "ezz-skin-mod-1.21.jar" to 65, // Java 21
            "ezz-skin-mod-1.26.jar" to 65, // Java 21
            "ezz-skin-mod-universal.jar" to 61 // Java 17
        )

        for ((jarName, expectedMaxMajor) in expectedVersions) {
            val jarFile = File(resourcesDir, jarName)
            assertTrue(jarFile.exists(), "Mod JAR must exist: $jarName")
            assertTrue(jarFile.length() > 1000, "Mod JAR $jarName must not be empty (was ${jarFile.length()} bytes)")

            JarFile(jarFile).use { jf ->
                // 1. Verify critical metadata files
                val fabricEntry = jf.getJarEntry("fabric.mod.json")
                assertNotNull(fabricEntry, "JAR $jarName must contain fabric.mod.json")
                val fabricJson = jf.getInputStream(fabricEntry).bufferedReader().readText()
                assertTrue(fabricJson.contains("\"id\": \"ezzskin\""), "fabric.mod.json in $jarName must have id ezzskin")

                val mixinEntry = jf.getJarEntry("ezzskin.mixins.json")
                assertNotNull(mixinEntry, "JAR $jarName must contain ezzskin.mixins.json")
                val mixinJson = jf.getInputStream(mixinEntry).bufferedReader().readText()
                assertTrue(mixinJson.contains("AbstractClientPlayerEntity"), "$jarName must contain AbstractClientPlayerEntity mixin")
                assertTrue(mixinJson.contains("PlayerListEntry"), "$jarName must contain PlayerListEntry mixin")
                assertTrue(mixinJson.contains("DefaultSkinHelper"), "$jarName must contain DefaultSkinHelper mixin")

                // 2. Verify bytecode compatibility of all class entries
                val classEntries = jf.entries().asSequence().filter { it.name.endsWith(".class") }.toList()
                assertTrue(classEntries.isNotEmpty(), "JAR $jarName must contain class files")

                var hasPlayerMixin = false
                var hasEntrypoint = false

                for (entry in classEntries) {
                    val classBytes = jf.getInputStream(entry).readBytes()
                    val majorVersion = getMajorVersion(classBytes)

                    assertTrue(
                        majorVersion <= expectedMaxMajor,
                        "Class ${entry.name} in $jarName has major version $majorVersion (Java ${majorVersion - 44}), " +
                                "which exceeds max expected version $expectedMaxMajor (Java ${expectedMaxMajor - 44})!"
                    )

                    if (entry.name.contains("AbstractClientPlayerEntityMixin")) hasPlayerMixin = true
                    if (entry.name.contains("EzzSkinMod")) hasEntrypoint = true
                }

                assertTrue(hasPlayerMixin, "JAR $jarName must contain compiled AbstractClientPlayerEntityMixin.class")
                assertTrue(hasEntrypoint, "JAR $jarName must contain compiled EzzSkinMod.class")
                println("[Bytecode PASS] $jarName: verified ${classEntries.size} classes, max target = Java ${expectedMaxMajor - 44}")
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
}
