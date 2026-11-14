@file:OptIn(ExperimentalForeignApi::class)

package com.baijum.purandara.recording

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set

/**
 * Bulk copies between Swift `Data` and Kotlin byte arrays, for the
 * recordings' `.pitch` files (element by element through the bridge is slow).
 */
object ByteBridge {
    /** New array with [count] bytes read from [source]. */
    fun toByteArray(source: CPointer<ByteVar>, count: Int): ByteArray = ByteArray(count) { source[it] }

    /** Writes all of [source] to [destination], which must hold `source.size` bytes. */
    fun write(source: ByteArray, destination: CPointer<ByteVar>) {
        for (i in source.indices) destination[i] = source[i]
    }
}
