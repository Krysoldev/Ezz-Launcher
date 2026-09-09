package io.ezz.launcher.ui.instance.content

import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.instance.LocalInstanceManager
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
import io.ezz.launcher.ui.instance.content.model.ContentLoadState
import io.ezz.launcher.ui.instance.content.service.InstanceContentHydrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstanceContentHydratorTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository
    private lateinit var manager: LocalInstanceManager
    private lateinit var hydrator: InstanceContentHydrator
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_hydrator_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalInstanceRepository(pathProvider)
        manager = LocalInstanceManager(pathProvider, repository)

        hydrator = InstanceContentHydrator(
            instanceManager = manager,
            getInstanceDir = { id -> pathProvider.getInstanceDirectory(id).toFile() },
            getInstance = { id -> runBlocking { repository.getInstance(id) } },
            scope = testScope,
            dispatcher = Dispatchers.Unconfined
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitialHydrationPopulatesContent() = runBlocking {
        val instance = repository.createInstance(
            name = "Hydration Test Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
        val modsDir = File(gameDir, "mods").apply { mkdirs() }
        val rpDir = File(gameDir, "resourcepacks").apply { mkdirs() }

        // Create a dummy mod jar
        val modJar = File(modsDir, "fabric-api-0.100.0.jar")
        ZipOutputStream(FileOutputStream(modJar)).use { zos ->
            zos.putNextEntry(ZipEntry("fabric.mod.json"))
            val fabricJson = """{"id": "fabric-api", "name": "Fabric API", "version": "0.100.0"}"""
            zos.write(fabricJson.toByteArray())
            zos.closeEntry()
        }

        // Create a dummy resource pack
        val rpZip = File(rpDir, "faithful-32x.zip")
        ZipOutputStream(FileOutputStream(rpZip)).use { zos ->
            zos.putNextEntry(ZipEntry("pack.mcmeta"))
            val mcmeta = """{"pack": {"pack_format": 34, "description": "Faithful 32x"}}"""
            zos.write(mcmeta.toByteArray())
            zos.closeEntry()
        }

        val job = hydrator.hydrateInstance(instance.id, forceRefresh = false)
        job.join()

        val state = hydrator.contentState.value
        assertNotNull(state)
        assertEquals(instance.id, state.instanceId)
        assertFalse(state.isHydrating)

        assertTrue(state.modsState is ContentLoadState.Success)
        val mods = (state.modsState as ContentLoadState.Success).data
        assertEquals(1, mods.size)
        assertEquals("Fabric API", mods[0].name)

        assertTrue(state.resourcePacksState is ContentLoadState.Success)
        val packs = (state.resourcePacksState as ContentLoadState.Success).data
        assertEquals(1, packs.size)
        assertEquals("faithful-32x", packs[0].name)

        assertTrue(state.statisticsState is ContentLoadState.Success)
        val stats = (state.statisticsState as ContentLoadState.Success).data
        assertEquals(1, stats.modsCount)
        assertEquals(1, stats.resourcePacksCount)
    }

    @Test
    fun testForceRefreshDetectsNewContent() = runBlocking {
        val instance = repository.createInstance(
            name = "Refresh Test",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
        val modsDir = File(gameDir, "mods").apply { mkdirs() }

        // Initially 0 mods
        hydrator.hydrateInstance(instance.id, forceRefresh = false).join()
        assertEquals(0, hydrator.contentState.value?.mods?.size)

        // Add a mod to disk
        val modJar = File(modsDir, "iris-1.7.0.jar")
        ZipOutputStream(FileOutputStream(modJar)).use { zos ->
            zos.putNextEntry(ZipEntry("fabric.mod.json"))
            val fabricJson = """{"id": "iris", "name": "Iris Shaders", "version": "1.7.0"}"""
            zos.write(fabricJson.toByteArray())
            zos.closeEntry()
        }

        // Force refresh
        hydrator.hydrateInstance(instance.id, forceRefresh = true).join()
        val refreshed = hydrator.contentState.value
        assertNotNull(refreshed)
        assertEquals(1, refreshed.mods.size)
        assertEquals("Iris Shaders", refreshed.mods[0].name)
    }

    @Test
    fun testEpochGuardsAgainstCrossInstanceStaleResults() = runBlocking {
        val instA = repository.createInstance(name = "Instance A", minecraftVersion = "1.20.1", loaderType = LoaderType.FABRIC)
        val instB = repository.createInstance(name = "Instance B", minecraftVersion = "1.21.1", loaderType = LoaderType.FABRIC)

        // Trigger hydration for A, immediately switch to B
        hydrator.hydrateInstance(instA.id, forceRefresh = false)
        val jobB = hydrator.hydrateInstance(instB.id, forceRefresh = false)
        jobB.join()

        val finalState = hydrator.contentState.value
        assertNotNull(finalState)
        assertEquals(instB.id, finalState.instanceId, "Final hydrated state must belong to the latest active instance B")
    }

    @Test
    fun testThreeStateProgression() {
        val initial = hydrator.contentState.value
        assertEquals(null, initial)

        // Starting hydration transitions into Loading state
        hydrator.hydrateInstance("test-inst-id", forceRefresh = true)
        val inFlight = hydrator.contentState.value
        assertNotNull(inFlight)
        assertTrue(inFlight.isHydrating)
        assertTrue(inFlight.modsState.isLoading)
        assertTrue(inFlight.resourcePacksState.isLoading)
        assertTrue(inFlight.shadersState.isLoading)
        assertTrue(inFlight.worldsState.isLoading)
        assertTrue(inFlight.screenshotsState.isLoading)
    }
}
