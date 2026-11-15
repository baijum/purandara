package com.baijum.purandara.ui.recordings

import com.baijum.purandara.recording.Scale

/** What the recordings screens can do (docs/plan-recordings.md). */
data class RecordingsActions(
    val onBack: () -> Unit,
    val onNew: () -> Unit,
    val onOpen: (String) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onAllowMic: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeek: (Double) -> Unit,
    val onRename: (String, String) -> Unit,
    val onChangeSa: (String, Int, Int) -> Unit,
    val onChangeRaga: (String, String?) -> Unit,
    val onDelete: (String) -> Unit,
    /** The Record screen's "What are you singing?" (a raga key and scale; nulls for singing freely). */
    val onIntent: (String?, Scale?) -> Unit = { _, _ -> },
    /** A recording's "What are you singing?". */
    val onChangeIntent: (String, String?, Scale?) -> Unit = { _, _, _ -> },
    /** The Record screen's "External tanpura playing" switch. */
    val onTanpura: (Boolean) -> Unit = {},
    /** A recording's tanpura switch (the swaras are read again). */
    val onChangeTanpura: (String, Boolean) -> Unit = { _, _ -> },
    /** Opens the raga editor: one of the student's ragas by key, or a new one with null. */
    val onEditRaga: (String?) -> Unit = {},
    /** Debug builds only: read the swaras again (for tuning the analysis); null hides the button. */
    val onReanalyse: ((String) -> Unit)? = null,
)
