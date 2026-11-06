package com.baijum.purandara.practice

import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.singalong.PassResult
import com.baijum.purandara.singalong.PassSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PracticeHistoryTest {
    private val catalog = VarisaiCatalog.all

    /** A pass of [notes] notes, [judged] judged, [good] of them in tune or close. */
    private fun pass(notes: Int, judged: Int, good: Int) = PassResult(
        pass = 0,
        marks = List(notes) { com.baijum.purandara.singalong.NoteMark(fakeEvent, com.baijum.purandara.singalong.NoteResult.NoVoice) },
        summary = PassSummary(judged = judged, inTune = good, close = 0, noVoice = notes - judged, attention = emptyList()),
    )

    private val fakeEvent = com.baijum.purandara.domain.schedule.Schedule.of(
        catalog.first(), com.baijum.purandara.domain.schedule.PerformanceSettings(saHz = 130.81),
    ).notes.first()

    @Test
    fun emptyHistoryHasNothingToContinue() {
        val h = PracticeHistory()
        assertNull(h.continueWith(catalog))
        assertNull(h.nextUp(catalog))
        assertEquals(0, h.practisedCount(VarisaiSet.SARALI, catalog))
    }

    @Test
    fun continueIsTheMostRecentAndNextUpFollowsItInTeachingOrder() {
        val h = PracticeHistory()
            .practised("sarali-5", nowMillis = 1_000)
            .practised("sarali-2", nowMillis = 3_000)
            .practised("plain-scale-1", nowMillis = 2_000)
        assertEquals("sarali-2", h.continueWith(catalog)?.id)
        assertEquals("sarali-3", h.nextUp(catalog)?.id)
        assertEquals(2, h.practisedCount(VarisaiSet.SARALI, catalog))
        assertEquals(1, h.practisedCount(VarisaiSet.PLAIN_SCALE, catalog))
    }

    @Test
    fun practisingAgainCountsAndMovesToTheFront() {
        val h = PracticeHistory().practised("sarali-1", 1).practised("sarali-2", 2).practised("sarali-1", 3)
        assertEquals(2, h["sarali-1"]?.timesPracticed)
        assertEquals("sarali-1", h.continueWith(catalog)?.id)
    }

    @Test
    fun nothingIsNextAfterTheLastExercise() {
        val last = catalog.last()
        assertNull(PracticeHistory().practised(last.id, 1).nextUp(catalog))
    }

    @Test
    fun bestKeepsTheHighestRatioAmongPassesThatJudgedHalfTheNotes() {
        var h = PracticeHistory().practised("sarali-1", 1)
        h = h.scored("sarali-1", pass(notes = 16, judged = 16, good = 12), 2)
        assertEquals(12 to 16, h["sarali-1"]!!.let { it.bestInTuneOrClose to it.bestJudged })
        h = h.scored("sarali-1", pass(notes = 16, judged = 14, good = 13), 3) // 93% beats 75%
        assertEquals(13 to 14, h["sarali-1"]!!.let { it.bestInTuneOrClose to it.bestJudged })
        h = h.scored("sarali-1", pass(notes = 16, judged = 16, good = 10), 4)
        assertEquals(13 to 14, h["sarali-1"]!!.let { it.bestInTuneOrClose to it.bestJudged })
    }

    @Test
    fun aPassThatHeardLittleOrNothingIsNotABest() {
        val h = PracticeHistory().practised("sarali-1", 1)
        assertSame(h, h.scored("sarali-1", pass(notes = 16, judged = 7, good = 7), 2))
        assertSame(h, h.scored("sarali-1", pass(notes = 16, judged = 0, good = 0), 2))
    }

    @Test
    fun aScoreAloneDoesNotMarkTheExercisePractised() {
        val h = PracticeHistory().scored("sarali-1", pass(16, 16, 16), 1)
        assertTrue(h["sarali-1"]!!.hasBest)
        assertNull(h.continueWith(catalog))
        assertEquals(0, h.practisedCount(VarisaiSet.SARALI, catalog))
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val h = PracticeHistory().practised("sarali-1", 10).practised("janta-2", 20)
            .scored("sarali-1", pass(16, 16, 15), 30)
        assertEquals(h, PracticeHistory.decode(h.encode()))
    }

    @Test
    fun unreadableTextGivesAnEmptyHistory() {
        assertEquals(PracticeHistory(), PracticeHistory.decode(null))
        assertEquals(PracticeHistory(), PracticeHistory.decode(""))
        assertEquals(PracticeHistory(), PracticeHistory.decode("{not json"))
        assertEquals(PracticeHistory(), PracticeHistory.decode("""[{"unexpected":1}]"""))
    }
}
