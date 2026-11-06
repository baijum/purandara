package com.baijum.purandara.domain

/** The seven swara letters used in notation. The raga decides each one's variant. */
enum class SwaraLetter(val symbol: Char) {
    S('S'),
    R('R'),
    G('G'),
    M('M'),
    P('P'),
    D('D'),
    N('N'),
    ;

    companion object {
        fun fromSymbol(symbol: Char): SwaraLetter? = entries.firstOrNull { it.symbol == symbol }
    }
}

/**
 * A raga's scale: which [Swara] each written letter stands for.
 *
 * Compact notation such as `S R G M` only becomes pitches once a raga is
 * chosen: in Mayamalavagowla, R is R1 and G is G3.
 */
class Raga(val name: String, private val swaras: Map<SwaraLetter, Swara>) {
    init {
        require(swaras.keys == SwaraLetter.entries.toSet()) { "$name must define all seven letters" }
        require(swaras[SwaraLetter.S] == Swara.S) { "$name: S must be Sa" }
        require(swaras[SwaraLetter.P] == Swara.P) { "$name: P must be Pa" }
    }

    fun swara(letter: SwaraLetter): Swara = swaras.getValue(letter)

    override fun toString(): String = name

    companion object {
        /** 15th melakarta; the raga of Sarali Varisai and the other graded exercises. */
        val MAYAMALAVAGOWLA: Raga = Raga(
            "Mayamalavagowla",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R1,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D1,
                SwaraLetter.N to Swara.N3,
            ),
        )
    }
}
