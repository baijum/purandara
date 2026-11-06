package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.schedule.NoteEvent
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Scoring synthetic "sung" frame streams built from the schedule. */
class NoteScorerTest {
    private val sa = Pitch.midiToHz(48)

    private fun schedule(id: String = "plain-scale-1", tempo: Int = 60, kala: Int = 1) =
        Schedule.of(VarisaiCatalog.byId(id)!!, PerformanceSettings(saHz = sa, tempo = tempo, kala = kala))

    private fun scorer(s: Schedule) = NoteScorer(s, SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, s.settings.tuning))

    /** ~86 frames per second, each sung at [sing](note, time-in-note) or silent. */
    private fun frames(s: Schedule, sing: (NoteEvent, Double) -> Double?): List<ScoredFrame> {
        val step = 512.0 / 44_100
        return generateSequence(0.0) { it + step }.takeWhile { it < s.durationSeconds }.map { t ->
            val event = s.notes.last { it.startSeconds <= t }
            val hz = sing(event, t - event.startSeconds)
            ScoredFrame(t, hz, voice = hz != null)
        }.toList()
    }

    private fun cents(hz: Double, c: Double) = hz * 2.0.pow(c / 1200)

    @Test
    fun perfectSingingIsAllInTune() {
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, _ -> e.frequencyHz })
        assertEquals(16, result.summary.judged)
        assertEquals(16, result.summary.inTune)
        assertTrue(result.summary.attention.isEmpty())
        result.marks.forEach { assertEquals(Zone.IN_TUNE, assertIs<NoteResult.Judged>(it.result).zone) }
    }

    @Test
    fun consistentlySharpIsHighEverywhere() {
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, _ -> cents(e.frequencyHz, 30.0) })
        assertEquals(0, result.summary.inTuneOrClose)
        result.marks.forEach {
            val r = assertIs<NoteResult.Judged>(it.result)
            assertEquals(Zone.OFF, r.zone)
            assertTrue(r.high)
            assertEquals(30.0, r.cents, 0.01)
        }
        assertTrue(result.summary.attention.all { it.tendency == Tendency.HIGH })
    }

    @Test
    fun oneSlightlyFlatSwaraIsCloseAndNamed() {
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, _ -> if (e.note.swara == Swara.D1) cents(e.frequencyHz, -18.0) else e.frequencyHz })
        assertEquals(14, result.summary.inTune)
        assertEquals(2, result.summary.close)
        val top = result.summary.attention.single()
        assertEquals(Note(Swara.D1), top.note)
        assertEquals(Tendency.LOW, top.tendency)
        assertEquals(2, top.times)
        assertEquals(2, top.of)
    }

    @Test
    fun singingAnOctaveLowIsAnOctaveIssueNotWrongNotes() {
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, _ -> e.frequencyHz / 2 })
        result.marks.forEach {
            val r = assertIs<NoteResult.Judged>(it.result)
            assertEquals(-1, r.octaveShift)
            assertEquals(0.0, r.cents, 0.01)
        }
        assertEquals(0, result.summary.inTuneOrClose)
        assertTrue(result.summary.attention.all { it.tendency == Tendency.OCTAVE_LOW })
    }

    @Test
    fun aLateOrSlidingOnsetDoesNotCount() {
        // Each note starts 80 cents low and slides up over its first quarter.
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, t ->
            val q = e.durationSeconds / 4
            if (t < q) cents(e.frequencyHz, -80.0 * (1 - t / q)) else e.frequencyHz
        })
        assertEquals(16, result.summary.inTune)
    }

    @Test
    fun vibratoAroundTheTargetIsInTune() {
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, t -> cents(e.frequencyHz, 30.0 * sin(2 * PI * 5.5 * t)) })
        assertEquals(16, result.summary.inTune, result.marks.map { it.result }.toString())
    }

    @Test
    fun silenceAndBleedFramesAreNoVoice() {
        val s = schedule()
        // Silent on Ma; on Pa only leakage (voice = false) carrying the exact target pitch.
        val raw = frames(s) { e, _ -> if (e.note.swara == Swara.M1) null else e.frequencyHz }
        val withBleed = raw.map { f ->
            val e = s.notes.last { it.startSeconds <= f.secondsInPass }
            if (e.note.swara == Swara.P) f.copy(voice = false) else f
        }
        val result = scorer(s).score(0, withBleed)
        val byNote = result.marks.groupBy { it.event.note.swara }
        byNote.getValue(Swara.M1).forEach { assertEquals(NoteResult.NoVoice, it.result) }
        byNote.getValue(Swara.P).forEach { assertEquals(NoteResult.NoVoice, it.result) }
        assertEquals(4, result.summary.noVoice)
        assertEquals(12, result.summary.inTune)
        assertEquals(16, result.summary.judged)
    }

    @Test
    fun aDifferentSwaraIsReportedByName() {
        // Singing R2 (9/8) where the raga wants R1 (16/15).
        val s = schedule()
        val result = scorer(s).score(0, frames(s) { e, _ -> if (e.note.swara == Swara.R1) sa * 9 / 8 else e.frequencyHz })
        val ri = result.marks.filter { it.event.note.swara == Swara.R1 }.map { assertIs<NoteResult.Judged>(it.result) }
        ri.forEach {
            assertEquals(Zone.OFF, it.zone)
            assertEquals(Swara.R2, it.sung?.swara)
        }
        assertEquals(Tendency.WRONG_SWARA, result.summary.attention.first().tendency)
    }

    @Test
    fun fastKalasAreNotJudged() {
        val s = schedule(kala = 3) // 0.25 s at tempo 60 → exactly the limit; use tempo 75 to go under
        val fast = schedule(tempo = 75, kala = 3)
        assertTrue(fast.notes.all { it.durationSeconds < NoteScorer.MIN_JUDGED_SECONDS })
        val result = scorer(fast).score(0, frames(fast) { e, _ -> e.frequencyHz })
        assertTrue(result.marks.all { it.result == NoteResult.TooShort })
        assertEquals(0, result.summary.judged)
        assertTrue(s.notes.isNotEmpty())
    }

    @Test
    fun karvaiNotesAreJudgedOverTheirWholeLength() {
        val s = schedule("sarali-4")
        val result = scorer(s).score(0, frames(s) { e, _ -> e.frequencyHz })
        assertEquals(30, result.marks.size)
        assertEquals(30, result.summary.inTune)
    }
}
