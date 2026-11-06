package com.baijum.purandara.domain

/** The hand action that marks an akshara. */
enum class Kriya {
    /** Clap (written `x`). */
    CLAP,

    /** Finger count after the clap in a laghu (written 1, 2, 3...). */
    COUNT,

    /** Wave of the hand (written `v`). */
    WAVE,
}

/** One akshara of a tala cycle. [label] is how it is written above the notation. */
data class TalaBeat(val kriya: Kriya, val label: String, val startsAnga: Boolean)

/** The kinds of anga a suladi tala is built from. */
enum class AngaKind {
    /** One clap, then finger counts: as long as the jati (I). */
    LAGHU,

    /** A clap and a wave (O). */
    DRUTAM,

    /** A single clap (U). */
    ANUDRUTAM,
}

/** Jati: the length of a laghu. */
enum class Jati(val displayName: String, val count: Int) {
    TISRA("Tisra", 3),
    CHATURASRA("Chaturasra", 4),
    KHANDA("Khanda", 5),
    MISRA("Misra", 7),
    SANKEERNA("Sankeerna", 9),
}

/**
 * A tala: the beats of one avarta (cycle) and how they group into angas.
 *
 * [key] identifies it in notation headers (`tala: dhruva-4`); [jati] is set
 * for the suladi talas built by [suladi].
 */
class Tala(
    val name: String,
    val beats: List<TalaBeat>,
    val key: String = name.lowercase(),
    val jati: Jati? = null,
) {
    init {
        require(beats.isNotEmpty()) { "$name has no beats" }
        require(beats.first().startsAnga) { "$name must start an anga on its first beat" }
    }

    /** Aksharas per avarta. */
    val aksharas: Int get() = beats.size

    /** Anga lengths, e.g. [4, 2, 2] for Adi tala. */
    val angas: List<Int>
        get() {
            val starts = beats.indices.filter { beats[it].startsAnga } + beats.size
            return starts.zipWithNext { a, b -> b - a }
        }

    override fun toString(): String = name

    companion object {
        /**
         * A suladi tala: [shape] in angas, with laghus as long as [jati].
         * A laghu is written `x 1 2 …`, a drutam `x v`, an anudrutam `x`.
         * Ported from the author's ThaaLam app (with Dhruva corrected to I O I I).
         */
        fun suladi(key: String, name: String, shape: List<AngaKind>, jati: Jati): Tala = Tala(
            name = name,
            beats = shape.flatMap { kind ->
                when (kind) {
                    AngaKind.LAGHU -> listOf(TalaBeat(Kriya.CLAP, "x", startsAnga = true)) +
                        (1 until jati.count).map { TalaBeat(Kriya.COUNT, it.toString(), startsAnga = false) }
                    AngaKind.DRUTAM -> listOf(
                        TalaBeat(Kriya.CLAP, "x", startsAnga = true),
                        TalaBeat(Kriya.WAVE, "v", startsAnga = false),
                    )
                    AngaKind.ANUDRUTAM -> listOf(TalaBeat(Kriya.CLAP, "x", startsAnga = true))
                }
            },
            key = key,
            jati = jati,
        )

        /** Adi tala: a four-akshara laghu then two drutas — x 1 2 3 | x v | x v. */
        val ADI: Tala = Tala(
            "Adi",
            listOf(
                TalaBeat(Kriya.CLAP, "x", startsAnga = true),
                TalaBeat(Kriya.COUNT, "1", startsAnga = false),
                TalaBeat(Kriya.COUNT, "2", startsAnga = false),
                TalaBeat(Kriya.COUNT, "3", startsAnga = false),
                TalaBeat(Kriya.CLAP, "x", startsAnga = true),
                TalaBeat(Kriya.WAVE, "v", startsAnga = false),
                TalaBeat(Kriya.CLAP, "x", startsAnga = true),
                TalaBeat(Kriya.WAVE, "v", startsAnga = false),
            ),
            key = "adi",
            jati = Jati.CHATURASRA,
        )
    }
}

/**
 * The talas notation can name. Adi is Chaturasra Triputa; the others are the
 * jatis in which the seven sapta tala alankarams are sung.
 */
object Talas {
    private val L = AngaKind.LAGHU
    private val D = AngaKind.DRUTAM
    private val U = AngaKind.ANUDRUTAM

    val ADI: Tala = Tala.ADI
    val DHRUVA: Tala = Tala.suladi("dhruva-4", "Dhruva", listOf(L, D, L, L), Jati.CHATURASRA)
    val MATYA: Tala = Tala.suladi("matya-4", "Matya", listOf(L, D, L), Jati.CHATURASRA)
    val RUPAKA: Tala = Tala.suladi("rupaka-4", "Rupaka", listOf(D, L), Jati.CHATURASRA)
    val JHAMPA: Tala = Tala.suladi("jhampa-7", "Jhampa", listOf(L, U, D), Jati.MISRA)
    val TRIPUTA_TISRA: Tala = Tala.suladi("triputa-3", "Tisra Triputa", listOf(L, D, D), Jati.TISRA)
    val ATA: Tala = Tala.suladi("ata-5", "Ata", listOf(L, L, D, D), Jati.KHANDA)
    val EKA: Tala = Tala.suladi("eka-4", "Eka", listOf(L), Jati.CHATURASRA)

    val all: List<Tala> = listOf(ADI, DHRUVA, MATYA, RUPAKA, JHAMPA, TRIPUTA_TISRA, ATA, EKA)

    fun byKey(key: String): Tala? = all.firstOrNull { it.key == key }
}
