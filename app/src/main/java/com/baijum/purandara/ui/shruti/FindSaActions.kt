package com.baijum.purandara.ui.shruti

/** What the Find my Sa guide can do. */
data class FindSaActions(
    val onBack: () -> Unit,
    val onUse: () -> Unit,
    val onRetry: () -> Unit,
    val onAllowMic: () -> Unit,
)
