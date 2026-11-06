package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
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
        assertEquals(
            listOf(
                "Sri Gananatha", "Kunda Gaura", "Kereya Neeranu", "Padumanabha", "Analekara",
                "Kamalajadala", "Re Re Sri Ramachandra", "Sri Ramachandra",
                "Kamala Sulochana",
            ),
            gitams.map { it.title },
        )
        assertEquals(gitams.map { it.title }, gitams.map { it.shortTitle })
        assertEquals(
            listOf(
                Talas.RUPAKA, Talas.RUPAKA, Talas.TRIPUTA_TISRA, Talas.TRIPUTA_TISRA,
                Talas.TRIPUTA_TISRA, Talas.TRIPUTA_TISRA, Talas.TRIPUTA_TISRA, Talas.DHRUVA,
                Talas.ADI,
            ),
            gitams.map { it.tala },
        )
        gitams.take(4).forEach { assertEquals(Raga.MALAHARI, it.raga, it.id) }
        assertEquals(Raga.SUDDHA_SAVERI, gitam(5).raga)
        assertEquals(Raga.KALYANI, gitam(6).raga)
        assertEquals(Raga.ARABHI, gitam(7).raga)
        assertEquals(Raga.BHAIRAVI, gitam(8).raga)
        assertEquals(Raga.ANANDABHAIRAVI, gitam(9).raga)
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
    fun singleSourceGitamGoldens() {
        // Kamalajadala: Tisra Triputa (issue #3), its 7 aksharas as 3 + 2 + 2.
        val kamala = gitam(6)
        assertEquals(Talas.byKey("triputa-3"), kamala.tala)
        assertEquals(12, kamala.avartas.size)
        kamala.avartas.forEach { assertEquals(7, it.aksharas.size, "$kamala: $it") }
        assertEquals("S S S N D N S", kamala.avartas[0].toString())
        assertEquals(listOf("ka", "ma", "la", "ja", "-", "da", "la"), kamala.avartas[0].words)
        // The all-karvai avarta holds the final "tha" through the last cycle.
        assertTrue(kamala.avartas[7].aksharas.all { it == Akshara.Karvai })
        // Re Re: mandra swaras as the source's lowercase letters.
        val rere = gitam(7)
        assertEquals(21, rere.avartas.size)
        rere.avartas.forEach { assertEquals(7, it.aksharas.size, "$rere: $it") }
        assertEquals("P , P M M P ,", rere.avartas[0].toString())
        assertEquals("P̣ Ṃ P̣ Ḍ S S ,", rere.avartas[10].toString())
        // Sri Ramachandra: Dhruva's 14 aksharas, with both Dhaivatas written.
        val sri = gitam(8)
        assertEquals(7, sri.avartas.size)
        sri.avartas.forEach { assertEquals(14, it.aksharas.size, "$sri: $it") }
        assertEquals("P D1 P S N S P D1 M P G , R S", sri.avartas[6].toString())
        assertEquals("no tu", sri.avartas[6].spokenWords())
    }

    @Test
    fun rangeIsLowerPaToUpperMaWithoutOctaveLeaps() {
        gitams.take(5).forEach { v ->
            val steps = v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
                .map { v.raga.swara(it.letter).semitone + 12 * it.octave.offset }
            assertTrue(steps.all { it in -5..19 }, "${v.id}: outside lower Pa..upper Pa")
            // A leap of more than an octave in a gitam is almost surely a missing octave mark.
            steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "${v.id}: leap $a → $b") }
        }
        // The only lower note: Padumanabha's Ḍ, twice in each of the three pallavis.
        val mandra = gitams.take(5).associate { v ->
            v.id to v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
                .filter { it.octave == Octave.MANDRA }.map { it.toString() }
        }
        assertEquals(mapOf("gitam-1" to emptyList(), "gitam-2" to emptyList(), "gitam-3" to emptyList(), "gitam-4" to List(6) { "D\u0323" }, "gitam-5" to emptyList()), mandra)
    }

    @Test
    fun theSingleSourceGitamsStayInRangeAndKeepTheirVariants() {
        // Kamalajadala spans the full middle octave; Re Re tops at Arabhi's
        // ascent Dha and reaches mandra Ma (its "poshakure" phrase); Sri
        // Ramachandra tops at Bhairavi's N2; Kamala Sulochana spans mandra Ni
        // to tara Ga (its "vimalata taakini" phrase).
        assertEquals(
            listOf(0..11, -7..9, 0..10, -2..15),
            gitams.drop(5).map { v ->
                val steps = v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
                    .map { it.swaraIn(v.raga).semitone + 12 * it.octave.offset }
                steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "${v.id}: leap $a → $b") }
                steps.min()..steps.max()
            },
        )
        // Sri Ramachandra sings both Dhaivatas: the raga's D2 and the written D1 variant.
        val bhairavi = gitam(8)
        val dhaivatas = bhairavi.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>()
            .flatMap { it.swaras }.filter { it.letter == SwaraLetter.D }.map { it.swaraIn(bhairavi.raga) }
        assertEquals(setOf(Swara.D1, Swara.D2), dhaivatas.toSet())
    }

    @Test
    fun kamalaSulochanaGolden() {
        val kamala = gitam(9)
        assertEquals(17, kamala.avartas.size)
        kamala.avartas.forEach { assertEquals(8, it.aksharas.size, "$kamala: $it") }
        assertEquals("N D N S′ S′ , N S′", kamala.avartas[0].toString())
        assertEquals(listOf("ka", "ma", "la", "su", "lo", "-", "cha", "na"), kamala.avartas[0].words)
        // The descent into mandra Ni under "bimbaa", and the tara phrase of
        // "vimalata taakini" repeated at "naaM bhoja".
        assertEquals("S , Ṇ , S G G M", kamala.avartas[4].toString())
        assertEquals("G′ R′ S′ N N D P M", kamala.avartas[12].toString())
        assertEquals("vi du man da la re", kamala.avartas[16].spokenWords())
    }
}
