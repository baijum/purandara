package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
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
        SwaraLetter.entries.map(raga::swara).associateBy { it.semitone }

    fun read(frequencyHz: Double): SwaraReading? {
        if (frequencyHz <= 0.0 || !frequencyHz.isFinite()) return null
        val semitones = (SEMITONES * log2(frequencyHz / saHz)).roundToInt()
        val octave = floor(semitones / SEMITONES).toInt()
        val position = semitones - octave * SEMITONES.toInt()
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

    private companion object {
        const val SEMITONES = 12.0

        /** Name used for a position the raga does not use. */
        val DEFAULT_NAMES = mapOf(
            0 to Swara.S, 1 to Swara.R1, 2 to Swara.R2, 3 to Swara.G2, 4 to Swara.G3, 5 to Swara.M1,
            6 to Swara.M2, 7 to Swara.P, 8 to Swara.D1, 9 to Swara.D2, 10 to Swara.N2, 11 to Swara.N3,
        )
    }
}
