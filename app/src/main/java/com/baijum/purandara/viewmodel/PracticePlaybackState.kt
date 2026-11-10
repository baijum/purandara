package com.baijum.purandara.viewmodel

import com.baijum.purandara.audio.PracticePhase
import com.baijum.purandara.audio.PracticeSnapshot
import com.baijum.purandara.audio.PracticeSegment
import com.baijum.purandara.domain.schedule.Schedule

/** UI follows heard audio, so rendering a future recovery never clears the sounding cursor. */
internal fun PlaybackState.fromPractice(snapshot: PracticeSnapshot, heard: Long, remaining: Int, schedule: Schedule): PlaybackState {
    val phase = snapshot.phaseAt(heard)
    val segment = snapshot.segmentAt(heard)
    val countIn = phase == PracticePhase.COUNT_IN
    val sounding = phase == PracticePhase.PLAYING || phase == PracticePhase.LISTEN || phase == PracticePhase.YOUR_TURN
    // Derive the rate from the exact performance frame count, avoiding a second timing scheduler.
    val rate = segment?.let { it.performanceFrames / schedule.durationSeconds }
    val seconds = if (segment != null && rate != null) (heard - segment.performanceStartFrame) / rate else 0.0
    val countBeat = if (countIn) segment?.countBeat(heard, schedule) else null
    return copy(
        playing = phase != PracticePhase.FINISHED,
        phase = phase,
        countingIn = countIn,
        attempt = segment?.attempt ?: snapshot.pendingPreparation?.attempt ?: attempt,
        recoverySeconds = remaining,
        position = if (sounding) schedule.cursorAt(seconds) else null,
        beatInAvarta = countBeat ?: if (sounding) schedule.beatAt(seconds)?.beatInAvarta else null,
        pausedForContinue = false,
    )
}

private fun PracticeSegment.countBeat(heard: Long, schedule: Schedule): Int? {
    val frames = countInFrames / (schedule.tala.aksharas * schedule.varisai.kalai)
    return if (frames > 0) ((heard - startFrame) / frames).toInt() else null
}
