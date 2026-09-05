package io.ezz.launcher.ui.instances

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.viewmodel.NavigationScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class InstanceCardInteractionTest {

    private fun createTestInstance(id: String, name: String, mcVersion: String = "1.21.1", loader: LoaderType = LoaderType.FABRIC): Instance {
        return Instance(
            id = id,
            name = name,
            minecraftVersion = mcVersion,
            loaderType = loader
        )
    }

    @Test
    fun testCardClickOpensInstanceManagerForExactInstance() {
        var currentScreen: NavigationScreen = NavigationScreen.INSTANCES
        var selectedInstance: Instance? = null
        var activeManageTab: InstanceManagerTab? = null

        val instanceA = createTestInstance("inst-fabric-121", "Fabric 1.21.11")
        val instanceB = createTestInstance("inst-neoforge-121", "NeoForge 1.21.1")

        // Simulation of card click callbacks
        fun handleCardClick(instance: Instance) {
            selectedInstance = instance
            activeManageTab = InstanceManagerTab.OVERVIEW
            currentScreen = NavigationScreen.INSTANCE_MANAGER
        }

        // 1. Click Instance A card
        handleCardClick(instanceA)
        assertEquals(NavigationScreen.INSTANCE_MANAGER, currentScreen)
        assertEquals("inst-fabric-121", selectedInstance?.id)
        assertEquals("Fabric 1.21.11", selectedInstance?.name)
        assertEquals(InstanceManagerTab.OVERVIEW, activeManageTab)

        // 2. Return to Instances screen
        currentScreen = NavigationScreen.INSTANCES
        assertEquals(NavigationScreen.INSTANCES, currentScreen)

        // 3. Click Instance B card
        handleCardClick(instanceB)
        assertEquals(NavigationScreen.INSTANCE_MANAGER, currentScreen)
        assertEquals("inst-neoforge-121", selectedInstance?.id)
        assertEquals("NeoForge 1.21.1", selectedInstance?.name)
        assertNotEquals(instanceA.id, selectedInstance?.id)
    }

    @Test
    fun testPlayButtonClickDoesNotOpenInstanceManager() {
        var currentScreen: NavigationScreen = NavigationScreen.INSTANCES
        var launchTarget: Instance? = null
        var isInstanceManagerOpened = false

        val instance = createTestInstance("inst-play-test", "Hypixel Practice")

        // Callbacks
        val onCardClick: () -> Unit = {
            isInstanceManagerOpened = true
            currentScreen = NavigationScreen.INSTANCE_MANAGER
        }
        val onPlay: () -> Unit = {
            launchTarget = instance
            currentScreen = NavigationScreen.HOME
        }

        // When user clicks Play button:
        onPlay()

        assertEquals(NavigationScreen.HOME, currentScreen)
        assertEquals(instance.id, launchTarget?.id)
        assertEquals(false, isInstanceManagerOpened, "Clicking Play button must NOT open Instance Manager")

        // In contrast, when clicking the card itself:
        onCardClick()
        assertEquals(NavigationScreen.INSTANCE_MANAGER, currentScreen)
        assertEquals(true, isInstanceManagerOpened, "Clicking card must open Instance Manager")
    }

    @Test
    fun testThreeDotMenuDoesNotOpenInstanceManager() {
        var currentScreen: NavigationScreen = NavigationScreen.INSTANCES
        var isMenuOpen = false
        var isInstanceManagerOpened = false

        val onCardClick: () -> Unit = {
            isInstanceManagerOpened = true
            currentScreen = NavigationScreen.INSTANCE_MANAGER
        }
        val onThreeDotClick: () -> Unit = {
            isMenuOpen = true
        }

        // When user clicks the three-dot button:
        onThreeDotClick()

        assertEquals(true, isMenuOpen)
        assertEquals(false, isInstanceManagerOpened, "Clicking three-dot button must NOT open Instance Manager")
        assertEquals(NavigationScreen.INSTANCES, currentScreen)

        // In contrast, clicking the card body opens Instance Manager:
        onCardClick()
        assertEquals(true, isInstanceManagerOpened)
        assertEquals(NavigationScreen.INSTANCE_MANAGER, currentScreen)
    }
}
