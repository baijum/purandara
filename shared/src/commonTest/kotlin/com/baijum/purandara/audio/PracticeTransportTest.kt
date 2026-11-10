package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.TanpuraBank
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.PracticePolicy
import com.baijum.purandara.practice.RecoveryMode
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeTransportTest {
    private val sr = 1000
    private val bank = SampleBank(
        sr,
        HarmoniumBank.midiRange.associateWith { FloatArray(2200) { 0.4f } },
        TanpuraBank.SA_MIDIS.associateWith { FloatArray(997) { i -> (0.2 * sin(2 * PI * i / 997)).toFloat() } },
    )
    private val schedule = Schedule.of(
        VarisaiCatalog.byId("sarali-1")!!,
        PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 120, kala = 4),
    )
    private val options = RenderOptions(click = true, countIn = true)
    private val timed = PracticePolicy(repeat = true, recoveryMode = RecoveryMode.TIMED, recoverySeconds = 1)

    private fun transport(policy: PracticePolicy = timed, mix: RenderOptions = options) =
        PracticeTransport.create(bank, schedule, mix, policy, 7)

    private fun render(source: PcmSource, frames: Int, block: Int = frames): FloatArray {
        val result = FloatArray(frames)
        var at = 0
        while (at < frames) {
            val n = source.render(result, at, minOf(block, frames - at))
            assertTrue(n > 0, "stopped at $at/$frames")
            at += n
        }
        return result
    }

    private fun first(t: PracticeTransport): PracticeSegment {
        t.render(FloatArray(1))
        return t.segments.first()
    }

    @Test
    fun defaultsAndValidationPreserveExistingPlayback() {
        assertFalse(PracticePolicy().needsTransport)
        assertFalse(PracticePolicy(repeat = true).needsTransport)
        assertTrue(timed.needsTransport)
        assertTrue(PracticePolicy(mode = PracticeMode.LISTEN_REPEAT).needsTransport)
        assertEquals(5, PracticePolicy().recoverySeconds)
        assertFalse(PracticePolicy().responseGuide)
        assertFailsWith<IllegalArgumentException> { PracticePolicy(recoverySeconds = 0) }
        assertFailsWith<IllegalArgumentException> { PracticePolicy(recoverySeconds = 31) }
    }

    @Test
    fun timedBreakStartsAfterReleaseAndEachAttemptHasCountIn() {
        val t = transport()
        val one = first(t)
        render(t, (one.endFrame - t.position + sr + 1).toInt())
        val two = t.segments[1]
        assertEquals(one.endFrame + sr, two.startFrame)
        assertEquals(one.countInFrames, two.countInFrames)
        assertEquals(one.performanceFrames, two.performanceFrames)
        assertEquals(one.releaseFrames, two.releaseFrames)
        assertEquals(2, two.attempt)
        val snapshot = t.snapshot()
        assertEquals(PracticePhase.RELEASE, snapshot.phaseAt(one.performanceEndFrame))
        assertEquals(PracticePhase.RECOVERY, snapshot.phaseAt(one.endFrame))
        assertEquals(sr.toLong(), snapshot.remainingFramesAt(one.endFrame))
        assertEquals(PracticePhase.COUNT_IN, snapshot.phaseAt(two.startFrame))
        assertEquals(PracticePhase.PLAYING, snapshot.phaseAt(two.performanceStartFrame))
    }

    @Test
    fun boundariesInsideBlocksAndArbitraryBlockSizesAreIdentical() {
        val whole = transport()
        val chunked = transport()
        val frames = 23_173
        assertTrue(render(whole, frames).contentEquals(render(chunked, frames, 137)))
        assertEquals(whole.snapshot(), chunked.snapshot())
    }

    @Test
    fun tanpuraPhaseDoesNotRestartDuringCountInReleaseRestOrNextAttempt() {
        val mix = options.copy(harmoniumGain = 0f, click = false, countIn = false)
        val t = transport(mix = mix)
        val reference = SampleRenderer(bank, schedule, mix.copy(loop = true))
        assertTrue(render(t, 20_331, 151).contentEquals(render(reference, 20_331, 299)))
        assertTrue(t.segments.size > 1)
    }

    @Test
    fun recoveryHasNoExerciseClicksAndDroneOffIsSilent() {
        val t = transport(mix = options.copy(drone = false))
        val one = first(t)
        render(t, (one.endFrame - t.position).toInt())
        assertTrue(render(t, sr).all { it == 0f })
    }

    @Test
    fun readinessGatesCountInWithoutBlockingRenderOrStartingEarly() {
        val t = transport(timed.copy(feedback = true))
        val preparation = assertNotNull(t.pendingPreparation)
        assertTrue(preparation.scored)
        render(t, 737)
        assertTrue(t.segments.isEmpty())
        assertEquals(PracticePhase.WAITING, t.snapshot().phaseAt(736))
        assertFalse(t.setReady(8, preparation.segmentId, true))
        assertFalse(t.setReady(7, preparation.segmentId + 1, true))
        assertTrue(t.setReady(7, preparation.segmentId, false))
        assertTrue(t.setReady(7, preparation.segmentId, true))
        val first = first(t)
        assertEquals(737, first.startFrame)
        assertTrue(first.countInFrames > 0)
        render(t, (first.endFrame - t.position + sr + 500).toInt())
        assertEquals(1, t.segments.size)
        val second = assertNotNull(t.pendingPreparation)
        assertEquals(2, second.attempt)
        assertTrue(t.setReady(7, second.segmentId, true))
        val nextStart = t.position
        t.render(FloatArray(1))
        assertEquals(nextStart, t.segments[1].startFrame)
    }

    @Test
    fun whenReadyAndWaitForMeRequireExplicitContinue() {
        val t = transport(timed.copy(recoveryMode = RecoveryMode.WHEN_READY))
        val one = first(t)
        render(t, (one.endFrame - t.position + 2 * sr).toInt())
        assertEquals(1, t.segments.size)
        assertEquals(PracticePhase.WAITING, t.snapshot().phaseAt(t.position - 1))
        assertTrue(t.continuePractice(7))
        t.render(FloatArray(1))
        val two = t.segments[1]
        assertTrue(t.waitForMe(7)) // during sound, cancel the next automatic continuation too
        render(t, (two.endFrame - t.position + 2 * sr).toInt())
        assertEquals(2, t.segments.size)
        assertTrue(t.continuePractice(7))
        t.render(FloatArray(1))
        assertEquals(3, t.segments.size)
    }

    @Test
    fun waitForMeCancelsTimedDeadlineAndContinueDoesNotUndoQueuedAudio() {
        val t = transport()
        val one = first(t)
        render(t, (one.endFrame - t.position + sr / 2).toInt())
        assertTrue(t.waitForMe(7))
        render(t, 2 * sr)
        assertEquals(1, t.segments.size)
        val start = t.position
        assertTrue(t.continuePractice(7))
        t.render(FloatArray(1))
        assertEquals(start, t.segments[1].startFrame)
        val committed = t.segments[1]
        assertTrue(t.waitForMe(7))
        assertEquals(committed, t.segments[1])
    }

    @Test
    fun recoveryChangesRecomputeOriginalDeadlineAndElapsedChangesRequireContinue() {
        val t = transport(timed.copy(recoverySeconds = 5))
        val one = first(t)
        render(t, (one.endFrame - t.position + 2 * sr).toInt())
        assertTrue(t.updatePolicy(7, timed.copy(recoverySeconds = 3)))
        assertEquals(one.endFrame + 3 * sr, t.snapshot().recoveryAt(t.position)!!.deadlineFrame)
        assertTrue(t.updatePolicy(7, timed)) // the new 1s deadline already passed
        assertNull(t.snapshot().recoveryAt(t.position)!!.deadlineFrame)
        render(t, 5 * sr)
        assertEquals(1, t.segments.size)
        assertTrue(t.continuePractice(7))
        t.render(FloatArray(1))
        assertEquals(2, t.segments.size)
    }

    @Test
    fun listenResponseIsUnscoredWithoutFeedbackAndGuideIsOffByDefault() {
        val t = transport(PracticePolicy(mode = PracticeMode.LISTEN_REPEAT), options.copy(countIn = false, click = false, drone = false))
        val listen = first(t)
        assertTrue(listen.listening)
        assertFalse(listen.scored)
        assertEquals(0, listen.countInFrames)
        assertTrue(render(t, (listen.performanceEndFrame - t.position).toInt()).any { it > 0f })
        render(t, listen.releaseFrames + 1)
        val response = t.segments[1]
        assertTrue(response.response)
        assertFalse(response.scored)
        assertNull(t.pendingPreparation)
        assertTrue(render(t, (response.endFrame - t.position).toInt()).all { it == 0f })
        assertEquals(PracticeCompletion.NATURAL, t.snapshot().completion)
        render(t, (0.08 * sr).toInt())
        assertEquals(0, t.render(FloatArray(1)))
        assertEquals(2, t.segments.size)
        assertEquals(PracticePhase.FINISHED, t.snapshot().phaseAt(t.position))
    }

    @Test
    fun listenFeedbackPreparesOnlyResponseAndFreshCalibrationCountInIsForced() {
        val t = transport(PracticePolicy(mode = PracticeMode.LISTEN_REPEAT, feedback = true), options.copy(countIn = false))
        val listen = first(t)
        assertNull(t.pendingPreparation)
        render(t, (listen.endFrame - t.position).toInt())
        val p = assertNotNull(t.snapshot().pendingPreparation)
        assertTrue(p.response && p.scored)
        render(t, sr)
        assertEquals(1, t.segments.size)
        t.setReady(7, p.segmentId, true)
        t.render(FloatArray(1))
        assertTrue(t.segments[1].countInFrames > 0)
        val snapshot = t.snapshot()
        assertEquals(PracticePhase.LISTEN, snapshot.phaseAt(listen.performanceStartFrame))
        assertEquals(PracticePhase.YOUR_TURN, snapshot.phaseAt(t.segments[1].performanceStartFrame))
    }

    @Test
    fun listenRepeatRunsPairsAndResponseGuideIsExplicit() {
        val policy = timed.copy(mode = PracticeMode.LISTEN_REPEAT, responseGuide = true)
        val t = transport(policy, options.copy(countIn = false, drone = false, click = false))
        val listen = first(t)
        render(t, (listen.endFrame - t.position + sr + 1).toInt())
        val response = t.segments[1]
        assertTrue(render(t, response.performanceFrames - 1).any { it > 0f })
        render(t, response.releaseFrames + sr + 1)
        assertEquals(listOf(true, false, true), t.segments.map { it.listening })
        assertEquals(listOf(1, 1, 2), t.segments.map { it.attempt })
    }

    @Test
    fun stopInvalidatesEveryPendingContinuationAndFadeCanCrossSegmentEnd() {
        val t = transport()
        val one = first(t)
        render(t, (one.endFrame - t.position - 3).toInt())
        t.fadeOut(0.1)
        assertFalse(t.continuePractice(7))
        assertFalse(t.waitForMe(7))
        assertFalse(t.updatePolicy(7, timed))
        assertNull(t.pendingPreparation)
        val final = render(t, sr / 10, 17)
        assertTrue(final.all { it.isFinite() && it in -1f..1f })
        assertEquals(0, t.render(FloatArray(1)))
        assertEquals(1, t.segments.size)
        assertEquals(PracticeCompletion.REQUESTED_STOP, t.snapshot().completion)
    }

    @Test
    fun stopInReadinessWaitAndResetRejectsStalePreparations() {
        val t = transport(timed.copy(feedback = true))
        val old = assertNotNull(t.pendingPreparation)
        t.reset()
        assertFalse(t.setReady(7, old.segmentId, true))
        val new = assertNotNull(t.pendingPreparation)
        assertTrue(new.segmentId > old.segmentId)
        t.terminate(PracticeCompletion.INTERRUPTION, 0.02)
        assertFalse(t.setReady(7, new.segmentId, true))
        render(t, 20)
        assertEquals(0, t.render(FloatArray(1)))
        assertTrue(t.segments.isEmpty())
        assertEquals(PracticeCompletion.INTERRUPTION, t.snapshot().completion)
    }

    @Test
    fun disablingRepeatWhileWaitingCancelsNextAttemptButPreservesRequiredResponse() {
        for (recovery in listOf(RecoveryMode.TIMED, RecoveryMode.WHEN_READY)) {
            val normal = transport(timed.copy(recoveryMode = recovery))
            val one = first(normal)
            render(normal, (one.endFrame - normal.position).toInt())
            assertTrue(normal.updatePolicy(7, timed.copy(recoveryMode = recovery, repeat = false)))
            assertNull(normal.pendingPreparation)
            assertEquals(PracticeCompletion.NATURAL, normal.snapshot().completion)
            assertFalse(normal.continuePractice(7))
            render(normal, 80)
            assertEquals(0, normal.render(FloatArray(1)))
            assertEquals(1, normal.segments.size)

            val pairPolicy = timed.copy(mode = PracticeMode.LISTEN_REPEAT, recoveryMode = recovery)
            val pair = transport(pairPolicy)
            val listen = first(pair)
            render(pair, (listen.endFrame - pair.position).toInt())
            assertTrue(pair.updatePolicy(7, pairPolicy.copy(repeat = false)))
            assertTrue(assertNotNull(pair.pendingPreparation).response)
            pair.continuePractice(7)
            pair.render(FloatArray(1))
            val response = pair.segments[1]
            render(pair, (response.endFrame - pair.position + 80).toInt())
            assertEquals(0, pair.render(FloatArray(1)))
            assertEquals(2, pair.segments.size)
        }
    }

    @Test
    fun togglingFeedbackCannotReuseStaleReadinessReply() {
        val t = transport(timed.copy(feedback = true))
        val old = assertNotNull(t.pendingPreparation)
        t.updatePolicy(7, timed.copy(feedback = false))
        val unscored = assertNotNull(t.pendingPreparation)
        assertTrue(unscored.segmentId > old.segmentId)
        t.updatePolicy(7, timed.copy(feedback = true))
        val fresh = assertNotNull(t.pendingPreparation)
        assertTrue(fresh.segmentId > unscored.segmentId)
        assertFalse(t.setReady(7, old.segmentId, true))
        render(t, 100)
        assertTrue(t.segments.isEmpty())
        assertTrue(t.setReady(7, fresh.segmentId, true))
        t.render(FloatArray(1))
        assertEquals(fresh.segmentId, t.segments.first().segmentId)
    }

    @Test
    fun finalMixClipsOnlyAfterUnclippedExerciseAndDroneHaveBeenAdded() {
        val loudBank = SampleBank(
            sr,
            HarmoniumBank.midiRange.associateWith { FloatArray(2200) { 2f } },
            TanpuraBank.SA_MIDIS.associateWith { FloatArray(997) { -0.4f } },
        )
        val mix = options.copy(harmoniumGain = 1f, droneGain = 1f, click = false, countIn = false)
        val t = PracticeTransport.create(loudBank, schedule, mix, timed, 7)
        val output = render(t, 900)
        assertEquals(1f, output[700]) // clip(2 - .64), not clip(clip(2) - .64) = .36
        assertTrue(output.all { it in -1f..1f })
    }

    @Test
    fun snapshotIsImmutableAndSegmentsArriveBeforeTheirFirstOutputFrame() {
        val t = transport()
        val before = t.snapshot()
        var called = false
        t.onSegmentStarted = { segment ->
            called = true
            assertEquals(segment.startFrame, t.position)
        }
        first(t)
        assertTrue(called)
        assertTrue(before.segments.isEmpty())
        assertEquals(0, before.renderedFrames)
        assertEquals(1, t.snapshot().segments.size)
    }
}
