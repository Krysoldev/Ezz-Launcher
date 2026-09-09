package io.ezz.launcher.core.storage.mrpack

import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.MrpackExportOptions
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MrpackManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository
    private lateinit var mrpackManager: MrpackManager

    // Valid minimal 1x1 PNG bytes for testing
    private val samplePngBytes = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
        0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
        0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_mrpack_test", "").apply {
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
    fun testValidateAndPreviewFabricMrpack() = runBlocking {
        val sampleMrpack = File(tempDir, "fabric_pack.mrpack")
        ZipOutputStream(FileOutputStream(sampleMrpack)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.2.0",
                    "name": "Fabulously Optimized",
                    "summary": "A simple Minecraft modpack focusing on performance and graphics",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "fabric-loader": "0.16.9"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = mrpackManager.previewMrpack(sampleMrpack)
        assertTrue(result.isSuccess)

        val preview = result.getOrNull()
        assertNotNull(preview)
        assertEquals("Fabulously Optimized", preview.name)
        assertEquals("1.21.1", preview.minecraftVersion)
        assertEquals(LoaderType.FABRIC, preview.loaderType)
        assertEquals("0.16.9", preview.loaderVersion)
        assertEquals("1.2.0", preview.versionId)
    }

    @Test
    fun testRejectForgePackWithActionableError() = runBlocking {
        val forgeMrpack = File(tempDir, "forge_pack.mrpack")
        ZipOutputStream(FileOutputStream(forgeMrpack)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "All The Mods Forge",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.20.1",
                        "forge": "47.2.0"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = mrpackManager.previewMrpack(forgeMrpack)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("Forge"), "Error message should mention Forge: $msg")
        assertTrue(msg.contains("47.2.0"), "Error message should mention version: $msg")
    }

    @Test
    fun testRejectNeoForgePackWithActionableError() = runBlocking {
        val neoMrpack = File(tempDir, "neoforge_pack.mrpack")
        ZipOutputStream(FileOutputStream(neoMrpack)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "NeoForge Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "neoforge": "21.1.65"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = mrpackManager.previewMrpack(neoMrpack)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("NeoForge"), "Error message should mention NeoForge: $msg")
        assertTrue(msg.contains("21.1.65"), "Error message should mention version: $msg")
    }

    @Test
    fun testQuiltPackCompatibility() = runBlocking {
        val quiltMrpack = File(tempDir, "quilt_pack.mrpack")
        ZipOutputStream(FileOutputStream(quiltMrpack)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "Quilt Performance Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "quilt-loader": "0.26.0"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = mrpackManager.previewMrpack(quiltMrpack)
        assertTrue(result.isSuccess)
        val preview = result.getOrNull()!!
        assertEquals(LoaderType.FABRIC, preview.loaderType)
        assertEquals("0.26.0", preview.loaderVersion)
    }

    @Test
    fun testRejectInvalidZipWithoutIndex() = runBlocking {
        val invalidZip = File(tempDir, "random.zip")
        ZipOutputStream(FileOutputStream(invalidZip)).use { zos ->
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("Some text".toByteArray())
            zos.closeEntry()
        }

        val result = mrpackManager.previewMrpack(invalidZip)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("modrinth.index.json") == true)
    }

    @Test
    fun testRejectCorruptedMrpack() = runBlocking {
        val corruptFile = File(tempDir, "corrupted.mrpack")
        corruptFile.writeText("This is not a zip file at all")

        val result = mrpackManager.previewMrpack(corruptFile)
        assertTrue(result.isFailure)
    }

    @Test
    fun testRejectEmptyFile() = runBlocking {
        val emptyFile = File(tempDir, "empty.mrpack")
        emptyFile.createNewFile()

        val result = mrpackManager.previewMrpack(emptyFile)
        assertTrue(result.isFailure)
    }

    @Test
    fun testZipSlipInOverridesProtection() = runBlocking {
        val maliciousMrpack = File(tempDir, "malicious.mrpack")
        ZipOutputStream(FileOutputStream(maliciousMrpack)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "Malicious Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "fabric-loader": "0.16.9"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Zip Slip entry escaping root directory
            zos.putNextEntry(ZipEntry("overrides/../../malicious.txt"))
            zos.write("Dangerous payload".toByteArray())
            zos.closeEntry()
        }

        val importResult = mrpackManager.importMrpack(maliciousMrpack)
        assertTrue(importResult.isFailure)
        assertTrue(importResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testExtractAndPersistPackIconFromRoot() = runBlocking {
        val mrpackWithIcon = File(tempDir, "icon_root.mrpack")
        ZipOutputStream(FileOutputStream(mrpackWithIcon)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "Icon Root Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "fabric-loader": "0.16.9"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Root icon entry
            zos.putNextEntry(ZipEntry("icon.png"))
            zos.write(samplePngBytes)
            zos.closeEntry()
        }

        // Preview should detect icon
        val previewResult = mrpackManager.previewMrpack(mrpackWithIcon)
        assertTrue(previewResult.isSuccess)
        val preview = previewResult.getOrNull()!!
        assertNotNull(preview.iconBytes)
        assertEquals(samplePngBytes.size, preview.iconBytes!!.size)

        // Import should persist icon into instance folder
        val importResult = mrpackManager.importMrpack(mrpackWithIcon, "Instance With Root Icon")
        assertTrue(importResult.isSuccess)
        val instance = importResult.getOrNull()!!
        assertNotNull(instance.customIconPath)

        val iconFile = File(instance.customIconPath!!)
        assertTrue(iconFile.exists(), "Instance customIconPath file must exist")
        assertEquals(samplePngBytes.size.toLong(), iconFile.length())
    }

    @Test
    fun testExtractAndPersistPackIconFromOverrides() = runBlocking {
        val mrpackWithOverrideIcon = File(tempDir, "icon_override.mrpack")
        ZipOutputStream(FileOutputStream(mrpackWithOverrideIcon)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "Override Icon Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "fabric-loader": "0.16.9"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Override icon entry
            zos.putNextEntry(ZipEntry("overrides/icon.png"))
            zos.write(samplePngBytes)
            zos.closeEntry()
        }

        val importResult = mrpackManager.importMrpack(mrpackWithOverrideIcon, "Instance With Override Icon")
        assertTrue(importResult.isSuccess)
        val instance = importResult.getOrNull()!!
        assertNotNull(instance.customIconPath)

        val iconFile = File(instance.customIconPath!!)
        assertTrue(iconFile.exists(), "Override icon must be copied to instance root")
        assertEquals(samplePngBytes.size.toLong(), iconFile.length())
    }

    @Test
    fun testGracefulFallbackOnCorruptedIcon() = runBlocking {
        val mrpackCorruptIcon = File(tempDir, "corrupted_icon.mrpack")
        ZipOutputStream(FileOutputStream(mrpackCorruptIcon)).use { zos ->
            val manifest = """
                {
                    "formatVersion": 1,
                    "game": "minecraft",
                    "versionId": "1.0.0",
                    "name": "Corrupt Icon Pack",
                    "files": [],
                    "dependencies": {
                        "minecraft": "1.21.1",
                        "fabric-loader": "0.16.9"
                    }
                }
            """.trimIndent()
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Malformed icon (not a valid image)
            zos.putNextEntry(ZipEntry("icon.png"))
            zos.write("Corrupted text data instead of image bytes".toByteArray())
            zos.closeEntry()
        }

        val importResult = mrpackManager.importMrpack(mrpackCorruptIcon, "Corrupted Icon Instance")
        assertTrue(importResult.isSuccess, "Import must not fail due to invalid icon image")
        val instance = importResult.getOrNull()!!
        assertNull(instance.customIconPath, "Corrupted icon must fall back cleanly without creating a broken icon path")
    }

    @Test
    fun testSequentialImportsIndependentInstances() = runBlocking {
        val packNames = listOf("Alpha Pack", "Beta Pack", "Gamma Pack")
        val createdInstances = mutableListOf<io.ezz.launcher.core.model.instance.Instance>()

        for (name in packNames) {
            val packFile = File(tempDir, "${name.replace(' ', '_')}.mrpack")
            ZipOutputStream(FileOutputStream(packFile)).use { zos ->
                val manifest = """
                    {
                        "formatVersion": 1,
                        "game": "minecraft",
                        "versionId": "1.0.0",
                        "name": "$name",
                        "files": [],
                        "dependencies": {
                            "minecraft": "1.21.1",
                            "fabric-loader": "0.16.9"
                        }
                    }
                """.trimIndent()
                zos.putNextEntry(ZipEntry("modrinth.index.json"))
                zos.write(manifest.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val result = mrpackManager.importMrpack(packFile, name)
            assertTrue(result.isSuccess, "Import of $name should succeed")
            createdInstances.add(result.getOrNull()!!)
        }

        assertEquals(3, createdInstances.size)
        assertEquals(3, createdInstances.map { it.id }.distinct().size, "All 3 instances must have unique IDs")

        val allInstances = repository.instances.value
        for (name in packNames) {
            assertTrue(allInstances.any { it.name == name }, "Repository must contain $name")
        }
    }

    @Test
    fun testExportAndReimportRoundTrip() = runBlocking {
        // 1. Create original instance
        val originalInstance = repository.createInstance(
            name = "Original Studio Pack",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )

        // 2. Put sample configs, options, and mods into instance
        val instDir = pathProvider.getInstanceDirectory(originalInstance.id).toFile()
        val mcDir = File(instDir, ".minecraft")
        val configDir = File(mcDir, "config")
        configDir.mkdirs()
        File(configDir, "options_custom.json").writeText("""{"renderDistance": 16}""")

        val optionsTxt = File(mcDir, "options.txt")
        optionsTxt.writeText("fov:90.0\nguiScale:2")

        val modsDir = File(mcDir, "mods")
        modsDir.mkdirs()
        File(modsDir, "sample-mod-1.0.jar").writeText("Mock Mod Jar Content")

        // 3. Export to .mrpack
        val exportedMrpackFile = File(tempDir, "exported_pack.mrpack")
        val exportOptions = MrpackExportOptions(
            customName = "Exported Studio Pack",
            customSummary = "Seamless round-trip test pack",
            versionId = "2.0.0",
            includeConfigs = true,
            includeMods = true,
            includeResourcePacks = true,
            includeShaderPacks = true
        )

        val exportResult = mrpackManager.exportMrpack(
            instance = originalInstance,
            targetFile = exportedMrpackFile,
            options = exportOptions
        )
        assertTrue(exportResult.isSuccess)
        assertTrue(exportedMrpackFile.exists())
        assertTrue(exportedMrpackFile.length() > 0)

        // 4. Validate the exported .mrpack preview
        val previewResult = mrpackManager.previewMrpack(exportedMrpackFile)
        assertTrue(previewResult.isSuccess)
        val preview = previewResult.getOrNull()!!
        assertEquals("Exported Studio Pack", preview.name)
        assertEquals("1.21.1", preview.minecraftVersion)
        assertEquals(LoaderType.FABRIC, preview.loaderType)
        assertEquals("0.16.9", preview.loaderVersion)

        // 5. Re-import the exported .mrpack as a brand new instance
        val importResult = mrpackManager.importMrpack(
            file = exportedMrpackFile,
            targetInstanceName = "Imported Rebuilt Instance"
        )
        assertTrue(importResult.isSuccess)

        val importedInstance = importResult.getOrNull()!!
        assertEquals("Imported Rebuilt Instance", importedInstance.name)
        assertEquals("1.21.1", importedInstance.minecraftVersion)
        assertEquals(LoaderType.FABRIC, importedInstance.loaderType)
        assertEquals("0.16.9", importedInstance.loaderVersion)

        // 6. Verify imported instance directory & files
        val importedInstDir = pathProvider.getInstanceDirectory(importedInstance.id).toFile()
        val importedMcDir = File(importedInstDir, ".minecraft")
        assertTrue(importedMcDir.exists())

        val importedConfigFile = File(importedMcDir, "config/options_custom.json")
        assertTrue(importedConfigFile.exists(), "Exported config file should be restored")
        assertEquals("""{"renderDistance": 16}""", importedConfigFile.readText())

        val importedOptionsTxt = File(importedMcDir, "options.txt")
        assertTrue(importedOptionsTxt.exists(), "Exported options.txt should be restored")
        assertEquals("fov:90.0\nguiScale:2", importedOptionsTxt.readText())

        val importedModFile = File(importedMcDir, "mods/sample-mod-1.0.jar")
        assertTrue(importedModFile.exists(), "Exported mod file should be restored")
        assertEquals("Mock Mod Jar Content", importedModFile.readText())
    }

    @Test
    fun testExportMrpackWithWorldsAndInspectZipLayout() = runBlocking {
        // 1. Create source instance with world and mods
        val sourceInstance = repository.createInstance(
            name = "World And Mod Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDir = pathProvider.getInstanceDirectory(sourceInstance.id).toFile()
        val mcDir = File(instDir, ".minecraft").apply { mkdirs() }

        // Add config
        val configDir = File(mcDir, "config").apply { mkdirs() }
        File(configDir, "client.json").writeText("""{"uiTheme": "dark"}""")

        // Add world in saves/
        val saveDir = File(mcDir, "saves/SurvivalWorld").apply { mkdirs() }
        File(saveDir, "level.dat").writeText("Level Data Binary Mock")

        // Add mod
        val modsDir = File(mcDir, "mods").apply { mkdirs() }
        File(modsDir, "sodium-fabric-0.5.jar").writeText("Mock Sodium Jar")

        // 2. Export with includeWorlds = true
        val targetMrpack = File(tempDir, "world_test.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = sourceInstance,
            targetFile = targetMrpack,
            options = MrpackExportOptions(
                customName = "Custom World Pack",
                customSummary = "Pack with included world save",
                versionId = "1.0.5",
                includeConfigs = true,
                includeMods = true,
                includeWorlds = true
            )
        )
        assertTrue(exportResult.isSuccess)
        assertTrue(targetMrpack.exists())

        // 3. Inspect raw zip archive entries
        val entryNames = mutableListOf<String>()
        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryNames.add(entry.name)
                // Ensure no Windows backslashes in ZIP entries (standard format compliance)
                assertTrue(!entry.name.contains('\\'), "Zip entry path must use forward slashes: ${entry.name}")
            }

            // Verify modrinth.index.json at root
            val manifestEntry = zip.getEntry("modrinth.index.json")
            assertNotNull(manifestEntry, "modrinth.index.json must be present at archive root")

            val manifestContent = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            assertTrue(manifestContent.contains(""""formatVersion": 1"""))
            assertTrue(manifestContent.contains(""""game": "minecraft""""))
            assertTrue(manifestContent.contains(""""versionId": "1.0.5""""))
            assertTrue(manifestContent.contains(""""minecraft": "1.21.1""""))
            assertTrue(manifestContent.contains(""""fabric-loader": "0.16.9""""))
        }

        assertTrue(entryNames.contains("modrinth.index.json"), "Must contain root manifest")
        assertTrue(entryNames.contains("overrides/config/client.json"), "Must contain config override")
        assertTrue(entryNames.contains("overrides/saves/SurvivalWorld/level.dat"), "Must contain world save override")
        assertTrue(entryNames.contains("overrides/mods/sodium-fabric-0.5.jar"), "Unhosted mod must fall back to overrides")
    }

    @Test
    fun testExportMrpackWithCustomPngIconRetainsExactBytes(): Unit = runBlocking {
        val instance = repository.createInstance(
            name = "Pack With PNG Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        val iconFile = File(instDir, "icon.png")
        iconFile.writeBytes(samplePngBytes)

        val updated = instance.copy(customIconPath = iconFile.absolutePath)
        repository.updateInstance(updated)

        val targetMrpack = File(tempDir, "pack_with_png.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = updated,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess)

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNotNull(iconEntry, "icon.png must be present at archive root")
            val bytesInArchive = zip.getInputStream(iconEntry).use { it.readBytes() }
            assertTrue(bytesInArchive.contentEquals(samplePngBytes), "Archive icon bytes must match original exactly")
        }
    }

    @Test
    fun testExportMrpackWithTransparentPngIconPreservesTransparency(): Unit = runBlocking {
        // Create 2x2 transparent PNG
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, Color(255, 0, 0, 128).rgb) // 50% translucent red
        img.setRGB(1, 1, Color(0, 255, 0, 0).rgb)   // 100% transparent green
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        val transparentBytes = baos.toByteArray()

        val instance = repository.createInstance(
            name = "Pack With Transparent Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        val iconFile = File(instDir, "icon.png")
        iconFile.writeBytes(transparentBytes)
        val updated = instance.copy(customIconPath = iconFile.absolutePath)
        repository.updateInstance(updated)

        val targetMrpack = File(tempDir, "transparent_pack.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = updated,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess)

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNotNull(iconEntry)
            val bytesInArchive = zip.getInputStream(iconEntry).use { it.readBytes() }
            assertTrue(bytesInArchive.contentEquals(transparentBytes), "Transparent PNG bytes must be 100% preserved")
        }
    }

    @Test
    fun testExportMrpackWithGameDirIconPacksIcon(): Unit = runBlocking {
        // KrysolDev reproduction: instance.customIconPath is null, but .minecraft/icon.png exists on disk
        val instance = repository.createInstance(
            name = "Pack With GameDir Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        assertNull(instance.customIconPath)

        val instDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        val gameDir = File(instDir, ".minecraft").apply { mkdirs() }
        val gameDirIcon = File(gameDir, "icon.png")
        gameDirIcon.writeBytes(samplePngBytes)

        val targetMrpack = File(tempDir, "gamedir_icon.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = instance,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess)

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNotNull(iconEntry, "Icon located in .minecraft/ must be discovered and packed as icon.png")
            val bytes = zip.getInputStream(iconEntry).use { it.readBytes() }
            assertTrue(bytes.contentEquals(samplePngBytes))
        }
    }

    @Test
    fun testExportMrpackWithJpegIconConvertsToPng(): Unit = runBlocking {
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "JPEG", baos)
        val jpegBytes = baos.toByteArray()

        val instance = repository.createInstance(
            name = "Pack With JPEG Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        val jpegFile = File(instDir, "icon.jpg")
        jpegFile.writeBytes(jpegBytes)

        val targetMrpack = File(tempDir, "jpeg_pack.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = instance,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess)

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNotNull(iconEntry, "icon.png must be present even when source was JPEG")
            val bytes = zip.getInputStream(iconEntry).use { it.readBytes() }
            assertTrue(io.ezz.launcher.core.storage.instance.InstanceIconResolver.isPng(bytes), "Converted archive icon must be valid PNG format")
        }
    }

    @Test
    fun testExportMrpackWithoutIconPreservesFallback(): Unit = runBlocking {
        val instance = repository.createInstance(
            name = "Pack Without Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.VANILLA,
            loaderVersion = null
        )

        val targetMrpack = File(tempDir, "no_icon.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = instance,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess)

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNull(iconEntry, "Pack without custom icon must not include icon.png, preserving launcher fallback")
        }
    }

    @Test
    fun testExportMrpackWithCorruptedIconLogsErrorAndContinues(): Unit = runBlocking {
        val instance = repository.createInstance(
            name = "Pack With Corrupted Icon",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDir = pathProvider.getInstanceDirectory(instance.id).toFile()
        val badFile = File(instDir, "icon.png")
        badFile.writeText("THIS IS NOT A VALID IMAGE FILE DATA AT ALL")
        val updated = instance.copy(customIconPath = badFile.absolutePath)
        repository.updateInstance(updated)

        val targetMrpack = File(tempDir, "corrupted_icon.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = updated,
            targetFile = targetMrpack
        )
        assertTrue(exportResult.isSuccess, "Export should succeed and not crash on bad icon")

        java.util.zip.ZipFile(targetMrpack).use { zip ->
            val iconEntry = zip.getEntry("icon.png")
            assertNull(iconEntry, "Corrupted icon must not be written to archive")
            assertNotNull(zip.getEntry("modrinth.index.json"), "Archive manifest must remain valid")
        }
    }

    @Test
    fun testExportAndImportRoundTripPreservesIcon(): Unit = runBlocking {
        // 1. Instance A with custom icon
        val instanceA = repository.createInstance(
            name = "Instance A",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.9"
        )
        val instDirA = pathProvider.getInstanceDirectory(instanceA.id).toFile()
        val iconA = File(instDirA, "icon.png")
        iconA.writeBytes(samplePngBytes)

        val updatedA = instanceA.copy(customIconPath = iconA.absolutePath)
        repository.updateInstance(updatedA)

        // 2. Export to .mrpack
        val mrpackFile = File(tempDir, "InstanceA.mrpack")
        val exportResult = mrpackManager.exportMrpack(
            instance = updatedA,
            targetFile = mrpackFile,
            options = MrpackExportOptions(customName = "Exported Pack A")
        )
        assertTrue(exportResult.isSuccess)

        // 3. Import as Instance B
        val importResult = mrpackManager.importMrpack(
            file = mrpackFile,
            targetInstanceName = "Instance B"
        )
        assertTrue(importResult.isSuccess)
        val instanceB = importResult.getOrNull()!!

        // 4. Verify Instance B has icon restored
        assertNotNull(instanceB.customIconPath, "Imported instance must have customIconPath set")
        val iconB = File(instanceB.customIconPath!!)
        assertTrue(iconB.exists(), "Imported icon file must exist on disk")
        assertTrue(iconB.length() > 0, "Imported icon file must not be empty")
        val importedBytes = iconB.readBytes()
        assertTrue(importedBytes.contentEquals(samplePngBytes), "Imported icon bytes must be identical to Instance A")
    }
}

