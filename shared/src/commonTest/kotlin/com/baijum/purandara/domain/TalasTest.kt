package com.baijum.purandara.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The sapta tala alankaram talas (docs/plan-alankaram-gitam.md). */
class TalasTest {
    private fun strip(tala: Tala) = tala.beats.joinToString(" ") { it.label }

    @Test
    fun theSevenTalasHaveTheirTraditionalShapes() {
        val expected = mapOf(
            "dhruva-4" to listOf(4, 2, 4, 4),
            "matya-4" to listOf(4, 2, 4),
            "rupaka-4" to listOf(2, 4),
            "jhampa-7" to listOf(7, 1, 2),
            "triputa-3" to listOf(3, 2, 2),
            "ata-5" to listOf(5, 5, 2, 2),
            "eka-4" to listOf(4),
        )
        expected.forEach { (key, angas) ->
            val tala = Talas.byKey(key)!!
            assertEquals(angas, tala.angas, key)
            assertEquals(angas.sum(), tala.aksharas, key)
        }
    }

    @Test
    fun beatsAreLabelledAsTheyAreCounted() {
        assertEquals("x 1 2 3 x v x 1 2 3 x 1 2 3", strip(Talas.DHRUVA))
        assertEquals("x v x 1 2 3", strip(Talas.RUPAKA))
        assertEquals("x 1 2 3 4 5 6 x x v", strip(Talas.JHAMPA))
        assertEquals("x 1 2 x v x v", strip(Talas.TRIPUTA_TISRA))
        assertEquals(
            listOf(Kriya.CLAP, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT, Kriya.CLAP, Kriya.CLAP, Kriya.WAVE),
            Talas.JHAMPA.beats.map { it.kriya },
        )
    }

    @Test
    fun adiIsUnchangedAndChaturasraTriputa() {
        assertSame(Tala.ADI, Talas.byKey("adi"))
        assertEquals("x 1 2 3 x v x v", strip(Tala.ADI))
        val triputa = Tala.suladi("t", "Triputa", listOf(AngaKind.LAGHU, AngaKind.DRUTAM, AngaKind.DRUTAM), Jati.CHATURASRA)
        assertEquals(Tala.ADI.beats, triputa.beats)
    }

    @Test
    fun keysAreUniqueAndUnknownKeysAreNull() {
        assertEquals(Talas.all.size, Talas.all.map { it.key }.toSet().size)
        assertNull(Talas.byKey("dhruva"))
    }

    @Test
    fun misraChapuIsClappedAsThreePlusFour() {
        val chapu = Talas.byKey("misra-chapu")!!
        assertEquals("Misra Chapu", chapu.name)
        assertEquals(7, chapu.aksharas)
        assertEquals(listOf(3, 4), chapu.angas)
        assertNull(chapu.jati)
        // A clap starts each chapu anga; the rest are finger counts.
        assertEquals("x 1 2 x 1 2 3", strip(chapu))
        assertEquals(listOf(Kriya.CLAP, Kriya.COUNT, Kriya.COUNT, Kriya.CLAP, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT), chapu.beats.map { it.kriya })
        // Seven aksharas fit one row of notation.
        assertEquals(listOf(0), chapu.rowStarts)
    }

    @Test
    fun longTalasSplitIntoTwoRowsAtAnAngaBoundary() {
        assertEquals(listOf(0), Tala.ADI.rowStarts)
        assertEquals(8, Tala.ADI.rowAksharas)
        assertEquals(listOf(0), Talas.TRIPUTA_TISRA.rowStarts)
        assertEquals(listOf(0, 6), Talas.DHRUVA.rowStarts)
        assertEquals(8, Talas.DHRUVA.rowAksharas)
        assertEquals(listOf(0, 6), Talas.MATYA.rowStarts)
        assertEquals(listOf(0, 7), Talas.JHAMPA.rowStarts)
        assertEquals(listOf(0, 5), Talas.ATA.rowStarts)
        assertEquals(9, Talas.ATA.rowAksharas)
    }

    @Test
    fun severalCellsToABeatWrapByAngaAndBeat() {
        assertEquals(Tala.ADI.rowStarts, Tala.ADI.rowStartsFor(1))
        // Adi two to a beat: laghu | drutam drutam.
        assertEquals(listOf(0, 8), Tala.ADI.rowStartsFor(2))
        assertEquals(8, Tala.ADI.rowCellsFor(2))
        // Four to a beat: the laghu in two rows of two beats, then each drutam.
        assertEquals(listOf(0, 8, 16, 24), Tala.ADI.rowStartsFor(4))
        assertEquals(true, Tala.ADI.startsAngaAt(16, 4))
        assertEquals(false, Tala.ADI.startsAngaAt(8, 4))
        assertEquals(false, Tala.ADI.startsAngaAt(17, 4))
    }
}
