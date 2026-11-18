package com.baijum.purandara.domain

import kotlin.concurrent.Volatile

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
 * [allowedVariants] lists the letters a piece may write with a digit (`D1`)
 * to override the raga's default for that letter; anything else is an error.
 * [arohanam] and [avarohanam] are shown for reference only: notation is never
 * checked against them, since compositions use phrases that cross them.
 */
class Raga(
    val name: String,
    private val swaraMap: Map<SwaraLetter, Swara>,
    val key: String = name.lowercase(),
    val arohanam: List<Note> = emptyList(),
    val avarohanam: List<Note> = emptyList(),
    val allowedVariants: Map<SwaraLetter, List<Swara>> = emptyMap(),
    /** The melakarta number (1–72) when the raga is one of the parent scales, or null. */
    val mela: Int? = null,
) {
    init {
        require(swaraMap[SwaraLetter.S] == Swara.S) { "$name: S must be Sa" }
        require(swaraMap[SwaraLetter.P] in setOf(null, Swara.P)) { "$name: P must be Pa" }
        require(swaraMap.size >= MIN_LETTERS) { "$name needs at least $MIN_LETTERS letters" }
        require(mela == null || (mela in MELA_RANGE && swaraMap.size == SwaraLetter.entries.size)) {
            "$name: a melakarta has all seven letters and a number in $MELA_RANGE"
        }
        allowedVariants.forEach { (letter, variants) ->
            require(letter in swaraMap) { "$name: cannot allow variants of ${letter.symbol}, which it does not use" }
            require(variants.all { it !in swaraMap.values || it == swaraMap[letter] }) {
                "$name: a variant of ${letter.symbol} must be a different swara than its default"
            }
        }
    }

    /** The letters this raga uses, in scale order. */
    val letters: List<SwaraLetter> = SwaraLetter.entries.filter { it in swaraMap }

    /** The raga's swaras, in scale order. */
    val swaras: List<Swara> = letters.map { swaraMap.getValue(it) }

    fun uses(letter: SwaraLetter): Boolean = letter in swaraMap

    /** The swara [letter] stands for; [letter] must be one the raga [uses]. */
    fun swara(letter: SwaraLetter): Swara =
        swaraMap[letter] ?: throw IllegalArgumentException("$name has no ${letter.symbol}")

    /** Whether a piece may write [variant] for [letter] (a digit after the letter). */
    fun allows(letter: SwaraLetter, variant: Swara): Boolean =
        allowedVariants[letter]?.contains(variant) == true

    override fun toString(): String = name

    companion object {
        private const val MIN_LETTERS = 5
        private val MELA_RANGE = 1..72

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
            mela = 15,
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

        /**
         * Janya of Harikambhoji (28): ascent S M1 G3 M1 P D2 N2 S′ (R left out,
         * M–G3 pairs), descent S′ N2 D2 P M1 G3 R2 S. The raga of the Sambasiva
         * swarajati.
         */
        val KHAMAS: Raga = Raga(
            "Khamas",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N2,
            ),
            arohanam = scale(Swara.S, Swara.M1, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N2).reversed(),
        )

        /** The 65th melakarta: S R2 G3 M2 P D2 N3 S′ — the raga of the Kamalajadala geetham. */
        val KALYANI: Raga = Raga(
            "Kalyani",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M2,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N3,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M2, Swara.P, Swara.D2, Swara.N3),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M2, Swara.P, Swara.D2, Swara.N3).reversed(),
            mela = 65,
        )

        /**
         * Janya of Shankarabharanam (29): ascent S R2 M1 P D2 S′ (no G, no N),
         * descent S′ N3 D2 P M1 G3 R2 S. The raga of the Re Re Sri Ramachandra
         * geetham. (Shivkumar.org's page prints the descent with N2/G2; the
         * standard Arabhi, per karnatik.com's raga page, takes N3/G3.)
         */
        val ARABHI: Raga = Raga(
            "Arabhi",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N3,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.M1, Swara.P, Swara.D2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N3).reversed(),
        )

        /**
         * Janya of Kharaharapriya (22): S R2 G2 M1 P D2 N2 S′. Pieces may write
         * its Dhaivata as D1 beside the default D2 (the Sri Ramachandra gitam
         * does); other variants are errors.
         */
        val BHAIRAVI: Raga = Raga(
            "Bhairavi",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G2,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N2,
            ),
            allowedVariants = mapOf(SwaraLetter.D to listOf(Swara.D1)),
            arohanam = scale(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.P, Swara.D2, Swara.N2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.P, Swara.D2, Swara.N2).reversed(),
        )

        /**
         * Janya of Natabhairavi (20): a vakra ascent S G2 R2 G2 M1 P D2 P N2 S′
         * (R after G, Pa twice), descent S′ N2 D2 P M1 G2 R2 S. The raga of the
         * Kamala Sulochana sanchari geetham.
         */
        val ANANDABHAIRAVI: Raga = Raga(
            "Anandabhairavi",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G2,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N2,
            ),
            arohanam = scale(Swara.S, Swara.G2, Swara.R2, Swara.G2, Swara.M1, Swara.P, Swara.D2, Swara.P, Swara.N2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.P, Swara.D2, Swara.N2).reversed(),
        )

        /**
         * Janya of Harikambhoji (28): ascent S R2 G2 M1 D2 S′ (no Pa, no Ni),
         * descent S′ D2 M1 G2 R2 S. The raga of the Evvari Bodhana varnam.
         * (Some singers take an anya N2 in the ascent; the chosen source's
         * notation has none.)
         */
        val ABHOGI: Raga = Raga(
            "Abhogi",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G2,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.D to Swara.D2,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.D2),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.D2).reversed(),
        )

        /** The 29th melakarta: S R2 G3 M1 P D2 N3 S′ — the raga of the Sami Ninne Kori varnam. */
        val SHANKARABHARANAM: Raga = Raga(
            "Shankarabharanam",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1,
                SwaraLetter.P to Swara.P,
                SwaraLetter.D to Swara.D2,
                SwaraLetter.N to Swara.N3,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N3),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N3).reversed(),
            mela = 29,
        )

        /** Janya of Shankarabharanam (29): ascent S R2 G3 P N3 S′ (no M, no D), descent S′ N3 P G3 R2 S. */
        val HAMSADHWANI: Raga = Raga(
            "Hamsadhwani",
            mapOf(
                SwaraLetter.S to Swara.S,
                SwaraLetter.R to Swara.R2,
                SwaraLetter.G to Swara.G3,
                SwaraLetter.P to Swara.P,
                SwaraLetter.N to Swara.N3,
            ),
            arohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.P, Swara.N3),
            avarohanam = scale(Swara.S, Swara.R2, Swara.G3, Swara.P, Swara.N3).reversed(),
        )

        /** 8th melakarta, Hanumatodi; known in concerts as Todi. */
        val TODI: Raga = melakarta("Todi", 8, Swara.R1, Swara.G2, Swara.M1, Swara.D1, Swara.N2)

        /** 20th melakarta, the parent of Bhairavi and Anandabhairavi. */
        val NATABHAIRAVI: Raga = melakarta("Natabhairavi", 20, Swara.R2, Swara.G2, Swara.M1, Swara.D1, Swara.N2)

        /** 21st melakarta. */
        val KEERAVANI: Raga = melakarta("Keeravani", 21, Swara.R2, Swara.G2, Swara.M1, Swara.D1, Swara.N3)

        /** 22nd melakarta. */
        val KHARAHARAPRIYA: Raga = melakarta("Kharaharapriya", 22, Swara.R2, Swara.G2, Swara.M1, Swara.D2, Swara.N2)

        /** 26th melakarta. */
        val CHARUKESI: Raga = melakarta("Charukesi", 26, Swara.R2, Swara.G3, Swara.M1, Swara.D1, Swara.N2)

        /** 28th melakarta, the parent of Mohanam and Khamas. */
        val HARIKAMBHOJI: Raga = melakarta("Harikambhoji", 28, Swara.R2, Swara.G3, Swara.M1, Swara.D2, Swara.N2)

        /** 51st melakarta, Kamavardhini; known in concerts as Pantuvarali. */
        val PANTUVARALI: Raga = melakarta("Pantuvarali", 51, Swara.R1, Swara.G3, Swara.M2, Swara.D1, Swara.N3)

        /** 56th melakarta. */
        val SHANMUKHAPRIYA: Raga = melakarta("Shanmukhapriya", 56, Swara.R2, Swara.G2, Swara.M2, Swara.D1, Swara.N2)

        /** 57th melakarta. */
        val SIMHENDRAMADHYAMAM: Raga = melakarta("Simhendramadhyamam", 57, Swara.R2, Swara.G2, Swara.M2, Swara.D1, Swara.N3)

        /** 58th melakarta. */
        val HEMAVATI: Raga = melakarta("Hemavati", 58, Swara.R2, Swara.G2, Swara.M2, Swara.D2, Swara.N2)

        /** 63rd melakarta. */
        val LATANGI: Raga = melakarta("Latangi", 63, Swara.R2, Swara.G3, Swara.M2, Swara.D1, Swara.N3)

        /** 64th melakarta. */
        val VACHASPATI: Raga = melakarta("Vachaspati", 64, Swara.R2, Swara.G3, Swara.M2, Swara.D2, Swara.N2)

        /** A melakarta: all seven swaras, the same up and down. */
        private fun melakarta(name: String, number: Int, r: Swara, g: Swara, m: Swara, d: Swara, n: Swara): Raga {
            val swaras = listOf(Swara.S, r, g, m, Swara.P, d, n)
            return Raga(
                name,
                SwaraLetter.entries.zip(swaras).toMap(),
                arohanam = scale(*swaras.toTypedArray()),
                avarohanam = scale(*swaras.toTypedArray()).reversed(),
                mela = number,
            )
        }

        /** [swaras] in the middle octave, then upper Sa. */
        private fun scale(vararg swaras: Swara): List<Note> =
            swaras.map { Note(it) } + Note(Swara.S, Octave.TARA)
    }
}

