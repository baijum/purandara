package com.baijum.purandara.pitch

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.pitch.VoiceSignals.window
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** YIN on voice-like signals across the Carnatic vocal range (G1-ish to C6). */
class VoicePitchDetectionTest {
    /** 60 Hz to ~1 kHz in 3-semitone steps. */
    private val range = generateSequence(60.0) { it * 2.0.pow(3.0 / 12) }.takeWhile { it <= 1000.0 }.toList()

    private fun cents(target: Double, measured: Double) = Pitch.cents(target, measured)

    private fun steadyError(f0: Double, sampleRate: Int, fundamentalLevel: Double = 1.0): Double {
        val signal = VoiceSignals.voice(f0, 0.5, sampleRate, fundamentalLevel = fundamentalLevel)
        val result = assertNotNull(PitchDetector().detect(signal.window(sampleRate / 4), sampleRate), "$f0 Hz not detected")
        return cents(f0, result.frequencyHz)
    }

    @Test
    fun steadyVoiceWithinTwoCentsAcrossTheRange() {
        for (rate in listOf(44_100, 48_000)) {
            for (f0 in range) {
                val err = steadyError(f0, rate)
                assertTrue(abs(err) < 2.0, "$f0 Hz at $rate: $err cents")
            }
        }
    }

    @Test
    fun weakFundamentalDoesNotJumpAnOctave() {
        // Low voices often carry more energy in the 2nd harmonic than the fundamental.
        for (f0 in range.filter { it < 300 }) {
            val err = steadyError(f0, 44_100, fundamentalLevel = 0.2)
            assertTrue(abs(err) < 2.0, "$f0 Hz with weak fundamental: $err cents (an octave error is ±1200)")
        }
    }

    @Test
    fun vibratoIsTrackedAroundItsCentre() {
        val f0 = 220.0
        val depth = 30.0
        val rate = 44_100
        val signal = VoiceSignals.voice(f0, 2.0, rate, vibratoCents = depth)
        val detector = PitchDetector()
        var previous: Double? = null
        val readings = (0 until (signal.size - 2048) step 512).mapNotNull { start ->
            detector.detect(signal.window(start), rate, previousFrequency = previous)?.also { previous = it.frequencyHz }
        }
        assertTrue(readings.size > 150, "too few readings: ${readings.size}")
        val centsList = readings.map { cents(f0, it.frequencyHz) }
        assertTrue(centsList.all { abs(it) < depth + 5 }, "a reading strayed beyond the vibrato: ${centsList.maxOf { abs(it) }}")
        assertTrue(abs(centsList.average()) < 3.0, "vibrato mean ${centsList.average()} cents")
    }

    @Test
    fun breathNoiseAt20DbStillReadsWithinFiveCents() {
        for (f0 in listOf(98.0, 196.0, 392.0, 784.0)) {
            val signal = VoiceSignals.voice(f0, 0.5, noiseDb = -20.0)
            val result = assertNotNull(PitchDetector().detect(signal.window(11_025), 44_100), "$f0 Hz in noise")
            assertTrue(abs(cents(f0, result.frequencyHz)) < 5.0, "$f0 Hz in noise: ${cents(f0, result.frequencyHz)}")
        }
    }

    @Test
    fun silenceAndNoiseGiveNoReading() {
        assertNull(PitchDetector().detect(FloatArray(2048), 44_100))
        val noise = VoiceSignals.noise(2.0)
        val detector = PitchDetector()
        val windows = (0 until noise.size - 2048 step 1024).toList()
        val detections = windows.count { detector.detect(noise.window(it), 44_100) != null }
        assertTrue(detections <= windows.size / 20, "noise produced $detections readings out of ${windows.size}")
    }

    @Test
    fun softOnsetSettlesOnTheRightPitch() {
        val f0 = 146.83
        val signal = VoiceSignals.voice(f0, 1.0, onsetSeconds = 0.15, noiseDb = -30.0)
        val result = assertNotNull(PitchDetector().detect(signal.window(22_050), 44_100))
        assertTrue(abs(cents(f0, result.frequencyHz)) < 2.0)
    }

    @Test
    fun resampledVoiceKeepsItsPitchFor16kHzModels() {
        for (rate in listOf(44_100, 48_000)) {
            val resampler = AudioResampler(rate)
            for (f0 in listOf(65.0, 130.81, 261.63, 523.25, 987.77)) {
                val signal = VoiceSignals.voice(f0, 0.5, rate)
                val down = resampler.downsample(signal).copyOf()
                assertTrue(abs((signal.size * 16_000.0 / rate) - down.size) <= 1.0, "resampled length ${down.size}")
                val result = assertNotNull(PitchDetector().detect(down.window(4000, 1024), 16_000), "$f0 Hz at 16 kHz")
                assertTrue(abs(cents(f0, result.frequencyHz)) < 3.0, "$f0 Hz from $rate: ${cents(f0, result.frequencyHz)}")
            }
        }
    }

    @Test
    fun bassFloorCoversLowMalePa() {
        // Sa = G2 (98 Hz) puts mandra Pa near 73 Hz; a low Sa of C2-ish gets close to 55 Hz.
        val err = steadyError(57.0, 44_100)
        assertTrue(abs(err) < 2.0, "57 Hz: $err cents")
        assertNull(PitchDetector().detect(VoiceSignals.voice(45.0, 0.5).window(11_025), 44_100), "below the floor")
    }
}
