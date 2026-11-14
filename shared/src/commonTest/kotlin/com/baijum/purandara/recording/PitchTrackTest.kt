package com.baijum.purandara.recording

import com.baijum.purandara.singalong.PitchFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchTrackTest {
    private fun sample(): PitchTrack {
        val b = PitchTrack.Builder(0.0116, 0.023)
        repeat(3000) { i -> b.add(PitchFrame(0.0, if (i % 7 == 0) null else 100.0 + i, i / 3000f)) }
        return b.build()
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val track = sample()
        val back = PitchTrack.decode(track.encode())
        assertEquals(track, back)
        assertNull(back!!.frequencyAt(0))
        assertEquals(101.0, back.frequencyAt(1)!!, 1e-3)
        assertEquals(0.023 + 3000 * 0.0116, back.durationSeconds, 1e-9)
    }

    @Test
    fun corruptBytesAreRejected() {
        val bytes = sample().encode()
        assertNull(PitchTrack.decode(ByteArray(0)))
        assertNull(PitchTrack.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(PitchTrack.decode(bytes.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(PitchTrack.decode(bytes.copyOf().also { it[4] = 9 }))
    }

    @Test
    fun indexAtIsClamped() {
        val track = sample()
        assertEquals(0, track.indexAt(-1.0))
        assertEquals(track.size - 1, track.indexAt(1e6))
        assertEquals(10, track.indexAt(track.timeAt(10) + 0.001))
    }

    @Test
    fun emptyTrack() {
        val empty = PitchTrack.Builder(0.01, 0.0).build()
        assertEquals(0.0, empty.durationSeconds)
        assertEquals(empty, PitchTrack.decode(empty.encode()))
    }
}
