package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the transcription itself, not just the parser: counts, numbering,
 * range, and the structural facts the workbook states about each set.
 */
class VarisaiCatalogTest {
    private val plainAscent = "S R G M P D N S′"
    private val plainDescent = "S′ N D P M G R S"

    @Test
    fun setSizes() {
        val expected = mapOf(
            VarisaiSet.PLAIN_SCALE to 1,
            VarisaiSet.SARALI to 16,
            VarisaiSet.MADHYA_STHAYI to 5,
            VarisaiSet.JANTA to 7,
            VarisaiSet.VAKRA_JANTA to 2,
            VarisaiSet.DHATTU to 6,
        )
        assertEquals(expected, VarisaiSet.entries.associateWith { VarisaiCatalog.inSet(it).size })
        assertEquals(37, VarisaiCatalog.all.size)
    }

    @Test
    fun numbersAreSequentialAndIdsUnique() {
        VarisaiSet.entries.forEach { set ->
            assertEquals((1..VarisaiCatalog.inSet(set).size).toList(), VarisaiCatalog.inSet(set).map { it.number }, "$set")
        }
        assertEquals(VarisaiCatalog.all.size, VarisaiCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyAvartaIsOneAdiCycle() {
        VarisaiCatalog.all.forEach { v ->
            v.avartas.forEach { assertEquals(8, it.aksharas.size, "${v.id}: $it") }
        }
    }

    @Test
    fun rangeIsSaToUpperSa() {
        VarisaiCatalog.all.forEach { v ->
            swaras(v).forEach { s ->
                assertTrue(s.octave == Octave.MADHYA || s == WrittenSwara(SwaraLetter.S, Octave.TARA), "${v.id}: $s")
            }
        }
    }

    @Test
    fun plainScaleIsAscentThenDescent() {
        val plain = VarisaiCatalog.inSet(VarisaiSet.PLAIN_SCALE).single()
        assertEquals(listOf(plainAscent, plainDescent), plain.avartas.map { it.toString() })
        assertEquals("plain-scale-1", plain.id)
        assertEquals("Plain scale", plain.title)
    }

    @Test
    fun everySaraliIsFourAvartasWithThePlainScaleInTheSecondAndFourth() {
        // Workbook, chapter 10: "The second and fourth avartas of every
        // Sarali exercise are the plain ascent and descent."
        VarisaiCatalog.inSet(VarisaiSet.SARALI).forEach { v ->
            assertEquals(4, v.avartas.size, v.id)
            assertEquals(plainAscent, v.avartas[1].toString(), "${v.id} avarta 2")
            assertEquals(plainDescent, v.avartas[3].toString(), "${v.id} avarta 4")
        }
    }

    @Test
    fun jantaPairsEverySwara() {
        // Janta 1-6 write each akshara as a pair of swaras.
        VarisaiCatalog.inSet(VarisaiSet.JANTA).filter { it.number <= 6 }.forEach { v ->
            v.avartas.flatMap { it.aksharas }.forEach { a ->
                val swaras = (a as? Akshara.Swaras)?.swaras
                assertEquals(2, swaras?.size, "${v.id}: $a")
            }
        }
    }

    @Test
    fun spotChecksAgainstTheWorkbook() {
        val s4 = assertNotNull(VarisaiCatalog.byId("sarali-4"))
        assertEquals("S R G M P , S R", s4.avartas[0].toString())
        val s16 = assertNotNull(VarisaiCatalog.byId("sarali-16"))
        assertEquals("P D N S′ M G R S", s16.avartas[2].toString())
        val janta1 = assertNotNull(VarisaiCatalog.byId("janta-1"))
        assertEquals("SS RR GG MM PP DD NN S′S′", janta1.avartas[0].toString())
        val dhattu6 = assertNotNull(VarisaiCatalog.byId("dhattu-6"))
        assertEquals(20, dhattu6.avartas.size)
        assertEquals("M R G R M G R S", dhattu6.avartas.last().toString())
    }

    @Test
    fun noExerciseStartsWithKarvai() {
        VarisaiCatalog.all.forEach { v ->
            assertTrue(v.avartas.first().aksharas.first() != Akshara.Karvai, v.id)
        }
    }

    private fun swaras(v: Varisai): List<WrittenSwara> =
        v.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
}
