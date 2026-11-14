package com.baijum.purandara.recording

import com.baijum.purandara.domain.Swara
import com.baijum.purandara.singalong.PitchFrame
import kotlin.math.log2
import kotlin.math.pow

/** Synthetic pitch tracks for the transcriber tests, at the analyser's hop. */
object RecordingTestTracks {
    const val SA_HZ = 130.81
    const val HOP = 512.0 / 44_100
    const val VOICE_RMS = 0.1f
    const val ROOM_RMS = 0.002f

    /** Cents from Sa of [swara] [octave] octaves from madhya, just intonation. */
    fun cents(swara: Swara, octave: Int = 0): Double = 1200.0 * (log2(swara.ratio) + octave)

    /** A track lasting [seconds] whose pitch is [centsAt] (cents from Sa, null = silence). */
    fun track(seconds: Double, centsAt: (Double) -> Double?): PitchTrack {
        val builder = PitchTrack.Builder(HOP, HOP / 2)
        val frames = (seconds / HOP).toInt()
        for (i in 0 until frames) {
            val t = HOP / 2 + i * HOP
            val c = centsAt(t)
            builder.add(PitchFrame(t, c?.let { SA_HZ * 2.0.pow(it / 1200.0) }, if (c != null) VOICE_RMS else ROOM_RMS))
        }
        return builder.build()
    }

    /** Steady segments: (cents or null for silence, seconds). */
    fun steps(vararg segments: Pair<Double?, Double>): PitchTrack {
        val ends = segments.runningFold(0.0) { acc, s -> acc + s.second }.drop(1)
        return track(ends.last()) { t -> segments[ends.indexOfFirst { t < it }.coerceAtLeast(0)].first }
    }
}
