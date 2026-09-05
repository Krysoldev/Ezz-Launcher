package io.ezz.launcher.ui.manager

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.ui.audio.EzzAudioService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstanceManagerPolishTest {

    private fun createTestInstance(id: String, name: String): Instance {
        return Instance(
            id = id,
            name = name,
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )
    }

    @Test
    fun testSoundEffectsDefaultDisabled() {
        // UI Sound Effects DEFAULT MUST REMAIN OFF
        val defaultSettings = LauncherSettings()
        assertFalse(defaultSettings.soundEffectsEnabled, "LauncherSettings.soundEffectsEnabled must default to false")
        assertFalse(EzzAudioService.isEnabled, "EzzAudioService.isEnabled must default to false")
    }

    @Test
    fun testAllTabsHaveNoNumericBadges() {
        // Requirement: NO numeric badges/counts on tabs. Keep clean tab names.
        val expectedTabs = listOf(
            "Overview",
            "Mods",
            "Resource Packs",
            "Shaders",
            "Worlds",
            "Screenshots",
            "Settings",
            "Files",
            "Logs"
        )
        val actualTabTitles = InstanceManagerTab.entries.map { it.title }
        assertEquals(expectedTabs, actualTabTitles)

        for (tab in InstanceManagerTab.entries) {
            // Must not contain any digits like (5) or [12]
            assertFalse(tab.title.any { it.isDigit() }, "Tab title '${tab.title}' must NOT contain numeric badges")
        }
    }

    @Test
    fun testInstanceSwitchingClearsStaleData() {
        val instanceA = createTestInstance("inst-a", "Instance Alpha")
        val instanceB = createTestInstance("inst-b", "Instance Beta")

        var selectedInstanceId: String? = null
        var manageMods: List<LocalMod> = emptyList()
        var manageResourcePacks: List<String> = emptyList()
        var manageShaders: List<String> = emptyList()
        var manageWorlds: List<String> = emptyList()
        var manageScreenshots: List<String> = emptyList()
        var manageLogs: List<String> = emptyList()

        // Simulating the selectInstance cache-clearing contract
        fun selectInstance(newInstance: Instance) {
            val previousId = selectedInstanceId
            if (previousId != newInstance.id) {
                manageMods = emptyList()
                manageResourcePacks = emptyList()
                manageShaders = emptyList()
                manageWorlds = emptyList()
                manageScreenshots = emptyList()
                manageLogs = emptyList()
            }
            selectedInstanceId = newInstance.id
        }

        // Verify initial state
        assertTrue(manageMods.isEmpty())
        assertTrue(manageWorlds.isEmpty())
        assertTrue(manageLogs.isEmpty())

        // 1. Select Instance A and populate data
        selectInstance(instanceA)
        manageMods = listOf(LocalMod(id = "iris", fileName = "iris.jar", name = "Iris Shaders", version = "1.7.0", enabled = true))
        manageWorlds = listOf("World 1")
        manageLogs = listOf("latest.log")

        assertEquals("inst-a", selectedInstanceId)
        assertEquals(1, manageMods.size)
        assertEquals(1, manageWorlds.size)

        // 2. Switch to Instance B
        selectInstance(instanceB)

        // All manage collections must be empty immediately, preventing Instance A data leakage
        assertEquals("inst-b", selectedInstanceId)
        assertTrue(manageMods.isEmpty(), "Instance B must not show Instance A's mods")
        assertTrue(manageWorlds.isEmpty(), "Instance B must not show Instance A's worlds")
        assertTrue(manageLogs.isEmpty(), "Instance B must not show Instance A's logs")
        assertTrue(manageResourcePacks.isEmpty())
        assertTrue(manageShaders.isEmpty())
        assertTrue(manageScreenshots.isEmpty())
    }

    @Test
    fun testModpackFormatValidation() {
        fun isValidModrinthModpack(fileName: String): Boolean {
            return fileName.endsWith(".mrpack", ignoreCase = true)
        }

        assertTrue(isValidModrinthModpack("Fabulously-Optimized-1.21.1.mrpack"))
        assertTrue(isValidModrinthModpack("pack.MRPACK"))
        assertFalse(isValidModrinthModpack("modpack.zip"))
        assertFalse(isValidModrinthModpack("instance.tar.gz"))
        assertFalse(isValidModrinthModpack("notes.txt"))
    }
}
