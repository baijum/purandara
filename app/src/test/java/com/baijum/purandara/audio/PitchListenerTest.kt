package com.baijum.purandara.audio

import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.pitch.SwiftF0Output
import com.baijum.purandara.singalong.PitchPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private class CountingModel : SwiftF0Model {
        val inputSizes = mutableListOf<Int>()
        override fun run(audio16k: FloatArray, range: PitchRange): SwiftF0Output {
            inputSizes.add(audio16k.size)
            val frames = audio16k.size / SwiftF0.HOP
            return SwiftF0Output(FloatArray(frames) { 196f }, FloatArray(frames) { 0.9f })
        }
    }

    @Test
    fun aModelThatArrivesMidSessionIsPickedUp() {
        // Issue #14: the listener must not capture the model at construction:
        // it loads asynchronously, so the first session (started before loading
        // finishes) would otherwise stay YIN-only until the student navigated
        // away and back.
        val mic = FakeMic()
        var loaded: SwiftF0Model? = null
        var consults = 0
        val listener = PitchListener(mic, { consults++; loaded }, PitchRange.FULL, timeOf = { 0.0 }, onFrame = {})
        listener.start()
        fun feed(windows: Int) {
            repeat(windows) { mic.onWindow!!(FloatArray(PitchPipeline.WINDOW_SIZE) { 0.1f }, it.toLong()) }
        }

        // Before the model loads it is looked for on every 5th window and is
        // missing: YIN alone, and nothing counts as a failure.
        feed(10)
        assertEquals(2, consults)

        // The loader completes mid-session; later windows use it without
        // restarting the listener, once the 176 ms buffer has filled.
        val late = CountingModel()
        loaded = late
        feed(30)
        assertTrue("the model runs once the buffer is full", late.inputSizes.isNotEmpty())
        assertTrue(late.inputSizes.all { it == SwiftF0.LIVE_BUFFER_SAMPLES })
        assertEquals(5, late.inputSizes.size)
        listener.stop()
    }
}
