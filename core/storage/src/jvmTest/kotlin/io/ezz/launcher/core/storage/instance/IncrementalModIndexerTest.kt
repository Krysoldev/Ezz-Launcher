package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.LocalMod
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncrementalModIndexerTest {

    private lateinit var tempModFile: File

    @BeforeTest
    fun setUp() {
        IncrementalModIndexer.clear()
        tempModFile = File.createTempFile("sodium-fabric", ".jar")
        tempModFile.writeText("dummy content")
    }

    @AfterTest
    fun tearDown() {
        tempModFile.delete()
        IncrementalModIndexer.clear()
    }

    @Test
    fun testCachePutAndGet() {
        assertNull(IncrementalModIndexer.getCached(tempModFile))

        val mod = LocalMod(
            id = "sodium",
            name = "Sodium",
            version = "0.5.11",
            fileName = tempModFile.name,
            fileSize = tempModFile.length(),
            loader = "FABRIC",
            enabled = true
        )

        IncrementalModIndexer.put(tempModFile, mod)

        val cached = IncrementalModIndexer.getCached(tempModFile)
        assertNotNull(cached)
        assertEquals("sodium", cached.id)
        assertEquals("Sodium", cached.name)
        assertTrue(cached.enabled)
    }

    @Test
    fun testFileModificationInvalidatesModCache() {
        val mod = LocalMod(
            id = "lithium",
            name = "Lithium",
            version = "0.12.0",
            fileName = tempModFile.name,
            fileSize = tempModFile.length()
        )
        IncrementalModIndexer.put(tempModFile, mod)
        assertNotNull(IncrementalModIndexer.getCached(tempModFile))

        // Modify file
        Thread.sleep(20)
        tempModFile.appendText("new bytes")

        assertNull(IncrementalModIndexer.getCached(tempModFile))
    }
}
