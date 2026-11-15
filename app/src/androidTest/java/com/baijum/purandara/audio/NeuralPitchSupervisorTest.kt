package com.baijum.purandara.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Chunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin

/** Runs the bundled SwiftF0 v0.3.0 model through ONNX Runtime on the device. */
@RunWith(AndroidJUnit4::class)
class NeuralPitchSupervisorTest {
    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    /** Half a second of a sung-like tone at the model's 16 kHz. */
    private fun voice(f0: Double, n: Int = 8000) = FloatArray(n) { i ->
        (0.3 * (1..8).sumOf { h -> sin(2 * PI * h * f0 * i / SwiftF0.SAMPLE_RATE) / h } / 2.7).toFloat()
    }

    /** The middle frame of a run: pitch and confidence. */
    private fun NeuralPitchSupervisor.middle(f0: Double, range: PitchRange): Pair<Float, Float> {
        val out = requireNotNull(run(voice(f0), range)) { "$f0 Hz: the model failed" }
        assertEquals(8000 / SwiftF0.HOP, out.hz.size)
        return out.hz[out.hz.size / 2] to out.confidence[out.hz.size / 2]
    }

    @Test
    fun readsSungPitchesAcrossTheRange() {
        NeuralPitchSupervisor(assets).use { model ->
            for (f0 in listOf(82.41, 98.0, 146.83, 220.0, 392.0, 659.26)) {
                val (hz, confidence) = model.middle(f0, PitchRange.FULL)
                val cents = 1200 * log2(hz / f0)
                assertTrue("$f0 Hz read $hz ($cents cents)", abs(cents) < 50)
                assertTrue(confidence >= SwiftF0.VOICED_CONFIDENCE)
            }
        }
    }

    @Test
    fun theRangeRulesOutPitchesOutsideIt() {
        NeuralPitchSupervisor(assets).use { model ->
            val range = SwiftF0.range(146.83)
            assertTrue(model.middle(220.0, range).second >= SwiftF0.VOICED_CONFIDENCE)
            assertTrue("above tara Pa of D3", model.middle(659.26, range).second < SwiftF0.VOICED_CONFIDENCE)
        }
    }

    @Test
    fun silenceHasNoPitchThroughTheChunker() {
        NeuralPitchSupervisor(assets).use { model ->
            val track = SwiftF0Chunker(model, PitchRange.FULL).apply { feed(FloatArray(16_000)) }.finish()
            assertTrue(track.hz.all { it == 0f })
        }
    }
}
