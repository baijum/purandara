package com.baijum.purandara.shruti

import com.baijum.purandara.domain.Pitch

enum class FindSaStep { LOWEST, HIGHEST, DONE }

/** Where Find my Sa is, after a frame. */
data class FindSaProgress(
    val step: FindSaStep = FindSaStep.LOWEST,
    /** Progress holding the current note, 0..1. */
    val holding: Double = 0.0,
    /** The pitch being heard now, if any. */
    val hearingHz: Double? = null,
    /** The "highest" note is not clearly above the lowest. */
    val notHigher: Boolean = false,
    val lowestHz: Double? = null,
    val highestHz: Double? = null,
    val suggestion: SaSuggestion? = null,
)

/**
 * The Find my Sa steps (docs/plan-shruti.md): catch the lowest comfortable
 * note, then one at least [MIN_SPAN_CENTS] higher, then suggest a Sa.
 * Feed one frame at a time from one thread.
 */
class FindSaGuide {
    private val catcher = NoteCatcher()
    private var progress = FindSaProgress()

    fun onFrame(timeSeconds: Double, frequencyHz: Double?): FindSaProgress {
        val p = progress
        var next = p.copy(hearingHz = frequencyHz, notHigher = false)
        when (p.step) {
            FindSaStep.LOWEST -> catcher.onFrame(timeSeconds, frequencyHz)?.let {
                catcher.clear()
                next = next.copy(step = FindSaStep.HIGHEST, lowestHz = it)
            }
            FindSaStep.HIGHEST -> {
                val low = p.lowestHz ?: 0.0
                if (frequencyHz != null && Pitch.cents(low, frequencyHz) < MIN_SPAN_CENTS) {
                    catcher.clear()
                    next = next.copy(notHigher = true)
                } else {
                    catcher.onFrame(timeSeconds, frequencyHz)?.let { high ->
                        next = next.copy(step = FindSaStep.DONE, highestHz = high, suggestion = SaFinder.suggest(low, high))
                    }
                }
            }
            FindSaStep.DONE -> Unit
        }
        progress = next.copy(holding = if (next.step == FindSaStep.DONE) 1.0 else catcher.progress)
        return progress
    }

    companion object {
        /** The highest note must be at least a fourth above the lowest. */
        const val MIN_SPAN_CENTS = 500.0
    }
}
