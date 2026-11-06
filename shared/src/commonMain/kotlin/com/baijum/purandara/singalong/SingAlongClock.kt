package com.baijum.purandara.singalong

import kotlin.math.floor

/** Where in the exercise a sung moment belongs. */
data class SungPosition(val pass: Int, val secondsInPass: Double)

/**
 * Maps microphone capture time to the moment of the exercise the singer was
 * hearing.
 *
 * A rendered frame reaches the ear [outputLatencySeconds] after it is
 * rendered; what the singer sings then reaches the capture buffer
 * [inputLatencySeconds] later. With the mic clock and the playback clock
 * both starting at 0 when playback starts:
 *
 *     heard = capture − inputLatency − outputLatency
 *
 * @param countInSeconds length of the count-in before pass 0.
 * @param passSeconds length of one pass of the exercise.
 * @param loop whether passes repeat; otherwise only pass 0 exists.
 */
class SingAlongClock(
    private val countInSeconds: Double,
    private val passSeconds: Double,
    private val loop: Boolean,
    private val outputLatencySeconds: Double,
    private val inputLatencySeconds: Double,
) {
    init {
        require(passSeconds > 0.0) { "pass length must be positive" }
        require(countInSeconds >= 0.0 && outputLatencySeconds >= 0.0 && inputLatencySeconds >= 0.0) { "negative time" }
    }

    /** Playback-timeline time the singer was responding to at [captureSeconds]. */
    fun heardSeconds(captureSeconds: Double): Double = captureSeconds - inputLatencySeconds - outputLatencySeconds

    /** True while the singer is hearing the count-in. */
    fun inCountIn(captureSeconds: Double): Boolean = heardSeconds(captureSeconds) in 0.0..<countInSeconds

    /** The pass and time within it, or null during the count-in, before playback or after the end. */
    fun locate(captureSeconds: Double): SungPosition? {
        val sinceStart = heardSeconds(captureSeconds) - countInSeconds
        if (sinceStart < 0.0) return null
        val pass = floor(sinceStart / passSeconds).toInt()
        if (!loop && pass > 0) return null
        return SungPosition(pass, sinceStart - pass * passSeconds)
    }
}
