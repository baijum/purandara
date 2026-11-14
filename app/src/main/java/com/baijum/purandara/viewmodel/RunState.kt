package com.baijum.purandara.viewmodel

import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.practice.RunStep

/** Where a practice run is: setting it up, playing in the normal player, or done. */
enum class RunPhase { SETUP, PLAYING, COMPLETE }

/** What the run planner shows (docs/plan-practice-run.md). */
data class RunUiState(
    val set: VarisaiSet,
    val phase: RunPhase,
    /** Every varisai in the set, in order, with its chosen speed and repeats. */
    val steps: List<RunStep>,
    /** The varisai to start at next: the saved continue point, or the current one after a stop. */
    val startStep: Int,
    /** The varisai being sung (or the resume point in the setup screen). */
    val step: Int,
    /** 0-based repeat of the current kala. */
    val pass: Int = 0,
    /** The kala sounding now: the climb starts at 1. */
    val kala: Int = 1,
    /** The whole run from [startStep], for the estimate. */
    val totalSeconds: Double = 0.0,
) {
    val current: RunStep? get() = steps.getOrNull(step)
    val stepCount: Int get() = steps.size
}
