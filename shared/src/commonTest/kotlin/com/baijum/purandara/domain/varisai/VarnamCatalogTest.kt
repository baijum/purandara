package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tana varnams: two-kalai Adi at two cells per kriya, purvanga @1 then @2, uttaranga @2. */
class VarnamCatalogTest {
    private val varnams = VarisaiCatalog.inSet(VarisaiSet.VARNAM)
    private val ninnukori = VarisaiCatalog.byId("varnam-1")!!

    @Test
    fun theVarnamsAreInTheTeachingOrderWithTheirRagas() {
        assertEquals(
            listOf(
                Triple("Ninnukori", Raga.MOHANAM, 4),
                Triple("Evvari Bodhana", Raga.ABHOGI, 4),
                Triple("Sami Ninne Kori", Raga.SHANKARABHARANAM, 4),
                Triple("Jalajaksha", Raga.HAMSADHWANI, 4),
            ),
            varnams.map { Triple(it.title, it.raga, it.avartas.takeWhile { a -> a.speed == 1 }.size) },
        )
        // Each purvanga section is written @1 (its four avartas) and repeated @2.
        varnams.forEach { v ->
            assertEquals(1, v.avartas[0].speed, v.id)
            assertEquals(2, v.avartas[4].speed, v.id)
            assertEquals(v.avartas[0].aksharas, v.avartas[4].aksharas, v.id)
        }
    }

    @Test
    fun everyVarnamIsTwoKalaiAdiAtTwoCellsPerKriya() {
        varnams.forEach { v ->
            assertEquals(2, v.beat, v.id)
            assertEquals(2, v.kalai, v.id)
            assertEquals(Tala.ADI, v.tala, v.id)
            v.avartas.forEach { assertEquals(16, it.aksharas.size, "${v.id}: $it") }
        }
    }

    @Test
    fun everyTwoKalaiSectionFillsWholeCyclesAndPlaysAtEveryKala() {
        varnams.forEach { v ->
            val sections = v.sections
            assertTrue(sections.isNotEmpty(), v.id)
            sections.forEach { span ->
                val written = span.lastAvarta - span.firstAvarta + 1
                val speed = v.avartas[span.firstAvarta].speed
                assertTrue((written * 1.0 / speed) % 1.0 == 0.0, "${span.label}: $written at @$speed")
            }
            for (kala in PerformanceSettings.KALA_RANGE) {
                val s = Schedule.of(v, PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 60, kala = kala))
                assertEquals(0, s.beats.size % 16, "${v.id} kala $kala ends mid-cycle")
                assertEquals(s.durationSeconds, s.beats.size * 0.5, 1e-9, "${v.id} kala $kala beats at count density")
            }
        }
    }

    @Test
    fun ninnukoriIsMohanamAdiTwoCellsPerKriyaInTwoKalai() {
        assertEquals("Ninnukori", ninnukori.title)
        assertEquals(Raga.MOHANAM, ninnukori.raga)
        assertEquals(Tala.ADI, ninnukori.tala)
        assertEquals(2, ninnukori.beat)
        assertEquals(2, ninnukori.kalai)
        ninnukori.avartas.forEach { assertEquals(16, it.aksharas.size, "$it") }
    }

    @Test
    fun thePurvangaIsSungAtFirstSpeedThenSecondAndTheUttarangaAtSecondOnly() {
        assertEquals(
            listOf(
                "Pallavi", "Pallavi",
                "Anupallavi", "Anupallavi",
                "Muktayi swaram", "Muktayi swaram",
                "Charanam · swaram", "Charanam · sahityam",
                "Chitta swaram 1", "Charanam",
                "Chitta swaram 2", "Charanam",
                "Chitta swaram 3", "Charanam",
                "Chitta swaram 4", "Charanam",
            ),
            ninnukori.avartas.mapNotNull { it.section },
        )
        // Written pallavi @1, its repeat @2; every uttaranga section @2.
        val speeds = ninnukori.avartas.map { it.speed }
        assertEquals(1, speeds[0])
        assertEquals(2, speeds[4])
        assertEquals(2, speeds[24])
        // Every @2 section's avartas fill whole cycles (a @2 section packs two
        // written avartas into one 16-count cycle).
        val sections = ninnukori.sections
        sections.forEach { span ->
            val written = span.lastAvarta - span.firstAvarta + 1
            val speed = ninnukori.avartas[span.firstAvarta].speed
            assertTrue((written * 1.0 / speed) % 1.0 == 0.0, "${span.label}: $written at @$speed")
        }
    }

    @Test
    fun wordsAlignAndEveryAvartaIsOneTwoKalaiCycle() {
        ninnukori.avartas.filter { it.words != null }.forEach { assertEquals(16, it.words!!.size, "$it") }
        // The sahityam sits one syllable per cell, holds written `-`.
        assertEquals(
            listOf("nin", "-", "nu", "-", "ko", "-", "-", "-", "ri", "-", "-", "-", "-", "-", "-", "-"),
            ninnukori.avartas[0].words,
        )
        assertEquals("nin nu ko ri", ninnukori.avartas[0].spokenWords())
        // The charanam's rest (the hooked comma) sits in its second avarta.
        val charanam = ninnukori.avartas.first { it.section == "Charanam · sahityam" }
        assertTrue(ninnukori.avartas[ninnukori.avartas.indexOf(charanam) + 1].aksharas[5] == Akshara.Rest)
    }

    @Test
    fun goldens() {
        assertEquals(
            "G , G , R , , , S S R R G G R R",
            ninnukori.avartas[0].toString(),
        )
        assertEquals(
            "G P G G R S R G R R S D S R G R",
            ninnukori.avartas[2].toString(),
        )
        val muktayi = ninnukori.avartas.first { it.section == "Muktayi swaram" }
        assertEquals(
            "G , R G R S R , S R S G R G S R",
            muktayi.toString(),
        )
        // The muktayi's third avarta holds its rests (the hooked commas).
        val muktayiRests = ninnukori.avartas[18]
        assertEquals(
            "G P D P D S R G R _ G S _ R D ,",
            muktayiRests.toString(),
        )
    }

    @Test
    fun staysInOneOctaveAndFillsWholeCyclesAtEveryKala() {
        val steps = ninnukori.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
            .map { ninnukori.raga.swara(it.letter).semitone + 12 * it.octave.offset }
        steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "leap $a → $b") }
        assertTrue(steps.all { it in 0..9 }, "Mohanam stays in one octave: $steps")
        // The sung order fills whole two-kalai cycles at every kala, with the
        // beats at count density.
        for (kala in PerformanceSettings.KALA_RANGE) {
            val s = Schedule.of(ninnukori, PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 60, kala = kala))
            assertEquals(0, s.beats.size % 16, "kala $kala ends mid-cycle")
            assertEquals(s.durationSeconds, s.beats.size * 0.5, 1e-9, "kala $kala beats at count density")
        }
    }
}
