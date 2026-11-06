package com.baijum.purandara.viewmodel

import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldState
import com.baijum.purandara.shruti.HoldTarget
import com.baijum.purandara.shruti.SaSuggestion
import com.baijum.purandara.singalong.SwaraReading
import com.baijum.purandara.singalong.Zone

/** One point of the free pitch trace: seconds since the tanpura started, cents from Sa. */
data class FreeTracePoint(val seconds: Double, val centsFromSa: Double, val zone: Zone)

/** What the Shruti practice screen shows (docs/plan-shruti.md). */
data class ShrutiUiState(
    val open: Boolean = false,
    /** The opening room check: the student should stay quiet. */
    val listeningToRoom: Boolean = false,
    val reading: SwaraReading? = null,
    /** About the last ten seconds. */
    val trace: List<FreeTracePoint> = emptyList(),
    val target: HoldTarget = HoldTarget.SA,
    val goal: HoldGoal = HoldGoal.SHORT,
    val hold: HoldState? = null,
)

enum class FindSaStep { LOWEST, HIGHEST, DONE }

/** What the Find my Sa guide shows. */
data class FindSaUiState(
    val open: Boolean = false,
    val step: FindSaStep = FindSaStep.LOWEST,
    /** Progress holding the current note, 0..1. */
    val progress: Double = 0.0,
    /** Keyboard name of the note being heard, e.g. "C3". */
    val hearing: String? = null,
    /** The highest note must be clearly above the lowest. */
    val notHigher: Boolean = false,
    val lowestHz: Double? = null,
    val highestHz: Double? = null,
    val suggestion: SaSuggestion? = null,
)

/**
 * The Find my Sa steps, fed from the mic thread: catch the lowest comfortable
 * note, then a clearly higher one, then suggest a Sa.
 */
class FindSaGuide {
    private val catcher = com.baijum.purandara.shruti.NoteCatcher()
    private var step = FindSaStep.LOWEST
    private var lowest: Double? = null
    private var highest: Double? = null
    private var suggestion: SaSuggestion? = null

    fun onFrame(timeSeconds: Double, frequencyHz: Double?): FindSaUiState {
        var notHigher = false
        val low = lowest
        when (step) {
            FindSaStep.LOWEST -> catcher.onFrame(timeSeconds, frequencyHz)?.let {
                lowest = it
                step = FindSaStep.HIGHEST
                catcher.clear()
            }
            FindSaStep.HIGHEST -> if (frequencyHz != null && low != null &&
                com.baijum.purandara.domain.Pitch.cents(low, frequencyHz) < MIN_SPAN_CENTS
            ) {
                notHigher = true
                catcher.clear()
            } else {
                catcher.onFrame(timeSeconds, frequencyHz)?.let { high ->
                    highest = high
                    suggestion = com.baijum.purandara.shruti.SaFinder.suggest(low ?: high, high)
                    step = FindSaStep.DONE
                }
            }
            FindSaStep.DONE -> Unit
        }
        return FindSaUiState(
            open = true,
            step = step,
            progress = if (step == FindSaStep.DONE) 1.0 else catcher.progress,
            hearing = frequencyHz?.let { com.baijum.purandara.domain.Shruti.noteName(com.baijum.purandara.domain.Pitch.nearestMidi(it)) },
            notHigher = notHigher,
            lowestHz = lowest,
            highestHz = highest,
            suggestion = suggestion,
        )
    }

    private companion object {
        /** The highest note must be at least a fourth above the lowest. */
        const val MIN_SPAN_CENTS = 500.0
    }
}
