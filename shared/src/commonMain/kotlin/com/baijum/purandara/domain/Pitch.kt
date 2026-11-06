package com.baijum.purandara.domain

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Equal-tempered pitch helpers. MIDI note numbers: C4 = 60, A4 = 69. */
object Pitch {
    /** Concert pitch the bundled samples are tuned to. */
    const val A4_HZ: Double = 440.0
    const val A4_MIDI: Int = 69
    private const val SEMITONES_PER_OCTAVE = 12
    private const val CENTS_PER_OCTAVE = 1200.0

    /** Frequency in Hz of a (possibly fractional) MIDI note. */
    fun midiToHz(midi: Double): Double =
        A4_HZ * 2.0.pow((midi - A4_MIDI) / SEMITONES_PER_OCTAVE)

    fun midiToHz(midi: Int): Double = midiToHz(midi.toDouble())

    /** Fractional MIDI note for a frequency. */
    fun hzToMidi(hz: Double): Double {
        require(hz > 0.0) { "frequency must be positive, was $hz" }
        return A4_MIDI + SEMITONES_PER_OCTAVE * log2(hz / A4_HZ)
    }

    /** Nearest equal-tempered MIDI note for a frequency. */
    fun nearestMidi(hz: Double): Int = hzToMidi(hz).roundToInt()

    /** Interval from [fromHz] to [toHz] in cents (positive when [toHz] is higher). */
    fun cents(fromHz: Double, toHz: Double): Double = CENTS_PER_OCTAVE * log2(toHz / fromHz)
}
