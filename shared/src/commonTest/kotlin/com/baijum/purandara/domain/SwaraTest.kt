package com.baijum.purandara.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SwaraTest {
    private val sa = 138.5913 // C#3

    @Test
    fun sixteenNamesOverTwelvePositions() {
        assertEquals(16, Swara.entries.size)
        assertEquals((0..11).toSet(), Swara.entries.map { it.semitone }.toSet())
    }

    @Test
    fun sharedPositionsHaveIdenticalPitch() {
        listOf(
            Swara.R2 to Swara.G1,
            Swara.R3 to Swara.G2,
            Swara.D2 to Swara.N1,
            Swara.D3 to Swara.N2,
        ).forEach { (a, b) ->
            assertEquals(a.semitone, b.semitone, "$a/$b semitone")
            assertEquals(a.ratio, b.ratio, 0.0, "$a/$b ratio")
        }
    }

    @Test
    fun ratiosAreWithinOneOctaveAndAscendWithPosition() {
        val byPosition = Swara.entries.sortedBy { it.semitone }
        byPosition.zipWithNext().forEach { (lower, higher) ->
            assertTrue(lower.ratio <= higher.ratio, "$lower should not be above $higher")
        }
        Swara.entries.forEach { assertTrue(it.ratio >= 1.0 && it.ratio < 2.0, "$it ratio ${it.ratio}") }
    }

    @Test
    fun justRatiosStayCloseToEqualTemperament() {
        // Just intonation differs from 12-TET by at most ~20 cents for these ratios.
        Swara.entries.forEach {
            val etRatio = Pitch.midiToHz(60 + it.semitone) / Pitch.midiToHz(60)
            val cents = Pitch.cents(etRatio, it.ratio)
            assertTrue(abs(cents) < 20.0, "$it is $cents cents from equal temperament")
        }
    }

    @Test
    fun mayamalavagowlaFrequencies() {
        // The raga of Sarali Varisai: S R1 G3 M1 P D1 N3.
        assertEquals(sa, Note(Swara.S).frequency(sa), 1e-9)
        assertEquals(sa * 16 / 15, Note(Swara.R1).frequency(sa), 1e-9)
        assertEquals(sa * 5 / 4, Note(Swara.G3).frequency(sa), 1e-9)
        assertEquals(sa * 4 / 3, Note(Swara.M1).frequency(sa), 1e-9)
        assertEquals(sa * 3 / 2, Note(Swara.P).frequency(sa), 1e-9)
        assertEquals(sa * 8 / 5, Note(Swara.D1).frequency(sa), 1e-9)
        assertEquals(sa * 15 / 8, Note(Swara.N3).frequency(sa), 1e-9)
    }

    @Test
    fun octavesHalveAndDouble() {
        val madhya = Note(Swara.P).frequency(sa)
        assertEquals(madhya / 2, Note(Swara.P, Octave.MANDRA).frequency(sa), 1e-9)
        assertEquals(madhya * 2, Note(Swara.P, Octave.TARA).frequency(sa), 1e-9)
        assertEquals(sa * 2, Note(Swara.S, Octave.TARA).frequency(sa), 1e-9)
    }

    @Test
    fun rejectsNonPositiveSa() {
        assertFailsWith<IllegalArgumentException> { Note(Swara.S).frequency(0.0) }
    }
}
