package com.baijum.purandara.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FillHopTest {
    /** Returns the scripted counts in turn, filling each read with its call number. */
    private class ScriptedReads(private val hop: FloatArray, vararg counts: Int) {
        private val counts = ArrayDeque(counts.toList())
        var calls = 0
        fun read(offset: Int, count: Int): Int {
            calls++
            val n = minOf(counts.removeFirstOrNull() ?: count, count)
            for (i in offset until offset + maxOf(n, 0)) hop[i] = calls.toFloat()
            return n
        }
    }

    @Test
    fun anEmptyReadIsRetriedAndTheHopStillFills() {
        val hop = FloatArray(8)
        val reads = ScriptedReads(hop, 4, 0, 4)
        var pauses = 0
        assertTrue(fillHop(hop, { true }, reads::read, maxEmptyReads = 3) { pauses++ })
        assertEquals(1, pauses)
        assertEquals(listOf(1f, 1f, 1f, 1f, 3f, 3f, 3f, 3f), hop.toList())
    }

    @Test
    fun emptyReadsInARowOnlyCountUntilDataArrives() {
        val hop = FloatArray(4)
        val reads = ScriptedReads(hop, 0, 0, 1, 0, 0, 3)
        assertTrue(fillHop(hop, { true }, reads::read, maxEmptyReads = 2) {})
    }

    @Test
    fun persistentEmptyReadsFailTheCapture() {
        val hop = FloatArray(4)
        val reads = ScriptedReads(hop, 0, 0, 0, 0)
        assertThrows(IllegalStateException::class.java) {
            fillHop(hop, { true }, reads::read, maxEmptyReads = 3) {}
        }
        assertEquals(4, reads.calls)
    }

    @Test
    fun anErrorCodeFailsAtOnce() {
        val hop = FloatArray(4)
        assertThrows(IllegalStateException::class.java) {
            fillHop(hop, { true }, ScriptedReads(hop, -3)::read) {}
        }
    }

    @Test
    fun stoppingEndsWithoutFailingEvenOnAnErrorRead() {
        val hop = FloatArray(4)
        var running = true
        // stop() unblocks a blocking read, which then returns an error code.
        val read = { _: Int, _: Int -> running = false; -3 }
        assertFalse(fillHop(hop, { running }, read) {})
    }
}
