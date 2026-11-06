package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Talas
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Malahari gitams: structure, words and range (docs/plan-alankaram-gitam.md). */
class GitamCatalogTest {
    private val gitams = VarisaiCatalog.inSet(VarisaiSet.GITAM)
    private fun gitam(n: Int) = VarisaiCatalog.byId("gitam-$n")!!

    @Test
    fun gitamsInTeachingOrder() {
        assertEquals(listOf("Sri Gananatha", "Kunda Gaura", "Kereya Neeranu", "Padumanabha", "Analekara"), gitams.map { it.title })
        assertEquals(gitams.map { it.title }, gitams.map { it.shortTitle })
        assertEquals(listOf(Talas.RUPAKA, Talas.RUPAKA, Talas.TRIPUTA_TISRA, Talas.TRIPUTA_TISRA, Talas.TRIPUTA_TISRA), gitams.map { it.tala })
        gitams.take(4).forEach { assertEquals(Raga.MALAHARI, it.raga, it.id) }
        assertEquals(Raga.SUDDHA_SAVERI, gitam(5).raga)
    }

    @Test
    fun sectionsFollowTheSungOrderWithRefrainsExpanded() {
        fun sections(n: Int) = gitam(n).avartas.mapNotNull { it.section }
        assertEquals(
            listOf("Charanam 1", "Pallavi", "Charanam 2", "Pallavi", "Charanam 3", "Pallavi"),
            sections(1),
        )
        assertEquals(listOf("Charanam 1", "Pallavi", "Charanam 2", "Pallavi"), sections(3))
        assertEquals(listOf("Pallavi", "Anupallavi", "Pallavi", "Charanam", "Pallavi"), sections(4))
        // Sri Gananatha: charanams of 4 avartas, a pallavi of 6, sung three times.
        assertEquals(4 * 3 + 6 * 3, gitam(1).avartas.size)
        // A repeated refrain is the same notation and words as the first time.
        val avartas = gitam(1).avartas
        val first = avartas.indexOfFirst { it.section == "Pallavi" }
        val again = avartas.indexOfLast { it.section == "Pallavi" }
        assertEquals(avartas.subList(first + 1, first + 6), avartas.subList(again + 1, again + 6))
    }

    @Test
    fun everyAvartaHasOneWordPerAkshara() {
        gitams.forEach { v ->
            v.avartas.forEach { a ->
                val words = a.words
                assertTrue(words != null && words.size == a.aksharas.size, "${v.id}: $a")
                // A held syllable never starts a section.
                if (a.section != null) assertTrue(words.first() != HELD, "${v.id}: ${a.section}")
            }
        }
    }

    @Test
    fun goldensWithWords() {
        val sri = gitam(1).avartas
        assertEquals("M P D S′ S′ R′", sri[0].toString())
        assertEquals(listOf("sri", "-", "ga", "na", "na", "tha"), sri[0].words)
        // The refrain as shivkumar.org and Sangeeta Bala Padam write it.
        val refrain = sri.first { it.section == "Pallavi" }
        assertEquals("S , R M G R", refrain.toString())
        assertEquals("lam bo da ra", refrain.spokenWords())
        assertEquals("D R′ R′ S′ D P", gitam(2).avartas[2].toString())
        assertEquals("D S′ S′ D P M P", gitam(3).avartas[0].toString())
        val padu = gitam(4).avartas[0]
        assertEquals("R S Ḍ S , S ,", padu.toString())
        assertEquals("pa du ma na bha", padu.spokenWords())
    }

    @Test
    fun rangeIsLowerPaToUpperMaWithoutOctaveLeaps() {
        gitams.forEach { v ->
            val steps = v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
                .map { v.raga.swara(it.letter).semitone + 12 * it.octave.offset }
            assertTrue(steps.all { it in -5..19 }, "${v.id}: outside lower Pa..upper Pa")
            // A leap of more than an octave in a gitam is almost surely a missing octave mark.
            steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "${v.id}: leap $a → $b") }
        }
        // The only lower note: Padumanabha's Ḍ, twice in each of the three pallavis.
        val mandra = gitams.associate { v ->
            v.id to v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
                .filter { it.octave == Octave.MANDRA }.map { it.toString() }
        }
        assertEquals(mapOf("gitam-1" to emptyList(), "gitam-2" to emptyList(), "gitam-3" to emptyList(), "gitam-4" to List(6) { "D\u0323" }, "gitam-5" to emptyList()), mandra)
    }
}
