package com.baijum.purandara.viewmodel

import com.baijum.purandara.audio.SampleRenderer
import com.baijum.purandara.audio.VarisaiAudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repeat this cycle (docs/plan-repeat-cycle.md) for the player's own
 * renderer: from the end of the cycle playing, the heard cycle (or the one
 * just finished, if pressed in its first beat) plays again until pressed
 * again (Continue), when the cycle playing finishes and the exercise carries
 * on into the next. The tanpura and tala carry on throughout. Main thread.
 */
class CycleRepeatControl internal constructor(
    private val player: VarisaiAudioPlayer,
    private val state: MutableStateFlow<PracticeUiState>,
) {
    private var renderer: SampleRenderer? = null
    private var cycles = 1

    /** Output frame (since the count-in) heard when Repeat was pressed, while repeating. */
    private var heardFrom: Long? = null

    /** Passes in which a cycle was repeated in this play: none is a best score. */
    private val repeatedPasses = mutableSetOf<Int>()

    /** A new play: [renderer] is streamed directly (null under the practice transport, which can't repeat). */
    internal fun start(renderer: SampleRenderer?, cycles: Int) {
        this.renderer = renderer
        this.cycles = cycles
        heardFrom = null
        repeatedPasses.clear()
    }

    fun toggle() {
        val r = renderer ?: return
        if (state.value.playback.repeatingBlock != null) {
            player.enqueue(r) { r.continueAfterRepeat() }
            heardFrom = null
            state.update { it.copy(playback = it.playback.copy(repeatingBlock = null, repeatTimes = 0)) }
            return
        }
        val heard = player.outputFrameAt(System.nanoTime()) ?: return
        val cycle = r.cycleToRepeat(heard) ?: return
        repeatedPasses += (cycle / cycles).toInt()
        heardFrom = heard - r.countInFrames
        player.enqueue(r) { r.repeatCycle(cycle) }
        state.update { it.copy(playback = it.playback.copy(repeatingBlock = (cycle % cycles).toInt(), repeatTimes = 1)) }
    }

    /** Times the repeated cycle has been heard by output frame [frame], counting the first; 0 when not repeating. */
    internal fun timesHeard(r: SampleRenderer, frame: Long): Int =
        heardFrom?.let { 1 + r.repeats.jumpsBetween(it, frame - r.countInFrames) } ?: 0

    /** Whether a cycle of pass [pass] was repeated in this play. */
    internal fun repeatedIn(pass: Int): Boolean = pass in repeatedPasses
}
