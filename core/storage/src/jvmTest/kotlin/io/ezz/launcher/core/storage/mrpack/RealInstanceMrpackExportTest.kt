package io.ezz.launcher.core.storage.mrpack

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.MrpackExportOptions
import io.ezz.launcher.core.storage.instance.InstanceIconResolver
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealInstanceMrpackExportTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository
    private lateinit var mrpackManager: MrpackManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_krysol_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalInstanceRepository(pathProvider)
        mrpackManager = MrpackManager(pathProvider, repository)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRealWorldKrysolDevInstanceExportAndImportRoundTrip(): Unit = runBlocking {
        // 1. Recreate KrysolDev structure: customIconPath is null, icon is at .minecraft/icon.png
        val krysolInstance = repository.createInstance(
            name = "KrysolDev",
            minecraftVersion = "1.21.11",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.19.3"
        )
        assertEquals(null, krysolInstance.customIconPath)

        val instDir = pathProvider.getInstanceDirectory(krysolInstance.id).toFile()
        val gameDir = File(instDir, ".minecraft").apply { mkdirs() }

        // Find actual user icon if on disk, or generate sample PNG
        val userHome = System.getProperty("user.home") ?: "."
        val realIconFile = File(userHome, "AppData/Roaming/.ezzlauncher/instances/2d82d4fa-6f43-4e2e-b0af-aba34e2c7908/.minecraft/icon.png")
        val iconBytes = if (realIconFile.exists()) {
            realIconFile.readBytes()
        } else {
            byteArrayOf(
                0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
                0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
                0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
            )
        }

        val gameDirIcon = File(gameDir, "icon.png")
        gameDirIcon.writeBytes(iconBytes)

        // 2. Export to .mrpack
        val targetMrpack = File(tempDir, "KrysolDev.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = krysolInstance,
            targetFile = targetMrpack,
            options = MrpackExportOptions(
                customName = "KrysolDev Exported",
                customSummary = "KrysolDev Modpack with Logo"
            )
        )
        assertTrue(exportResult.isSuccess, "Export must succeed")
        assertTrue(targetMrpack.exists(), "Exported .mrpack must exist on disk")

        // 3. Programmatically inspect archive structure
        ZipFile(targetMrpack).use { zip ->
            // Check manifest
            val manifestEntry = zip.getEntry("modrinth.index.json")
            assertNotNull(manifestEntry, "modrinth.index.json must be present at archive root")

            // Check icon.png
            val iconEntry = zip.getEntry("icon.png")
            assertNotNull(iconEntry, "icon.png must be present at archive root")
            val archivedBytes = zip.getInputStream(iconEntry).use { it.readBytes() }
            assertTrue(archivedBytes.contentEquals(iconBytes), "Archived icon bytes must be identical to source icon")
            assertTrue(InstanceIconResolver.isPng(archivedBytes), "Archived icon must have standard PNG header")

            // Ensure NO absolute or machine paths in zip entries
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                assertTrue(!name.contains('\\'), "Zip entry must use forward slashes: $name")
                assertTrue(!name.contains(':'), "Zip entry must not contain Windows drive letters: $name")
                assertTrue(!name.startsWith("/"), "Zip entry must not be an absolute path: $name")
                assertTrue(!name.contains(".."), "Zip entry must not contain directory traversal: $name")
                assertTrue(!name.contains("AppData", ignoreCase = true), "Zip entry must not contain local AppData path: $name")
                assertTrue(!name.contains("Users", ignoreCase = true), "Zip entry must not contain local Users path: $name")
            }
        }

        // 4. Re-import into a clean instance workspace
        val importResult = mrpackManager.importMrpack(
            file = targetMrpack,
            targetInstanceName = "KrysolDev Imported"
        )
        assertTrue(importResult.isSuccess, "Import must succeed")
        val importedInstance = importResult.getOrNull()!!

        assertEquals("KrysolDev Imported", importedInstance.name)
        assertEquals("1.21.11", importedInstance.minecraftVersion)
        assertEquals(LoaderType.FABRIC, importedInstance.loaderType)

        // 5. Verify the logo is preserved and restored in the imported instance
        assertNotNull(importedInstance.customIconPath, "Imported instance must have customIconPath set")
        val importedIconFile = File(importedInstance.customIconPath!!)
        assertTrue(importedIconFile.exists(), "Imported icon file must exist on disk")
        assertTrue(importedIconFile.length() > 0, "Imported icon file must not be empty")
        val importedBytes = importedIconFile.readBytes()
        assertTrue(importedBytes.contentEquals(iconBytes), "Imported icon bytes must be 100% identical to original")

        // 6. Verify InstanceIconResolver on imported instance
        val resolvedImportedIcon = InstanceIconResolver.resolveIconFile(
            instance = importedInstance,
            instanceDir = pathProvider.getInstanceDirectory(importedInstance.id).toFile()
        )
        assertNotNull(resolvedImportedIcon, "InstanceIconResolver must resolve imported icon")
        assertTrue(resolvedImportedIcon.exists())
    }
}
