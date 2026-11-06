@file:OptIn(ExperimentalForeignApi::class)

package com.baijum.purandara.audio

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.set

/**
 * Bulk copies between Swift audio buffers and Kotlin arrays. Doing this
 * element by element through the Swift/Kotlin bridge is far slower.
 */
object FloatBridge {
    /** New array with [count] floats read from [source] (e.g. `AVAudioPCMBuffer.floatChannelData[0]`). */
    fun toFloatArray(source: CPointer<FloatVar>, count: Int): FloatArray = FloatArray(count) { source[it] }

    /** Writes the first [count] values of [source] to [destination]. */
    fun write(source: FloatArray, destination: CPointer<FloatVar>, count: Int) {
        require(count in 0..source.size) { "count $count outside 0..${source.size}" }
        for (i in 0 until count) destination[i] = source[i]
    }
}
