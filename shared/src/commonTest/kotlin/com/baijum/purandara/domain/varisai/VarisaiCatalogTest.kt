package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala
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
            VarisaiSet.ALANKARAM to 7,
            VarisaiSet.GITAM to 9,
            VarisaiSet.SWARAJATI to 2,
            VarisaiSet.VARNAM to 1,
        )
        assertEquals(expected, VarisaiSet.entries.associateWith { VarisaiCatalog.inSet(it).size })
        assertEquals(56, VarisaiCatalog.all.size)
    }

    @Test
    fun numbersAreSequentialAndIdsUnique() {
        VarisaiSet.entries.forEach { set ->
            assertEquals((1..VarisaiCatalog.inSet(set).size).toList(), VarisaiCatalog.inSet(set).map { it.number }, "$set")
        }
        assertEquals(VarisaiCatalog.all.size, VarisaiCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyAvartaIsOneCycleOfItsTala() {
        VarisaiCatalog.all.forEach { v ->
            v.avartas.forEachIndexed { i, avarta ->
                val gati = v.cellsPerBeatIn(i)
                assertTrue(gati in listOf(1, 2, 4, 5), "${v.id}: gati $gati")
                assertEquals(v.tala.aksharas * gati, avarta.aksharas.size, "${v.id}: $avarta")
            }
        }
        // The varisais are all in Adi tala.
        VarisaiCatalog.all.filter { it.set !in setOf(VarisaiSet.ALANKARAM, VarisaiSet.GITAM, VarisaiSet.SWARAJATI) }
            .forEach { assertEquals(Tala.ADI, it.tala, it.id) }
    }

    @Test
    fun alankaramsAreTheSevenSaptaTalasClimbingAndDescending() {
        val alankarams = VarisaiCatalog.inSet(VarisaiSet.ALANKARAM)
        assertEquals(
            listOf("dhruva-4", "matya-4", "rupaka-4", "jhampa-7", "triputa-3", "ata-5", "eka-4"),
            alankarams.map { it.tala.key },
        )
        assertEquals("Alankaram 1 · Dhruva tala", alankarams.first().title)
        alankarams.forEach { v ->
            assertEquals(Raga.MAYAMALAVAGOWLA, v.raga)
            assertEquals(10, v.avartas.size, v.id)
            // Each avarta starts one step higher for five avartas, then one lower from upper Sa.
            val starts = v.avartas.map { (it.aksharas.first() as Akshara.Swaras).swaras.first().toString() }
            assertEquals(listOf("S", "R", "G", "M", "P", "S′", "N", "D", "P", "M"), starts, v.id)
            assertEquals("S", v.avartas.last().aksharas.last { it is Akshara.Swaras }.toString(), v.id)
        }
    }

    @Test
    fun alankaramGoldens() {
        fun a(n: Int) = VarisaiCatalog.byId("alankaram-$n")!!.avartas
        assertEquals("S R G M G R S R G R S R G M", a(1).first().toString())
        assertEquals("M G R G M G M G R S", a(2).last().toString())
        assertEquals("S R S R G M", a(3).first().toString())
        assertEquals("S R G S R S R G M ,", a(4).first().toString())
        assertEquals("M G R M G R S", a(5).last().toString())
        assertEquals("S R , G , S , R G , M , M ,", a(6).first().toString())
        assertEquals("M G R S", a(7).last().toString())
    }

    @Test
    fun rangeIsSaToUpperSa() {
        VarisaiCatalog.all.filter { it.set !in setOf(VarisaiSet.GITAM, VarisaiSet.SWARAJATI) }.forEach { v ->
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

class SpokenAvartaTest {
    @Test
    fun angasAreSeparatedAndKarvaiIsSpoken() {
        val sarali4 = VarisaiCatalog.byId("sarali-4")!!
        assertEquals("S R G M, P hold, S R", sarali4.avartas[0].spoken(com.baijum.purandara.domain.Tala.ADI, "hold"))
        assertEquals("S R G M, P D, N S′", sarali4.avartas[1].spoken(com.baijum.purandara.domain.Tala.ADI, "hold"))
    }

    @Test
    fun pairsStayTogether() {
        val janta1 = VarisaiCatalog.byId("janta-1")!!
        assertEquals("SS RR GG MM, PP DD, NN S′S′", janta1.avartas[0].spoken(com.baijum.purandara.domain.Tala.ADI, "hold"))
    }
}
