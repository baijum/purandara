package com.baijum.purandara.audio

import com.baijum.purandara.domain.Pitch
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Decoded harmonium samples, keyed by MIDI note, all at [sampleRate].
 *
 * Platforms decode `audio/harmonium/<midi>.flac` to mono float PCM and build
 * this once. Each sample must be exactly its MIDI pitch (as prepared by
 * `tools/samples/prepare_harmonium.py`), which lets [LoopedClip] loop it in
 * phase.
 */
class SampleBank(
    val sampleRate: Int,
    samples: Map<Int, FloatArray>,
    /** Tanpura drone loops keyed by their Sa's MIDI note (see TanpuraBank); may be empty. */
    tanpura: Map<Int, FloatArray> = emptyMap(),
) {
    init {
        require(sampleRate > 0) { "sample rate must be positive" }
    }

    internal val clips: Map<Int, LoopedClip> =
        samples.mapValues { (midi, data) -> LoopedClip(data, Pitch.midiToHz(midi), sampleRate) }

    private val tanpuraClips: Map<Int, CircularClip> = tanpura.mapValues { (_, data) -> CircularClip(data) }

    operator fun contains(midi: Int): Boolean = midi in clips

    /** True when the tanpura loops are loaded. */
    val hasTanpura: Boolean get() = tanpuraClips.isNotEmpty()

    internal fun clip(midi: Int): LoopedClip =
        clips[midi] ?: throw IllegalArgumentException("sample bank has no MIDI $midi")

    internal fun tanpuraClip(saMidi: Int): CircularClip =
        tanpuraClips[saMidi] ?: throw IllegalArgumentException("sample bank has no tanpura for Sa MIDI $saMidi")
}

/** Anything a voice can read at a fractional position. */
internal interface Clip {
    fun value(position: Double): Float
}

/**
 * A recording prepared as a seamless loop (its end already blended into its
 * start, see tools/samples/prepare_tanpura.py), read round and round.
 */
internal class CircularClip(private val data: FloatArray) : Clip {
    init {
        require(data.size > 1) { "loop too short" }
    }

    override fun value(position: Double): Float {
        if (position < 0.0) return 0f
        val p = position % data.size
        val i = p.toInt()
        val frac = (p - i).toFloat()
        val next = if (i + 1 == data.size) 0 else i + 1
        return data[i] * (1f - frac) + data[next] * frac
    }
}

/**
 * A sample that can be read at any (fractional) position, however long.
 *
 * Past the attack it loops the steady part of the note. The loop length is
 * a whole number of periods of [frequencyHz], so the two sides of the loop
 * are in phase and a short linear crossfade joins them without a dip or click.
 */
internal class LoopedClip(private val data: FloatArray, frequencyHz: Double, sampleRate: Int) : Clip {
    private val loopStart: Double = LOOP_START_SECONDS * sampleRate
    private val loopLength: Double
    private val crossfade: Double
    private val loopEnd: Double

    init {
        val usableEnd = data.size - END_MARGIN_SECONDS * sampleRate
        require(usableEnd - loopStart >= MIN_LOOP_SECONDS * sampleRate) {
            "sample too short to loop: ${data.size} frames"
        }
        val periodFrames = sampleRate / frequencyHz
        loopLength = floor((usableEnd - loopStart) / periodFrames) * periodFrames
        loopEnd = loopStart + loopLength
        crossfade = minOf(CROSSFADE_SECONDS * sampleRate, loopLength / 2)
    }

    /** Sample value at [position] frames from the start of the clip. */
    override fun value(position: Double): Float {
        if (position < 0.0) return 0f
        val fadeStart = loopEnd - crossfade
        if (position < fadeStart) return interpolate(position)
        // Fold into [fadeStart, fadeStart + loopLength).
        val q = fadeStart + (position - fadeStart) % loopLength
        return if (q < loopEnd) {
            val w = ((q - fadeStart) / crossfade).toFloat()
            interpolate(q) * (1f - w) + interpolate(q - loopLength) * w
        } else {
            interpolate(q - loopLength)
        }
    }

    private fun interpolate(position: Double): Float {
        val i = position.toInt()
        val frac = (position - i).toFloat()
        return data[i] * (1f - frac) + data[i + 1] * frac
    }

    private companion object {
        /** Skip the bellows attack before looping. */
        const val LOOP_START_SECONDS = 0.6

        /** Stay clear of the prepared file's 80 ms end fade. */
        const val END_MARGIN_SECONDS = 0.12
        const val CROSSFADE_SECONDS = 0.1
        const val MIN_LOOP_SECONDS = 0.3
    }
}

/** Frames for [seconds] at [sampleRate], rounded to the nearest frame. */
internal fun framesOf(seconds: Double, sampleRate: Int): Int = (seconds * sampleRate).roundToInt()
