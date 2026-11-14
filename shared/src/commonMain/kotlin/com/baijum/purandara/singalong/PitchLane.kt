package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.recording.Transcription
import kotlin.math.log2

/**
 * The swara lines and vertical range of the sing-along pitch lane, from the
 * notes the piece actually uses: always Sa to upper Sa (the varisais), and
 * down to lower Dha or up to upper Ri when a gitam goes there. Lines are the
 * raga's own swaras only. Shared so both apps draw the same lane.
 */
class PitchLane(swaras: List<Swara>, usedCents: List<Double>) {
    /** The lane for a piece: its raga's swaras, over the notes it uses. */
    constructor(schedule: Schedule) : this(schedule.raga.swaras, schedule.notes.map { centsOf(it.note) })

    /** Lowest and highest cents from Sa shown, with a margin beyond the outer notes. */
    val lowCents: Double
    val highCents: Double

    /** The raga's swaras within the range, lowest first. */
    val lines: List<Note>

    init {
        val lowest = minOf(0.0, usedCents.minOrNull() ?: 0.0)
        val highest = maxOf(CENTS_PER_OCTAVE, usedCents.maxOrNull() ?: CENTS_PER_OCTAVE)
        lowCents = lowest - MARGIN_CENTS
        highCents = highest + MARGIN_CENTS
        lines = Octave.entries.flatMap { octave -> swaras.map { Note(it, octave) } }
            .filter { centsOf(it) in lowest..highest }
            .sortedBy { centsOf(it) }
    }

    /** Fraction of the lane's height from the bottom (0) to the top (1). */
    fun fraction(cents: Double): Double = (cents - lowCents) / (highCents - lowCents)

    companion object {
        private const val CENTS_PER_OCTAVE = 1200.0

        /** A little room above and below the outer lines. */
        const val MARGIN_CENTS: Double = 150.0

        /**
         * The lane for a recording (docs/plan-recordings.md): the raga's
         * swaras, or with no raga the swaras heard plus Sa and Pa, over the
         * notes heard.
         */
        fun forRecording(transcription: Transcription, raga: Raga?): PitchLane {
            val swaras = raga?.swaras
                ?: (transcription.notes.map { it.swara } + Swara.S + Swara.P).distinct().sortedBy { it.semitone }
            val used = transcription.notes.map { CENTS_PER_OCTAVE * (log2(it.swara.ratio) + it.octave) }
            return PitchLane(swaras, used)
        }

        /** Just-intonation cents of [note] from Sa (the lane's vertical position). */
        fun centsOf(note: Note): Double = CENTS_PER_OCTAVE * (log2(note.swara.ratio) + note.octave.offset)

        /** A line's label: the letter, with `′` above Sa′'s octave and a dot below for mandra ("Ḍ"). */
        fun label(note: Note): String = note.swara.symbol.take(1) + when (note.octave) {
            Octave.TARA -> "′"
            Octave.MANDRA -> "̣"
            Octave.MADHYA -> ""
        }
    }
}
