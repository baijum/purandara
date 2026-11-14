package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.singalong.Display
import com.baijum.purandara.singalong.SwaraMapper
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * One swara heard in a recording.
 *
 * @property octave octaves from madhya (-1 mandra, 1 tara; can be ±2).
 * @property centsOff the note's median pitch from the swara's target.
 * @property inRaga whether the raga uses it (always true with no raga).
 * @property holds how many `,` follow it: 0 for an ordinary note, up to 3 for a long hold.
 */
data class HeardNote(
    val swara: Swara,
    val octave: Int,
    val startSeconds: Double,
    val durationSeconds: Double,
    val centsOff: Double,
    val inRaga: Boolean,
    val holds: Int = 0,
) {
    val endSeconds: Double get() = startSeconds + durationSeconds

    /** "R₁", "S′", with a dot below for mandra (as elsewhere in the app). */
    val label: String get() = Display.swaraLabel(swara, octave)
}

/** The swaras of a recording, a line per phrase. */
class Transcription(val phrases: List<List<HeardNote>>) {
    val notes: List<HeardNote> = phrases.flatten()

    /** Index into [notes] of the note sounding at [seconds], or null between notes. */
    fun noteIndexAt(seconds: Double): Int? {
        var lo = 0
        var hi = notes.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val note = notes[mid]
            when {
                seconds < note.startSeconds -> hi = mid - 1
                seconds >= note.endSeconds -> lo = mid + 1
                else -> return mid
            }
        }
        return null
    }

    /** Lowest and highest notes heard, as (octave, swara) by pitch; null when nothing was heard. */
    val lowest: HeardNote? get() = notes.minByOrNull { pitchOrder(it) }
    val highest: HeardNote? get() = notes.maxByOrNull { pitchOrder(it) }

    private fun pitchOrder(note: HeardNote) = note.octave * SEMITONES + note.swara.semitone

    private companion object {
        const val SEMITONES = 12
    }
}

/**
 * Reads swaras from a [PitchTrack] (docs/plan-recordings.md, "Analysis").
 * Approximate by design: gamakas and slides are read as their steadiest
 * notes, so the apps always show the pitch trace alongside.
 *
 * 1. Voiced frames: a pitch, and a level above the recording's own noise
 *    floor (twice the median level of its pitchless frames, capped well
 *    below its voiced level).
 * 2. Each voiced frame is named by [SwaraMapper]; frames more than
 *    [STEADY_CENTS] from their swara (mid-slide) are movement.
 * 3. A run of one swara lasting [MIN_NOTE_SECONDS] is a note; flickers up to
 *    [FLICKER_SECONDS] inside a run don't break it.
 * 4. Notes of the same swara merge across movement with no other note in
 *    between (up to [MERGE_MOVEMENT_SECONDS]: a kampita around its anchor),
 *    or across silence up to [MERGE_SILENCE_SECONDS] (a consonant).
 * 5. A silence of [PHRASE_GAP_SECONDS] starts a new phrase.
 * 6. A note at least twice the recording's median note length gets a `,`
 *    for each further multiple, up to [MAX_HOLDS].
 */
object SwaraTranscriber {
    const val STEADY_CENTS = 50.0
    const val MIN_NOTE_SECONDS = 0.1
    const val FLICKER_SECONDS = 0.03
    const val MERGE_MOVEMENT_SECONDS = 0.25
    const val MERGE_SILENCE_SECONDS = 0.08
    const val PHRASE_GAP_SECONDS = 0.6
    const val MAX_HOLDS = 3
    private const val HOLD_SLACK = 0.2

    /**
     * The noise floor is the median level of the frames with no pitch (the
     * room between phrases) times [FLOOR_FACTOR]; with fewer than
     * [MIN_FLOOR_FRAMES] of them, only [MIN_VOICE_RMS] applies. It never
     * rises above [FLOOR_CAP] of the median pitched level: with little
     * silence, the pitchless frames are mostly loud consonants and note
     * changes, not the room.
     */
    private const val FLOOR_FACTOR = 2.0f
    private const val FLOOR_CAP = 0.25f
    private const val MIN_FLOOR_FRAMES = 10

    /** Never treat anything quieter than this as voice (below sing-along's 0.01: recordings have no gain control). */
    private const val MIN_VOICE_RMS = 0.003f

    fun transcribe(track: PitchTrack, saHz: Double, raga: Raga?, tuning: Tuning): Transcription {
        val mapper = SwaraMapper(saHz, raga, tuning)
        val floor = voiceFloor(track)
        val labels = arrayOfNulls<Label>(track.size)
        val voiced = BooleanArray(track.size)
        for (i in 0 until track.size) {
            val hz = track.frequencyAt(i) ?: continue
            if (track.rmsAt(i) < floor) continue
            voiced[i] = true
            val reading = mapper.read(hz) ?: continue
            if (abs(reading.centsOff) <= STEADY_CENTS) labels[i] = Label(reading.swara, reading.octave)
        }
        fillFlickers(labels, framesFor(FLICKER_SECONDS, track))
        val runs = steadyRuns(labels, framesFor(MIN_NOTE_SECONDS, track))
        val merged = merge(runs, voiced, track)
        val notes = merged.map { run -> note(run, track, mapper) }
        return Transcription(withHolds(phrases(notes, voiced, track)))
    }

