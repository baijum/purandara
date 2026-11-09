package com.baijum.purandara.viewmodel

import com.baijum.purandara.audio.PracticePhase
import com.baijum.purandara.audio.PracticePlayback
import com.baijum.purandara.audio.PracticePreparation
import com.baijum.purandara.audio.PracticeRecovery
import com.baijum.purandara.audio.PracticeSegment
import com.baijum.purandara.audio.PracticeSnapshot
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.RecoveryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePlaybackStateTest {
    private val schedule = Schedule.of(VarisaiCatalog.all.first(), PracticeSettings().performance())
    private val segment = PracticeSegment(1, 2, 1, false, true, false, 100, 800, 8_000, 100)
    private val snapshot = PracticeSnapshot(1, listOf(segment), listOf(PracticeRecovery(segment.endFrame, null, segment.endFrame + 500, false, false)), 20_000, null, null, PracticePreparation(1, 3, 2, false, true), 5, null)

    @Test
    fun renderedRecoveryDoesNotClearHeardPerformance() {
        val state = PlaybackState(playing = true).fromPractice(snapshot, segment.performanceStartFrame + 10, 5, schedule)
        assertEquals(PracticePhase.PLAYING, state.phase)
        assertNotNull(state.position)
        assertEquals(1, state.attempt)
    }

    @Test
    fun recoveryHasNoCursorBeatOrCountInAndNextAttemptNumber() {
        val state = PlaybackState().fromPractice(snapshot, segment.endFrame + 1, 5, schedule)
        assertTrue(state.recovering)
        assertEquals(2, state.attempt)
        assertNull(state.position)
        assertNull(state.beatInAvarta)
        assertFalse(state.countingIn)
    }

    @Test
    fun backgroundRecoveryNeverAutoResumes() {
        val waiting = PlaybackState(playing = true, phase = PracticePhase.RECOVERY)
        assertNull(PracticeUiState(playback = waiting).backgroundAudio())
        assertEquals(BackgroundAudio.PLAYBACK, PracticeUiState(playback = waiting.copy(phase = PracticePhase.PLAYING)).backgroundAudio())
        assertNull(PracticeUiState(playback = waiting.copy(playing = false, pausedForContinue = true)).backgroundAudio())
    }

    @Test
    fun transportIsOptInAndFeedbackDoesNotFollowListenMode() {
        val normal = PracticeSettings()
        assertFalse(PracticePlayback.policy(normal, false).needsTransport)
        assertFalse(PracticePlayback.policy(normal.copy(loop = true), false).needsTransport)
        val listen = PracticePlayback.policy(normal.copy(practiceMode = PracticeMode.LISTEN_REPEAT), false)
        assertTrue(listen.needsTransport)
        assertFalse(listen.feedback)
        assertFalse(listen.responseGuide)
        val recovery = normal.copy(loop = true, recoveryMode = RecoveryMode.TIMED)
        assertTrue(PracticePlayback.policy(recovery, true).needsTransport)
        assertTrue(recovery.usesTransport())
        assertFalse(recovery.copy(recoveryMode = RecoveryMode.CONTINUOUS).usesTransport())
        assertFalse(recovery.copy(loop = false).usesTransport())
        assertTrue(recovery.copy(loop = false, practiceMode = PracticeMode.LISTEN_REPEAT).usesTransport())
    }

    @Test
    fun captureIsOffThroughoutTimedAndIndefiniteRestUntilContinuation() {
        assertFalse(PracticePlayback.captureMayStart(snapshot, segment.endFrame + 1))
        assertTrue(PracticePlayback.captureMayStart(snapshot, segment.endFrame + 500))
        val waiting = snapshot.copy(recoveries = listOf(PracticeRecovery(segment.endFrame, null, null, true, true)))
        assertFalse(PracticePlayback.captureMayStart(waiting, segment.endFrame + 10_000))
        val initial = snapshot.copy(segments = emptyList(), recoveries = listOf(PracticeRecovery(0, null, 0, true, false)))
        assertTrue(PracticePlayback.captureMayStart(initial, 0))
    }

    @Test
    fun feedbackOffRejectsAQueuedScoredPreparationAndNewTokenReprepares() {
        val queued = PracticePreparation(1, 4, 2, true, true)
        assertFalse(PracticePlayback.shouldPrepareCapture(false, queued, null))
        assertTrue(PracticePlayback.shouldPrepareCapture(true, queued, null))
        assertFalse(PracticePlayback.shouldPrepareCapture(true, queued, 4))
        assertTrue(PracticePlayback.shouldPrepareCapture(true, queued.copy(segmentId = 5), 4))
        assertFalse(PracticePlayback.shouldPrepareCapture(true, queued.copy(scored = false), null))
    }

    @Test
    fun currentTransportMicFailureStopsButReplacedOrFinishedCaptureDoesNot() {
        assertTrue(acceptsMicFailure(4, 4, hasCapture = true))
        assertFalse(acceptsMicFailure(3, 4, hasCapture = true))
        assertFalse(acceptsMicFailure(4, 4, hasCapture = false))
    }
}
