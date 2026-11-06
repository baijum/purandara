package com.baijum.purandara.domain.schedule

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScheduleTest {
    private val saC3 = Pitch.midiToHz(48)
    private val plain = VarisaiCatalog.inSet(VarisaiSet.PLAIN_SCALE).single()
    private fun varisai(id: String) = VarisaiCatalog.byId(id)!!

    @Test
    fun plainScaleGolden() {
        val s = Schedule.of(plain, PerformanceSettings(saHz = saC3, tempo = 60))
        // One swara per second; just intonation over Sa = C3.
        val expected = listOf(
            Triple(0.0, Swara.S, 48), Triple(1.0, Swara.R1, 49), Triple(2.0, Swara.G3, 52),
            Triple(3.0, Swara.M1, 53), Triple(4.0, Swara.P, 55), Triple(5.0, Swara.D1, 56),
            Triple(6.0, Swara.N3, 59), Triple(7.0, Swara.S, 60), Triple(8.0, Swara.S, 60),
            Triple(9.0, Swara.N3, 59), Triple(10.0, Swara.D1, 56), Triple(11.0, Swara.P, 55),
            Triple(12.0, Swara.M1, 53), Triple(13.0, Swara.G3, 52), Triple(14.0, Swara.R1, 49),
            Triple(15.0, Swara.S, 48),
        )
        assertEquals(expected.size, s.notes.size)
        s.notes.zip(expected).forEach { (e, x) ->
            assertEquals(x.first, e.startSeconds, 1e-9)
            assertEquals(1.0, e.durationSeconds, 1e-9)
            assertEquals(x.second, e.note.swara)
            assertEquals(x.third, e.playback.midi)
        }
        assertEquals(16.0, s.durationSeconds, 1e-9)
        assertEquals(16, s.beats.size)
        assertEquals(1, s.repetitions)
    }

    @Test
    fun sarali1AtSixtyLastsThirtyTwoSeconds() {
        val s = Schedule.of(varisai("sarali-1"), PerformanceSettings(saHz = saC3))
        assertEquals(32.0, s.durationSeconds, 1e-9)
        assertEquals(32, s.beats.size)
        assertEquals(32, s.notes.size)
    }

    @Test
    fun karvaiExtendsThePreviousNote() {
        // Sarali 4 opens: S R G M | P , | S R
        val s = Schedule.of(varisai("sarali-4"), PerformanceSettings(saHz = saC3, tempo = 60))
        val pa = s.notes[4]
        assertEquals(Swara.P, pa.note.swara)
        assertEquals(4.0, pa.startSeconds, 1e-9)
        assertEquals(2.0, pa.durationSeconds, 1e-9)
        assertEquals(6.0, s.notes[5].startSeconds, 1e-9)
        // The karvai still moves the highlight.
        assertTrue(s.cursor.any { abs(it.startSeconds - 5.0) < 1e-9 && it.position.akshara == 5 })
        assertEquals(30, s.notes.size) // 32 aksharas; two karvais (P , and M ,)
    }

    @Test
    fun pairsSplitTheAkshara() {
        val s = Schedule.of(varisai("janta-1"), PerformanceSettings(saHz = saC3, tempo = 60))
        assertEquals(0.0, s.notes[0].startSeconds, 1e-9)
        assertEquals(0.5, s.notes[1].startSeconds, 1e-9)
        assertEquals(0.5, s.notes[0].durationSeconds, 1e-9)
        assertEquals(1, s.notes[1].position.slot)
    }

    @Test
    fun kalasFillWholeAvartasLikeTheWorkbook() {
        // Plain scale: kala 1 = 2 avartas; kala 2 = 1 avarta;
        // kala 3 = sung twice in one avarta; kala 4 = four times in one avarta.
        val expected = mapOf(1 to (1 to 16), 2 to (1 to 8), 3 to (2 to 8), 4 to (4 to 8))
        expected.forEach { (kala, repsAndBeats) ->
            val s = Schedule.of(plain, PerformanceSettings(saHz = saC3, tempo = 60, kala = kala))
            assertEquals(repsAndBeats.first, s.repetitions, "kala $kala repetitions")
            assertEquals(repsAndBeats.second, s.beats.size, "kala $kala beats")
            assertEquals(repsAndBeats.second.toDouble(), s.durationSeconds, 1e-9, "kala $kala duration")
            assertEquals(16 * repsAndBeats.first, s.notes.size)
            assertEquals(1.0 / 2.0.pow(kala - 1), s.notes[0].durationSeconds, 1e-9)
        }
    }

    @Test
    fun beatsFollowAdiTala() {
        val s = Schedule.of(varisai("sarali-1"), PerformanceSettings(saHz = saC3, tempo = 120))
        assertEquals(0.5, s.beats[1].startSeconds, 1e-9)
        assertEquals((0 until 32).map { it % 8 }, s.beats.map { it.beatInAvarta })
        assertEquals(Kriya.CLAP, s.beats[8].beat.kriya)
        assertEquals(Kriya.WAVE, s.beats[13].beat.kriya)
    }

    @Test
    fun notesAreContiguousAndCoverTheWholePerformance() = runTest {
        checkAll(Arb.element(VarisaiCatalog.all), Arb.int(1..4), Arb.int(30..120)) { v, kala, tempo ->
            val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = tempo, kala = kala))
            s.notes.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(a.startSeconds + a.durationSeconds - b.startSeconds) < 1e-9, "${v.id}: gap at ${b.startSeconds}")
            }
            val last = s.notes.last()
            assertEquals(s.durationSeconds, last.startSeconds + last.durationSeconds, 1e-9)
            assertEquals(s.durationSeconds, s.beats.size * s.settings.talaAksharaSeconds, 1e-9)
            assertEquals(0, s.beats.size % 8, "${v.id} kala $kala ends mid-avarta")
        }
    }

    @Test
    fun everyExercisePlayableForEverySaInRangeAndBothTunings() = runTest {
        val halfSemitone = 2.0.pow(0.5 / 12)
        checkAll(Arb.element(VarisaiCatalog.all), Arb.int(43..60), Arb.enum<Tuning>()) { v, saMidi, tuning ->
            val s = Schedule.of(v, PerformanceSettings(saHz = Pitch.midiToHz(saMidi), tuning = tuning))
            s.notes.forEach { n ->
                assertTrue(n.playback.midi in HarmoniumBank.midiRange)
                assertTrue(n.playback.rate in 1 / halfSemitone..halfSemitone, "${v.id} Sa $saMidi: rate ${n.playback.rate}")
                assertEquals(n.frequencyHz, Pitch.midiToHz(n.playback.midi) * n.playback.rate, 1e-9 * n.frequencyHz)
            }
        }
    }

    @Test
    fun equalTuningLandsExactlyOnSamples() {
        val s = Schedule.of(plain, PerformanceSettings(saHz = saC3, tuning = Tuning.EQUAL))
        s.notes.forEach { assertEquals(1.0, it.playback.rate, 1e-12) }
    }

    @Test
    fun saOutsideTheBankIsRejected() {
        // Sa = C5: upper Sa would be C6, beyond the bank.
        assertFailsWith<IllegalArgumentException> { Schedule.of(plain, PerformanceSettings(saHz = Pitch.midiToHz(72))) }
    }

    @Test
    fun invalidSettingsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = saC3, kala = 0) }
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = saC3, kala = 5) }
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = saC3, tempo = 29) }
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = saC3, tempo = 121) }
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { PerformanceSettings(saHz = 0.0) }
    }
}

