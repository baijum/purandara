package com.baijum.purandara.viewmodel

import com.baijum.purandara.shruti.FindSaProgress
import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldState
import com.baijum.purandara.shruti.HoldTarget
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
    val goal: HoldGoal = HoldGoal.TEN,
    val hold: HoldState? = null,
)

/** What the Find my Sa guide shows. */
data class FindSaUiState(
    val open: Boolean = false,
    val progress: FindSaProgress = FindSaProgress(),
)
