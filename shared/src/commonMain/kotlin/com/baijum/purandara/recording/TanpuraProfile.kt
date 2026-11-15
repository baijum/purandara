package com.baijum.purandara.recording

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Shruti
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The student's own tanpura, learnt from the first [LISTEN_SECONDS] of a
 * recording, when it plays alone (docs/plan-recordings-tanpura.md).
 *
 * @property rms its level at the microphone (the median of those seconds).
 * @property saHz its Sa, if its strings could be told apart (any octave).
 * @property otherHz its other string (Pa, or Ma / Ni), if heard.
 */
class TanpuraProfile(
    val rms: Float,
    val saHz: Double?,
    val otherHz: Double?,
    val listenSeconds: Double = LISTEN_SECONDS,
    /** Whether a tanpura was heard at all: its strings were told apart (not just room noise). */
    val heard: Boolean = saHz != null,
) {
    /**
     * Whether a frame is the student's voice rather than the tanpura: clearly
     * louder than the tanpura, and louder still at the tanpura's own pitches
     * (a sung Sa over a Sa drone adds to the level; the drone alone doesn't).
     */
    fun isVoice(hz: Double, frameRms: Float): Boolean {
        if (frameRms < rms * VOICE_OVER_TANPURA) return false
        val atDrone = listOfNotNull(saHz, otherHz).any { samePitchClass(hz, it) }
        return !atDrone || frameRms >= rms * VOICE_OVER_DRONE_PITCH
    }

    /**
     * The tanpura's Sa as a Sa choice (MIDI and fine-tune cents) in the octave
     * nearest [currentSaHz], or null when it isn't known, is outside the Sa
     * choices, or is within [OFFER_CENTS] of [currentSaHz] already.
     */
    fun suggestedSa(currentSaHz: Double): Pair<Int, Int>? {
        val sa = saHz ?: return null
        var hz = sa
        while (hz < currentSaHz / SQRT2) hz *= 2
        while (hz >= currentSaHz * SQRT2) hz /= 2
        if (abs(Pitch.cents(currentSaHz, hz)) <= OFFER_CENTS) return null
        return Shruti.choiceNear(hz)
    }

    companion object {
        /** Seconds of tanpura alone at the start of a recording. */
        const val LISTEN_SECONDS = 3.0

        /** A frame must be this much louder than the tanpura to be voice (about +4 dB). */
        const val VOICE_OVER_TANPURA = 1.6f

        /** At the tanpura's own pitches, this much louder (about +8 dB). */
        const val VOICE_OVER_DRONE_PITCH = 2.5f

        /** The tanpura's Sa is offered when it differs from the recording's by more than this. */
        const val OFFER_CENTS = 10.0

        private const val SKIP_SECONDS = 0.3
        private const val DRONE_CENTS = 50.0
        private const val BIN_CENTS = 20
        private const val BINS = 1200 / BIN_CENTS
        private const val MIN_SHARE = 0.25
        private const val PARTNER_SHARE = 0.08
        private const val FIFTH = 700.0
        private const val FOURTH = 500.0
        private const val INTERVAL_SLACK = 40.0
        private const val CENTS_PER_SEMITONE = 100.0
        private const val SQRT2 = 1.4142135623730951

        /**
         * The profile from the first [listenSeconds] of [track] (skipping the
         * first moment, the tap on Record), or null if that's too short.
         */
        fun from(track: PitchTrack, listenSeconds: Double = LISTEN_SECONDS): TanpuraProfile? {
            val frames = (0 until track.size).filter { track.timeAt(it) in SKIP_SECONDS..listenSeconds }
            if (frames.size < 2) return null
            val levels = frames.map { track.rmsAt(it) }.sorted()
            val pitches = frames.mapNotNull { track.frequencyAt(it) }
            val (sa, other) = strings(pitches)
            return TanpuraProfile(levels[levels.size / 2], sa, other, listenSeconds)
        }

        /**
         * Its Sa and other string from the pitches heard: folded into one
         * octave, the biggest peak is Sa (three of the four strings), and the
         * other string is a peak a fifth or a fourth away.
         */
        private fun strings(pitches: List<Double>): Pair<Double?, Double?> {
            if (pitches.isEmpty()) return null to null
            val classes = pitches.map { folded(it) }
            val counts = IntArray(BINS)
            classes.forEach { counts[(it / BIN_CENTS).toInt() % BINS]++ }
            // Smooth over neighbouring bins so a pitch on a bin edge counts once.
            val smooth = IntArray(BINS) { counts[(it + BINS - 1) % BINS] + counts[it] + counts[(it + 1) % BINS] }
            val top = smooth.indices.maxBy { smooth[it] }
            if (smooth[top] < pitches.size * MIN_SHARE) return null to null
            val sa = medianNear(pitches, classes, top * BIN_CENTS + BIN_CENTS / 2.0) ?: return null to null
            val saClass = folded(sa)
            val partner = smooth.indices
                .filter { smooth[it] >= pitches.size * PARTNER_SHARE }
                .map { it * BIN_CENTS + BIN_CENTS / 2.0 }
                .filter { c -> listOf(FIFTH, FOURTH).any { abs(interval(saClass, c) - it) <= INTERVAL_SLACK } }
                .maxByOrNull { c -> smooth[(c / BIN_CENTS).toInt() % BINS] }
            return sa to partner?.let { medianNear(pitches, classes, it) }
        }

        /** The median of the pitches within a drone tolerance of pitch class [centre]. */
        private fun medianNear(pitches: List<Double>, classes: List<Double>, centre: Double): Double? {
            val near = pitches.indices.filter { abs(interval(centre, classes[it]).let { d -> if (d > 600) d - 1200 else d }) <= DRONE_CENTS }
            if (near.isEmpty()) return null
            // Compare in one octave: the median class, placed in the octave of the commonest pitch.
            val sortedClasses = near.map { classes[it] }.sorted()
            val medianClass = sortedClasses[sortedClasses.size / 2]
            val reference = near.map { pitches[it] }.sorted()[near.size / 2]
            val shift = interval(folded(reference), medianClass).let { if (it > 600) it - 1200 else it }
            return reference * 2.0.pow(shift / 1200)
        }

        /** Cents above C in one octave, 0 until 1200. */
        private fun folded(hz: Double): Double = ((Pitch.hzToMidi(hz) * CENTS_PER_SEMITONE) % 1200 + 1200) % 1200

        /** Upward interval from class [a] to class [b], 0 until 1200. */
        private fun interval(a: Double, b: Double): Double = ((b - a) % 1200 + 1200) % 1200

        private fun samePitchClass(a: Double, b: Double): Boolean {
            val d = interval(folded(a), folded(b))
            return d <= DRONE_CENTS || d >= 1200 - DRONE_CENTS
        }
    }
}
