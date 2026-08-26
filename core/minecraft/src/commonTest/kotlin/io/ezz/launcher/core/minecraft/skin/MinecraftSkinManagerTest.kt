package io.ezz.launcher.core.minecraft.skin

import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalVaultSkinRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import kotlin.test.assertTrue

class MinecraftSkinManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var vaultSkinRepo: LocalVaultSkinRepository
    private lateinit var skinManager: MinecraftSkinManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_skin_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        vaultSkinRepo = LocalVaultSkinRepository(pathProvider)

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteArray(0),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "image/png")
            )
        }

        val httpClient = HttpClient(mockEngine)
        skinManager = MinecraftSkinManager(pathProvider, httpClient, vaultSkinRepo)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testGetHeadBytesReturnsValidDefaultSteveHeadWhenOffline() {
        val account = OfflineAccount(
            id = "test-id",
            username = "TestPlayer",
            uuid = "test-uuid"
        )

        val headBytes = skinManager.getHeadBytes(account)
        assertNotNull(headBytes)
        assertTrue(headBytes.isNotEmpty())
        // PNG header check
        assertTrue(headBytes.size > 8)
        assertEquals(0x89.toByte(), headBytes[0])
        assertEquals('P'.code.toByte(), headBytes[1])
        assertEquals('N'.code.toByte(), headBytes[2])
        assertEquals('G'.code.toByte(), headBytes[3])
    }

    @Test
    fun testOnSkinChangedUpdatesHeadReactively() {
        val account = OfflineAccount(
            id = "test-acc-reactive",
            username = "ReactivePlayer",
            uuid = "uuid-reactive"
        )

        // Create a 64x64 dummy skin image
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                img.setRGB(x, y, 0xFFFF0000.toInt())
            }
        }
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        val skinBytes = baos.toByteArray()

        skinManager.onSkinChanged(account, skinBytes)

        val cachedMap = skinManager.skinHeads.value
        assertTrue(cachedMap.containsKey(account.id))
        val headBytes = cachedMap[account.id]
        assertNotNull(headBytes)
        assertTrue(headBytes.isNotEmpty())
        assertEquals(0x89.toByte(), headBytes[0])
    }

    @Test
    fun testResolveEffectiveSkinReturnsDefaultSteveWhenAccountIsNull() {
        val skinBytes = skinManager.resolveEffectiveSkinBytes(null)
        assertNotNull(skinBytes)
        assertTrue(skinBytes.isNotEmpty())
        assertEquals(DefaultMinecraftSkin.steveSkinBytes.size, skinBytes.size)
    }

    @Test
    fun testResolveEffectiveSkinReturnsDefaultSteveWhenOfflineAccountHasNoVaultSkin() {
        val account = OfflineAccount(
            id = "acc-no-skin",
            username = "PlayerNoSkin",
            uuid = "uuid-no-skin"
        )
        val skinBytes = skinManager.resolveEffectiveSkinBytes(account)
        assertNotNull(skinBytes)
        assertTrue(skinBytes.isNotEmpty())
        assertEquals(DefaultMinecraftSkin.steveSkinBytes.size, skinBytes.size)
    }

    @Test
    fun testDefaultMinecraftSkinHasValidCanonicalDimensionsAndPngHeaders() {
        val steve = DefaultMinecraftSkin.getSteveSkinBufferedImage()
        assertEquals(64, steve.width)
        assertEquals(64, steve.height)

        val headBytes = DefaultMinecraftSkin.steveHeadBytes
        assertTrue(headBytes.size > 8)
        assertEquals(0x89.toByte(), headBytes[0])
        assertEquals('P'.code.toByte(), headBytes[1])
        assertEquals('N'.code.toByte(), headBytes[2])
        assertEquals('G'.code.toByte(), headBytes[3])
    }
}
