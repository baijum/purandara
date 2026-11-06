package com.baijum.purandara.singalong

import com.baijum.purandara.pitch.NeuralArbitrator
import com.baijum.purandara.pitch.NeuralPitchResult
import com.baijum.purandara.pitch.PitchDetector

/**
 * One analysed microphone window.
 *
 * @property timeSeconds when the window was captured, on the mic stream's clock.
 * @property frequencyHz smoothed pitch, or null for silence / unvoiced sound.
 * @property rms level of the window (for voice-vs-bleed decisions).
 */
data class PitchFrame(val timeSeconds: Double, val frequencyHz: Double?, val rms: Float)

/**
 * Microphone windows → [PitchFrame]s: YIN on every window, arbitrated with
 * the platform's SwiftF0 estimates (if available), then median-smoothed.
 *
 * Per window:
 * ```
 * if (pipeline.beginFrame()) pipeline.onNeuralResult(platformSwiftF0(window))  // every 5th frame
 * val frame = pipeline.process(window, captureTime)
 * ```
 * Not thread-safe: one instance per stream, used from one thread.
 */
class PitchPipeline(private val sampleRate: Int) {
    private val detector = PitchDetector()
    private val arbitrator = NeuralArbitrator()
    private var previous: Double? = null
    private val recent = ArrayDeque<Double>(SMOOTHING_FRAMES)

    /** True when the neural model has failed repeatedly and is no longer consulted. */
    val neuralDisabled: Boolean get() = arbitrator.isDisabledByFailures

    /** Call once per window, before [process]. True when SwiftF0 should run on this window. */
    fun beginFrame(): Boolean = arbitrator.shouldRunInference() && !arbitrator.isDisabledByFailures

    /** Result of the SwiftF0 run requested by [beginFrame]; null if inference failed. */
    fun onNeuralResult(result: NeuralPitchResult?) {
        if (result != null) arbitrator.onInferenceResult(result) else arbitrator.onInferenceFailure()
    }

    fun process(window: FloatArray, timeSeconds: Double): PitchFrame {
        val rms = detector.rms(window)
        val yin = detector.detect(window, sampleRate, previousFrequency = previous)
        if (yin == null) {
            previous = null
            recent.clear()
            return PitchFrame(timeSeconds, null, rms)
        }
        val hz = arbitrator.arbitrate(yin, arbitrator.currentResult()).frequencyHz
        previous = hz
        if (recent.size == SMOOTHING_FRAMES) recent.removeFirst()
        recent.addLast(hz)
        return PitchFrame(timeSeconds, median(recent), rms)
    }

    fun reset() {
        arbitrator.reset()
        previous = null
        recent.clear()
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        /** YIN window: two periods at 55 Hz at 44.1 kHz. */
        const val WINDOW_SIZE = 2048

        /** Hop between windows (~86 frames per second at 44.1 kHz). */
        const val HOP_SIZE = 512

        private const val SMOOTHING_FRAMES = 5
    }
}
