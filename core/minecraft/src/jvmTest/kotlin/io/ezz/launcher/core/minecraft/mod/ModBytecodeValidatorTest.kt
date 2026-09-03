package io.ezz.launcher.core.minecraft.mod

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModBytecodeValidatorTest {

    @Test
    fun testClassVersionConversion() {
        assertEquals(8, ModBytecodeValidator.classVersionToJavaRelease(52))
        assertEquals(17, ModBytecodeValidator.classVersionToJavaRelease(61))
        assertEquals(21, ModBytecodeValidator.classVersionToJavaRelease(65))
        assertEquals(26, ModBytecodeValidator.classVersionToJavaRelease(70))

        assertEquals(52, ModBytecodeValidator.javaReleaseToClassVersion(8))
        assertEquals(61, ModBytecodeValidator.javaReleaseToClassVersion(17))
        assertEquals(65, ModBytecodeValidator.javaReleaseToClassVersion(21))
        assertEquals(70, ModBytecodeValidator.javaReleaseToClassVersion(26))
    }

    @Test
    fun testValidateCompatibleJar() {
        val jarBytes = createFakeJar(classMajorVersion = 65) // Java 21 class
        val result = ModBytecodeValidator.validateJarBytes("test-mod.jar", jarBytes, javaMajorVersion = 21)
        assertTrue(result is ModCompatibilityResult.Compatible)
    }

    @Test
    fun testValidateIncompatibleJar() {
        val jarBytes = createFakeJar(classMajorVersion = 70) // Java 26 class
        val result = ModBytecodeValidator.validateJarBytes("ezz-skin-mod-1.21.jar", jarBytes, javaMajorVersion = 21)
        assertTrue(result is ModCompatibilityResult.Incompatible)
        val incompatible = result as ModCompatibilityResult.Incompatible
        assertEquals("ezz-skin-mod-1.21.jar", incompatible.modName)
        assertEquals(70, incompatible.maxMajorVersionFound)
        assertEquals(26, incompatible.requiredJavaVersion)
        assertEquals(21, incompatible.currentJavaVersion)
        assertTrue(incompatible.errorMessage.contains("INCOMPATIBLE MOD DETECTED"))
        assertTrue(incompatible.errorMessage.contains("Required:\nJava 26 / newer (Class file version 70)"))
    }

    private fun createFakeJar(classMajorVersion: Int): ByteArray {
        val classBytes = ByteArray(8)
        val buffer = ByteBuffer.wrap(classBytes)
        buffer.putInt(0xCAFEBABE.toInt())
        buffer.putShort(0)
        buffer.putShort(classMajorVersion.toShort())

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("io/ezz/test/TestClass.class"))
            zos.write(classBytes)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
