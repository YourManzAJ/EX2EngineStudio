package com.ex2.enginestudio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.ex2.enginestudio.engine.EngineSimulator
import kotlin.math.*

/**
 * Simple real-time engine sound generator for the first prototype.
 *
 * Uses additive synthesis (multiple sine/saw partials) driven by
 * the EngineSimulator state. This is intentionally lightweight so
 * it runs reliably on the EX2's Android 9 head unit.
 *
 * Future versions will layer real multi-samples + granular processing.
 */
class EngineAudioEngine(private val simulator: EngineSimulator) {

    companion object {
        private const val TAG = "EngineAudio"
        private const val SAMPLE_RATE = 22050          // Lower rate = less CPU on old head unit
        private const val BUFFER_SIZE_FACTOR = 2
    }

    enum class OutputMode { INSIDE, OUTSIDE, BOTH }

    @Volatile
    var outputMode: OutputMode = OutputMode.INSIDE

    @Volatile
    var masterVolume: Float = 0.7f

    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null
    @Volatile
    private var running = false

    // Synthesis state
    private var phase1 = 0.0
    private var phase2 = 0.0
    private var phase3 = 0.0
    private var phaseExhaust = 0.0

    fun start() {
        if (running) return
        running = true

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBuf * BUFFER_SIZE_FACTOR

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        renderThread = Thread({
            val buffer = ShortArray(bufferSize / 2)
            Log.i(TAG, "Render thread started")
            while (running) {
                fillBuffer(buffer)
                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Write error", e)
                    break
                }
            }
            Log.i(TAG, "Render thread stopped")
        }, "EngineAudioRender").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun stop() {
        running = false
        try {
            renderThread?.join(500)
        } catch (_: InterruptedException) {}
        renderThread = null

        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) {}
        }
        audioTrack = null
    }

    private fun fillBuffer(buffer: ShortArray) {
        if (!simulator.isRunning) {
            // Silence when engine is off
            buffer.fill(0)
            return
        }

        val rpm = simulator.currentRpm.coerceAtLeast(400f)
        val load = simulator.load
        val throttle = simulator.throttlePercent
        val profile = simulator.getProfile()

        // Fundamental frequency from RPM and cylinder count (firing frequency)
        val firingsPerRev = profile.cylinders / 2.0   // 4-stroke
        val fundamental = (rpm / 60.0) * firingsPerRev

        val vol = masterVolume * profile.baseIntensity * (0.35f + load * 0.65f)

        for (i in buffer.indices) {
            // Main engine partials
            val f1 = fundamental
            val f2 = fundamental * 2.0
            val f3 = fundamental * 3.05   // slight inharmonicity

            phase1 += 2.0 * PI * f1 / SAMPLE_RATE
            phase2 += 2.0 * PI * f2 / SAMPLE_RATE
            phase3 += 2.0 * PI * f3 / SAMPLE_RATE
            phaseExhaust += 2.0 * PI * (fundamental * 0.5) / SAMPLE_RATE

            if (phase1 > 2 * PI) phase1 -= 2 * PI
            if (phase2 > 2 * PI) phase2 -= 2 * PI
            if (phase3 > 2 * PI) phase3 -= 2 * PI
            if (phaseExhaust > 2 * PI) phaseExhaust -= 2 * PI

            // Simple waveforms (later replace with better wavetables / samples)
            var sample = 0.0
            sample += 0.45 * sin(phase1)
            sample += 0.25 * sin(phase2)
            sample += 0.12 * sin(phase3)

            // Exhaust "rumble" component
            sample += 0.18 * profile.exhaustCharacter * sin(phaseExhaust) * (0.6 + throttle * 0.4)

            // Very light noise for mechanical texture
            sample += (Math.random() - 0.5) * 0.04 * (0.3 + load * 0.7)

            // Soft clip
            sample = tanh(sample * 1.4)

            val out = (sample * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = out.toShort()
        }
    }

    /**
     * Placeholder for future exterior routing.
     * Currently everything goes to the default media stream (cabin).
     * Outside / Both modes are accepted but do not yet change the physical output.
     */
    fun setOutputMode(mode: OutputMode) {
        outputMode = mode
        Log.i(TAG, "Output mode set to $mode (exterior routing not yet implemented)")
    }
}
