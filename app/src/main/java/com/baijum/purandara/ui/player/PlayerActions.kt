package com.baijum.purandara.ui.player

import com.baijum.purandara.data.PracticeSettings

/** What the player screen can ask for. */
class PlayerActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSettingsChange: ((PracticeSettings) -> PracticeSettings) -> Unit,
    /** Turn sing-along on or off (asks for the microphone the first time). */
    val onToggleSingAlong: () -> Unit = {},
    /** Open the system settings page for this app (after a refused permission). */
    val onOpenAppSettings: () -> Unit = {},
)
