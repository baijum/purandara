package com.baijum.purandara.audio

import com.baijum.purandara.pitch.NeuralPitchResult
import com.baijum.purandara.singalong.PitchPipeline
import org.junit.Assert.assertEquals
import org.junit.Test

class PitchListenerTest {
    /** Delivers windows on demand, like the mic thread would. */
    private class FakeMic : MicSource {
        override val sampleRate = 44_100
        var onWindow: ((FloatArray, Long) -> Unit)? = null
        override fun start(onWindow: (window: FloatArray, captureNanos: Long) -> Unit) {
            this.onWindow = onWindow
        }
        override fun stop() {
            onWindow = null
        }
    }

    private class RecordingSupervisor : PitchSupervisor {
        val windows = mutableListOf<FloatArray>()
        override fun estimate(window: FloatArray): NeuralPitchResult? {
            windows.add(window.copyOf())
            return NeuralPitchResult(196.0, 0.9)
        }
    }

    @Test
    fun aSupervisorThatArrivesMidSessionIsPickedUp() {
        // Issue #14: the listener must not capture the supervisor at
        // construction — the model loads asynchronously, so the first session
        // (started before loading finishes) would otherwise stay YIN-only
        // until the student navigated away and back.
        val mic = FakeMic()
        var loaded: PitchSupervisor? = null
        var consults = 0
        val listener = PitchListener(mic, { consults++; loaded }, timeOf = { 0.0 }, onFrame = {})
        listener.start()
        fun feed(windows: Int) {
            repeat(windows) { mic.onWindow!!(FloatArray(PitchPipeline.WINDOW_SIZE), it.toLong()) }
        }

        // Before the model loads the provider is consulted per inference
        // window (every 5th) and returns null: YIN alone, and the pipeline
        // records two failures — still well short of the disable threshold.
        feed(10)
        assertEquals(2, consults)
        assertEquals(0, RecordingSupervisor().windows.size) // sanity: none so far

        // The loader completes mid-session; later windows use it without
        // restarting the listener.
        val late = RecordingSupervisor()
        loaded = late
        feed(10)
        assertEquals(2, late.windows.size)
        listener.stop()
    }
}
