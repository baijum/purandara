package com.baijum.purandara.ui.library

import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.practice.RunPlans
import com.baijum.purandara.domain.varisai.VarisaiSet

/** What Home and the set pages show. */
data class LibraryState(
    val history: PracticeHistory = PracticeHistory(),
    val saMidi: Int,
    val saCents: Int = 0,
    val tanpuraPlaying: Boolean = false,
    val samplesLoaded: Boolean = false,
    val loadError: Boolean = false,
    /** Saved run plans, for each set's continue point. */
    val runPlans: RunPlans = RunPlans(),
)

/** What Home and the set pages can do. */
data class LibraryActions(
    val onSelect: (Varisai) -> Unit,
    val onToggleTanpura: () -> Unit = {},
    val onSaChange: (Int) -> Unit = {},
    /** Open the practice run for a numbered varisai set. */
    val onOpenRun: (VarisaiSet) -> Unit = {},
    /** Home: open a set's page. */
    val onOpenSet: (VarisaiSet) -> Unit = {},
    /** A set page shown on its own (not as a menu destination): back to Home. */
    val onBack: () -> Unit = {},
)
