package com.baijum.purandara.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeuralPitchSupervisorParseTest {
    @Test
    fun takesTheMiddleFrame() {
        val r = NeuralPitchSupervisor.parseMiddleEstimate(floatArrayOf(100f, 196f, 300f), floatArrayOf(0.1f, 0.9f, 0.2f))!!
        assertEquals(196.0, r.frequencyHz, 1e-6)
        assertEquals(0.9, r.confidence, 1e-6)
    }

    @Test
    fun rejectsLowConfidenceOutOfRangeAndEmpty() {
        assertNull(NeuralPitchSupervisor.parseMiddleEstimate(floatArrayOf(196f), floatArrayOf(0.3f)))
        assertNull(NeuralPitchSupervisor.parseMiddleEstimate(floatArrayOf(30f), floatArrayOf(0.99f)))
        assertNull(NeuralPitchSupervisor.parseMiddleEstimate(floatArrayOf(), null))
        assertNull(NeuralPitchSupervisor.parseMiddleEstimate(floatArrayOf(196f), null))
    }

    @Test
    fun flattensNestedOutputs() {
        val nested = arrayOf(floatArrayOf(1f, 2f), floatArrayOf(3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), NeuralPitchSupervisor.extractFloatSeries(nested), 0f)
        assertArrayEquals(floatArrayOf(4f, 5f), NeuralPitchSupervisor.extractFloatSeries(arrayOf(4.0, 5.0)), 0f)
        assertNull(NeuralPitchSupervisor.extractFloatSeries("nope"))
    }
}
