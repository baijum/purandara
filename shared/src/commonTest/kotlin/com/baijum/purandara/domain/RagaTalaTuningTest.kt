package com.baijum.purandara.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RagaTalaTuningTest {
    @Test
    fun mayamalavagowlaScale() {
        val raga = Raga.MAYAMALAVAGOWLA
        assertEquals(
            listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3),
            SwaraLetter.entries.map(raga::swara),
        )
    }

    @Test
    fun ragaMustDefineEveryLetterWithFixedSaAndPa() {
        assertFailsWith<IllegalArgumentException> { Raga("Partial", mapOf(SwaraLetter.S to Swara.S)) }
        val withWrongPa = SwaraLetter.entries.associateWith { Swara.S } + (SwaraLetter.P to Swara.M2)
        assertFailsWith<IllegalArgumentException> { Raga("Bad", withWrongPa) }
    }

    @Test
    fun adiTala() {
        val adi = Tala.ADI
        assertEquals(8, adi.aksharas)
        assertEquals(listOf(4, 2, 2), adi.angas)
        assertEquals("x 1 2 3 x v x v", adi.beats.joinToString(" ") { it.label })
        assertEquals(
            listOf(Kriya.CLAP, Kriya.COUNT, Kriya.COUNT, Kriya.COUNT, Kriya.CLAP, Kriya.WAVE, Kriya.CLAP, Kriya.WAVE),
            adi.beats.map { it.kriya },
        )
    }

    @Test
    fun equalTemperamentMatchesKeyboardPitches() {
        val sa = Pitch.midiToHz(48) // C3, as in the workbook's reference table
        val expected = mapOf(
            Swara.S to 130.81, Swara.R1 to 138.59, Swara.G3 to 164.81, Swara.M1 to 174.61,
            Swara.P to 196.00, Swara.D1 to 207.65, Swara.N3 to 246.94,
        )
        expected.forEach { (swara, hz) -> assertEquals(hz, Tuning.EQUAL.frequency(Note(swara), sa), 0.01, "$swara") }
        assertEquals(261.63, Tuning.EQUAL.frequency(Note(Swara.S, Octave.TARA), sa), 0.01)
    }

    @Test
    fun justAndEqualAgreeOnSaAndDifferOnGa() {
        val sa = 138.59
        assertEquals(Tuning.JUST.frequency(Note(Swara.S), sa), Tuning.EQUAL.frequency(Note(Swara.S), sa), 1e-9)
        val cents = Pitch.cents(Tuning.EQUAL.frequency(Note(Swara.G3), sa), Tuning.JUST.frequency(Note(Swara.G3), sa))
        assertEquals(-13.69, cents, 0.01) // pure major third is ~14 cents flat of the keyboard
    }
}
