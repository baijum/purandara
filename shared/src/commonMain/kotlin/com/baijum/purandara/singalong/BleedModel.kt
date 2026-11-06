package com.baijum.purandara.singalong

/**
 * Decides whether the microphone heard the student or only the app's own
 * sound leaking back from the speaker.
 *
 * During the count-in the student is silent, so every (mic level, output
 * level) pair there measures the leakage ratio: how loud the app's output
 * is at the mic per unit of output. During the exercise, the mic level
 * expected from leakage alone is that ratio times the current output level
 * (harmonium included, which the count-in does not play). Sound clearly
 * above it, by [VOICE_MARGIN], is a voice.
 *
 * With headphones or working echo cancellation the ratio is near zero, so
 * almost any sound counts as voice.
 */
class BleedModel {
    private val ratios = mutableListOf<Double>()
    private var ratio = 0.0
    private var calibrated = false

    /** Feed one count-in frame: what the mic heard and what the app was playing. */
    fun calibrate(micRms: Float, outputRms: Float) {
        if (outputRms >= MIN_OUTPUT_FOR_RATIO) ratios += micRms.toDouble() / outputRms
    }

    /** Call when the count-in ends. Uses a high percentile so leakage peaks are covered. */
    fun finishCalibration() {
        ratio = if (ratios.isEmpty()) 0.0 else ratios.sorted()[((ratios.size - 1) * CALIBRATION_PERCENTILE).toInt()]
        calibrated = true
    }

    val isCalibrated: Boolean get() = calibrated

    /** Measured leakage ratio (mic level per unit of output level). */
    val leakageRatio: Double get() = ratio

    /** Mic level expected from leakage alone while the app plays at [outputRms]. */
    fun expectedBleed(outputRms: Float): Double = ratio * outputRms

    /** True if [micRms] is clearly more than leakage of [outputRms] and above the absolute floor. */
    fun isVoice(micRms: Float, outputRms: Float): Boolean =
        micRms >= MIN_VOICE_RMS && micRms >= expectedBleed(outputRms) * VOICE_MARGIN

    fun reset() {
        ratios.clear()
        ratio = 0.0
        calibrated = false
    }

    companion object {
        /** Voice must be this many times the expected leakage (≈ 6 dB). */
        const val VOICE_MARGIN = 2.0

        /** Below this the detector's own silence gate applies anyway. */
        const val MIN_VOICE_RMS = 0.01f

        private const val MIN_OUTPUT_FOR_RATIO = 1e-4f
        private const val CALIBRATION_PERCENTILE = 0.9
    }
}