    private data class Label(val swara: Swara, val octave: Int)

    /** A run of frames [first, last] of one label. */
    private data class Run(val label: Label, val first: Int, val last: Int)

    private fun framesFor(seconds: Double, track: PitchTrack): Int = max(1, (seconds / track.hopSeconds + 0.5).toInt())

    private fun voiceFloor(track: PitchTrack): Float {
        val (pitched, room) = (0 until track.size).partition { track.frequencyAt(it) != null }
        if (room.size < MIN_FLOOR_FRAMES) return MIN_VOICE_RMS
        val roomFloor = median(room.map { track.rmsAt(it) }) * FLOOR_FACTOR
        val cap = if (pitched.isEmpty()) roomFloor else median(pitched.map { track.rmsAt(it) }) * FLOOR_CAP
        return maxOf(MIN_VOICE_RMS, minOf(roomFloor, cap))
    }

    private fun median(levels: List<Float>): Float = levels.sorted()[levels.size / 2]

    /** Gives a short interruption between two frames of the same label that label. */
    private fun fillFlickers(labels: Array<Label?>, maxFrames: Int) {
        var i = 0
        while (i < labels.size) {
            val label = labels[i]
            if (label == null || i + 1 >= labels.size || labels[i + 1] == label) {
                i++
                continue
            }
            var j = i + 1
            while (j < labels.size && j - i - 1 <= maxFrames && labels[j] != label) j++
            if (j < labels.size && labels[j] == label && j - i - 1 <= maxFrames) {
                for (k in i + 1 until j) labels[k] = label
                i = j
            } else {
                i++
            }
        }
    }

    private fun steadyRuns(labels: Array<Label?>, minFrames: Int): List<Run> {
        val runs = mutableListOf<Run>()
        var start = 0
        while (start < labels.size) {
            val label = labels[start]
            var end = start
            while (end + 1 < labels.size && labels[end + 1] == label) end++
            if (label != null && end - start + 1 >= minFrames) runs += Run(label, start, end)
            start = end + 1
        }
        return runs
    }

    private fun merge(runs: List<Run>, voiced: BooleanArray, track: PitchTrack): List<Run> {
        val out = mutableListOf<Run>()
        for (run in runs) {
            val last = out.lastOrNull()
            if (last != null && last.label == run.label && mergeable(last.last + 1, run.first, voiced, track)) {
                out[out.size - 1] = last.copy(last = run.last)
            } else {
                out += run
            }
        }
        return out
    }

    /** Whether the gap [from, until) between two runs of one swara joins them. */
    private fun mergeable(from: Int, until: Int, voiced: BooleanArray, track: PitchTrack): Boolean {
        val seconds = (until - from) * track.hopSeconds
        val silent = (from until until).any { !voiced[it] }
        return seconds <= if (silent) MERGE_SILENCE_SECONDS else MERGE_MOVEMENT_SECONDS
    }

    private fun note(run: Run, track: PitchTrack, mapper: SwaraMapper): HeardNote {
        val pitches = (run.first..run.last).mapNotNull { i ->
            track.frequencyAt(i)?.let { hz -> mapper.read(hz)?.takeIf { Label(it.swara, it.octave) == run.label } }
        }.sortedBy { it.centsOff }
        val median = pitches[pitches.size / 2]
        return HeardNote(
            swara = run.label.swara,
            octave = run.label.octave,
            startSeconds = track.timeAt(run.first) - track.hopSeconds / 2,
            durationSeconds = (run.last - run.first + 1) * track.hopSeconds,
            centsOff = median.centsOff,
            inRaga = median.inRaga,
        )
    }

    private fun phrases(notes: List<HeardNote>, voiced: BooleanArray, track: PitchTrack): List<List<HeardNote>> {
        val phrases = mutableListOf<MutableList<HeardNote>>()
        val gapFrames = framesFor(PHRASE_GAP_SECONDS, track)
        var previous: HeardNote? = null
        for (note in notes) {
            if (previous == null || longSilence(previous, note, voiced, track, gapFrames)) phrases += mutableListOf<HeardNote>()
            phrases.last() += note
            previous = note
        }
        return phrases
    }

    private fun longSilence(a: HeardNote, b: HeardNote, voiced: BooleanArray, track: PitchTrack, gapFrames: Int): Boolean {
        var silent = 0
        for (i in track.indexAt(a.endSeconds) until track.indexAt(b.startSeconds)) {
            silent = if (voiced[i]) 0 else silent + 1
            if (silent >= gapFrames) return true
        }
        return false
    }

    private fun withHolds(phrases: List<List<HeardNote>>): List<List<HeardNote>> {
        val durations = phrases.flatten().map { it.durationSeconds }.sorted()
        if (durations.isEmpty()) return phrases
        val median = durations[durations.size / 2]
        return phrases.map { phrase ->
            phrase.map { note ->
                // A little slack: frame edges make a note three times as long read as 2.97.
                val multiple = floor(note.durationSeconds / median + HOLD_SLACK).toInt()
                if (multiple >= 2) note.copy(holds = minOf(MAX_HOLDS, multiple - 1)) else note
            }
        }
    }
}
