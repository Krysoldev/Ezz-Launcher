package io.ezz.launcher.core.runtime.detector

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HardwareDetectorTest {

    @Test
    fun testHardwareDetection() {
        val profile = HardwareDetector.detectHardware()

        assertNotNull(profile.cpuModel)
        assertTrue(profile.cpuModel.isNotBlank(), "CPU model should not be blank")
        assertTrue(profile.cpuCores >= 1, "CPU cores should be >= 1")
        assertTrue(profile.totalRamMb >= 1024, "System RAM should be >= 1 GB")
        assertTrue(profile.recommendedMaxRamMb >= profile.recommendedMinRamMb, "Max RAM should be >= Min RAM")
        assertTrue(profile.recommendedMaxRamMb <= profile.totalRamMb, "Max RAM should not exceed total RAM")
        assertNotNull(profile.primaryGpu)
        assertNotNull(profile.recommendedProfile)
        assertNotNull(profile.recommendedGpuPreference)
    }
}
