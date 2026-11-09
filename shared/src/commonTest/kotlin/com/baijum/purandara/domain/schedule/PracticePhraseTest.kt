package com.baijum.purandara.domain.schedule

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.varisai.PhraseReview
import com.baijum.purandara.domain.varisai.PracticePhrase
import com.baijum.purandara.domain.varisai.PracticePhrases
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiParser
import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticePhraseTest {
    private val settings = PerformanceSettings(saHz = Pitch.midiToHz(48))

    private fun piece(body: String): Varisai = VarisaiParser().parse("1\n$body", VarisaiSet.SARALI).single()

    // Synthetic review metadata tests the gate; it is never bundled as approved repertoire.
    private fun phrase(v: Varisai, first: Int = 0, last: Int = 0): PracticePhrase = PracticePhrase(
        "fixture", v.id, "Test unit", first, last, "Synthetic test notation",
        PhraseReview.APPROVED, "Test fixture", "Synthetic boundaries only",
    )

    @Test
    fun unreviewedPhrasesCannotBeScheduled() {
        val v = piece("S R G M P D N S′ || S′ N D P M G R S")
        val pending = phrase(v).copy(review = PhraseReview.PENDING, reviewedBy = "", reviewNotes = "")
        pending.validateFor(v)
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(v, settings, pending) }
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(reviewedBy = "") }
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(reviewNotes = "") }
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(sourceNotes = "") }
    }

    @Test
    fun approvedPhrasesRetainPiecePositionsAndFillCycles() {
        val v = piece("S R G M P D N S′ || S′ N D P M G R S")
        val p = phrase(v, 1, 1)
        for (kala in PerformanceSettings.KALA_RANGE) {
            val s = Schedule.ofPhrase(v, settings.copy(kala = kala), p)
            assertEquals(PracticeSelection.PHRASE, s.selection)
            assertEquals(p, s.phrase)
            assertEquals(null, s.section)
            assertFalse(s.isWholePiece)
            assertEquals(1..1, s.avartaRange)
            assertTrue(s.cursor.all { it.position.avarta == 1 })
            assertTrue(s.notes.all { it.position.avarta == 1 })
            assertEquals(0, s.beats.size % s.tala.aksharas)
            assertEquals(0.0, s.notes.first().startSeconds)
            assertEquals(s.durationSeconds, s.beats.size * s.settings.talaAksharaSeconds, 1e-9)
        }
    }

    @Test
    fun fullRangeSectionsAndPhrasesDoNotBecomeWholePieceRequests() {
        val v = piece("[Unit]\nS R G M P D N S′ || S′ N D P M G R S")
        val full = Schedule.of(v, settings)
        val section = Schedule.ofSection(v, settings, v.sections.single())
        val p = phrase(v, 0, 1)
        val selectedPhrase = Schedule.ofPhrase(v, settings, p)
        assertTrue(full.isWholePiece)
        assertEquals(null, full.section)
        assertEquals(null, full.phrase)
        assertFalse(section.isWholePiece)
        assertEquals(PracticeSelection.SECTION, section.selection)
        assertEquals(v.sections.single(), section.section)
        assertFalse(selectedPhrase.isWholePiece)
        assertEquals(full.notes, selectedPhrase.notes)
        assertEquals(full.durationSeconds, selectedPhrase.durationSeconds)
    }

    @Test
    fun phraseIdentityAndRangeMustMatchThePiece() {
        val v = piece("S R G M P D N S′ || S′ N D P M G R S")
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(firstAvarta = -1) }
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(firstAvarta = 1, lastAvarta = 0) }
        assertFailsWith<IllegalArgumentException> { phrase(v).copy(id = "") }
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(v, settings, phrase(v, 0, 2)) }
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(v, settings, phrase(v).copy(varisaiId = "gitam-1")) }
    }

    @Test
    fun phraseCannotSplitAContinuationAtEitherBoundary() {
        val held = piece("S R G M P D N S′ || , N D P M G R S")
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(held, settings, phrase(held, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(held, settings, phrase(held, 1, 1)) }
        val syllable = piece("S R G M P D N S′ || S′ N D P M G R S\n\" sa ri ga ma pa da ni sa || - ni da pa ma ga ri sa")
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(syllable, settings, phrase(syllable, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { Schedule.ofPhrase(syllable, settings, phrase(syllable, 1, 1)) }
    }

    @Test
    fun catalogueHasNoInventedTeacherApprovals() {
        for (set in VarisaiSet.entries) {
            for (v in VarisaiCatalog.inSet(set)) assertTrue(PracticePhrases.forVarisai(v).isEmpty())
        }
    }
}
