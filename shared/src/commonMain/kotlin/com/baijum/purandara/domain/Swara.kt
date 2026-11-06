package com.baijum.purandara.domain

/**
 * The 16 Carnatic swara names over the 12 swarasthanas (pitch positions).
 *
 * Four pairs share a position (R2/G1, R3/G2, D2/N1, D3/N2): same pitch,
 * different name depending on the raga. [ratio] is the just-intonation
 * frequency ratio to Sa; [semitone] is the nearest equal-tempered position.
 */
enum class Swara(
    val symbol: String,
    val semitone: Int,
    val numerator: Int,
    val denominator: Int,
) {
    S("S", 0, 1, 1),
    R1("R1", 1, 16, 15),
    R2("R2", 2, 9, 8),
    G1("G1", 2, 9, 8),
    R3("R3", 3, 6, 5),
    G2("G2", 3, 6, 5),
    G3("G3", 4, 5, 4),
    M1("M1", 5, 4, 3),
    M2("M2", 6, 45, 32),
    P("P", 7, 3, 2),
    D1("D1", 8, 8, 5),
    D2("D2", 9, 5, 3),
    N1("N1", 9, 5, 3),
    D3("D3", 10, 9, 5),
    N2("N2", 10, 9, 5),
    N3("N3", 11, 15, 8),
    ;

    /** Frequency ratio to Sa within one octave, in [1, 2). */
    val ratio: Double get() = numerator.toDouble() / denominator
}

/** Sthayi (register) relative to the singer's middle octave. */
enum class Octave(val offset: Int) {
    MANDRA(-1),
    MADHYA(0),
    TARA(1),
}

/** A swara sung in a particular octave. */
data class Note(val swara: Swara, val octave: Octave = Octave.MADHYA) {
    /** Frequency in Hz for a given Sa (tonic) frequency, using just intonation. */
    fun frequency(saHz: Double): Double {
        require(saHz > 0.0) { "Sa frequency must be positive, was $saHz" }
        val octaveFactor = when (octave) {
            Octave.MANDRA -> 0.5
            Octave.MADHYA -> 1.0
            Octave.TARA -> 2.0
        }
        return saHz * swara.ratio * octaveFactor
    }
}
