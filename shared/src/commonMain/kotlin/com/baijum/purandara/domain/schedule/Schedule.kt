package com.baijum.purandara.domain.schedule

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.SamplePlayback
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.TalaBeat
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.varisai.Akshara
import com.baijum.purandara.domain.varisai.SectionSpan
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.WrittenSwara

/** How to perform an exercise. Its raga and tala come from the exercise itself. */
data class PerformanceSettings(
    val saHz: Double,
    /** Tala aksharas per minute (the beat you clap). */
    val tempo: Int = DEFAULT_TEMPO,
    /** 1..4: kala k puts 2^(k-1) written aksharas in each tala akshara. */
    val kala: Int = 1,
    val tuning: Tuning = Tuning.JUST,
) {
    init {
        require(saHz > 0.0 && saHz.isFinite()) { "Sa must be a positive frequency, was $saHz" }
        require(tempo in TEMPO_RANGE) { "tempo $tempo outside $TEMPO_RANGE aksharas per minute" }
        require(kala in KALA_RANGE) { "kala $kala outside $KALA_RANGE" }
    }

    /** Seconds per tala akshara. */
    val talaAksharaSeconds: Double get() = SECONDS_PER_MINUTE / tempo

    /** Written aksharas per tala akshara. */
    val aksharasPerBeat: Int get() = 1 shl (kala - 1)

    /** Seconds per written akshara. */
    val writtenAksharaSeconds: Double get() = talaAksharaSeconds / aksharasPerBeat

    companion object {
        /** For callers without default arguments (Swift). */
        fun create(saHz: Double, tempo: Int, kala: Int, tuning: Tuning): PerformanceSettings =
            PerformanceSettings(saHz = saHz, tempo = tempo, kala = kala, tuning = tuning)

        const val DEFAULT_TEMPO = 60
        val TEMPO_RANGE = 30..120
        val KALA_RANGE = 1..4
        private const val SECONDS_PER_MINUTE = 60.0
    }
}

/** Where a moment of playback sits in the written notation. */
data class NotationPosition(
    /** Which pass through the exercise (0-based); see [Schedule.repetitions]. */
    val repetition: Int,
    val avarta: Int,
    val akshara: Int,
    /** Swara slot within the akshara, 0 for a karvai. */
    val slot: Int,
)

/** A swara to sound. A karvai lengthens the previous event rather than adding one. */
data class NoteEvent(
    val startSeconds: Double,
    val durationSeconds: Double,
    val written: WrittenSwara,
    val note: Note,
    val frequencyHz: Double,
    val playback: SamplePlayback,
    val position: NotationPosition,
)

/** A tala beat, for the display and the click. */
data class BeatEvent(
    val startSeconds: Double,
    /** Beat number from the start of playback. */
    val index: Int,
    /** 0-based position within the avarta. */
    val beatInAvarta: Int,
    val beat: TalaBeat,
)

/** A highlight change: every swara slot and every karvai. */
data class CursorEvent(val startSeconds: Double, val position: NotationPosition)

/**
 * Timed events for one performance of a [varisai].
 *
 * At higher kalas the exercise is repeated until it fills whole tala
 * avartas, as the workbook describes: the plain scale fills one avarta in
 * the second kala and is sung twice per avarta in the third, four times in the fourth.
 */
