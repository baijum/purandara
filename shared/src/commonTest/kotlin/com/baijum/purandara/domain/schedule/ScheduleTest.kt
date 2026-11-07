package com.baijum.purandara.domain.schedule

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Talas
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiParser
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
    fun restIsSilenceThatHoldsNothing() {
        // A rest neither sounds nor extends the swara before it.
        val parser = VarisaiParser()
        val v = parser.parse(
            """
            1
            S R G M | P _ | M _ || S R G M | P D | N S′
            """.trimIndent(),
            VarisaiSet.SARALI,
        ).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        // Pa at 4.0 lasts its own second — the rest at 5.0 is silence.
        val pa = s.notes.first { it.position.akshara == 4 }
        assertEquals(4.0, pa.startSeconds, 1e-9)
        assertEquals(1.0, pa.durationSeconds, 1e-9)
        // Ma at 6.0 also lasts one second: the rest after it extends nothing.
        val ma = s.notes.first { it.position.akshara == 6 }
        assertEquals(6.0, ma.startSeconds, 1e-9)
        assertEquals(1.0, ma.durationSeconds, 1e-9)
        // After the two rests the next avarta starts on time.
        assertEquals(8.0, s.notes[6].startSeconds, 1e-9)
        // The cursor still moves through the rests.
        assertTrue(s.cursor.any { abs(it.startSeconds - 5.0) < 1e-9 && it.position.akshara == 5 })
        assertTrue(s.cursor.any { abs(it.startSeconds - 7.0) < 1e-9 && it.position.akshara == 7 })
        // The tala keeps its beat through the silence.
        assertEquals(16, s.beats.size)
        assertEquals(16.0, s.durationSeconds, 1e-9)
    }

    @Test
    fun varnamSpeedsKeepWholeCyclesAtEveryKala() {
        // A varnam's shape: the pallavi sung @1 then repeated @2, and a @2
        // second half — six written avartas filling four tala cycles.
        val parser = VarisaiParser()
        val v = parser.parse(
            """
            1
            [pallavi]
            S R G M P D N S′ || S′ N D P M G R S ||
            " sa ri ga ma pa da ni sa || sa ni da pa ma ga ri sa ||
            (pallavi @2)
            [muktayi @2]
            S R G M P D N S′ || S′ N D P M G R S ||
            " - - - - - - - - || - - - - - - - - ||
            """.trimIndent(),
            VarisaiSet.SARALI,
        ).single()
        for (kala in 1..4) {
            val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60, kala = kala))
            // Whole cycles: 4 cycles per pass at kala 1, halving per kala; from
            // kala 4 the piece repeats to fill its last cycle, as the workbook has.
            val cycles = 4.0 / (1 shl (kala - 1))
            var reps = 1
            while (reps * cycles % 1.0 != 0.0) reps++
            assertEquals(reps, s.repetitions, "kala $kala repetitions")
            assertEquals(reps * cycles * 8.0, s.durationSeconds, 1e-9, "kala $kala duration")
            assertEquals(reps * cycles * 8.0, s.beats.size.toDouble(), 1e-9, "kala $kala beats")
            // Beats land on the tala, not on the notation: one per tala akshara, always.
            assertEquals(listOf(0.0, 1.0, 2.0, 3.0), s.beats.take(4).map { it.startSeconds }, "kala $kala")
        }
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        // The @1 pallavi takes 8 seconds an avarta; the @2 repeats take 4.
        assertEquals(8.0, s.avartaSecondsIn(0), 1e-9)
        assertEquals(4.0, s.avartaSecondsIn(2), 1e-9)
        assertEquals(8.0, s.avartaSeconds, 1e-9)
        // Starts: pallavi 0-8-16, its repeat 16-20-24, muktayi 24-28-32.
        assertEquals(listOf(0.0, 8.0, 16.0, 20.0, 24.0, 28.0, 32.0), (0..6).map { s.avartaStartSeconds(it) })
        // A @2 avarta's notes last half a second each (one swara per cell).
        val fast = s.notes.first { it.position.avarta == 2 }
        assertEquals(0.5, fast.durationSeconds, 1e-9)
        assertEquals(16.0, fast.startSeconds, 1e-9)
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
    fun beatsFollowTheExerciseTalaAtEveryKala() {
        val parser = VarisaiParser()
        for (tala in Talas.all) {
            val line = List(tala.aksharas) { "S" }.joinToString(" ")
            val v = parser.parse("1\ntala: ${tala.key}\n$line\n$line", VarisaiSet.SARALI).single()
            for (kala in PerformanceSettings.KALA_RANGE) {
                val s = Schedule.of(v, PerformanceSettings(saHz = saC3, kala = kala))
                assertEquals(tala, s.tala)
                assertEquals(0, s.beats.size % tala.aksharas, "${tala.key} kala $kala fills whole avartas")
                s.beats.forEach { b ->
                    assertEquals(b.index % tala.aksharas, b.beatInAvarta)
                    assertEquals(tala.beats[b.beatInAvarta], b.beat)
                }
                assertEquals(s.durationSeconds, s.beats.size * s.settings.talaAksharaSeconds, 1e-9)
            }
        }
    }

    @Test
    fun severalAksharasToABeatShareTheBeat() {
        val line = List(16) { "S" }.joinToString(" ")
        val v = VarisaiParser().parse("1\nbeat: 2\n$line", VarisaiSet.SARALI).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        assertEquals(0.5, s.aksharaSeconds, 1e-9)
        assertEquals(8.0, s.avartaSeconds, 1e-9)
        assertEquals(8.0, s.durationSeconds, 1e-9)
        assertEquals(8, s.beats.size)
        val second = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60, kala = 2))
        assertEquals(2, second.repetitions) // fills one whole avarta of beats
        assertEquals(8, second.beats.size)
    }

    @Test
    fun twoKalaiCountsSplitEveryBeat() {
        val parser = VarisaiParser()
        val v = parser.parse(
            """
            1
            kalai: 2
            S R G M P D N S′ || S′ N D P M G R S ||
            """.trimIndent(),
            VarisaiSet.SARALI,
        ).single()
        assertEquals(2, v.kalai)
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        // Two cycles, 32 counts half a second apart; the written swaras are still
        // one per second (the notation's beat: is 1 here).
        assertEquals(32, s.beats.size)
        assertEquals(16.0, s.durationSeconds, 1e-9)
        assertEquals(0.5, s.beats[1].startSeconds, 1e-9)
        assertEquals(1.0, s.beats[2].startSeconds, 1e-9)
        // Count 8 is the drutam clap, four seconds in; count 16 starts the second cycle.
        assertEquals(4.0, s.beats[8].startSeconds, 1e-9)
        // The kriya repeats on its second count; the cycle's kriyas follow the tala.
        assertEquals(1, s.beats[1].countInKriya)
        assertEquals(s.beats[0].beat, s.beats[1].beat)
        assertEquals(Kriya.CLAP, s.beats[8].beat.kriya)
        assertEquals(0, s.beats[8].countInKriya)
        assertEquals(1, s.beats[9].countInKriya)
        assertEquals((0 until 32).map { it % 16 }, s.beats.map { it.beatInAvarta })
        // At every kala the counts stay 16 per cycle and whole.
        for (kala in PerformanceSettings.KALA_RANGE) {
            val k = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60, kala = kala))
            assertEquals(0, k.beats.size % 16, "kala $kala fills whole two-kalai cycles")
            assertEquals(k.durationSeconds, k.beats.size * 0.5, 1e-9, "kala $kala")
        }
    }

    @Test
    fun misraChapuKeepsItsSevenBeatsAtEveryKala() {
        val line = List(7) { "S" }.joinToString(" ")
        val v = VarisaiParser().parse("1\ntala: misra-chapu\n$line\n$line", VarisaiSet.SARALI).single()
        assertEquals(Talas.byKey("misra-chapu"), v.tala)
        for (kala in PerformanceSettings.KALA_RANGE) {
            val s = Schedule.of(v, PerformanceSettings(saHz = saC3, kala = kala))
            assertEquals(0, s.beats.size % 7, "kala $kala ends mid-avarta")
            // The chapu angas start on beats 1 and 4 of each cycle, both claps.
            val angaStarts = s.beats.withIndex().filter { it.value.beat.startsAnga }.map { it.index }
            assertEquals((0 until s.beats.size).filter { it % 7 == 0 || it % 7 == 3 }, angaStarts)
            assertEquals(s.durationSeconds, s.beats.size * s.settings.talaAksharaSeconds, 1e-9)
        }
    }

    @Test
    fun khandaGatiSectionPacksMoreCellsIntoTheSameBeats() {
        // One 16-cell avarta (beat 2) then one 40-cell khanda-gati avarta (beat 5).
        val v = VarisaiParser().parse(
            "1\ntala: adi\nbeat: 2\n${List(16) { "S" }.joinToString(" ")} ||\n" +
                "[khanda]\nbeat: 5\n${List(40) { "S" }.joinToString(" ")} ||\n",
            VarisaiSet.SARALI,
        ).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        // Both avartas fill 8 beats: the tala beat is unchanged, only the subdivision differs.
        assertEquals(16, s.beats.size)
        assertEquals(16.0, s.durationSeconds, 1e-9)
        assertEquals(16.0, s.beats.size * s.settings.talaAksharaSeconds, 1e-9)
        assertEquals(8.0, s.avartaSeconds, 1e-9)
        // The khanda cells are 2/5 the length of the beat-2 cells, starting at 8 s.
        assertEquals(0.5, s.notes[0].durationSeconds, 1e-9)
        val khanda = s.notes.first { it.position.avarta == 1 }
        assertEquals(8.0, khanda.startSeconds, 1e-9)
        assertEquals(0.2, khanda.durationSeconds, 1e-9)
        // Notes and beats stay aligned through the khanda avarta.
        assertEquals(0.0, khanda.startSeconds - s.beats[8].startSeconds, 1e-9)
        // The khanda section alone also fills whole cycles, at every kala.
        val khandaSection = v.sections.single()
        PerformanceSettings.KALA_RANGE.forEach { kala ->
            val section = Schedule.ofSection(v, PerformanceSettings(saHz = saC3, kala = kala), khandaSection)
            assertEquals(0, section.beats.size % v.tala.aksharas, "kala $kala ends mid-avarta")
            assertEquals(section.durationSeconds, section.beats.size * section.settings.talaAksharaSeconds, 1e-9)
        }
    }

    @Test
    fun oneSectionKeepsThePieceAvartaNumbers() {
        val gitam = varisai("gitam-1")
        assertEquals(
            listOf("Charanam 1", "Pallavi", "Charanam 2", "Charanam 3"),
            gitam.sections.map { it.label },
        )
        val pallavi = gitam.sections[1]
        assertEquals(4..9, pallavi.firstAvarta..pallavi.lastAvarta)
        val s = Schedule.ofSection(gitam, PerformanceSettings(saHz = saC3), pallavi)
        assertEquals(pallavi, s.section)
        assertEquals(6 * s.avartaSeconds, s.durationSeconds, 1e-9)
        assertEquals(4, s.notes.first().position.avarta)
        assertEquals(9, s.notes.last().position.avarta)
        assertEquals(0.0, s.avartaStartSeconds(4), 1e-9)
        assertEquals(2 * s.avartaSeconds, s.avartaStartSeconds(6), 1e-9)
        assertEquals(null, Schedule.of(gitam, PerformanceSettings(saHz = saC3)).section)
        // Exercises without sections offer none.
        assertTrue(varisai("sarali-1").sections.isEmpty())
    }

    @Test
    fun ragaComesFromTheExercise() {
        val v = VarisaiParser().parse("1\nraga: malahari\ntala: eka-4\nS R G M", VarisaiSet.SARALI).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3))
        assertEquals(Raga.MALAHARI, s.raga)
        assertEquals(listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1), s.notes.map { it.note.swara })
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
            assertEquals(0, s.beats.size % v.tala.aksharas, "${v.id} kala $kala ends mid-avarta")
        }
    }

    @Test
    fun everyExercisePlayableForEverySaItAllowsAndBothTunings() = runTest {
        val halfSemitone = 2.0.pow(0.5 / 12)
        checkAll(Arb.element(VarisaiCatalog.all), Arb.int(43..60), Arb.enum<Tuning>(), Arb.int(-50..50)) { v, picked, tuning, cents ->
            val saMidi = v.nearestPlayableSa(picked)
            val s = Schedule.of(v, PerformanceSettings(saHz = Shruti.saHz(saMidi, cents), tuning = tuning))
            s.notes.forEach { n ->
                assertTrue(n.playback.midi in HarmoniumBank.midiRange)
                // At most half a semitone (a fine-tune of ±50 cents can land exactly there).
                assertTrue(n.playback.rate in (1 / halfSemitone - 1e-9)..(halfSemitone + 1e-9), "${v.id} Sa $saMidi: rate ${n.playback.rate}")
                assertEquals(n.frequencyHz, Pitch.midiToHz(n.playback.midi) * n.playback.rate, 1e-9 * n.frequencyHz)
            }
        }
    }

    @Test
    fun playableSaRangeIsWholeForTheExercisesAndCappedWhereAPieceClimbs() {
        VarisaiCatalog.all.filter { it.set !in setOf(VarisaiSet.GITAM, VarisaiSet.SWARAJATI) }
            .forEach { assertEquals(Shruti.SA_RANGE, it.playableSaMidis, it.id) }
        VarisaiCatalog.all.forEach { v ->
            assertTrue(Shruti.DEFAULT_SA_MIDI in v.playableSaMidis, "${v.id}: the default Sa must be playable")
            assertEquals(v.playableSaMidis.last, v.nearestPlayableSa(Shruti.SA_RANGE.last))
        }
        // Analekara climbs to upper Ma: F5 at Sa C4 is past the last sample (D5).
        val analekara = VarisaiCatalog.byId("gitam-5")!!
        assertTrue(analekara.playableSaMidis.last < Shruti.SA_RANGE.last)
        val tooHigh = analekara.playableSaMidis.last + 1
        assertFailsWith<IllegalArgumentException> { Schedule.of(analekara, PerformanceSettings(saHz = Shruti.saHz(tooHigh, 50))) }
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
