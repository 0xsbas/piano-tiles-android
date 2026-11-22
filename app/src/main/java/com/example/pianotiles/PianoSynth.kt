package com.example.pianotiles

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

object PianoSynth {
    private const val SAMPLE_RATE = 44100
    private val cache = HashMap<Int, ShortArray>()

    fun playMidiNote(midi: Int, durationMs: Int = 380) {
        if (midi <= 0) return
        val key = midi * 10000 + durationMs
        val samples = cache.getOrPut(key) { generateSamples(midi, durationMs) }

        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 40L)
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun generateSamples(midi: Int, durationMs: Int): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(count)
        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (t < 0.008) t / 0.008 else 1.0
            val decay = exp(-4.8 * t)
            val env = attack * decay

            // Fundamental + 2nd and 3rd piano harmonics
            val wave = 0.65 * sin(2.0 * PI * freq * t) +
                0.25 * sin(2.0 * PI * freq * 2.0 * t) * exp(-2.0 * t) +
                0.10 * sin(2.0 * PI * freq * 3.0 * t) * exp(-4.0 * t)

            val value = (wave * env * 22000.0).toInt().coerceIn(-32767, 32767)
            buffer[i] = value.toShort()
        }
        return buffer
    }
}
