package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter

/** A swara as written: a letter and its octave (`S′` is S in the tara octave). */
data class WrittenSwara(val letter: SwaraLetter, val octave: Octave = Octave.MADHYA) {
    override fun toString(): String = letter.symbol + if (octave == Octave.TARA) "′" else ""
}

/** One akshara of written notation. */
sealed interface Akshara {
    /** One or more swaras sharing the akshara evenly (`S`, `SR`, `SRGM`). */
    data class Swaras(val swaras: List<WrittenSwara>) : Akshara {
        init {
            require(swaras.isNotEmpty()) { "an akshara needs at least one swara" }
        }

        override fun toString(): String = swaras.joinToString("")
    }

    /** Karvai (`,`): the previous swara continues through this akshara. */
    data object Karvai : Akshara {
        override fun toString(): String = ","
    }
}

/** One avarta (tala cycle) of notation. */
data class Avarta(val aksharas: List<Akshara>) {
    override fun toString(): String = aksharas.joinToString(" ")
}

/** The graded exercise sets, in teaching order. */
enum class VarisaiSet(val title: String) {
    PLAIN_SCALE("Plain scale"),
    SARALI("Sarali Varisai"),
    MADHYA_STHAYI("Madhya Sthayi Varisai"),
    JANTA("Janta Varisai"),
    VAKRA_JANTA("Vakra Janta Varisai"),
    DHATTU("Dhattu Varisai"),
}

/** One numbered exercise. */
data class Varisai(val set: VarisaiSet, val number: Int, val avartas: List<Avarta>) {
    /** Stable identifier, e.g. `sarali-4`. */
    val id: String get() = "${set.name.lowercase().replace('_', '-')}-$number"

    /** Display title, e.g. "Sarali Varisai 4". */
    val title: String get() = if (set == VarisaiSet.PLAIN_SCALE) set.title else "${set.title} $number"

    /** Written aksharas across all avartas. */
    val aksharaCount: Int get() = avartas.sumOf { it.aksharas.size }
}