class Schedule private constructor(
    val varisai: Varisai,
    val settings: PerformanceSettings,
    /** The avartas played: the whole piece, or one section ([section]). */
    val avartaRange: IntRange,
    val repetitions: Int,
    val notes: List<NoteEvent>,
    val beats: List<BeatEvent>,
    val cursor: List<CursorEvent>,
    val durationSeconds: Double,
) {
    /** The exercise's tala. */
    val tala: Tala get() = varisai.tala

    /** The exercise's raga. */
    val raga: Raga get() = varisai.raga

    /** Seconds per written akshara (notation cell): a swarajati writes several to a beat. */
    val aksharaSeconds: Double get() = settings.writtenAksharaSeconds / varisai.beat

    /** Seconds per written avarta (one row block of notation). */
    val avartaSeconds: Double get() = aksharaSeconds * varisai.tala.aksharas * varisai.beat

    /** The section played, or null for the whole piece. */
    val section: SectionSpan? get() = varisai.sections.firstOrNull { it.firstAvarta..it.lastAvarta == avartaRange }

    /** When avarta [avarta] (a piece-wide index) starts within one pass. */
    fun avartaStartSeconds(avarta: Int): Double = (avarta - avartaRange.first) * avartaSeconds

    /** Notation position sounding at [seconds], or null before the start or after the end. */
    fun cursorAt(seconds: Double): NotationPosition? {
        if (seconds < 0.0 || seconds >= durationSeconds) return null
        return cursor[lastStartingAtOrBefore(cursor, seconds) { it.startSeconds }].position
    }

    /** Tala beat sounding at [seconds], or null before the start or after the end. */
    fun beatAt(seconds: Double): BeatEvent? {
        if (seconds < 0.0 || seconds >= durationSeconds) return null
        return beats[lastStartingAtOrBefore(beats, seconds) { it.startSeconds }]
    }

    companion object {
        /** Index of the last element whose start is <= [seconds]; list is sorted and starts at 0. */
        private inline fun <T> lastStartingAtOrBefore(list: List<T>, seconds: Double, start: (T) -> Double): Int {
            var lo = 0
            var hi = list.lastIndex
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (start(list[mid]) <= seconds + TIME_EPSILON) lo = mid else hi = mid - 1
            }
            return lo
        }

        /** Tolerance for float rounding when a lookup lands exactly on an event. */
        private const val TIME_EPSILON = 1e-9

        /**
         * Builds the schedule.
         *
         * @throws IllegalArgumentException if a note falls outside the
         *   harmonium bank for this Sa.
         */
        fun of(varisai: Varisai, settings: PerformanceSettings): Schedule = of(varisai, settings, varisai.avartas.indices)

        /** Just one section of the piece; notation positions keep the piece's avarta numbers. */
        fun ofSection(varisai: Varisai, settings: PerformanceSettings, section: SectionSpan): Schedule =
            of(varisai, settings, section.firstAvarta..section.lastAvarta)

        private fun of(varisai: Varisai, settings: PerformanceSettings, range: IntRange): Schedule {
            require(!range.isEmpty() && range.first >= 0 && range.last < varisai.avartas.size) { "avartas $range outside the piece" }
            val reps = repetitionsToFillAvartas(range.count(), settings)
            val notes = mutableListOf<NoteEvent>()
            val cursor = mutableListOf<CursorEvent>()
            var time = 0.0 // seconds elapsed

            for (rep in 0 until reps) {
                for (avartaIndex in range) {
                    val avarta = varisai.avartas[avartaIndex]
                    // A khanda-gati section packs more cells into the same tala beat, so the
                    // cells are shorter but the avarta still fills the same number of beats.
                    val aksharaSeconds = settings.writtenAksharaSeconds / varisai.cellsPerBeatIn(avartaIndex)
                    avarta.aksharas.forEachIndexed { aksharaIndex, akshara ->
                        val aksharaStart = time
                        when (akshara) {
                            is Akshara.Karvai -> {
                                // A section can't start on a karvai in practice; if one did, it would be silent.
                                if (notes.isNotEmpty()) {
                                    val previous = notes.removeAt(notes.lastIndex)
                                    notes += previous.copy(durationSeconds = previous.durationSeconds + aksharaSeconds)
                                }
                                cursor += CursorEvent(aksharaStart, NotationPosition(rep, avartaIndex, aksharaIndex, 0))
                            }
                            is Akshara.Swaras -> {
                                val slotSeconds = aksharaSeconds / akshara.swaras.size
                                akshara.swaras.forEachIndexed { slot, swara ->
                                    val start = aksharaStart + slot * slotSeconds
                                    val position = NotationPosition(rep, avartaIndex, aksharaIndex, slot)
                                    notes += noteEvent(swara, start, slotSeconds, position, varisai.raga, settings)
                                    cursor += CursorEvent(start, position)
                                }
                            }
                        }
                        time += aksharaSeconds
                    }
                }
            }

            // Every avarta fills tala.aksharas / aksharasPerBeat beats, whatever its gati.
            val totalBeats = reps * range.count() * varisai.tala.aksharas / settings.aksharasPerBeat
            val tala = varisai.tala
            val beats = List(totalBeats) { i ->
                val inAvarta = i % tala.aksharas
                BeatEvent(i * settings.talaAksharaSeconds, i, inAvarta, tala.beats[inAvarta])
            }
            return Schedule(
                varisai = varisai,
                settings = settings,
                avartaRange = range,
                repetitions = reps,
                notes = notes,
                beats = beats,
                cursor = cursor,
                durationSeconds = time,
            )
        }

        /** Smallest number of passes whose avartas fill whole tala cycles. */
        fun repetitionsToFillAvartas(varisai: Varisai, settings: PerformanceSettings): Int =
            repetitionsToFillAvartas(varisai.avartas.size, settings)

        private fun repetitionsToFillAvartas(avartaCount: Int, settings: PerformanceSettings): Int {
            require(avartaCount > 0) { "exercise has no aksharas" }
            var reps = 1
            while ((avartaCount * reps) % settings.aksharasPerBeat != 0) reps++
            return reps
        }

        private fun noteEvent(
            written: WrittenSwara,
            start: Double,
            duration: Double,
            position: NotationPosition,
            raga: Raga,
            settings: PerformanceSettings,
        ): NoteEvent {
            val note = Note(raga.swara(written.letter), written.octave)
            val hz = settings.tuning.frequency(note, settings.saHz)
            val playback = requireNotNull(HarmoniumBank.playbackFor(hz)) {
                "$written (${hz.toInt()} Hz) is outside the harmonium bank for Sa ${settings.saHz} Hz"
            }
            return NoteEvent(start, duration, written, note, hz, playback, position)
        }
    }
}
