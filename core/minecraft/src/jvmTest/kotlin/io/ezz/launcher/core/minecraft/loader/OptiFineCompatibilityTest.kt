package io.ezz.launcher.core.minecraft.loader

import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineInstaller
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OptiFineCompatibilityTest {

    @Test
    fun testSupportedVersions() {
        assertTrue(OptiFineCompatibilityValidator.isVersionSupported("1.21.4"))
        assertTrue(OptiFineCompatibilityValidator.isVersionSupported("1.20.4"))
        assertTrue(OptiFineCompatibilityValidator.isVersionSupported("1.16.5"))
        assertTrue(OptiFineCompatibilityValidator.isVersionSupported("1.12.2"))
        assertTrue(OptiFineCompatibilityValidator.isVersionSupported("1.8.9"))
    }

    @Test
    fun testOptiFineInstallProfile() = runBlocking {
        val temp = File.createTempFile("ezz_optifine_test", "").apply { delete(); mkdirs() }
        try {
            val pathProvider = DefaultPathProvider(temp.absolutePath.toPath())
            pathProvider.initializeDirectories()
            val installer = OptiFineInstaller(pathProvider)

            val profile = installer.install("1.20.4", "HD_U_I6")
            assertNotNull(profile)
            assertEquals("1.20.4-OptiFine_HD_U_I6", profile.id)
            assertEquals("net.minecraft.client.main.Main", profile.mainClass)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun testUnsupportedVersionRejection() {
        val temp = File.createTempFile("ezz_optifine_test2", "").apply { delete(); mkdirs() }
        try {
            val pathProvider = DefaultPathProvider(temp.absolutePath.toPath())
            pathProvider.initializeDirectories()
            val installer = OptiFineInstaller(pathProvider)

            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    installer.install("1.0.0", "HD_U_A1")
                }
            }
        } finally {
            temp.deleteRecursively()
        }
    }
}
