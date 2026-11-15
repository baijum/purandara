package com.baijum.purandara.singalong

import com.baijum.purandara.pitch.NeuralArbitrator
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.pitch.SwiftF0Output
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivePitchTest {
    private val rate = 44_100

    private fun windows(count: Int, hz: Double = 146.83): List<FloatArray> {
        val total = PitchPipeline.WINDOW_SIZE + (count - 1) * PitchPipeline.HOP_SIZE
        val audio = FloatArray(total) { (0.3 * sin(2 * PI * hz * it / rate)).toFloat() }
        return List(count) { i -> audio.copyOfRange(i * PitchPipeline.HOP_SIZE, i * PitchPipeline.HOP_SIZE + PitchPipeline.WINDOW_SIZE) }
    }

    private fun run(live: LivePitch, count: Int) = windows(count).mapIndexed { i, w ->
        live.listen(w)
        live.process(w, i * 0.01)
    }

    @Test
    fun aModelNotLoadedYetIsNeverAFailure() {
        val live = LivePitch(rate, { null }, PitchRange.FULL)
        run(live, NeuralArbitrator.SUPERVISOR_INTERVAL * NeuralArbitrator.FAILURE_THRESHOLD * 2)
        assertFalse(live.neuralDisabled)
    }

    @Test
    fun aFailingModelIsSwitchedOff() {
        val live = LivePitch(rate, { SwiftF0Model { _, _ -> null } }, PitchRange.FULL)
        run(live, NeuralArbitrator.SUPERVISOR_INTERVAL * (NeuralArbitrator.FAILURE_THRESHOLD + 10))
        assertTrue(live.neuralDisabled)
    }

    @Test
    fun aModelHearingNoPitchStaysOn() {
        val silent = SwiftF0Model { audio, _ ->
            val n = audio.size / SwiftF0.HOP
            SwiftF0Output(FloatArray(n), FloatArray(n))
        }
        val live = LivePitch(rate, { silent }, PitchRange.FULL)
        run(live, NeuralArbitrator.SUPERVISOR_INTERVAL * NeuralArbitrator.FAILURE_THRESHOLD * 2)
        assertFalse(live.neuralDisabled, "no pitch is not a failure")
    }

    @Test
    fun theModelGetsTheRollingBufferAndTheRange() {
        val sizes = mutableListOf<Int>()
        val ranges = mutableListOf<PitchRange>()
        val model = SwiftF0Model { audio, range ->
            sizes.add(audio.size)
            ranges.add(range)
            val n = audio.size / SwiftF0.HOP
            SwiftF0Output(FloatArray(n) { 146.83f }, FloatArray(n) { 0.9f })
        }
        val range = SwiftF0.range(146.83)
        val live = LivePitch(rate, { model }, range)
        val frames = run(live, 60)
        assertTrue(sizes.isNotEmpty())
        assertTrue(sizes.all { it == SwiftF0.LIVE_BUFFER_SAMPLES })
        assertEquals(setOf(range), ranges.toSet())
        assertEquals(146.83, frames.last().frequencyHz!!, 1.0)
    }
}
