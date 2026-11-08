package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** How close a sung pitch is to its target (docs/plan-pitch-monitor.md, section 4.3). */
enum class Zone {
    IN_TUNE,
    CLOSE,
    OFF,
    ;

    companion object {
        /** The zone of [cents] off target, at [strictness] (Precise when omitted). */
        fun of(cents: Double, strictness: Strictness = Strictness.PRECISE): Zone = when {
            abs(cents) <= strictness.inTuneCents -> IN_TUNE
            abs(cents) <= strictness.closeCents -> CLOSE
            else -> OFF
        }
    }
}

/**
 * How near counts as "in tune" and "close" (docs/plan-shruti.md). A practice
 * setting: beginners start gentle and tighten as they improve.
 */
enum class Strictness(val inTuneCents: Double, val closeCents: Double) {
    GENTLE(20.0, 40.0),
    STEADY(15.0, 30.0),
    PRECISE(10.0, 25.0),
}

/**
 * A sung pitch named relative to the student's Sa.
 *
 * @property swara nearest swarasthana; the raga's own name when the raga uses
 *   that position, otherwise a default name (so a sung R2 in Mayamalavagowla
 *   is reported as R2, not snapped to R1).
 * @property octave octaves from the madhya sthayi (-1 mandra, 0 madhya, 1 tara; can be ±2).
 * @property centsOff from the swara's target in the chosen tuning.
 * @property inRaga whether the raga uses this swarasthana.
 * @property westernName keyboard note name, e.g. "G3".
 */
data class SwaraReading(
    val frequencyHz: Double,
    val swara: Swara,
    val octave: Int,
    val centsOff: Double,
    val inRaga: Boolean,
    val westernName: String,
    val strictness: Strictness = Strictness.PRECISE,
) {
    val zone: Zone get() = Zone.of(centsOff, strictness)
}

/** Names sung pitches relative to Sa in a raga and tuning. */
class SwaraMapper(
    private val saHz: Double,
    private val raga: Raga,
    private val tuning: Tuning,
    private val strictness: Strictness = Strictness.PRECISE,
) {
    init {
        require(saHz > 0.0 && saHz.isFinite()) { "Sa must be a positive frequency" }
    }

    private val ragaByPosition: Map<Int, Swara> =
        raga.swaras.associateBy { it.semitone }

    fun read(frequencyHz: Double): SwaraReading? {
        if (frequencyHz <= 0.0 || !frequencyHz.isFinite()) return null
        val (octave, position) = when (tuning) {
            Tuning.EQUAL -> nearestEqual(frequencyHz)
            Tuning.JUST -> nearestJust(frequencyHz)
        }
        val swara = ragaByPosition[position] ?: DEFAULT_NAMES.getValue(position)
        val target = targetHz(swara, octave)
        return SwaraReading(
            frequencyHz = frequencyHz,
            swara = swara,
            octave = octave,
            centsOff = Pitch.cents(target, frequencyHz),
            inRaga = position in ragaByPosition,
            westernName = Shruti.noteName(Pitch.nearestMidi(frequencyHz)),
            strictness = strictness,
        )
    }

    /** Target frequency of [swara] [octave] octaves from madhya. */
    fun targetHz(swara: Swara, octave: Int): Double {
        val madhya = when (tuning) {
            Tuning.JUST -> saHz * swara.ratio
            Tuning.EQUAL -> saHz * 2.0.pow(swara.semitone / SEMITONES)
        }
        return madhya * 2.0.pow(octave)
    }

    /** Nearest equal-tempered semitone: the position whose 100-cent step is closest. */
    private fun nearestEqual(frequencyHz: Double): Pair<Int, Int> {
        val semitones = (SEMITONES * log2(frequencyHz / saHz)).roundToInt()
        val octave = floor(semitones / SEMITONES).toInt()
        return octave to (semitones - octave * SEMITONES.toInt())
    }

    /**
     * Nearest swarasthana target in just intonation, across all 12 positions
     * (issue #16): snapping to the equal-tempered semitone first can pick the
     * wrong swara — just-tuned R1 sung 39 cents sharp sits nearer R1's target
     * than R2's, but rounds to R2's semitone and reads out-of-raga and OFF.
     */
    private fun nearestJust(frequencyHz: Double): Pair<Int, Int> {
        val cents = CENTS_PER_OCTAVE * log2(frequencyHz / saHz)
        var bestPosition = 0
        var bestDelta = Double.MAX_VALUE
        for ((position, target) in JUST_TARGET_CENTS) {
            val delta = cents - target - CENTS_PER_OCTAVE * ((cents - target) / CENTS_PER_OCTAVE).roundToInt()
            if (abs(delta) < abs(bestDelta)) {
                bestDelta = delta
                bestPosition = position
            }
        }
        // cents - bestDelta is the winning target plus a whole number of octaves.
        val octave = floor((cents - bestDelta) / CENTS_PER_OCTAVE + 1e-9).toInt()
        return octave to bestPosition
    }

    private companion object {
        const val SEMITONES = 12.0
        const val CENTS_PER_OCTAVE = 1200.0

        /** Name used for a position the raga does not use. */
        val DEFAULT_NAMES = mapOf(
            0 to Swara.S, 1 to Swara.R1, 2 to Swara.R2, 3 to Swara.G2, 4 to Swara.G3, 5 to Swara.M1,
            6 to Swara.M2, 7 to Swara.P, 8 to Swara.D1, 9 to Swara.D2, 10 to Swara.N2, 11 to Swara.N3,
        )

        /** Just-intonation target of each position, in cents above Sa. */
        val JUST_TARGET_CENTS: Map<Int, Double> =
            DEFAULT_NAMES.mapValues { (_, swara) -> CENTS_PER_OCTAVE * log2(swara.ratio) }
    }
}
