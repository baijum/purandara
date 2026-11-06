package com.baijum.purandara.ui.player

import com.baijum.purandara.data.PracticeSettings

/** What the player screen can ask for. */
class PlayerActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSettingsChange: ((PracticeSettings) -> PracticeSettings) -> Unit,
)
