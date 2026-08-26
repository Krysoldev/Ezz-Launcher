package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalModScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestPathProvider(): PathProvider {
        val root = tempFolder.newFolder("ezz_root").absolutePath.toPath()
        return io.ezz.launcher.core.storage.path.DefaultPathProvider(root)
    }

    private fun createFakeFabricModJar(file: File, id: String, name: String, version: String) {
        val fabricJson = """
            {
                "schemaVersion": 1,
                "id": "$id",
                "version": "$version",
                "name": "$name",
                "description": "High performance test mod",
                "authors": ["TestAuthor"],
                "environment": "*"
            }
        """.trimIndent()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("fabric.mod.json"))
            zos.write(fabricJson.toByteArray())
            zos.closeEntry()
        }
    }

    @Test
    fun testScanAndToggleMods() = runBlocking {
        val pathProvider = createTestPathProvider()
        pathProvider.initializeDirectories()

        val instanceId = "inst-test-1"
        val modsDir = pathProvider.getInstanceDirectory(instanceId).resolve(".minecraft").resolve("mods").toFile()
        modsDir.mkdirs()

        val sodiumJar = File(modsDir, "sodium-fabric-0.5.8.jar")
        createFakeFabricModJar(sodiumJar, "sodium", "Sodium", "0.5.8")

        val irisJar = File(modsDir, "iris-fabric-1.7.0.jar.disabled")
        createFakeFabricModJar(irisJar, "iris", "Iris Shaders", "1.7.0")

        val scanner = LocalModScanner(pathProvider)
        val scanned = scanner.scanMods(instanceId)

        assertEquals(2, scanned.size)

        val sodium = scanned.find { it.id == "sodium" }
        assertNotNull(sodium)
        assertEquals("Sodium", sodium.name)
        assertEquals("0.5.8", sodium.version)
        assertTrue(sodium.enabled)

        val iris = scanned.find { it.id == "iris" }
        assertNotNull(iris)
        assertEquals("Iris Shaders", iris.name)
        assertEquals("1.7.0", iris.version)
        assertFalse(iris.enabled)

        // Test toggle Sodium to disabled
        val newName = scanner.toggleMod(instanceId, "sodium-fabric-0.5.8.jar", enable = false)
        assertEquals("sodium-fabric-0.5.8.jar.disabled", newName)

        val rescan = scanner.scanMods(instanceId)
        val sodiumDisabled = rescan.find { it.id == "sodium" }
        assertNotNull(sodiumDisabled)
        assertFalse(sodiumDisabled.enabled)

        // Test delete mod
        val deleted = scanner.deleteMod(instanceId, "sodium-fabric-0.5.8.jar.disabled")
        assertTrue(deleted)

        val afterDelete = scanner.scanMods(instanceId)
        assertEquals(1, afterDelete.size)
    }
}
