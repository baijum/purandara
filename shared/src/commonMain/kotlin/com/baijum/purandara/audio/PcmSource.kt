package com.baijum.purandara.audio

/** Mono PCM rendered ahead on one platform worker, never on the real-time audio callback. */
interface PcmSource {
    val sampleRate: Int
    val position: Long

    fun render(out: FloatArray, offset: Int = 0, length: Int = out.size - offset): Int
    fun reset()
    fun fadeOut(seconds: Double = 0.08)
}
