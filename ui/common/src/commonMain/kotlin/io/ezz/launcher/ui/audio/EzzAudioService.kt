package io.ezz.launcher.ui.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * EzzAudioService — High-performance, non-blocking UI sound engine for Ezz Launcher.
 *
 * Core Principles:
 * 1. 100% In-Memory PCM: All waveforms synthesized once at initialization. Zero disk I/O, zero network calls.
 * 2. Non-blocking async dispatch: Audio playback is launched on Dispatchers.IO, never impacting frame rendering.
 * 3. Graceful degradation: If audio device is busy or unavailable, safely ignores errors without throwing.
 * 4. Micro-sound design: Sounds are extremely short (8ms–350ms), modern, subtle, and tactile.
 * 5. Debounce: Hover sounds are throttled to prevent spam when rapidly moving across lists.
 */
object EzzAudioService {

    var isEnabled: Boolean = false
    var volume: Float = 0.5f

    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var lastHoverTimestamp: Long = 0L
    private const val HOVER_DEBOUNCE_MS = 60L

    private enum class SoundEffect {
        HOVER,
        CLICK,
        SELECT,
        CONFIRMATION,
        LAUNCH,
        ERROR
    }

    private val clipCache = ConcurrentHashMap<SoundEffect, ByteArray>()
    @Volatile
    private var isSynthesized = false

    private fun ensureSynthesized() {
        if (!isSynthesized) {
            synchronized(this) {
                if (!isSynthesized) {
                    try {
                        synthesizeAll()
                        isSynthesized = true
                    } catch (t: Throwable) {
                        println("[EzzAudioService] Note: Audio synthesis initialized with warning: ${t.message}")
                    }
                }
            }
        }
    }

    fun updateSettings(enabled: Boolean, vol: Float) {
        isEnabled = enabled
        volume = vol.coerceIn(0.0f, 1.0f)
        if (enabled && volume > 0.01f) {
            audioScope.launch {
                ensureSynthesized()
            }
        }
    }

    fun playHover() {
        if (!isEnabled || volume <= 0.01f) return
        val now = System.currentTimeMillis()
        if (now - lastHoverTimestamp < HOVER_DEBOUNCE_MS) return
        lastHoverTimestamp = now
        playSound(SoundEffect.HOVER, gainFactor = 0.45f)
    }

    fun playClick() {
        if (!isEnabled || volume <= 0.01f) return
        playSound(SoundEffect.CLICK, gainFactor = 0.75f)
    }

    fun playSelect() {
        if (!isEnabled || volume <= 0.01f) return
        playSound(SoundEffect.SELECT, gainFactor = 0.8f)
    }

    fun playConfirmation() {
        if (!isEnabled || volume <= 0.01f) return
        playSound(SoundEffect.CONFIRMATION, gainFactor = 0.85f)
    }

    fun playLaunch() {
        if (!isEnabled || volume <= 0.01f) return
        playSound(SoundEffect.LAUNCH, gainFactor = 1.0f)
    }

    fun playError() {
        if (!isEnabled || volume <= 0.01f) return
        playSound(SoundEffect.ERROR, gainFactor = 0.7f)
    }

    private fun playSound(effect: SoundEffect, gainFactor: Float) {
        audioScope.launch {
            try {
                ensureSynthesized()
                val wavBytes = clipCache[effect] ?: return@launch
                val audioStream: AudioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wavBytes))
                val clip: Clip = AudioSystem.getClip()
                clip.open(audioStream)

                // Master Volume / Gain control
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    val effectiveVol = (volume * gainFactor).coerceIn(0.001f, 1.0f)
                    // Convert linear amplitude to decibels: 20 * log10(vol)
                    val db = (20.0 * kotlin.math.log10(effectiveVol.toDouble())).toFloat()
                    val clampedDb = db.coerceIn(gainControl.minimum, gainControl.maximum)
                    gainControl.value = clampedDb
                }

                clip.start()
                clip.addLineListener { event ->
                    if (event.type == javax.sound.sampled.LineEvent.Type.STOP) {
                        try {
                            clip.close()
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
                // Ignore audio playback failures (e.g. headless, audio driver sleeping)
            }
        }
    }

