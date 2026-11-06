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
 * chosen: in Mayamalavagowla, R is R1 and G is G3. A raga may leave letters
 * out (Malahari has no N); notation that uses them is rejected by the parser.
 * [arohanam] and [avarohanam] are shown for reference only: notation is never
 * checked against them, since compositions use phrases that cross them.
 */
class Raga(
    val name: String,
    private val swaraMap: Map<SwaraLetter, Swara>,
    val key: String = name.lowercase(),
    val arohanam: List<Note> = emptyList(),
    val avarohanam: List<Note> = emptyList(),
) {
    init {
        require(swaraMap[SwaraLetter.S] == Swara.S) { "$name: S must be Sa" }
        require(swaraMap[SwaraLetter.P] in setOf(null, Swara.P)) { "$name: P must be Pa" }
        require(swaraMap.size >= MIN_LETTERS) { "$name needs at least $MIN_LETTERS letters" }
    }

    /** The letters this raga uses, in scale order. */
    val letters: List<SwaraLetter> = SwaraLetter.entries.filter { it in swaraMap }

    /** The raga's swaras, in scale order. */
    val swaras: List<Swara> = letters.map { swaraMap.getValue(it) }

    fun uses(letter: SwaraLetter): Boolean = letter in swaraMap

    /** The swara [letter] stands for; [letter] must be one the raga [uses]. */
    fun swara(letter: SwaraLetter): Swara =
        swaraMap[letter] ?: throw IllegalArgumentException("$name has no ${letter.symbol}")

    override fun toString(): String = name

    companion object {
        private const val MIN_LETTERS = 5

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
            arohanam = scale(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3),
            avarohanam = scale(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3).reversed(),
        )

        /**
         * Janya of Mayamalavagowla, the raga of the first gitams: ascent
         * S R1 M1 P D1 S′, descent S′ D1 P M1 G3 R1 S (no N).
         */
        val MALAHARI: Raga = Raga(
            "Malahari",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R1,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D1,
            ),
            arohanam = scale(Swara.S, Swara.R1, Swara.M1, Swara.P, Swara.D1),
            avarohanam = scale(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1).reversed(),
        )

        /** Janya of Shankarabharanam (29): S R2 M1 P D2 S′ both ways (no G, no N). */
        val SUDDHA_SAVERI: Raga = Raga(
            "Suddha Saveri",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
            ),
            key = "suddha-saveri",
            arohanam = scale(Swara.S, Swara.R2, Swara.M1, Swara.P, Swara.D2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.M1, Swara.P, Swara.D2).reversed(),
        )

        /** Janya of Harikambhoji (28): S R2 G3 P D2 S′ both ways (no M, no N). */
        val MOHANAM: Raga = Raga(
            "Mohanam",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.P, Swara.D2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.P, Swara.D2).reversed(),
        )

        /** Janya of Shankarabharanam (29): ascent S R2 G3 P D2 S′, descent S′ N3 D2 P M1 G3 R2 S. */
        val BILAHARI: Raga = Raga(
            "Bilahari",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N3,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.P, Swara.D2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N3).reversed(),
        )

        /** [swaras] in the middle octave, then upper Sa. */
        private fun scale(vararg swaras: Swara): List<Note> =
            swaras.map { Note(it) } + Note(Swara.S, Octave.TARA)
    }
}

/** The ragas notation can name (`raga: malahari`). */
object Ragas {
    val all: List<Raga> = listOf(Raga.MAYAMALAVAGOWLA, Raga.MALAHARI, Raga.SUDDHA_SAVERI, Raga.MOHANAM, Raga.BILAHARI)

    fun byKey(key: String): Raga? = all.firstOrNull { it.key == key }
}
