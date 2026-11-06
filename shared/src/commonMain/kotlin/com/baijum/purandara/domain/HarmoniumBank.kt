package com.baijum.purandara.domain

import kotlin.math.abs

/**
 * Which bundled sample to play, and how fast, to sound [frequencyHz].
 *
 * Playing a sample at [rate] x normal speed multiplies its pitch by [rate]
 * (Android `SoundPool` rate, iOS `AVAudioUnitVarispeed.rate`). Rates stay
 * within half a semitone of 1.0 so the instrument's timbre is unaffected.
 */
data class SamplePlayback(
    val midi: Int,
    val assetPath: String,
    val rate: Double,
    val frequencyHz: Double,
)

/**
 * The bundled CC0 harmonium samples: one file per semitone, each tuned to
 * exact 12-TET at A4 = 440 Hz by `tools/samples/prepare_harmonium.py`.
 *
 * Files live once at `audio/harmonium/<midi>.flac` in the repository root and
 * are packaged into both apps. For an [assetPath] such as `harmonium/49.flac`:
 *  - Android: `context.assets.openFd("harmonium/49.flac")` (the `audio/`
 *    directory is an assets source directory);
 *  - iOS: `Bundle.main.url(forResource: "49", withExtension: "flac", subdirectory: "audio/harmonium")`
 *    (the `audio/` directory is copied as a bundle folder).
 */
object HarmoniumBank {
    /** C2. Must match MIDI_LOW in prepare_harmonium.py. */
    const val MIDI_LOW: Int = 36

    /** C5. Must match MIDI_HIGH in prepare_harmonium.py. */
    const val MIDI_HIGH: Int = 72

    const val DIRECTORY: String = "harmonium"

    val midiRange: IntRange = MIDI_LOW..MIDI_HIGH

    /** Lowest and highest frequencies the bank can play (half a semitone beyond the ends). */
    val minHz: Double = Pitch.midiToHz(MIDI_LOW - HALF_SEMITONE)
    val maxHz: Double = Pitch.midiToHz(MIDI_HIGH + HALF_SEMITONE)

    /** Relative path of the sample for [midi], e.g. `harmonium/49.flac`. */
    fun assetPath(midi: Int): String {
        require(midi in midiRange) { "midi $midi outside $midiRange" }
        return "$DIRECTORY/$midi.flac"
    }

    /** All asset paths, lowest first. */
    fun allAssetPaths(): List<String> = midiRange.map(::assetPath)

    /**
     * The sample and playback rate that produce [frequencyHz], or `null` if the
     * frequency is outside the bank (more than half a semitone beyond C2..C5)
     * or not a positive finite number.
     */
    fun playbackFor(frequencyHz: Double): SamplePlayback? {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return null
        val midiExact = Pitch.hzToMidi(frequencyHz)
        val midi = Pitch.nearestMidi(frequencyHz).coerceIn(midiRange)
        if (abs(midiExact - midi) > HALF_SEMITONE + EPSILON) return null
        return SamplePlayback(
            midi = midi,
            assetPath = assetPath(midi),
            rate = frequencyHz / Pitch.midiToHz(midi),
            frequencyHz = frequencyHz,
        )
    }

    /** Convenience: playback for [note] with Sa at [saHz]. */
    fun playbackFor(note: Note, saHz: Double): SamplePlayback? = playbackFor(note.frequency(saHz))

    private const val HALF_SEMITONE = 0.5
    private const val EPSILON = 1e-9
}
