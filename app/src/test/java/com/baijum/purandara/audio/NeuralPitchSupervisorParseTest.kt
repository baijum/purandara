package com.baijum.purandara.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeuralPitchSupervisorParseTest {
    @Test
    fun readsFloat64PitchAndFloat32Confidence() {
        val pitch = arrayOf(doubleArrayOf(146.83, 220.0))
        val confidence = arrayOf(floatArrayOf(0.9f, 0.4f))
        assertArrayEquals(floatArrayOf(146.83f, 220f), NeuralPitchSupervisor.floats(pitch), 1e-4f)
        assertArrayEquals(floatArrayOf(0.9f, 0.4f), NeuralPitchSupervisor.floats(confidence), 0f)
    }

    @Test
    fun acceptsFlatArrays() {
        assertArrayEquals(floatArrayOf(1f, 2f), NeuralPitchSupervisor.floats(floatArrayOf(1f, 2f)), 0f)
        assertArrayEquals(floatArrayOf(3f), NeuralPitchSupervisor.floats(doubleArrayOf(3.0)), 0f)
    }

    @Test
    fun rejectsUnexpectedShapes() {
        assertNull(NeuralPitchSupervisor.floats(null))
        assertNull(NeuralPitchSupervisor.floats("nope"))
        assertNull(NeuralPitchSupervisor.floats(arrayOf(floatArrayOf(1f), floatArrayOf(2f))))
        assertNull(NeuralPitchSupervisor.floats(arrayOf(intArrayOf(1))))
    }
}
