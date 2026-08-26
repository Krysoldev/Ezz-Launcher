package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstanceRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_test_storage", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalInstanceRepository(pathProvider)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCreateInstanceWithIsolatedDirectories() = runBlocking {
        val instance = repository.createInstance(
            name = "Test Survival",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.VANILLA,
            loaderVersion = null,
            minMemoryMb = 2048,
            maxMemoryMb = 4096,
            customJvmArgs = emptyList()
        )

        assertNotNull(instance.id)
        assertEquals("Test Survival", instance.name)
        assertEquals("1.21.4", instance.minecraftVersion)

        // Verify isolated directory structure on disk
        val instanceDir = pathProvider.getInstanceDirectory(instance.id)
        val mcDir = instanceDir.resolve(".minecraft")
        assertTrue(instanceDir.toFile().exists())
        assertTrue(mcDir.toFile().exists())
        assertTrue(mcDir.resolve("mods").toFile().exists())
        assertTrue(mcDir.resolve("resourcepacks").toFile().exists())
        assertTrue(mcDir.resolve("shaderpacks").toFile().exists())
    }

    @Test
    fun testUpdateInstance() = runBlocking {
        val created = repository.createInstance(
            name = "Vanilla Old",
            minecraftVersion = "1.20.1",
            loaderType = LoaderType.VANILLA
        )

        val updated = created.copy(name = "Vanilla Updated", maxMemoryMb = 8192)
        repository.updateInstance(updated)

        val fetched = repository.getInstance(created.id)
        assertNotNull(fetched)
        assertEquals("Vanilla Updated", fetched.name)
        assertEquals(8192, fetched.maxMemoryMb)
    }

    @Test
    fun testDeleteInstanceRemovesDirectory() = runBlocking {
        val created = repository.createInstance(
            name = "To Delete",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val instDir = pathProvider.getInstanceDirectory(created.id)
        assertTrue(instDir.toFile().exists())

        repository.deleteInstance(created.id)
        assertEquals(0, repository.instances.value.size)
        assertTrue(!instDir.toFile().exists())
    }

    @Test
    fun testDuplicateInstance() = runBlocking {
        val original = repository.createInstance(
            name = "Base Profile",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.FABRIC,
            loaderVersion = "0.16.10",
            maxMemoryMb = 6144
        )

        val duplicate = repository.duplicateInstance(original.id, "Base Profile (Copy)")
        assertNotNull(duplicate)
        assertEquals("Base Profile (Copy)", duplicate.name)
        assertEquals("1.21.4", duplicate.minecraftVersion)
        assertEquals(LoaderType.FABRIC, duplicate.loaderType)
        assertEquals("0.16.10", duplicate.loaderVersion)
        assertEquals(6144, duplicate.maxMemoryMb)

        assertEquals(2, repository.instances.value.size)
    }
}
