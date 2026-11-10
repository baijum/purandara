package com.baijum.purandara.viewmodel

import com.baijum.purandara.audio.PracticePlayback
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.schedule.Schedule

/** A waiting session may update its next mix, but selecting a new schedule cannot start sound. */
internal fun applyWaitingSettings(
    current: PracticeUiState,
    next: PracticeSettings,
    playback: PracticePlayback,
    feedback: Boolean,
    stop: (paused: Boolean) -> Unit,
) {
    val retuned = next.saMidi != current.settings.saMidi || next.saCents != current.settings.saCents
    val rescheduled = next.performance() != current.settings.performance() || next.practiceMode != current.settings.practiceMode
    val transportChanged = current.settings.usesTransport() != next.usesTransport()
    val droneChanged = next.drone != current.settings.drone || next.droneLevel != current.settings.droneLevel
    when {
        retuned -> stop(false)
        rescheduled || droneChanged || transportChanged -> stop(true)
        current.playback.playing -> playback.update(next, feedback)
    }
}

internal fun PracticeSettings.usesTransport(): Boolean = PracticePlayback.policy(this, false).needsTransport

internal fun PracticeUiState.practiceSchedule(): Schedule {
    val varisai = requireNotNull(selected)
    return phrase?.let { Schedule.ofPhrase(varisai, settings.performance(), it) }
        ?: section?.let { Schedule.ofSection(varisai, settings.performance(), it) }
        ?: Schedule.of(varisai, settings.performance())
}
