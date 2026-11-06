package com.baijum.purandara.singalong

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Decides whether the microphone heard the student or only the app's own
 * sound leaking back from the speaker (plus the room's own noise).
 *
 * During the count-in the student is silent, so what the mic hears there is
 * room noise plus leakage: mic² ≈ noise² + (ratio × output)². The noise floor
 * is the median mic level while the app is nearly silent (between count-in
 * clicks); with a continuous drone there is no such moment and the noise is
 * taken as zero, which attributes everything to leakage (the cautious side).
 * The leakage ratio is the median over frames where the app is clearly
 * audible, after taking the noise away. The median, not a high percentile:
 * echo cancellation is still adapting during the count-in and lets an odd
 * click through, and one such click must not decide the whole run (measured
 * on an iPad: most clicks cancelled to 0.004, an occasional one at 0.3).
 * Real leakage without cancellation affects every click, so the median still
 * sees it. (Dividing
 * raw mic level by output level instead blows up between count-in clicks, when
 * the output is nearly silent but the room is not, and rejects every voice.)
 *
 * During the exercise, the mic level expected without a voice is that
 * combination at the current output level (harmonium included, which the
 * count-in does not play). Sound clearly above it, by [VOICE_MARGIN], is a voice.
 *
 * With headphones or working echo cancellation the ratio is near zero, so
 * anything clearly above the room noise counts as voice.
 */
class BleedModel {
    private val micLevels = mutableListOf<Float>()
    private val outputLevels = mutableListOf<Float>()
    private var noise = 0.0
    private var ratio = 0.0
    private var calibrated = false

    /** Feed one count-in frame: what the mic heard and what the app was playing. */
    fun calibrate(micRms: Float, outputRms: Float) {
        micLevels += micRms
        outputLevels += outputRms
    }

    /** Call when the count-in ends. */
    fun finishCalibration() {
        val quiet = micLevels.indices.filter { outputLevels[it] < MAX_OUTPUT_FOR_NOISE }.map { micLevels[it].toDouble() }
        noise = if (quiet.size >= MIN_NOISE_FRAMES) percentile(quiet, 0.5) else 0.0
        val ratios = micLevels.indices
            .filter { outputLevels[it] >= MIN_OUTPUT_FOR_RATIO }
            .map { i ->
                val mic = micLevels[i].toDouble()
                sqrt(max(0.0, mic * mic - noise * noise)) / outputLevels[i]
            }
        ratio = percentile(ratios, CALIBRATION_PERCENTILE)
        calibrated = true
    }

    val isCalibrated: Boolean get() = calibrated

    /** Measured leakage ratio (mic level per unit of output level, room noise excluded). */
    val leakageRatio: Double get() = ratio

    /** Measured room noise at the mic (RMS). */
    val noiseFloor: Double get() = noise

    /** Mic level expected without a voice while the app plays at [outputRms]. */
    fun expectedBleed(outputRms: Float): Double {
        val leak = ratio * outputRms
        return sqrt(noise * noise + leak * leak)
    }

    /** True if [micRms] is clearly more than noise plus leakage of [outputRms], and above the absolute floor. */
    fun isVoice(micRms: Float, outputRms: Float): Boolean =
        micRms >= MIN_VOICE_RMS && micRms >= expectedBleed(outputRms) * VOICE_MARGIN

    fun reset() {
        micLevels.clear()
        outputLevels.clear()
        noise = 0.0
        ratio = 0.0
        calibrated = false
    }

    private fun percentile(values: List<Double>, p: Double): Double =
        if (values.isEmpty()) 0.0 else values.sorted()[((values.size - 1) * p).toInt()]

    companion object {
        /** Voice must be this many times the expected noise and leakage (≈ 6 dB). */
        const val VOICE_MARGIN = 2.0

        /** Below this the detector's own silence gate applies anyway. */
        const val MIN_VOICE_RMS = 0.01f

        /** Only frames where the app is clearly audible measure leakage. */
        const val MIN_OUTPUT_FOR_RATIO = 0.01f

        /** Frames with output below this hear only the room (between count-in clicks). */
        private const val MAX_OUTPUT_FOR_NOISE = 0.002f

        /** Enough quiet frames (about a second) to trust the noise floor. */
        private const val MIN_NOISE_FRAMES = 40

        /** Typical leakage, robust to the odd click echo cancellation misses. */
        private const val CALIBRATION_PERCENTILE = 0.5
    }
}
