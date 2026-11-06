package com.baijum.purandara.domain

import kotlin.math.pow

/** How a swara relative to Sa becomes a frequency. */
enum class Tuning {
    /** Just-intonation ratios ([Swara.ratio]); matches a tanpura drone. The default. */
    JUST,

    /** 12-tone equal temperament ([Swara.semitone]); matches a keyboard. */
    EQUAL,
    ;

    fun frequency(note: Note, saHz: Double): Double = when (this) {
        JUST -> note.frequency(saHz)
        EQUAL -> {
            require(saHz > 0.0) { "Sa frequency must be positive, was $saHz" }
            saHz * 2.0.pow(note.swara.semitone / SEMITONES + note.octave.offset)
        }
    }

    private companion object {
        const val SEMITONES = 12.0
    }
}
