package com.baijum.purandara.domain

/**
 * The student's Sa (tonic), chosen as a semitone on the keyboard.
 *
 * Carnatic students usually name their pitch in kattai (harmonium "black
 * and white" numbering): 1 = C, 1½ = C♯, 2 = D … 7 = B.
 */
object Shruti {
    /** Sa choices offered: G2..C4 (see docs/plan.md, section 7). */
    val SA_RANGE: IntRange = 43..60

    /** Default Sa: C3, as in the practice workbook. */
    const val DEFAULT_SA_MIDI: Int = 48

    private val NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val KATTAI = listOf("1", "1½", "2", "2½", "3", "4", "4½", "5", "5½", "6", "6½", "7")
    private const val SEMITONES = 12
    private const val MIDI_OCTAVE_OFFSET = 1

    /** Note name with octave, e.g. "C♯3". */
    fun noteName(midi: Int): String = NAMES[midi.mod(SEMITONES)] + (midi.floorDiv(SEMITONES) - MIDI_OCTAVE_OFFSET)

    /** Kattai number for the pitch class, e.g. "1½" for C♯. */
    fun kattai(midi: Int): String = KATTAI[midi.mod(SEMITONES)]

    /** Frequency of Sa in Hz (equal-tempered keyboard pitch, A4 = 440 Hz). */
    fun saHz(midi: Int): Double = Pitch.midiToHz(midi)

    /** Fine-tune range in cents, to match a teacher's shruti box or a recording. */
    val FINE_TUNE_RANGE: IntRange = -50..50

    /** Frequency of Sa [cents] above (or below) the keyboard pitch [midi]. */
    fun saHz(midi: Int, cents: Int): Double = Pitch.midiToHz(midi + cents.coerceIn(FINE_TUNE_RANGE) / 100.0)

    /** " +12¢" / " −8¢" after a Sa label, or "" when not fine-tuned. */
    fun fineTuneSuffix(cents: Int): String = when {
        cents > 0 -> " +$cents¢"
        cents < 0 -> " −${-cents}¢"
        else -> ""
    }

    /** Which voices usually sing at [midi] as Sa (for grouping the Sa list). */
    fun voiceRange(midi: Int): VoiceRange = VoiceRange.entries.first { midi in it.midis }
}

/** Sa choices grouped by the voices that usually use them (docs/plan-shruti.md). */
enum class VoiceRange(val title: String, val midis: IntRange) {
    LOWER("Lower voices", 43..47),
    MEN("Most men", 48..52),
    WOMEN("Most women", 53..58),
    HIGHER("Higher voices", 59..60),
}
