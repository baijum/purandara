package com.baijum.purandara.shruti

import com.baijum.purandara.domain.Swara
import com.baijum.purandara.singalong.SwaraReading
import com.baijum.purandara.singalong.Zone

/** What to hold in hold practice. */
enum class HoldTarget(val swara: Swara, val octave: Int) {
    SA(Swara.S, 0),
    PA(Swara.P, 0),
    UPPER_SA(Swara.S, 1),
}

/** How long one hold should last. */
enum class HoldGoal(val seconds: Double) {
    TEN(10.0),
    TWENTY(20.0),
    THIRTY(30.0),
}

/**
 * The state of hold practice after a frame.
 *
 * @property currentSeconds length of the hold in progress (0 when not holding).
 * @property bestSeconds longest hold so far.
 * @property onTarget the latest frame was on target.
 * @property reached a hold has lasted the goal.
 */
data class HoldState(
    val target: HoldTarget,
    val goal: HoldGoal,
    val currentSeconds: Double,
    val bestSeconds: Double,
    val onTarget: Boolean,
    val reached: Boolean,
)

/**
 * Times how long the student holds [target] (docs/plan-shruti.md): the target
 * swara in the right octave, within the close zone of the session's
 * strictness. Breaks up to [MAX_GAP_SECONDS] (a breath, a wobble) don't end
 * a hold.
 */
class HoldTracker(val target: HoldTarget, val goal: HoldGoal) {
    private var runStart: Double? = null
    private var lastOnTarget = 0.0
    private var best = 0.0
    private var reached = false

    var state: HoldState = HoldState(target, goal, 0.0, 0.0, onTarget = false, reached = false)
        private set

    /** Feed one frame at [timeSeconds]: what was sung (null when no voice). */
    fun onFrame(timeSeconds: Double, reading: SwaraReading?): HoldState {
        val on = reading != null && reading.swara == target.swara && reading.octave == target.octave &&
            reading.zone != Zone.OFF
        val start = runStart
        if (on) {
            if (start == null || timeSeconds - lastOnTarget > MAX_GAP_SECONDS) runStart = timeSeconds
            lastOnTarget = timeSeconds
        } else if (start != null && timeSeconds - lastOnTarget > MAX_GAP_SECONDS) {
            runStart = null
        }
        val current = runStart?.let { lastOnTarget - it } ?: 0.0
        if (current > best) best = current
        if (best >= goal.seconds) reached = true
        state = HoldState(target, goal, current, best, on, reached)
        return state
    }

    companion object {
        const val MAX_GAP_SECONDS = 0.3
    }
}
