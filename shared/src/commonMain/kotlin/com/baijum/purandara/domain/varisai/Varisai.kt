package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala

/**
 * A swara as written: a letter and its octave. `S′` is S in the tara
 * octave; `Ḍ` (a dot below) is D in the mandra octave.
 */
data class WrittenSwara(val letter: SwaraLetter, val octave: Octave = Octave.MADHYA) {
    override fun toString(): String = letter.symbol + when (octave) {
        Octave.TARA -> "′"
        Octave.MANDRA -> "\u0323"
        Octave.MADHYA -> ""
    }

    /** For screen readers: a dot below is not read aloud, so mandra is "low D". */
    fun spoken(): String = if (octave == Octave.MANDRA) "low ${letter.symbol}" else toString()
}

/** One akshara of written notation. */
sealed interface Akshara {
    /** One or more swaras sharing the akshara evenly (`S`, `SR`, `SRGM`). */
    data class Swaras(val swaras: List<WrittenSwara>) : Akshara {
        init {
            require(swaras.isNotEmpty()) { "an akshara needs at least one swara" }
        }

        override fun toString(): String = swaras.joinToString("")

        /** For screen readers; spaced out when a mandra swara is spoken as "low D". */
        fun spoken(): String =
            swaras.joinToString(if (swaras.any { it.octave == Octave.MANDRA }) " " else "") { it.spoken() }
    }

    /** Karvai (`,`): the previous swara continues through this akshara. */
    data object Karvai : Akshara {
        override fun toString(): String = ","
    }
}

/**
 * One avarta (tala cycle) of notation. In a gitam, [section] names the part
 * this avarta starts ("Pallavi", "Charanam 1"; null when it continues one),
 * and [words] holds the sahityam: one syllable per akshara, `-` where the
 * previous syllable is held, two joined by `·` when an akshara has two swaras.
 */
data class Avarta(
    val aksharas: List<Akshara>,
    val section: String? = null,
    val words: List<String>? = null,
) {
    init {
        require(words == null || words.size == aksharas.size) { "words must match the aksharas" }
    }

    override fun toString(): String = aksharas.joinToString(" ")

    /** The words to read aloud ("sri ga na na tha"), skipping held syllables; null without words. */
    fun spokenWords(): String? =
        words?.filter { it != HELD }?.joinToString(" ") { it.replace("·", " ") }

    /**
     * Text for screen readers: angas separated by commas, a karvai spoken as
     * [karvaiWord] (localised by the caller), e.g. "S R G M, P hold, S R".
     */
    fun spoken(tala: Tala, karvaiWord: String): String =
        aksharas.mapIndexed { i, a ->
            val word = when (a) {
                Akshara.Karvai -> karvaiWord
                is Akshara.Swaras -> a.spoken()
            }
            when {
                i == 0 -> word
                tala.beats[i].startsAnga -> ", $word"
                else -> " $word"
            }
        }.joinToString("")
}

/** What the notation of a piece with words (a gitam) shows: a practice setting. */
enum class WordsDisplay {
    SWARAS,
    WORDS,
    BOTH,
    ;

    val showsSwaras: Boolean get() = this != WORDS
    val showsWords: Boolean get() = this != SWARAS
}

/** In [Avarta.words]: the previous syllable is held through this akshara. */
const val HELD: String = "-"

/** The graded exercise sets, in teaching order, with what each trains (varisais: from the workbook). */
enum class VarisaiSet(val title: String, val description: String, val shortTitle: String = title) {
    PLAIN_SCALE("Plain scale", "The seven swaras up and down: settle each one against the tanpura."),
    SARALI("Sarali Varisai", "A short figure set against the plain ascent and descent: steady pitch, even timing."),
    MADHYA_STHAYI("Madhya Sthayi Varisai", "Longer figures across the octave: later, teacher-guided studies.", "Madhya Sthayi"),
    JANTA("Janta Varisai", "Each swara sung as a pair: clean, even repeated notes."),
    VAKRA_JANTA("Vakra Janta Varisai", "Paired notes in zigzags: find the next note without sliding.", "Vakra Janta"),
    DHATTU("Dhattu Varisai", "Leaps over a note and back: reach a distant pitch without sliding."),
    ALANKARAM("Alankaram", "One climbing figure in each of the seven suladi talas: keep the tala as you sing."),
    GITAM("Gitam", "First songs, with words: Purandara Dasa's gitams in raga Malahari."),
}

/**
 * One numbered exercise, in its [tala] and [raga]. [name] is the title of a
 * named piece (a gitam); exercises without one are titled by set and number.
 */
data class Varisai(
    val set: VarisaiSet,
    val number: Int,
    val avartas: List<Avarta>,
    val tala: Tala = Tala.ADI,
    val raga: Raga = Raga.MAYAMALAVAGOWLA,
    val name: String? = null,
) {
    /**
     * Title short enough for a phone's top bar, e.g. "Alankaram 1" or
     * "Vakra Janta 2" (the screen shows the tala and raga separately).
     */
    val shortTitle: String
        get() = name ?: when (set) {
            VarisaiSet.PLAIN_SCALE -> set.title
            else -> "${set.shortTitle} $number"
        }

    /** Stable identifier, e.g. `sarali-4`. */
    val id: String get() = "${set.name.lowercase().replace('_', '-')}-$number"

    /** Display title, e.g. "Sarali Varisai 4". */
    val title: String
        get() = name ?: when (set) {
            VarisaiSet.PLAIN_SCALE -> set.title
            VarisaiSet.ALANKARAM -> "${set.title} $number · ${tala.name} tala"
            else -> "${set.title} $number"
        }

    /** Written aksharas across all avartas. */
    val aksharaCount: Int get() = avartas.sumOf { it.aksharas.size }
}
