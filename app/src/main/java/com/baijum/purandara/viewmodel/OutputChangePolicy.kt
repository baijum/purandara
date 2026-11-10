package com.baijum.purandara.viewmodel

import com.baijum.purandara.shruti.FindSaProgress

/** Preserve destinations, settings, goals and a completed suggestion, but discard live calibration. */
internal fun PracticeUiState.afterOutputChange(): PracticeUiState = copy(
    outputChanged = true,
    shruti = shruti.copy(listeningToRoom = false, reading = null, trace = emptyList(), hold = null),
    findSa = findSa.copy(progress = if (findSa.progress.suggestion != null) findSa.progress else FindSaProgress()),
    singAlong = singAlong.copy(live = null, pass = null),
)

internal enum class BackgroundAudio { SHRUTI, FIND_SA, PLAYBACK, TANPURA }

/** Issue #11 resumes normally, except after an output change or a completed Find my Sa. */
internal fun PracticeUiState.backgroundAudio(): BackgroundAudio? = when {
    outputChanged -> null
    shruti.open -> BackgroundAudio.SHRUTI
    findSa.open && findSa.progress.suggestion == null -> BackgroundAudio.FIND_SA
    playback.playing && !playback.recovering -> BackgroundAudio.PLAYBACK
    tanpuraPlaying -> BackgroundAudio.TANPURA
    else -> null
}

internal fun PracticeUiState.canAutoStartAudio(backgrounded: Boolean): Boolean = !outputChanged && !backgrounded

internal fun acceptsMicFailure(generation: Long, currentGeneration: Long, hasCapture: Boolean): Boolean =
    generation == currentGeneration && hasCapture

/** Queued frames from a stopped/replaced guide must not overwrite the current guide. */
internal fun PracticeUiState.acceptsFindSaOutcome(generation: Long, currentGeneration: Long): Boolean =
    generation == currentGeneration && findSa.open && !outputChanged

/** Availability isn't routing: consult the active track instead whenever one exists. */
internal fun shouldStopForOutputAvailability(
    inventoryChanged: Boolean,
    hasActiveTrack: Boolean,
    hasMicrophone: Boolean,
    pendingResume: BackgroundAudio?,
): Boolean = inventoryChanged && !hasActiveTrack && (hasMicrophone || pendingResume != null)
