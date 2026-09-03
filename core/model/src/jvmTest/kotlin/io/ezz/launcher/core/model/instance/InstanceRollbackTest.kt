package io.ezz.launcher.core.model.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstanceRollbackTest {

    @Test
    fun testSnapshotAndRollback() {
        val originalInstance = Instance(
            id = "inst-test",
            name = "Stable Instance",
            minecraftVersion = "1.21.1",
            minMemoryMb = 2048,
            maxMemoryMb = 6144,
            customJvmArgs = listOf("-XX:+UseG1GC"),
            performanceProfile = PerformanceProfile.PERFORMANCE,
            gpuPreference = GpuPreference.HIGH_PERFORMANCE,
            processPriority = ProcessPriority.ABOVE_NORMAL,
            gcType = GarbageCollectorType.G1GC,
            fpsMode = FpsMode.UNLIMITED,
            customFpsLimit = 260
        )

        // Capture snapshot
        val snapshot = originalInstance.createPerformanceSnapshot()
        assertNotNull(snapshot)
        assertEquals(6144, snapshot.maxMemoryMb)
        assertEquals(PerformanceProfile.PERFORMANCE, snapshot.performanceProfile)
        assertEquals(FpsMode.UNLIMITED, snapshot.fpsMode)
        assertEquals(260, snapshot.customFpsLimit)

        // User changes instance to dangerous/unstable settings
        val modifiedInstance = originalInstance.copy(
            maxMemoryMb = 32768,
            customJvmArgs = listOf("-XX:+CrashFlag"),
            performanceProfile = PerformanceProfile.MAX_FPS,
            gpuPreference = GpuPreference.POWER_SAVING,
            processPriority = ProcessPriority.NORMAL,
            gcType = GarbageCollectorType.SHENANDOAH,
            fpsMode = FpsMode.CUSTOM,
            customFpsLimit = 60,
            knownGoodSnapshot = snapshot
        )

        assertEquals(32768, modifiedInstance.maxMemoryMb)

        // Perform rollback
        val rolledBackInstance = modifiedInstance.rollbackToSnapshot(snapshot)

        assertEquals(6144, rolledBackInstance.maxMemoryMb)
        assertEquals(listOf("-XX:+UseG1GC"), rolledBackInstance.customJvmArgs)
        assertEquals(PerformanceProfile.PERFORMANCE, rolledBackInstance.performanceProfile)
        assertEquals(GpuPreference.HIGH_PERFORMANCE, rolledBackInstance.gpuPreference)
        assertEquals(ProcessPriority.ABOVE_NORMAL, rolledBackInstance.processPriority)
        assertEquals(GarbageCollectorType.G1GC, rolledBackInstance.gcType)
        assertEquals(FpsMode.UNLIMITED, rolledBackInstance.fpsMode)
        assertEquals(260, rolledBackInstance.customFpsLimit)
    }
}
