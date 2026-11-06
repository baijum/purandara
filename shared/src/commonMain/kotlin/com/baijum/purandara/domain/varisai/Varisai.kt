package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala

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

    /**
     * Text for screen readers: angas separated by commas, a karvai spoken as
     * [karvaiWord] (localised by the caller), e.g. "S R G M, P hold, S R".
     */
    fun spoken(tala: Tala, karvaiWord: String): String =
        aksharas.mapIndexed { i, a ->
            val word = if (a == Akshara.Karvai) karvaiWord else a.toString()
            when {
                i == 0 -> word
                tala.beats[i].startsAnga -> ", $word"
                else -> " $word"
            }
        }.joinToString("")
}

/** The graded exercise sets, in teaching order, with what each trains (from the workbook). */
enum class VarisaiSet(val title: String, val description: String) {
    PLAIN_SCALE("Plain scale", "The seven swaras up and down: settle each one against the tanpura."),
    SARALI("Sarali Varisai", "A short figure set against the plain ascent and descent: steady pitch, even timing."),
    MADHYA_STHAYI("Madhya Sthayi Varisai", "Longer figures across the octave: later, teacher-guided studies."),
    JANTA("Janta Varisai", "Each swara sung as a pair: clean, even repeated notes."),
    VAKRA_JANTA("Vakra Janta Varisai", "Paired notes in zigzags: find the next note without sliding."),
    DHATTU("Dhattu Varisai", "Leaps over a note and back: reach a distant pitch without sliding."),
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
