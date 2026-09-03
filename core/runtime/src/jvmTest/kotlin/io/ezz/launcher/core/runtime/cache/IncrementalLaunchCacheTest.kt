package io.ezz.launcher.core.runtime.cache

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalLaunchCacheTest {

    private lateinit var tempFile: File

    @BeforeTest
    fun setUp() {
        IncrementalLaunchCache.clear()
        tempFile = File.createTempFile("test_lib", ".jar")
        tempFile.writeText("fake jar content")
    }

    @AfterTest
    fun tearDown() {
        tempFile.delete()
        IncrementalLaunchCache.clear()
    }

    @Test
    fun testFileValidationAndCaching() {
        assertFalse(IncrementalLaunchCache.isFileValid(File("non_existent_file.jar")))

        assertTrue(IncrementalLaunchCache.isFileValid(tempFile, expectedSize = tempFile.length()))

        // Subsequent check should be instant hit
        assertTrue(IncrementalLaunchCache.isFileValid(tempFile))
    }

    @Test
    fun testFileModificationInvalidatesCache() {
        assertTrue(IncrementalLaunchCache.isFileValid(tempFile))

        // Modify file size & timestamp
        Thread.sleep(20)
        tempFile.appendText(" extra data")

        // Should detect mismatch if wrong size is provided
        assertFalse(IncrementalLaunchCache.isFileValid(tempFile, expectedSize = 5L))

        // Should re-validate with new size
        assertTrue(IncrementalLaunchCache.isFileValid(tempFile, expectedSize = tempFile.length()))
    }

    @Test
    fun testExplicitInvalidation() {
        IncrementalLaunchCache.markValid(tempFile)
        assertTrue(IncrementalLaunchCache.isFileValid(tempFile))

        IncrementalLaunchCache.invalidate(tempFile)
        IncrementalLaunchCache.clear()
    }
}
