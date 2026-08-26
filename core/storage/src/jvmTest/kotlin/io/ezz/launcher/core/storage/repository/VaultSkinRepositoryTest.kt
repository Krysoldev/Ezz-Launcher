package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultSkinRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalVaultSkinRepository

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_vault_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalVaultSkinRepository(pathProvider)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createDummySkinPng(width: Int = 64, height: Int = 64, isAlex: Boolean = false): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(120, 80, 50, 255)
        g.fillRect(0, 0, width, height)
        if (isAlex) {
            // Set Alex transparent pixels
            for (x in 54..55) {
                for (y in 20..31) {
                    img.setRGB(x, y, 0)
                }
            }
        }
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        return baos.toByteArray()
    }

    @Test
    fun testImportSkinAndPersist() = runBlocking {
        val steveBytes = createDummySkinPng(64, 64, isAlex = false)
        val result = repository.importSkin(steveBytes, "My Steve Skin")

        assertTrue(result.isSuccess)
        val skin = result.getOrThrow()
        assertEquals("My Steve Skin", skin.name)
        assertEquals(SkinModelType.STEVE, skin.modelType)
        assertEquals(skin.id, repository.activeSkinId.value)
        assertEquals(1, repository.skins.value.size)

        // Verify restart persistence
        val reloadedRepo = LocalVaultSkinRepository(pathProvider)
        assertEquals(1, reloadedRepo.skins.value.size)
        assertEquals("My Steve Skin", reloadedRepo.skins.value.first().name)
        assertEquals(skin.id, reloadedRepo.activeSkinId.value)
    }

    @Test
    fun testAlexModelDetection() = runBlocking {
        val alexBytes = createDummySkinPng(64, 64, isAlex = true)
        val result = repository.importSkin(alexBytes, "My Alex Skin")

        assertTrue(result.isSuccess)
        val skin = result.getOrThrow()
        assertEquals(SkinModelType.ALEX, skin.modelType)
    }

    @Test
    fun testRenameAndDeleteSkin() = runBlocking {
        val skin1Bytes = createDummySkinPng(64, 64)
        val skin1 = repository.importSkin(skin1Bytes, "Skin 1").getOrThrow()

        val renameResult = repository.renameSkin(skin1.id, "Renamed Skin 1")
        assertTrue(renameResult.isSuccess)
        assertEquals("Renamed Skin 1", repository.getSkin(skin1.id)?.name)

        val deleted = repository.deleteSkin(skin1.id)
        assertTrue(deleted)
        assertEquals(0, repository.skins.value.size)
        assertNull(repository.activeSkinId.value)
    }

    @Test
    fun testDuplicateHashDetection() = runBlocking {
        val bytes = createDummySkinPng(64, 64)
        val skin = repository.importSkin(bytes, "Original").getOrThrow()

        val hash = repository.computeSha256(bytes)
        val duplicate = repository.findDuplicateByHash(hash)
        assertNotNull(duplicate)
        assertEquals(skin.id, duplicate.id)
    }
}
