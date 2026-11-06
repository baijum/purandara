package com.baijum.purandara.shruti

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Shruti
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Catches one held note: [HOLD_SECONDS] of pitch staying within
 * [TOLERANCE_CENTS] of its median (docs/plan-shruti.md, Find my Sa).
 */
class NoteCatcher {
    private val times = ArrayDeque<Double>()
    private val pitches = ArrayDeque<Double>()

    /** Progress towards a caught note, 0..1. */
    var progress: Double = 0.0
        private set

    /** Feed one frame; returns the caught note's frequency once it has been held. */
    fun onFrame(timeSeconds: Double, frequencyHz: Double?): Double? {
        if (frequencyHz == null) {
            // A short gap is fine; a long one starts over.
            if (times.isNotEmpty() && timeSeconds - times.last() > MAX_GAP_SECONDS) clear()
            return null
        }
        times.addLast(timeSeconds)
        pitches.addLast(frequencyHz)
        // Drop the start of the window while the newest pitch is far from the median.
        while (pitches.size > 1 && abs(Pitch.cents(median(), frequencyHz)) > TOLERANCE_CENTS) {
            times.removeFirst()
            pitches.removeFirst()
        }
        val held = times.last() - times.first()
        progress = (held / HOLD_SECONDS).coerceIn(0.0, 1.0)
        return if (held >= HOLD_SECONDS) median() else null
    }

    fun clear() {
        times.clear()
        pitches.clear()
        progress = 0.0
    }

    private fun median(): Double = pitches.sorted()[pitches.size / 2]

    companion object {
        const val HOLD_SECONDS = 1.5
        const val TOLERANCE_CENTS = 50.0
        const val MAX_GAP_SECONDS = 0.3
    }
}

/** A suggested Sa from the student's comfortable range. */
data class SaSuggestion(
    val saMidi: Int,
    /** The range is too narrow for both lower Pa and upper Ri at this Sa. */
    val narrowRange: Boolean,
)

/**
 * Suggests Sa from the lowest and highest comfortable notes: lower Pa
 * (Sa − 5 semitones) no lower than the lowest note, upper Ri (Sa + 14) no
 * higher than the highest, taking the middle of the possible Sas, within
 * [Shruti.SA_RANGE]. With too narrow a range, the closest compromise.
 */
object SaFinder {
    private const val LOWER_PA = 5
    private const val UPPER_RI = 14

    fun suggest(lowestHz: Double, highestHz: Double): SaSuggestion {
        val low = Pitch.nearestMidi(minOf(lowestHz, highestHz))
        val high = Pitch.nearestMidi(maxOf(lowestHz, highestHz))
        val from = low + LOWER_PA
        val to = high - UPPER_RI
        val range = Shruti.SA_RANGE
        val sa = ((from + to) / 2.0).roundToInt().coerceIn(range)
        return SaSuggestion(sa, narrowRange = from > to)
    }
}
