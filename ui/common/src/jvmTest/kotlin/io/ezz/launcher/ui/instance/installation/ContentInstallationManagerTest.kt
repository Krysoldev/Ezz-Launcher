package io.ezz.launcher.ui.instance.installation

import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.network.curseforge.CurseForgeService
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import io.ezz.launcher.ui.instance.installation.model.ContentType
import io.ezz.launcher.ui.instance.installation.model.InstallationStage
import io.ezz.launcher.ui.instance.installation.service.ContentInstallationManager
import io.ezz.launcher.ui.instance.installation.ui.ImportStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentInstallationManagerTest {

    private val testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    private fun createDummyManager(): ContentInstallationManager {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ezz_test_inst_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        return ContentInstallationManager(
            curseForgeService = CurseForgeService(apiKeyProvider = { "dummy_test_key" }),
            getInstanceDir = { tempDir },
            getInstalledMods = { emptyList() },
            onContentChanged = {},
            scope = testScope
        )
    }

    @Test
    fun testInitialQueueState() {
        val manager = createDummyManager()
        val queue = manager.queueState.value

        assertEquals(0, queue.totalActiveCount, "Active items must start empty")
        assertEquals(0, queue.completedHistory.size, "Completed history must start empty")
        assertFalse(queue.isDrawerOpen, "Drawer must start closed")
        assertNull(manager.focusedItem.value, "Focused item must start null")
    }

    @Test
    fun testDrawerControls() {
        val manager = createDummyManager()
        assertFalse(manager.queueState.value.isDrawerOpen)

        manager.openDrawer()
        assertTrue(manager.queueState.value.isDrawerOpen)

        manager.closeDrawer()
        assertFalse(manager.queueState.value.isDrawerOpen)

        manager.toggleDrawer()
        assertTrue(manager.queueState.value.isDrawerOpen)

        manager.toggleDrawer()
        assertFalse(manager.queueState.value.isDrawerOpen)
    }

    @Test
    fun testCancelInstallationMovesToHistory() {
        val manager = createDummyManager()
        val inst = Instance(id = "test-1", name = "Test Fabric", minecraftVersion = "1.21.1", loaderType = LoaderType.FABRIC)
        val mod = CurseForgeMod(id = 12345L, name = "Sodium", slug = "sodium", summary = "Rendering engine")

        val opId = manager.startCurseForgeModInstall(inst, mod)
        assertNotNull(opId)

        val queueWithActive = manager.queueState.value
        assertEquals(1, queueWithActive.activeItems.size)
        assertEquals("Sodium", queueWithActive.activeItems.first().name)

        // Cancel
        manager.cancelInstallation(opId)

        val queueAfterCancel = manager.queueState.value
        assertEquals(0, queueAfterCancel.activeItems.size, "Cancelled item must be removed from active items")
        assertEquals(1, queueAfterCancel.completedHistory.size, "Cancelled item must be added to history")
        assertEquals(InstallationStage.CANCELLED, queueAfterCancel.completedHistory.first().stage)

        // Clear completed
        manager.clearCompleted()
        assertEquals(0, manager.queueState.value.completedHistory.size, "History must be cleared")
    }

    @Test
    fun testFormatBytesUtility() {
        assertEquals("0 B", ContentInstallationItem.formatBytes(0))
        assertEquals("0 B", ContentInstallationItem.formatBytes(-10))
        assertEquals("0 B", ContentInstallationItem.formatBytes(0))
        assertEquals("500 B", ContentInstallationItem.formatBytes(500))
        assertEquals("1.0 KB", ContentInstallationItem.formatBytes(1024))
        assertEquals("2.5 MB", ContentInstallationItem.formatBytes(2621440))
        assertEquals("1.5 GB", ContentInstallationItem.formatBytes(1610612736))
    }

    @Test
    fun testInstallationStageProgression() {
        val stages = listOf(
            InstallationStage.SELECTED,
            InstallationStage.VALIDATING,
            InstallationStage.RESOLVING_DEPENDENCIES,
            InstallationStage.DOWNLOADING_MAIN,
            InstallationStage.DOWNLOADING_DEPENDENCIES,
            InstallationStage.INSTALLING,
            InstallationStage.VERIFYING,
            InstallationStage.COMPLETED
        )

        for (i in 0 until stages.size - 1) {
            assertTrue(
                stages[i].stepNumber < stages[i + 1].stepNumber,
                "Stage ${stages[i]} stepNumber must be less than ${stages[i+1]}"
            )
        }
    }

    @Test
    fun testLocalImportStageProgression() {
        val stages = listOf(
            ImportStage.SELECTED,
            ImportStage.READING,
            ImportStage.VALIDATING,
            ImportStage.INSTALLING,
            ImportStage.COMPLETE
        )

        for (i in 0 until stages.size - 1) {
            assertTrue(
                stages[i].stepNumber < stages[i + 1].stepNumber,
                "Import stage ${stages[i]} must be sequentially earlier than ${stages[i+1]}"
            )
        }
    }
}