    private fun synthesizeAll() {
        clipCache[SoundEffect.HOVER] = generateHoverWav()
        clipCache[SoundEffect.CLICK] = generateClickWav()
        clipCache[SoundEffect.SELECT] = generateSelectWav()
        clipCache[SoundEffect.CONFIRMATION] = generateConfirmationWav()
        clipCache[SoundEffect.LAUNCH] = generateLaunchWav()
        clipCache[SoundEffect.ERROR] = generateErrorWav()
    }

    // =========================================================================
    // Waveform Synthesizers (44.1kHz, 16-bit Mono PCM formatted as valid WAV)
    // =========================================================================

    private const val SAMPLE_RATE = 44100

    /**
     * Soft, high-frequency tick: 12ms burst at 1400Hz with exponential envelope.
     */
    private fun generateHoverWav(): ByteArray {
        val durationMs = 12
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 350.0)
            val sample = sin(2.0 * PI * 1400.0 * t) * envelope
            pcm[i] = (sample * 8000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    /**
     * Tactile crisp micro-click: 18ms damped pitch-drop from 850Hz to 380Hz.
     */
    private fun generateClickWav(): ByteArray {
        val durationMs = 18
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / (durationMs / 1000.0)
            val freq = 850.0 - (470.0 * progress)
            val envelope = exp(-t * 220.0)
            val sample = sin(2.0 * PI * freq * t) * envelope
            pcm[i] = (sample * 16000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    /**
     * Subtle UI selection pop: 22ms upward pitch slide 500Hz -> 820Hz.
     */
    private fun generateSelectWav(): ByteArray {
        val durationMs = 22
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / (durationMs / 1000.0)
            val freq = 500.0 + (320.0 * progress)
            val envelope = exp(-t * 180.0)
            val sample = sin(2.0 * PI * freq * t) * envelope
            pcm[i] = (sample * 18000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    /**
     * Smooth clean dual chime: 110ms dual harmonic (523Hz C5 + 784Hz G5).
     */
    private fun generateConfirmationWav(): ByteArray {
        val durationMs = 110
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 30.0)
            val tone1 = sin(2.0 * PI * 523.25 * t)
            val tone2 = sin(2.0 * PI * 783.99 * t) * 0.7
            val sample = ((tone1 + tone2) / 1.7) * envelope
            pcm[i] = (sample * 19000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    /**
     * Futuristic ascending launch chime: 320ms arpeggio (440Hz A4 -> 659Hz E5 -> 880Hz A5).
     */
    private fun generateLaunchWav(): ByteArray {
        val durationMs = 320
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val note = when {
                t < 0.08 -> 440.0
                t < 0.16 -> 659.25
                else -> 880.0
            }
            val noteTime = when {
                t < 0.08 -> t
                t < 0.16 -> t - 0.08
                else -> t - 0.16
            }
            val envelope = exp(-noteTime * 18.0) * (1.0 - exp(-noteTime * 200.0))
            val sample = sin(2.0 * PI * note * t) * envelope
            pcm[i] = (sample * 22000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    /**
     * Muted low error tone: 140ms double-frequency tone (220Hz + 330Hz).
     */
    private fun generateErrorWav(): ByteArray {
        val durationMs = 140
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * 22.0)
            val tone1 = sin(2.0 * PI * 220.0 * t)
            val tone2 = sin(2.0 * PI * 330.0 * t) * 0.5
            val sample = ((tone1 + tone2) / 1.5) * envelope
            pcm[i] = (sample * 16000).toInt().toShort()
        }
        return pcmToWav(pcm)
    }

    private fun pcmToWav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize
        val buffer = ByteBuffer.allocate(44 + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        buffer.putInt(totalSize)
        buffer.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())

        // fmt subchunk
        buffer.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat 1 = PCM
        buffer.putShort(1) // NumChannels = 1 (Mono)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        buffer.putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
        buffer.putShort(16) // BitsPerSample = 16

        // data subchunk
        buffer.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        buffer.putInt(dataSize)

        for (s in samples) {
            buffer.putShort(s)
        }

        return buffer.array()
    }
}
