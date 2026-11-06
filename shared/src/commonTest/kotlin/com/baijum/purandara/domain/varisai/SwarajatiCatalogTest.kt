package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Tala
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rara Venu Gopa Bala: four to a beat, charanams sung as swaras then words. */
class SwarajatiCatalogTest {
    private val raraVenu = VarisaiCatalog.byId("swarajati-1")!!
    private val sambasiva = VarisaiCatalog.byId("swarajati-2")!!

    @Test
    fun raraVenuIsBilahariAdiFourToABeat() {
        assertEquals("Rara Venu Gopa Bala", raraVenu.title)
        assertEquals("Rara Venu", raraVenu.shortTitle)
        assertEquals(Raga.BILAHARI, raraVenu.raga)
        assertEquals(Tala.ADI, raraVenu.tala)
        assertEquals(4, raraVenu.beat)
        raraVenu.avartas.forEach { assertEquals(32, it.aksharas.size) }
    }

    @Test
    fun eachCharanamIsSungAsSwarasThenWordsThenThePallaviReturns() {
        assertEquals(
            listOf(
                "Pallavi", "Anupallavi",
                "Charanam 1 · swaram", "Charanam 1 · sahityam", "Pallavi",
                "Charanam 2 · swaram", "Charanam 2 · sahityam", "Pallavi",
                "Charanam 3 · swaram", "Charanam 3 · sahityam", "Pallavi",
            ),
            raraVenu.avartas.mapNotNull { it.section },
        )
        val start = raraVenu.avartas.indexOfFirst { it.section == "Charanam 1 · swaram" }
        assertNull(raraVenu.avartas[start].words)
        assertEquals(raraVenu.avartas[start].aksharas, raraVenu.avartas[start + 2].aksharas)
        assertEquals("nan", raraVenu.avartas[start + 2].words!!.first())
    }

    @Test
    fun wordsAlignAndNoLeapPassesAnOctave() {
        raraVenu.avartas.filter { it.words != null }.forEach { assertEquals(32, it.words!!.size) }
        val steps = raraVenu.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
            .map { raraVenu.raga.swara(it.letter).semitone + 12 * it.octave.offset }
        steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "leap $a → $b") }
        assertTrue(steps.all { it in -5..16 }, "lower Pa to upper Ga")
    }

    @Test
    fun goldens() {
        val pallavi = raraVenu.avartas.first()
        assertEquals("S , , R G , P , D , S′ , N , D , P , D P M G R S R S N\u0323 D\u0323 S , , ,", pallavi.toString())
        assertEquals("ra ra ve nu go pa ba la ra ji ta sad gu na ja ya shi la", pallavi.spokenWords())
    }

    @Test
    fun sambasivaIsKhamasAdiTwoToABeatWithAKhandaGatiCharanam() {
        assertEquals("Sambasiva Yenave", sambasiva.title)
        assertEquals("Sambasiva", sambasiva.shortTitle)
        assertEquals(Raga.KHAMAS, sambasiva.raga)
        assertEquals(Tala.ADI, sambasiva.tala)
        assertEquals(2, sambasiva.beat)
        // 24 written avartas; each charanam is duplicated as swaram + sahityam,
        // so the sung order is 54 avartas. Charanam 5's khanda-gati avartas have 40 cells.
        assertEquals(54, sambasiva.avartas.size)
        sambasiva.avartas.forEachIndexed { i, avarta ->
            assertEquals(if (i in 48..51) 40 else 16, avarta.aksharas.size, "avarta $i")
            assertEquals(if (i in 48..51) 5 else 2, sambasiva.cellsPerBeatIn(i), "avarta $i")
        }
    }

    @Test
    fun sambasivaSectionsAreSungInTheBooksOrder() {
        assertEquals(
            listOf(
                "Pallavi", "Anupallavi",
                "Charanam 1 · swaram", "Charanam 1 · sahityam", "Pallavi",
                "Charanam 2 · swaram", "Charanam 2 · sahityam", "Pallavi",
                "Charanam 3 · swaram", "Charanam 3 · sahityam", "Pallavi",
                "Charanam 4 · swaram", "Charanam 4 · sahityam", "Pallavi",
                "Charanam 5 · swaram", "Charanam 5 · sahityam", "Pallavi",
            ),
            sambasiva.avartas.mapNotNull { it.section },
        )
        val khanda = sambasiva.avartas.indexOfFirst { it.section == "Charanam 5 · swaram" }
        assertNull(sambasiva.avartas[khanda].words)
        assertEquals(sambasiva.avartas[khanda].aksharas, sambasiva.avartas[khanda + 2].aksharas)
        assertEquals("sa", sambasiva.avartas[khanda + 2].words!!.first())
    }

    @Test
    fun sambasivaWordsAlignAndStayInOneOctave() {
        sambasiva.avartas.filter { it.words != null }.forEach {
            assertEquals(it.aksharas.size, it.words!!.size)
        }
        val steps = sambasiva.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
            .map { sambasiva.raga.swara(it.letter).semitone + 12 * it.octave.offset }
        steps.zipWithNext().forEach { (a, b) -> assertTrue(abs(a - b) <= 12, "leap $a → $b") }
        assertTrue(steps.all { it in 0..11 }, "middle Sa to upper Ni")
    }

    @Test
    fun sambasivaIsPlayableAndItsPallaviIsGolden() {
        assertTrue(!sambasiva.playableSaMidis.isEmpty(), "playable at some Sa")
        val pallavi = sambasiva.avartas.first()
        assertEquals("S , , , S , N , D , P , , M G ,", pallavi.toString())
        // Held syllables are dropped from the spoken row.
        assertEquals("sa mba shi va ya na", pallavi.spokenWords())
    }
}
