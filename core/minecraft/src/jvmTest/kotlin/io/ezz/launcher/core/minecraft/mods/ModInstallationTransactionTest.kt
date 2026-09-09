package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.InstallationPlan
import io.ezz.launcher.core.model.instance.PlanActionType
import io.ezz.launcher.core.model.instance.PlanItem
import io.ezz.launcher.core.model.instance.ResolvedEnvironment
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModInstallationTransactionTest {

    private lateinit var tempBaseDir: File
    private lateinit var instanceDir: File
    private lateinit var modsDir: File
    private lateinit var environment: ResolvedEnvironment

    @BeforeTest
    fun setUp() {
        tempBaseDir = File(System.getProperty("java.io.tmpdir"), "ezz_tx_test_${UUID.randomUUID()}").apply { mkdirs() }
        instanceDir = File(tempBaseDir, "instance_1").apply { mkdirs() }
        modsDir = File(instanceDir, ".minecraft/mods").apply { mkdirs() }

        environment = ResolvedEnvironment(
            instanceId = "test-instance",
            minecraftVersion = "1.21.1",
            loader = "fabric",
            minecraftDirectoryPath = File(instanceDir, ".minecraft").absolutePath
        )
    }

    @AfterTest
    fun tearDown() {
        tempBaseDir.deleteRecursively()
    }

    private fun createValidJar(targetFile: File, fabricModId: String) {
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            zos.putNextEntry(ZipEntry("fabric.mod.json"))
            val meta = """{"schemaVersion": 1, "id": "$fabricModId", "version": "1.0.0"}"""
            zos.write(meta.toByteArray())
            zos.closeEntry()
        }
    }

    @Test
    fun `test successful atomic commit adds new mod and preserves existing`() = runBlocking {
        // Setup existing mod
        val existingJar = File(modsDir, "sodium-0.5.8.jar")
        createValidJar(existingJar, "sodium")

        val initialFiles = setOf(existingJar.name)
        val expectedFinal = setOf(existingJar.name, "lithium-0.12.0.jar")

        val plan = InstallationPlan(
            environment = environment,
            targetModId = "lithium",
            targetModName = "Lithium",
            selectedVersionNumber = "0.12.0",
            items = listOf(
                PlanItem(
                    action = PlanActionType.KEEP,
                    modId = "sodium",
                    modName = "Sodium",
                    currentVersion = "0.5.8",
                    fileName = existingJar.name
                ),
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = "lithium",
                    modName = "Lithium",
                    targetVersion = "0.12.0",
                    fileName = "lithium-0.12.0.jar",
                    downloadUrl = "mock://lithium.jar",
                    isPrimary = true
                )
            ),
            initialModFileNames = initialFiles,
            expectedFinalModFileNames = expectedFinal
        )

        val result = ModInstallationTransaction.execute(
            plan = plan,
            javaMajorVersion = null,
            downloader = { _, target, _ ->
                createValidJar(target, "lithium")
                true
            },
            onProgress = { _, _ -> }
        )

        assertTrue(result.isSuccess)
        // Verify both jars exist on disk
        assertTrue(File(modsDir, "sodium-0.5.8.jar").exists(), "Existing sodium jar must remain preserved")
        assertTrue(File(modsDir, "lithium-0.12.0.jar").exists(), "Newly installed lithium jar must exist")
        assertEquals(2, modsDir.listFiles()?.count { it.name.endsWith(".jar") })
    }

    @Test
    fun `test stale plan is rejected before download starts`() = runBlocking {
        val existingJar = File(modsDir, "sodium-0.5.8.jar")
        createValidJar(existingJar, "sodium")

        // Plan recorded initial files as empty
        val plan = InstallationPlan(
            environment = environment,
            targetModId = "lithium",
            targetModName = "Lithium",
            selectedVersionNumber = "0.12.0",
            items = listOf(
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = "lithium",
                    modName = "Lithium",
                    targetVersion = "0.12.0",
                    fileName = "lithium-0.12.0.jar",
                    downloadUrl = "mock://lithium.jar"
                )
            ),
            initialModFileNames = emptySet(), // Stale! Disk has sodium-0.5.8.jar
            expectedFinalModFileNames = setOf("lithium-0.12.0.jar")
        )

        var downloadCalled = false
        val result = ModInstallationTransaction.execute(
            plan = plan,
            downloader = { _, _, _ ->
                downloadCalled = true
                true
            },
            onProgress = { _, _ -> }
        )

        assertTrue(result.isFailure)
        assertFalse(downloadCalled, "Downloader must never be called on stale plan")
        assertTrue(existingJar.exists(), "Existing mod must remain completely untouched")
    }

    @Test
    fun `test download failure triggers rollback and leaves existing mods untouched`() = runBlocking {
        val existingJar = File(modsDir, "iris-1.7.0.jar")
        createValidJar(existingJar, "iris")

        val plan = InstallationPlan(
            environment = environment,
            targetModId = "broken-mod",
            targetModName = "Broken Mod",
            selectedVersionNumber = "1.0.0",
            items = listOf(
                PlanItem(
                    action = PlanActionType.KEEP,
                    modId = "iris",
                    modName = "Iris",
                    currentVersion = "1.7.0",
                    fileName = existingJar.name
                ),
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = "broken-mod",
                    modName = "Broken Mod",
                    targetVersion = "1.0.0",
                    fileName = "broken-mod-1.0.0.jar",
                    downloadUrl = "mock://broken.jar"
                )
            ),
            initialModFileNames = setOf(existingJar.name),
            expectedFinalModFileNames = setOf(existingJar.name, "broken-mod-1.0.0.jar")
        )

        val result = ModInstallationTransaction.execute(
            plan = plan,
            downloader = { _, _, _ ->
                // Simulate network error / partial file
                false
            },
            onProgress = { _, _ -> }
        )

        assertTrue(result.isFailure)
        // Existing jar must remain
        assertTrue(existingJar.exists())
        assertEquals(1, modsDir.listFiles()?.count { it.name.endsWith(".jar") })
    }

    @Test
    fun `test verification failure for corrupted jar triggers rollback`() = runBlocking {
        val existingJar = File(modsDir, "sodium-0.5.8.jar")
        createValidJar(existingJar, "sodium")

        val plan = InstallationPlan(
            environment = environment,
            targetModId = "corrupted-mod",
            targetModName = "Corrupted Mod",
            selectedVersionNumber = "1.0.0",
            items = listOf(
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = "corrupted-mod",
                    modName = "Corrupted Mod",
                    targetVersion = "1.0.0",
                    fileName = "corrupted.jar",
                    downloadUrl = "mock://corrupted.jar"
                )
            ),
            initialModFileNames = setOf(existingJar.name),
            expectedFinalModFileNames = setOf(existingJar.name, "corrupted.jar")
        )

        val result = ModInstallationTransaction.execute(
            plan = plan,
            downloader = { _, target, _ ->
                // Write garbage non-zip bytes
                target.writeText("THIS IS NOT A VALID ZIP ARCHIVE")
                true
            },
            onProgress = { _, _ -> }
        )

        assertTrue(result.isFailure)
        assertTrue(existingJar.exists(), "Sodium must remain intact")
        assertFalse(File(modsDir, "corrupted.jar").exists(), "Corrupted jar must not be in mods directory")
    }
}
