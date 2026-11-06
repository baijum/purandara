package com.baijum.purandara.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PitchTest {
    @Test
    fun referencePitches() {
        assertEquals(440.0, Pitch.midiToHz(69), 1e-9)
        assertEquals(261.6256, Pitch.midiToHz(60), 1e-4) // C4
        assertEquals(65.4064, Pitch.midiToHz(36), 1e-4) // C2
        assertEquals(523.2511, Pitch.midiToHz(72), 1e-4) // C5
    }

    @Test
    fun octaveIsTwelveSemitonesAndTwelveHundredCents() {
        assertEquals(2.0, Pitch.midiToHz(61) / Pitch.midiToHz(49), 1e-12)
        assertEquals(1200.0, Pitch.cents(220.0, 440.0), 1e-9)
        assertEquals(-100.0, Pitch.cents(Pitch.midiToHz(50), Pitch.midiToHz(49)), 1e-9)
    }

    @Test
    fun nearestMidiRoundsToClosestSemitone() {
        assertEquals(69, Pitch.nearestMidi(440.0))
        assertEquals(69, Pitch.nearestMidi(Pitch.midiToHz(69.49)))
        assertEquals(70, Pitch.nearestMidi(Pitch.midiToHz(69.51)))
    }

    @Test
    fun rejectsNonPositiveFrequency() {
        assertFailsWith<IllegalArgumentException> { Pitch.hzToMidi(0.0) }
        assertFailsWith<IllegalArgumentException> { Pitch.hzToMidi(-1.0) }
    }

    @Test
    fun midiAndHzRoundTrip() = runTest {
        checkAll(Arb.double(0.0..127.0, includeNaNs = false)) { midi ->
            val back = Pitch.hzToMidi(Pitch.midiToHz(midi))
            assertTrue(abs(back - midi) < 1e-9, "midi $midi round-tripped to $back")
        }
    }
}
