package com.baijum.purandara.recording

import com.baijum.purandara.pitch.NeuralPitchResult
import com.baijum.purandara.singalong.PitchPipeline

/**
 * Decoded recording samples → [PitchTrack], offline (docs/plan-recordings.md).
 * The platform decodes the saved audio and [feed]s it in chunks of any size;
 * windows of [PitchPipeline.WINDOW_SIZE] every [PitchPipeline.HOP_SIZE]
 * samples go through the same YIN + SwiftF0 pipeline as live singing, with
 * SwiftF0 consulted every [neuralInterval] windows (1 = every window).
 *
 * [neural] is the platform's SwiftF0 for one capture-rate window (null when
 * the model is unavailable). Not thread-safe; use from one background thread.
 */
class RecordingAnalyzer(
    private val sampleRate: Int,
    neuralInterval: Int,
    private val neural: ((FloatArray) -> NeuralPitchResult?)?,
) {
    private val pipeline = PitchPipeline(sampleRate, neuralInterval)
    private val window = FloatArray(PitchPipeline.WINDOW_SIZE)
    private var filled = 0
    private var windows = 0
    private val track = PitchTrack.Builder(
        hopSeconds = PitchPipeline.HOP_SIZE.toDouble() / sampleRate,
        offsetSeconds = PitchPipeline.WINDOW_SIZE / 2.0 / sampleRate,
    )

    /** Seconds of audio analysed so far (for a progress bar). */
    val analysedSeconds: Double get() = windows.toDouble() * PitchPipeline.HOP_SIZE / sampleRate

    /** Feeds the next [count] samples of [samples] (mono, -1..1). */
    fun feed(samples: FloatArray, count: Int) {
        var i = 0
        while (i < count) {
            val take = minOf(count - i, window.size - filled)
            samples.copyInto(window, filled, i, i + take)
            filled += take
            i += take
            if (filled == window.size) {
                analyseWindow()
                window.copyInto(window, 0, PitchPipeline.HOP_SIZE, window.size)
                filled = window.size - PitchPipeline.HOP_SIZE
            }
        }
    }

    /** The track of everything fed so far. */
    fun finish(): PitchTrack = track.build()

    private fun analyseWindow() {
        if (pipeline.beginFrame()) pipeline.onNeuralResult(neural?.invoke(window))
        track.add(pipeline.process(window, windows.toDouble() * PitchPipeline.HOP_SIZE / sampleRate))
        windows++
    }

    companion object {
        /** SwiftF0 on every window: offline there's time, and it removes more octave slips. */
        const val NEURAL_EVERY_WINDOW = 1
    }
}