/**
 * The app's ragas ([all], the ones notation can name: `raga: malahari`) and
 * the student's own ([own], docs/plan-recording-insights.md), which the apps
 * set with [useOwn] when they load or change them.
 */
object Ragas {
    val all: List<Raga> = listOf(
        Raga.MAYAMALAVAGOWLA, Raga.MALAHARI, Raga.SUDDHA_SAVERI, Raga.MOHANAM,
        Raga.BILAHARI, Raga.KHAMAS, Raga.KALYANI, Raga.ARABHI, Raga.BHAIRAVI,
        Raga.ANANDABHAIRAVI, Raga.ABHOGI, Raga.SHANKARABHARANAM, Raga.HAMSADHWANI,
        Raga.NATABHAIRAVI, Raga.KEERAVANI, Raga.KHARAHARAPRIYA, Raga.HARIKAMBHOJI,
        Raga.TODI, Raga.CHARUKESI, Raga.PANTUVARALI, Raga.SHANMUKHAPRIYA, Raga.SIMHENDRAMADHYAMAM,
        Raga.HEMAVATI, Raga.LATANGI, Raga.VACHASPATI,
    )

    /**
     * The ragas the varisais and alankarams can be sung in
     * (docs/plan-other-ragas.md): Mayamalavagowla, the usual one, first, then
     * the others by melakarta number.
     */
    val melakartas: List<Raga> = listOf(
        Raga.MAYAMALAVAGOWLA, Raga.TODI, Raga.NATABHAIRAVI, Raga.KEERAVANI, Raga.KHARAHARAPRIYA,
        Raga.CHARUKESI, Raga.HARIKAMBHOJI, Raga.SHANKARABHARANAM, Raga.PANTUVARALI,
        Raga.SHANMUKHAPRIYA, Raga.SIMHENDRAMADHYAMAM, Raga.HEMAVATI, Raga.LATANGI,
        Raga.VACHASPATI, Raga.KALYANI,
    )

    /** One of [melakartas], by key. */
    fun melakarta(key: String): Raga? = melakartas.firstOrNull { it.key == key }

    /** The student's own ragas, by name. Replaced whole (read from analysis threads too). */
    @Volatile
    var own: List<Raga> = emptyList()
        private set

    fun useOwn(ragas: CustomRagas) {
        own = ragas.toRagas()
    }

    /** One of the app's ragas or the student's own. */
    fun byKey(key: String): Raga? = builtIn(key) ?: own.firstOrNull { it.key == key }

    /** One of the app's ragas only (what notation may name). */
    fun builtIn(key: String): Raga? = all.firstOrNull { it.key == key }
}
