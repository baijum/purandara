package com.baijum.purandara.recording

import com.baijum.purandara.pitch.NeuralPitchResult
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.StreamResampler
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Chunker
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.pitch.SwiftF0Track
import com.baijum.purandara.singalong.PitchPipeline
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decoded recording samples → [PitchTrack], offline (docs/plan-recordings.md).
 * The platform decodes the saved audio and [feed]s it in chunks of any size;
 * windows of [PitchPipeline.WINDOW_SIZE] every [PitchPipeline.HOP_SIZE]
 * samples go through the same YIN pipeline as live singing.
 *
 * With a [SwiftF0Model] (docs/plan-swiftf0-upgrade.md) the audio is also
 * resampled to 16 kHz and run through SwiftF0 in overlapping chunks
 * ([SwiftF0Chunker]), the reference way to run the model, and each YIN
 * window is arbitrated with the SwiftF0 frame nearest its centre. YIN
 * windows wait (a few seconds of audio) until their chunk is ready, so each
 * still starts from the previous arbitrated pitch, as live.
 *
 * Not thread-safe; use from one background thread.
 */
class RecordingAnalyzer private constructor(
    private val sampleRate: Int,
    neuralInterval: Int,
    private val neural: ((FloatArray) -> NeuralPitchResult?)?,
    model: SwiftF0Model?,
    range: PitchRange,
) {
    /** SwiftF0 v0.3 over the whole recording; [model] null for YIN alone. */
    constructor(sampleRate: Int, model: SwiftF0Model?, range: PitchRange) :
        this(sampleRate, NEURAL_EVERY_WINDOW, null, model, range)

    /** The old per-window SwiftF0, until both apps run the v0.3 model. */
    @Deprecated("Use RecordingAnalyzer(sampleRate, model, range)")
    constructor(sampleRate: Int, neuralInterval: Int, neural: ((FloatArray) -> NeuralPitchResult?)?) :
        this(sampleRate, neuralInterval, neural, null, PitchRange.FULL)

    private val pipeline = PitchPipeline(sampleRate, neuralInterval, octaveOnly = false, silenceRms = SILENCE_RMS)
    private val chunker = model?.let { SwiftF0Chunker(it, range) }
    private val resampler = if (model != null) StreamResampler(sampleRate) else null
    private val window = FloatArray(PitchPipeline.WINDOW_SIZE)
    private var pending = FloatArray(PitchPipeline.WINDOW_SIZE * 2)
    private var pendingLength = 0
    private var pendingStart = 0L // absolute index of pending[0]
    private var windows = 0
    private var finalTrack: SwiftF0Track? = null
    private val track = PitchTrack.Builder(
        hopSeconds = PitchPipeline.HOP_SIZE.toDouble() / sampleRate,
        offsetSeconds = PitchPipeline.WINDOW_SIZE / 2.0 / sampleRate,
    )

    /** Seconds of audio analysed so far (for a progress bar). */
    val analysedSeconds: Double get() = windows.toDouble() * PitchPipeline.HOP_SIZE / sampleRate

    /** Feeds the next [count] samples of [samples] (mono, -1..1). */
    fun feed(samples: FloatArray, count: Int) {
        if (pending.size < pendingLength + count) pending = pending.copyOf(maxOf(pendingLength + count, pending.size * 2))
        samples.copyInto(pending, pendingLength, 0, count)
        pendingLength += count
        if (chunker != null && resampler != null) {
            val out = resampler.process(samples, 0, count)
            chunker.feed(out, 0, resampler.lastCount)
        }
        analyseReady()
    }

    /** The track of everything fed so far. */
    fun finish(): PitchTrack {
        finalTrack = chunker?.finish()
        analyseReady()
        return track.build()
    }

    private fun analyseReady() {
        while (true) {
            val start = windows.toLong() * PitchPipeline.HOP_SIZE
            if (start + window.size > pendingStart + pendingLength) return
            val frame = swiftF0Frame(start)
            if (chunker != null && finalTrack == null && frame >= chunker.framesReady) return
            pending.copyInto(window, 0, (start - pendingStart).toInt(), (start - pendingStart).toInt() + window.size)
            analyseWindow(frame)
            val drop = (windows.toLong() * PitchPipeline.HOP_SIZE - pendingStart).toInt()
            pending.copyInto(pending, 0, drop, pendingLength)
            pendingLength -= drop
            pendingStart += drop
        }
    }

    /** The SwiftF0 frame nearest the centre of the window starting at sample [start]. */
    private fun swiftF0Frame(start: Long): Int {
        val centre = (start + window.size / 2).toDouble() / sampleRate
        return (centre * SwiftF0.SAMPLE_RATE / SwiftF0.HOP).roundToInt()
    }

    private fun analyseWindow(frame: Int) {
        if (pipeline.beginFrame()) {
            val done = finalTrack
            when {
                chunker == null -> pipeline.onNeuralResult(neural?.invoke(window))
                done != null -> report(done.hz, done.confidence, min(frame, done.size - 1))
                else -> report(chunker.hz, chunker.confidence, frame)
            }
        }
        track.add(pipeline.process(window, windows.toDouble() * PitchPipeline.HOP_SIZE / sampleRate))
        windows++
    }

    private fun report(hz: FloatArray, confidence: FloatArray, i: Int) {
        val c = confidence[i]
        when {
            c == SwiftF0Track.FAILED -> pipeline.onNeuralResult(null)
            hz[i] > 0f && c >= SwiftF0.VOICED_CONFIDENCE -> pipeline.onNeuralResult(NeuralPitchResult(hz[i].toDouble(), c.toDouble()))
            else -> pipeline.onNeuralNoPitch()
        }
    }

    companion object {
        /** SwiftF0 on every window: offline there's time, and it removes more octave slips. */
        const val NEURAL_EVERY_WINDOW = 1

        /**
         * YIN's silence level for recordings, far below live listening's
         * 0.01: recordings have no gain control, and an iPad's built-in mic
         * puts a sung voice at an RMS of 0.005–0.01, so 0.01 discarded most
         * of every recording. The transcriber's own floor (from the
         * recording's room level, at least 0.003) decides what is voice.
         */
        const val SILENCE_RMS = 0.001f
    }
}
