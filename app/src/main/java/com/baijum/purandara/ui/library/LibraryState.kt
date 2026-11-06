package com.baijum.purandara.ui.library

import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.practice.PracticeHistory

/** What the landing page shows. */
data class LibraryState(
    val history: PracticeHistory = PracticeHistory(),
    val saMidi: Int,
    val tanpuraPlaying: Boolean = false,
    val samplesLoaded: Boolean = false,
    val loadError: Boolean = false,
)

/** What the landing page can do. */
data class LibraryActions(
    val onSelect: (Varisai) -> Unit,
    val onToggleTanpura: () -> Unit = {},
    val onSaChange: (Int) -> Unit = {},
)
