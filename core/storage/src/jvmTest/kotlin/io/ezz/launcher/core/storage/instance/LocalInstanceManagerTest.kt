package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
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

class LocalInstanceManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository
    private lateinit var manager: LocalInstanceManager

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_test_manager", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalInstanceRepository(pathProvider)
        manager = LocalInstanceManager(pathProvider, repository)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testModScanningAndToggling() = runBlocking {
        val instance = repository.createInstance(
            name = "Mod Test Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
        val modsDir = File(gameDir, "mods").apply { mkdirs() }

        // Create a dummy mod jar
        val modJar = File(modsDir, "sodium-fabric-0.5.8.jar")
        ZipOutputStream(FileOutputStream(modJar)).use { zos ->
            zos.putNextEntry(ZipEntry("fabric.mod.json"))
            val fabricJson = """{"id": "sodium", "name": "Sodium", "version": "0.5.8", "authors": ["jellysquid3"]}"""
            zos.write(fabricJson.toByteArray())
            zos.closeEntry()
        }

        // 1. Scan mods
        val initialMods = manager.getMods(instance.id)
        assertEquals(1, initialMods.size)
        assertEquals("Sodium", initialMods[0].name)
        assertEquals("0.5.8", initialMods[0].version)
        assertTrue(initialMods[0].enabled)

        // 2. Disable mod
        val toggleRes = manager.toggleMod(instance.id, "sodium-fabric-0.5.8.jar", enable = false)
        assertTrue(toggleRes)

        val afterDisableMods = manager.getMods(instance.id)
        assertEquals(1, afterDisableMods.size)
        assertFalse(afterDisableMods[0].enabled)
        assertEquals("sodium-fabric-0.5.8.jar.disabled", afterDisableMods[0].fileName)

        // 3. Enable mod back
        val enableRes = manager.toggleMod(instance.id, "sodium-fabric-0.5.8.jar.disabled", enable = true)
        assertTrue(enableRes)

        val afterEnableMods = manager.getMods(instance.id)
        assertEquals(1, afterEnableMods.size)
        assertTrue(afterEnableMods[0].enabled)
        assertEquals("sodium-fabric-0.5.8.jar", afterEnableMods[0].fileName)

        // 4. Delete mod
        val delRes = manager.deleteMod(instance.id, "sodium-fabric-0.5.8.jar")
        assertTrue(delRes)
        assertEquals(0, manager.getMods(instance.id).size)
    }

    @Test
    fun testWorldBackupRestoreAndDuplicate() = runBlocking {
        val instance = repository.createInstance(
            name = "World Test Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.VANILLA
        )

        val gameDir = pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
        val savesDir = File(gameDir, "saves").apply { mkdirs() }
        val worldDir = File(savesDir, "My_Survival_World").apply { mkdirs() }
        File(worldDir, "level.dat").writeText("dummy nbt binary data")

        // 1. Scan worlds
        val worlds = manager.getWorlds(instance.id)
        assertEquals(1, worlds.size)
        assertEquals("My_Survival_World", worlds[0].folderName)

        // 2. Create World Backup
        val backup = manager.backupWorld(instance.id, instance.name, "My_Survival_World")
        assertNotNull(backup)
        assertTrue(File(backup.filePath).exists())

        // 3. List backups
        val backupsList = manager.getWorldBackups(instance.name, "My_Survival_World")
        assertEquals(1, backupsList.size)
        assertEquals(backup.fileName, backupsList[0].fileName)

        // 4. Duplicate World
        val dupRes = manager.duplicateWorld(instance.id, "My_Survival_World", "My_Survival_World_Copy")
        assertTrue(dupRes)
        val afterDupWorlds = manager.getWorlds(instance.id)
        assertEquals(2, afterDupWorlds.size)

        // 5. Delete original and restore from backup
        manager.deleteWorld(instance.id, "My_Survival_World")
        assertEquals(1, manager.getWorlds(instance.id).size)

        val restoreRes = manager.restoreWorldBackup(instance.id, backup.filePath, "My_Survival_World")
        assertTrue(restoreRes)
        assertEquals(2, manager.getWorlds(instance.id).size)
    }

    @Test
    fun testInstanceRepairReport() = runBlocking {
        val instance = repository.createInstance(
            name = "Repair Test Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val report = manager.repairInstance(instance)
        assertNotNull(report)
        assertTrue(report.passed.isNotEmpty())
        assertTrue(report.isHealthy)
    }

    @Test
    fun testInstanceDuplicationWithOptions() = runBlocking {
        val source = repository.createInstance(
            name = "Original Instance",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val srcGameDir = pathProvider.getInstanceDirectory(source.id).resolve(".minecraft").toFile()
        val savesDir = File(srcGameDir, "saves").apply { mkdirs() }
        File(File(savesDir, "World1").apply { mkdirs() }, "level.dat").writeText("test")

        // Duplicate with worlds included
        val dupWithWorlds = manager.duplicateInstance(source, "Clone With Worlds", includeWorlds = true)
        val dupWorlds = manager.getWorlds(dupWithWorlds.id)
        assertEquals(1, dupWorlds.size)

        // Duplicate without worlds included
        val dupNoWorlds = manager.duplicateInstance(source, "Clone Without Worlds", includeWorlds = false)
        val noWorlds = manager.getWorlds(dupNoWorlds.id)
        assertEquals(0, noWorlds.size)
    }
}
