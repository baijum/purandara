package com.baijum.purandara.ui.library

import com.baijum.purandara.domain.CustomRaga
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog
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
    /** The raga the varisais and alankarams are sung in. */
    val raga: Raga = VarisaiCatalog.raga,
    /** The raga whose scale is playing in the raga list, by key. */
    val previewRagaKey: String? = null,
    /** The student's own ragas, for the raga list (docs/plan-own-raga-varisais.md). */
    val ownRagas: List<CustomRaga> = emptyList(),
)

/** What Home and the set pages can do. */
data class LibraryActions(
    val onSelect: (Varisai) -> Unit,
    val onToggleTanpura: () -> Unit = {},
    val onSaChange: (Int) -> Unit = {},
    /** Home: sing the varisais and alankarams in another raga. */
    val onRagaChange: (Raga) -> Unit = {},
    /** The raga list: hear a raga's scale, or stop it. */
    val onPreviewRaga: (Raga) -> Unit = {},
    val onStopPreview: () -> Unit = {},
    /** Opens the raga editor for an own raga (null: a new one), from the varisais' raga list. */
    val onEditOwnRaga: (String?) -> Unit = {},
    /** Open the practice run for a numbered varisai set. */
    val onOpenRun: (VarisaiSet) -> Unit = {},
    /** Home: open a set's page. */
    val onOpenSet: (VarisaiSet) -> Unit = {},
    /** A set page shown on its own (not as a menu destination): back to Home. */
    val onBack: () -> Unit = {},
)
