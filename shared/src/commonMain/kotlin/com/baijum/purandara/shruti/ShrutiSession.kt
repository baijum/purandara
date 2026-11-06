package com.baijum.purandara.shruti

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.singalong.BleedModel
import com.baijum.purandara.singalong.OutputLevels
import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.Strictness
import com.baijum.purandara.singalong.SwaraMapper
import com.baijum.purandara.singalong.SwaraReading

/** Result of one mic frame in Shruti practice. */
data class ShrutiOutcome(
    /** Still in the opening room check: the student should stay quiet. */
    val listeningToRoom: Boolean,
    /** What the student is singing, or null (silence, or only the tanpura). */
    val reading: SwaraReading?,
    /** Hold practice, when a hold is set. */
    val hold: HoldState?,
)

/**
 * Free singing over the tanpura, with optional hold practice
 * (docs/plan-shruti.md). For the first [roomCheckSeconds] the student is
 * quiet and the room noise and tanpura leakage are measured (the sing-along
 * [BleedModel]); after that, sound clearly above them is the student's voice.
 *
 * Frames carry the heard time in seconds since the tanpura started; their
 * output level comes from [outputLevels], recorded by the renderer.
 */
class ShrutiSession(
    saHz: Double,
    raga: Raga,
    tuning: Tuning,
    strictness: Strictness = Strictness.PRECISE,
    private val sampleRate: Int,
    val outputLevels: OutputLevels = OutputLevels(),
    private val roomCheckSeconds: Double = DEFAULT_ROOM_CHECK_SECONDS,
) {
    private val mapper = SwaraMapper(saHz, raga, tuning, strictness)
    private val bleed = BleedModel()
    private var tracker: HoldTracker? = null

    /** Start (or restart) hold practice. */
    fun startHold(target: HoldTarget, goal: HoldGoal) {
        tracker = HoldTracker(target, goal)
    }

    fun stopHold() {
        tracker = null
    }

    val hold: HoldState? get() = tracker?.state

    fun onFrame(frame: PitchFrame): ShrutiOutcome {
        val heardFrame = (frame.timeSeconds * sampleRate).toLong()
        val output = outputLevels.rmsAt(heardFrame)
        if (frame.timeSeconds < roomCheckSeconds) {
            bleed.calibrate(frame.rms, output)
            return ShrutiOutcome(listeningToRoom = true, reading = null, hold = tracker?.state)
        }
        if (!bleed.isCalibrated) bleed.finishCalibration()
        val voice = frame.frequencyHz != null && bleed.isVoice(frame.rms, output)
        val reading = if (voice) frame.frequencyHz?.let(mapper::read) else null
        val hold = tracker?.onFrame(frame.timeSeconds, reading)
        return ShrutiOutcome(listeningToRoom = false, reading = reading, hold = hold)
    }

    companion object {
        const val DEFAULT_ROOM_CHECK_SECONDS = 2.0
    }
}
