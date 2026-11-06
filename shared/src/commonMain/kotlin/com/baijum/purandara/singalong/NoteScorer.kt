package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.NoteEvent
import com.baijum.purandara.domain.schedule.Schedule
import kotlin.math.abs
import kotlin.math.roundToInt

/** A frame placed in the exercise: time within the pass, pitch, and whether it was the student's voice. */
data class ScoredFrame(val secondsInPass: Double, val frequencyHz: Double?, val voice: Boolean)

/** Outcome for one note of the exercise. */
sealed interface NoteResult {
    /**
     * Judged on its settled middle.
     *
     * @property cents from the target after removing any whole-octave shift.
     * @property octaveShift 0 normally; -1/+1 when sung an octave low/high.
     * @property sung what was actually sung, when it was a different swara.
     */
    data class Judged(val cents: Double, val octaveShift: Int, val sung: SwaraReading?) : NoteResult {
        val zone: Zone get() = if (sung != null) Zone.OFF else Zone.of(cents)
        val high: Boolean get() = cents > 0
    }

    /** Not enough of the student's voice heard during the note. */
    data object NoVoice : NoteResult

    /** Too short to judge at this speed; the trace still shows it. */
    data object TooShort : NoteResult
}

/** The result for one [NoteEvent] of a pass. */
data class NoteMark(val event: NoteEvent, val result: NoteResult)

/** Which way a swara tends to go wrong. */
enum class Tendency { HIGH, LOW, OCTAVE_LOW, OCTAVE_HIGH, WRONG_SWARA }

/** "Ri₁ high in 3 of 4": a swara that needs attention. */
data class Attention(val note: Note, val tendency: Tendency, val times: Int, val of: Int)

/** Summary of one pass. */
data class PassSummary(
    val judged: Int,
    val inTune: Int,
    val close: Int,
    val noVoice: Int,
    val attention: List<Attention>,
) {
    val inTuneOrClose: Int get() = inTune + close
}

data class PassResult(val pass: Int, val marks: List<NoteMark>, val summary: PassSummary)

/**
 * Scores a pass of sung frames against the schedule
 * (docs/plan-pitch-monitor.md, section 4.4).
 */
class NoteScorer(private val schedule: Schedule, private val mapper: SwaraMapper) {

    fun score(pass: Int, frames: List<ScoredFrame>): PassResult {
        val sorted = frames.sortedBy { it.secondsInPass }
        val marks = schedule.notes.map { event -> NoteMark(event, judge(event, sorted)) }
        return PassResult(pass, marks, summarise(marks))
    }

    private fun judge(event: NoteEvent, frames: List<ScoredFrame>): NoteResult {
        if (event.durationSeconds < MIN_JUDGED_SECONDS) return NoteResult.TooShort
        val skip = maxOf(SKIP_MIN_SECONDS, event.durationSeconds * SKIP_FRACTION)
        val from = event.startSeconds + skip
        val to = event.startSeconds + event.durationSeconds
        val window = frames.filter { it.secondsInPass >= from && it.secondsInPass < to }
        val voiced = window.filter { it.voice && it.frequencyHz != null }
        if (window.isEmpty() || voiced.size < window.size * MIN_VOICED_FRACTION) return NoteResult.NoVoice

        val sungHz = median(voiced.map { it.frequencyHz!! })
        val target = event.frequencyHz
        val cents = Pitch.cents(target, sungHz)
        val octaveShift = (cents / CENTS_PER_OCTAVE).roundToInt()
        val residual = cents - octaveShift * CENTS_PER_OCTAVE
        return if (abs(residual) <= WRONG_SWARA_CENTS) {
            NoteResult.Judged(residual, octaveShift, sung = null)
        } else {
            NoteResult.Judged(cents, 0, sung = mapper.read(sungHz))
        }
    }

    private fun summarise(marks: List<NoteMark>): PassSummary {
        val judged = marks.filter { it.result is NoteResult.Judged || it.result == NoteResult.NoVoice }
        val results = marks.mapNotNull { it.result as? NoteResult.Judged }
        val byNote = marks.filter { it.result is NoteResult.Judged }.groupBy { it.event.note }
        val attention = byNote.flatMap { (note, list) ->
            val tendencies = list.mapNotNull { tendency(it.result as NoteResult.Judged) }
            tendencies.groupingBy { it }.eachCount().map { (t, n) -> Attention(note, t, n, list.size) }
        }.sortedWith(compareByDescending<Attention> { it.times.toDouble() / it.of }.thenByDescending { it.times })
            .take(MAX_ATTENTION)
        return PassSummary(
            judged = judged.size,
            inTune = results.count { it.zone == Zone.IN_TUNE && it.octaveShift == 0 },
            close = results.count { it.zone == Zone.CLOSE && it.octaveShift == 0 },
            noVoice = marks.count { it.result == NoteResult.NoVoice },
            attention = attention,
        )
    }

    private fun tendency(r: NoteResult.Judged): Tendency? = when {
        r.sung != null -> Tendency.WRONG_SWARA
        r.octaveShift < 0 -> Tendency.OCTAVE_LOW
        r.octaveShift > 0 -> Tendency.OCTAVE_HIGH
        r.zone == Zone.IN_TUNE -> null
        r.high -> Tendency.HIGH
        else -> Tendency.LOW
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        /** Notes shorter than this are not judged (fast kalas). */
        const val MIN_JUDGED_SECONDS = 0.25

        /** Skip at least this much of each note's onset… */
        const val SKIP_MIN_SECONDS = 0.12

        /** …or this fraction of it, whichever is longer. */
        const val SKIP_FRACTION = 0.3

        /** Fraction of the judged window that must be voice. */
        const val MIN_VOICED_FRACTION = 0.4

        /** Beyond this (after octave folding) the student sang a different swara. */
        const val WRONG_SWARA_CENTS = 60.0

        private const val CENTS_PER_OCTAVE = 1200.0
        private const val MAX_ATTENTION = 3
    }
}
