package com.baijum.purandara.ui.recordings

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
)
