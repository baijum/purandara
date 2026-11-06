package com.baijum.purandara.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarmoniumBankTest {
    private val halfSemitone = 2.0.pow(0.5 / 12)

    @Test
    fun coversC2ToD5() {
        assertEquals(36..74, HarmoniumBank.midiRange)
        assertEquals(39, HarmoniumBank.allAssetPaths().size)
        assertEquals("harmonium/36.flac", HarmoniumBank.allAssetPaths().first())
        assertEquals("harmonium/74.flac", HarmoniumBank.allAssetPaths().last())
    }

    @Test
    fun assetPathRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { HarmoniumBank.assetPath(35) }
        assertFailsWith<IllegalArgumentException> { HarmoniumBank.assetPath(75) }
    }

    @Test
    fun exactSemitoneUsesThatSampleAtNormalSpeed() {
        val p = assertNotNull(HarmoniumBank.playbackFor(Pitch.midiToHz(49)))
        assertEquals(49, p.midi)
        assertEquals("harmonium/49.flac", p.assetPath)
        assertEquals(1.0, p.rate, 1e-12)
    }

    @Test
    fun justIntonationNudgesNearestSample() {
        // Ga (G3 = 5/4) over Sa C#3 is ~14 cents below equal-tempered F3 (midi 53).
        val sa = Pitch.midiToHz(49)
        val p = assertNotNull(HarmoniumBank.playbackFor(Note(Swara.G3), sa))
        assertEquals(53, p.midi)
        assertEquals(-13.69, Pitch.cents(1.0, p.rate), 0.01)
        assertEquals(sa * 5 / 4, p.frequencyHz, 1e-9)
    }

    @Test
    fun outOfRangeFrequenciesReturnNull() {
        assertNull(HarmoniumBank.playbackFor(Pitch.midiToHz(35.0)))
        assertNull(HarmoniumBank.playbackFor(Pitch.midiToHz(74.6)))
        assertNull(HarmoniumBank.playbackFor(0.0))
        assertNull(HarmoniumBank.playbackFor(-10.0))
        assertNull(HarmoniumBank.playbackFor(Double.NaN))
        assertNull(HarmoniumBank.playbackFor(Double.POSITIVE_INFINITY))
        // Edges: up to half a semitone beyond the ends is still playable.
        assertEquals(36, HarmoniumBank.playbackFor(Pitch.midiToHz(35.6))?.midi)
        assertEquals(74, HarmoniumBank.playbackFor(Pitch.midiToHz(74.4))?.midi)
    }

    @Test
    fun rateAlwaysWithinHalfSemitoneAndReproducesFrequency() = runTest {
        checkAll(Arb.double(HarmoniumBank.minHz..HarmoniumBank.maxHz, includeNaNs = false)) { hz ->
            val p = assertNotNull(HarmoniumBank.playbackFor(hz), "no playback for $hz Hz")
            assertTrue(p.rate in (1 / halfSemitone - 1e-9)..(halfSemitone + 1e-9), "rate ${p.rate} for $hz Hz")
            assertTrue(abs(Pitch.midiToHz(p.midi) * p.rate - hz) < 1e-9 * hz)
        }
    }

    @Test
    fun everySwaraPlayableForTypicalShrutis() = runTest {
        // Sa anywhere from B2 (low male) to C4 (high female): every
        // madhya-sthayi swara must be playable from the bank.
        checkAll(Arb.int(47..60), Arb.element(Swara.entries)) { saMidi, swara ->
            val sa = Pitch.midiToHz(saMidi)
            val note = Note(swara, Octave.MADHYA)
            assertNotNull(HarmoniumBank.playbackFor(note, sa), "$swara with Sa midi $saMidi")
        }
    }
}
