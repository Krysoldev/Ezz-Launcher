package io.ezz.launcher.ui.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class EzzAudioServiceTest {

    @Test
    fun testAudioServiceConfiguration() {
        EzzAudioService.updateSettings(enabled = true, vol = 0.8f)
        assertEquals(true, EzzAudioService.isEnabled)
        assertEquals(0.8f, EzzAudioService.volume)

        // Test volume clamping
        EzzAudioService.updateSettings(enabled = true, vol = 1.5f)
        assertEquals(1.0f, EzzAudioService.volume)

        EzzAudioService.updateSettings(enabled = false, vol = -0.2f)
        assertEquals(false, EzzAudioService.isEnabled)
        assertEquals(0.0f, EzzAudioService.volume)

        // Reset to default disabled
        EzzAudioService.updateSettings(enabled = false, vol = 0.5f)
    }

    @Test
    fun testPlayTriggersDoNotThrow() {
        EzzAudioService.updateSettings(enabled = true, vol = 0.5f)

        // Calling play methods should not throw any exceptions even if running in headless/mock environments
        EzzAudioService.playHover()
        EzzAudioService.playClick()
        EzzAudioService.playSelect()
        EzzAudioService.playConfirmation()
        EzzAudioService.playLaunch()
        EzzAudioService.playError()

        // Reset to default disabled
        EzzAudioService.updateSettings(enabled = false, vol = 0.5f)
    }
}
