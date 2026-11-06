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

/**
 * A tala: the beats of one avarta (cycle) and how they group into angas.
 */
class Tala(val name: String, val beats: List<TalaBeat>) {
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
        )
    }
}
