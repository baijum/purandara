package com.baijum.purandara.domain

import kotlin.math.abs
import kotlin.math.log2

/** Which bundled tanpura loop plays for a Sa, and how fast. */
data class TanpuraPlayback(val saMidi: Int, val assetPath: String, val rate: Double)

/**
 * The bundled tanpura drone (pa SA SA sa): one seamless loop per Sa in
 * [SA_MIDIS], each pitch-shifted from Sankalp Gulati's recording to exact
 * 12-TET by `tools/samples/prepare_tanpura.py`. Files live at
 * `audio/tanpura/<midi>.flac`, packaged like the harmonium samples.
 */
object TanpuraBank {
    /** A#2, D3, F#3, A#3. Must match SA_MIDIS in prepare_tanpura.py. */
    val SA_MIDIS: List<Int> = listOf(46, 50, 54, 58)

    const val DIRECTORY: String = "tanpura"

    /** Largest speed change allowed (semitones); covers the whole Sa range G2..C4. */
    const val MAX_SHIFT_SEMITONES: Double = 3.0

    fun assetPath(saMidi: Int): String {
        require(saMidi in SA_MIDIS) { "no tanpura loop for Sa MIDI $saMidi" }
        return "$DIRECTORY/$saMidi.flac"
    }

    fun allAssetPaths(): List<String> = SA_MIDIS.map(::assetPath)

    /** The nearest loop and its playback rate for Sa at [saHz], or null if it would need too large a shift. */
    fun playbackFor(saHz: Double): TanpuraPlayback? {
        if (!saHz.isFinite() || saHz <= 0.0) return null
        val midiExact = Pitch.hzToMidi(saHz)
        val nearest = SA_MIDIS.minBy { abs(it - midiExact) }
        if (abs(midiExact - nearest) > MAX_SHIFT_SEMITONES + 1e-9) return null
        return TanpuraPlayback(nearest, assetPath(nearest), saHz / Pitch.midiToHz(nearest))
    }

    /** Semitones a playback is shifted from its recording (for tests and diagnostics). */
    fun shiftSemitones(p: TanpuraPlayback): Double = 12 * log2(p.rate)
}
