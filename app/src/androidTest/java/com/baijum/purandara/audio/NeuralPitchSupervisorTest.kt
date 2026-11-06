package com.baijum.purandara.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.pitch.NeuralArbitrator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.sin

/** Runs the bundled SwiftF0 model through ONNX Runtime on the device. */
@RunWith(AndroidJUnit4::class)
class NeuralPitchSupervisorTest {
    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    private fun voice(f0: Double, rate: Int, n: Int = 4096) = FloatArray(n) { i ->
        (0.3 * (1..8).sumOf { h -> sin(2 * PI * h * f0 * i / rate) / h } / 2.7).toFloat()
    }

    @Test
    fun estimatesSungPitchesAcrossTheRange() {
        for (rate in listOf(44_100, 48_000)) {
            NeuralPitchSupervisor(assets, rate).use { supervisor ->
                for (f0 in listOf(98.0, 146.83, 220.0, 392.0, 659.26)) {
                    val result = requireNotNull(supervisor.estimate(voice(f0, rate))) { "$f0 Hz at $rate: no estimate" }
                    val cents = 1200 * log2(result.frequencyHz / f0)
                    // SwiftF0 is the octave referee, not the fine tuner (YIN is): within a semitone.
                    assertTrue("$f0 Hz at $rate read ${result.frequencyHz} ($cents cents)", kotlin.math.abs(cents) < 100)
                    assertTrue(result.confidence >= NeuralPitchSupervisor.MIN_CONFIDENCE)
                }
            }
        }
    }

    @Test
    fun silenceGivesNoEstimate() {
        NeuralPitchSupervisor(assets, 44_100).use { assertTrue(it.estimate(FloatArray(4096)) == null) }
    }

    @Test
    fun noOctaveDisagreementWithItselfOnALowVoice() {
        NeuralPitchSupervisor(assets, 44_100).use { supervisor ->
            val r = requireNotNull(supervisor.estimate(voice(82.41, 44_100))) { "82 Hz: no estimate" }
            assertTrue("82 Hz read ${r.frequencyHz}", !NeuralArbitrator.isOctaveRelation(r.frequencyHz, 82.41))
        }
    }
}
