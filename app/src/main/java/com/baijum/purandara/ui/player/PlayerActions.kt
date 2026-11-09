package com.baijum.purandara.ui.player

import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.SectionSpan
import com.baijum.purandara.domain.varisai.PracticePhrase

/** What the player screen can ask for. */
class PlayerActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSettingsChange: ((PracticeSettings) -> PracticeSettings) -> Unit,
    /** Turn sing-along on or off (asks for the microphone the first time). */
    val onToggleSingAlong: () -> Unit = {},
    /** Open the system settings page for this app (after a refused permission). */
    val onOpenAppSettings: () -> Unit = {},
    /** Practise one section alone, or null for the whole piece. */
    val onSelectSection: (SectionSpan?) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onWaitForMe: () -> Unit = {},
    val onSelectPhrase: (PracticePhrase?) -> Unit = {},
)
