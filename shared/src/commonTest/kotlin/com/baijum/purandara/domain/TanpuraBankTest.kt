package com.baijum.purandara.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TanpuraBankTest {
    @Test
    fun everySaInTheRangeHasALoopWithinThreeSemitones() {
        for (midi in Shruti.SA_RANGE) {
            val p = assertNotNull(TanpuraBank.playbackFor(Pitch.midiToHz(midi)), "Sa $midi")
            assertTrue(abs(TanpuraBank.shiftSemitones(p)) <= TanpuraBank.MAX_SHIFT_SEMITONES + 1e-9, "Sa $midi shift ${TanpuraBank.shiftSemitones(p)}")
            assertEquals(Pitch.midiToHz(midi), Pitch.midiToHz(p.saMidi) * p.rate, 1e-9)
        }
    }

    @Test
    fun picksTheNearestLoop() {
        assertEquals(58, TanpuraBank.playbackFor(Pitch.midiToHz(60))!!.saMidi) // C4 from A#3, +2
        assertEquals(46, TanpuraBank.playbackFor(Pitch.midiToHz(43))!!.saMidi) // G2 from A#2, -3
        assertEquals(50, TanpuraBank.playbackFor(Pitch.midiToHz(49))!!.saMidi) // C#3 from D3, -1
        assertEquals(1.0, TanpuraBank.playbackFor(Pitch.midiToHz(54))!!.rate, 1e-12)
    }

    @Test
    fun rejectsSaFarOutsideTheLoops() {
        assertNull(TanpuraBank.playbackFor(Pitch.midiToHz(40)))
        assertNull(TanpuraBank.playbackFor(Pitch.midiToHz(64)))
        assertNull(TanpuraBank.playbackFor(Double.NaN))
        assertEquals(listOf("tanpura/46.flac", "tanpura/50.flac", "tanpura/54.flac", "tanpura/58.flac"), TanpuraBank.allAssetPaths())
    }
}
