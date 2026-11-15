package com.baijum.purandara.singalong

import com.baijum.purandara.pitch.NeuralArbitrator
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0Live
import com.baijum.purandara.pitch.SwiftF0Model

/**
 * Live listening (sing-along, Shruti practice, Find my Sa): microphone
 * windows → YIN, with SwiftF0 v0.3 on a rolling 176 ms buffer every
 * [NeuralArbitrator.SUPERVISOR_INTERVAL] windows, used only to correct
 * octaves (its reading is about 70 ms old; docs/plan-swiftf0-upgrade.md).
 *
 * Per mic window, in order:
 * ```
 * live.listen(window)                      // every window, even ones not scored
 * val frame = live.process(window, time)   // the windows you want frames for
 * ```
 * [model] is read on every run, not captured: it loads asynchronously, and a
 * session started before it arrives must pick it up (issue #14). A model not
 * loaded yet is simply not consulted; it never counts as a failure.
 * Not thread-safe: one instance per capture stream, used from its thread.
 */
class LivePitch(sampleRate: Int, private val model: () -> SwiftF0Model?, range: PitchRange) {
    private val pipeline = PitchPipeline(sampleRate, NeuralArbitrator.SUPERVISOR_INTERVAL, octaveOnly = true)
    private val buffer = SwiftF0Live(sampleRate).also { it.range = range }

    /** The band SwiftF0 searches; set it when the Sa changes. */
    var range: PitchRange
        get() = buffer.range
        set(value) {
            buffer.range = value
        }

    /** True when the model has failed repeatedly and is no longer consulted. */
    val neuralDisabled: Boolean get() = pipeline.neuralDisabled

    /** Feeds the window's newest hop to the SwiftF0 buffer. Call for every window. */
    fun listen(window: FloatArray) {
        val hop = minOf(PitchPipeline.HOP_SIZE, window.size)
        buffer.push(window, window.size - hop, hop)
    }

    /** The pitch frame for [window], captured at [timeSeconds]. */
    fun process(window: FloatArray, timeSeconds: Double): PitchFrame {
        if (pipeline.beginFrame()) consult()
        return pipeline.process(window, timeSeconds)
    }

    private fun consult() {
        val m = model() ?: return
        val input = buffer.input() ?: return
        val output = m.run(input, buffer.range)
        if (output == null) {
            pipeline.onNeuralResult(null)
            return
        }
        val reading = buffer.read(input, output)
        if (reading != null) pipeline.onNeuralResult(reading) else pipeline.onNeuralNoPitch()
    }

    fun reset() = pipeline.reset()
}
