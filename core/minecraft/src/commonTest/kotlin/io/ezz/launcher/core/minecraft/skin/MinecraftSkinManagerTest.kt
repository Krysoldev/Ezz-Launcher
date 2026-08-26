package io.ezz.launcher.core.minecraft.skin

import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinecraftSkinManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var skinManager: MinecraftSkinManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_skin_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteArray(0),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "image/png")
            )
        }

        val httpClient = HttpClient(mockEngine)
        skinManager = MinecraftSkinManager(pathProvider, httpClient)
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

    private fun assertEquals(expected: Byte, actual: Byte) {
        kotlin.test.assertEquals(expected, actual)
    }
}