class SchedulePositionTest {
    private val s = Schedule.of(
        VarisaiCatalog.byId("sarali-4")!!,
        PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 60),
    )

    @Test
    fun cursorFollowsSwarasAndKarvai() {
        assertEquals(NotationPosition(0, 0, 0, 0), s.cursorAt(0.0))
        assertEquals(NotationPosition(0, 0, 0, 0), s.cursorAt(0.999))
        assertEquals(NotationPosition(0, 0, 1, 0), s.cursorAt(1.0))
        assertEquals(NotationPosition(0, 0, 5, 0), s.cursorAt(5.5)) // the karvai after P
        assertEquals(NotationPosition(0, 3, 7, 0), s.cursorAt(31.9))
    }

    @Test
    fun beatFollowsTala() {
        assertEquals(0, s.beatAt(0.2)?.beatInAvarta)
        assertEquals(5, s.beatAt(13.4)?.beatInAvarta)
        assertEquals(13, s.beatAt(13.4)?.index)
    }

    @Test
    fun outsideThePerformanceIsNull() {
        assertEquals(null, s.cursorAt(-0.1))
        assertEquals(null, s.cursorAt(32.0))
        assertEquals(null, s.beatAt(40.0))
    }

    @Test
    fun pairsAtSecondKalaHighlightEachSlot() {
        val j = Schedule.of(VarisaiCatalog.byId("janta-1")!!, PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 60, kala = 2))
        assertEquals(NotationPosition(0, 0, 0, 1), j.cursorAt(0.3)) // 4 swaras per beat: S S | R R
        assertEquals(NotationPosition(0, 0, 1, 0), j.cursorAt(0.5))
    }
}
