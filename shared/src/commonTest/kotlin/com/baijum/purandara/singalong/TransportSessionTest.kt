package com.baijum.purandara.singalong

import com.baijum.purandara.audio.PracticeSegment
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransportSessionTest {
    private val sr = 1000
    private val schedule = Schedule.of(
        VarisaiCatalog.byId("sarali-1")!!,
        PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 120, kala = 1),
    )
    private fun segment(origin: Long = 10_000) = PracticeSegment(
        7, 2, 2, false, true, false, origin, 4000,
        (schedule.durationSeconds * sr).toInt(), 250,
    )

    @Test
    fun transportClockSubtractsOriginOnlyForLocateAndCompensatesLatencyOnce() {
        val s = segment()
        val clock = SingAlongClock.forSegment(s, sr, 0.1, 0.05)
        assertEquals(11.0, clock.heardSeconds(11.15), 1e-9)
        assertNull(clock.locate(9.0))
        assertTrue(clock.inCountIn(11.15))
        assertNull(clock.locate(11.15))
        val position = assertNotNull(clock.locate(14.4))
        assertEquals(0, position.pass)
        assertEquals(0.25, position.secondsInPass, 1e-9)
        assertNull(clock.locate(14.15 + schedule.durationSeconds + 0.01))
    }

    @Test
    fun latencyAdjustedCaptureClockDoesNotCompensateOutputLatencyTwice() {
        val clock = SingAlongClock.forSegment(segment(), sr, 0.0, 0.0)
        assertEquals(0.5, clock.locate(14.5)!!.secondsInPass, 1e-9)
    }

    @Test
    fun secondAttemptPreservesGlobalOutputLevelsAndCalibratesOnlyItsOwnCountIn() {
        val levels = OutputLevels()
        levels.record(0, 0.8f) // previous attempt, must not be mistaken for this attempt
        levels.record(10_000, 0.2f)
        val session = SingAlongSession.forSegment(schedule, segment(), sr, levels, Strictness.PRECISE, 0.0, 0.0)
        assertSame(levels, session.outputLevels)
        repeat(100) { i -> session.onFrame(PitchFrame(10.1 + i * 0.03, null, 0.1f)) }
        val first = schedule.notes.first()
        session.onFrame(PitchFrame(14.1, first.frequencyHz, 0.8f))
        assertTrue(session.bleedCalibrated)
        assertEquals(0.5, session.leakageRatio, 1e-6)
        assertEquals(0.8f, levels.rmsAt(0))
        assertEquals(0.2f, levels.rmsAt(10_100))
    }

    @Test
    fun finishIsIdempotentAndLateFramesCannotCreateAnotherPass() {
        val session = SingAlongSession.forSegment(schedule, segment(), sr, OutputLevels(), Strictness.PRECISE, 0.0, 0.0)
        val note = schedule.notes.first()
        session.onFrame(PitchFrame(14.2, note.frequencyHz, 0.5f))
        val result = assertNotNull(session.finish())
        assertSame(result, session.finish())
        assertNull(session.onFrame(PitchFrame(14.3, note.frequencyHz, 0.5f)).live.reading)
        assertEquals(1, session.passResults.size)
        assertSame(result, session.finish())
    }

    @Test
    fun finishBeforePerformanceRemainsNullAndIgnoresFutureFrames() {
        val session = SingAlongSession.forSegment(schedule, segment(), sr, OutputLevels(), Strictness.PRECISE, 0.0, 0.0)
        session.onFrame(PitchFrame(11.0, null, 0f))
        assertNull(session.finish())
        session.onFrame(PitchFrame(14.3, schedule.notes.first().frequencyHz, 0.5f))
        assertNull(session.finish())
        assertTrue(session.passResults.isEmpty())
    }

    @Test
    fun twoFiniteAttemptSessionsScoreGlobalFramesIndependently() {
        val levels = OutputLevels()
        val results = (1..2).map { attempt ->
            val descriptor = segment(attempt * 30_000L).copy(attempt = attempt, segmentId = attempt.toLong())
            levels.record(descriptor.startFrame, 0.1f)
            val session = SingAlongSession.forSegment(schedule, descriptor, sr, levels, Strictness.PRECISE, 0.0, 0.0)
            val start = descriptor.startFrame.toDouble() / sr
            repeat(100) { i -> session.onFrame(PitchFrame(start + 0.1 + i * 0.03, null, 0.001f)) }
            for (note in schedule.notes) {
                var seconds = note.startSeconds + 0.01
                while (seconds < note.startSeconds + note.durationSeconds) {
                    val capture = descriptor.performanceStartFrame.toDouble() / sr + seconds
                    session.onFrame(PitchFrame(capture, note.frequencyHz, 0.5f))
                    seconds += 0.02
                }
            }
            val result = assertNotNull(session.finish())
            assertEquals(1, session.passResults.size)
            assertEquals(0, result.pass)
            assertEquals(schedule.notes.size, result.summary.inTune)
            assertSame(result, session.finish())
            result
        }
        assertEquals(results[0].summary, results[1].summary)
        assertEquals(0.1f, levels.rmsAt(30_000))
    }

    @Test
    fun factoryRejectsModelAndUnscoredSegments() {
        for (s in listOf(segment().copy(listening = true), segment().copy(scored = false))) {
            assertFailsWith<IllegalArgumentException> {
                SingAlongSession.forSegment(schedule, s, sr, OutputLevels(), Strictness.PRECISE, 0.0, 0.0)
            }
        }
    }
}
