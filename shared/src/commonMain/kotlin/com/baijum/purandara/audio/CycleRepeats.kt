package com.baijum.purandara.audio

import com.baijum.purandara.platform.PlatformLock
import com.baijum.purandara.platform.withLock

/**
 * Where "Repeat this cycle" (docs/plan-repeat-cycle.md) has taken a
 * performance back in its own time.
 *
 * The output never stops: frames after the count-in run on as usual, but at
 * each jump the exercise goes back to the start of a tala cycle. Each jump
 * adds to a shift, so a performance frame is the output frame minus the
 * shift in force there. Without jumps the two are the same.
 *
 * Written by the render worker as it decides each jump (always ahead of
 * what is heard), read from the UI and microphone threads to map heard
 * frames back to the exercise.
 */
class CycleRepeats {
    private val lock = PlatformLock()
    private var ats = LongArray(INITIAL_CAPACITY)
    private var shifts = LongArray(INITIAL_CAPACITY)
    private var count = 0

    /** Jumps made so far. */
    val jumps: Int get() = lock.withLock { count }

    /** The total shift so far (frames the performance has been taken back). */
    val totalShift: Long get() = lock.withLock { if (count == 0) 0L else shifts[count - 1] }

    /** The shift in force at [frame] (output frames since the count-in ended). */
    fun shiftAt(frame: Long): Long = lock.withLock {
        var shift = 0L
        for (i in 0 until count) {
            if (ats[i] > frame) break
            shift = shifts[i]
        }
        shift
    }

    /** The performance frame heard at [frame] (output frames since the count-in ended). */
    fun performanceFrame(frame: Long): Long = frame - shiftAt(frame)

    /** Jumps made at output frames in [from, to]: how many times a repeated cycle has come round. */
    fun jumpsBetween(from: Long, to: Long): Int = lock.withLock {
        var n = 0
        for (i in 0 until count) if (ats[i] in from..to) n++
        n
    }

    /** At output frame [at] the performance goes back, so the total shift becomes [shift]. */
    internal fun add(at: Long, shift: Long) = lock.withLock {
        require(count == 0 || at > ats[count - 1]) { "jumps must arrive in order" }
        if (count == ats.size) {
            ats = ats.copyOf(count * 2)
            shifts = shifts.copyOf(count * 2)
        }
        ats[count] = at
        shifts[count] = shift
        count++
    }

    internal fun clear() = lock.withLock { count = 0 }

    private companion object {
        const val INITIAL_CAPACITY = 8
    }
}
