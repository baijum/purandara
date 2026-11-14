package com.baijum.purandara.ui.run

/** What the run screen can ask for. */
class RunActions(
    val onBack: () -> Unit,
    val onSetSpeed: (index: Int, speed: Int) -> Unit = { _, _ -> },
    val onSetRepeats: (index: Int, repeats: Int) -> Unit = { _, _ -> },
    val onApplySpeed: (speed: Int) -> Unit = {},
    val onApplyRepeats: (repeats: Int) -> Unit = {},
    val onStart: (from: Int) -> Unit = {},
    val onAgain: () -> Unit = {},
    val onDone: () -> Unit = {},
)
