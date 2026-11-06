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
}
