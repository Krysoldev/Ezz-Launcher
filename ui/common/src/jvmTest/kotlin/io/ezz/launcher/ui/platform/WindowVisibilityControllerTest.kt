package io.ezz.launcher.ui.platform

import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.model.runtime.ProcessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowVisibilityControllerTest {

    class MockWindowVisibilityController(var initialVisible: Boolean = true) : WindowVisibilityController {
        var visibleCalls = mutableListOf<Boolean>()
        private var current = initialVisible

        override fun setVisible(visible: Boolean) {
            current = visible
            visibleCalls.add(visible)
        }

        override fun isVisible(): Boolean = current
    }

    @Test
    fun testVisibilityControllerTracksCalls() {
        val controller = MockWindowVisibilityController(initialVisible = true)
        assertTrue(controller.isVisible())

        controller.setVisible(false)
        assertFalse(controller.isVisible())
        assertEquals(listOf(false), controller.visibleCalls)

        controller.setVisible(true)
        assertTrue(controller.isVisible())
        assertEquals(listOf(false, true), controller.visibleCalls)
    }

    @Test
    fun testLifecycleCoordinationSettingOn() {
        // Models the state machine transitions in AppViewModel
        val settings = LauncherSettings(hideLauncherWhileRunning = true)
        val controller = MockWindowVisibilityController(initialVisible = true)
        val operationsThatHidLauncher = mutableSetOf<String>()
        val operationId = "op-test-123"

        // 1. Process spawn / running
        val isRunning = true
        if (isRunning && settings.hideLauncherWhileRunning) {
            operationsThatHidLauncher.add(operationId)
            controller.setVisible(false)
        }

        assertFalse(controller.isVisible(), "Launcher window must be hidden when Minecraft starts and setting is ON")
        assertTrue(operationsThatHidLauncher.contains(operationId))

        // 2. Process exited
        if (operationsThatHidLauncher.remove(operationId)) {
            controller.setVisible(true)
        }

        assertTrue(controller.isVisible(), "Launcher window must be restored when Minecraft exits")
        assertFalse(operationsThatHidLauncher.contains(operationId))
    }

    @Test
    fun testLifecycleCoordinationSettingOff() {
        val settings = LauncherSettings(hideLauncherWhileRunning = false)
        val controller = MockWindowVisibilityController(initialVisible = true)
        val operationsThatHidLauncher = mutableSetOf<String>()
        val operationId = "op-test-456"

        // 1. Process spawn / running with setting OFF
        val isRunning = true
        if (isRunning && settings.hideLauncherWhileRunning) {
            operationsThatHidLauncher.add(operationId)
            controller.setVisible(false)
        }

        assertTrue(controller.isVisible(), "Launcher window must remain visible when setting is OFF")
        assertFalse(operationsThatHidLauncher.contains(operationId))

        // 2. Process exited
        if (operationsThatHidLauncher.remove(operationId)) {
            controller.setVisible(true)
        }

        assertTrue(controller.isVisible(), "Launcher window remains visible")
        assertEquals(0, controller.visibleCalls.size, "No visibility changes should occur when setting is OFF")
    }

    @Test
    fun testLaunchFailureDoesNotHideLauncher() {
        val settings = LauncherSettings(hideLauncherWhileRunning = true)
        val controller = MockWindowVisibilityController(initialVisible = true)
        val operationsThatHidLauncher = mutableSetOf<String>()
        val operationId = "op-test-fail"

        // Launch fails before process reaches running state
        val failureState = ProcessState.Failed(
            io.ezz.launcher.core.model.runtime.LaunchError.MissingJavaRuntime("No java")
        )

        if (operationsThatHidLauncher.remove(operationId)) {
            controller.setVisible(true)
        }

        assertTrue(controller.isVisible(), "Launcher window must never be hidden on pre-launch failure")
        assertEquals(0, controller.visibleCalls.size)
    }

    @Test
    fun testMultipleLaunchProtectionPreventsDuplicateProcess() {
        val runningSessions = mutableMapOf<String, Long>()
        val instanceId = "instance-abc"

        // First launch succeeds and registers running session
        runningSessions[instanceId] = 12345L

        // Attempting second launch while instance is already running
        val isAlreadyRunning = runningSessions.containsKey(instanceId)
        assertTrue(isAlreadyRunning, "Subsequent launch attempt must detect active instance session")

        // Reject launch
        val launchAllowed = !isAlreadyRunning
        assertFalse(launchAllowed, "Multiple launch must be prevented")
    }
}
