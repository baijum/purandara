package com.baijum.purandara.ui.shruti

import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldTarget

/** What the Shruti practice screen can do. */
data class ShrutiActions(
    val onBack: () -> Unit,
    val onHoldChange: (HoldTarget, HoldGoal) -> Unit,
    val onAllowMic: () -> Unit,
)
