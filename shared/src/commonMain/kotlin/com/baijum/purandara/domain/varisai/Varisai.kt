package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.Tuning

/**
 * A swara as written: a letter, its octave, and optionally the [variant]
 * the raga's default for the letter is overridden with (a digit after the
 * letter, e.g. `D1` in a Bhairavi gitam). `S′` is S in the tara octave;
 * `Ḍ` (a dot below) is D in the mandra octave.
 */
data class WrittenSwara(
    val letter: SwaraLetter,
    val octave: Octave = Octave.MADHYA,
    val variant: Swara? = null,
) {
    /** The letter with its octave mark, without the variant digit. */
    val baseText: String get() = letter.symbol + when (octave) {
        Octave.TARA -> "′"
        Octave.MANDRA -> "\u0323"
        Octave.MADHYA -> ""
    }

    /** The variant digit as written ("1" of `D1`), or null without a variant. */
    val variantDigit: String? get() = variant?.name?.removePrefix(letter.name)

    override fun toString(): String = letter.symbol + (variantDigit ?: "") + when (octave) {
        Octave.TARA -> "′"
        Octave.MANDRA -> "\u0323"
        Octave.MADHYA -> ""
    }

    /** For screen readers: a dot below is not read aloud, so mandra is "low D". */
    fun spoken(): String = if (octave == Octave.MANDRA) "low ${letter.symbol}" else toString()

    /** The pitch class this written swara sounds: its [variant], or the raga's default for the letter. */
    fun swaraIn(raga: Raga): Swara = variant ?: raga.swara(letter)
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

    /** A rest (`_`): silence — nothing sounds, and nothing is held through this akshara. */
    data object Rest : Akshara {
        override fun toString(): String = "_"
    }
}

/**
 * One avarta (tala cycle) of notation. In a gitam, [section] names the part
 * this avarta starts ("Pallavi", "Charanam 1"; null when it continues one),
 * and [words] holds the sahityam: one syllable per akshara, `-` where the
 * previous syllable is held, two joined by `·` when an akshara has two swaras.
 * [speed] is the playback speed of this sung avarta (a `(pallavi @2)` repeat
 * copies carry 2): the cells play twice as fast while the tala keeps its beat.
 */
data class Avarta(
    val aksharas: List<Akshara>,
    val section: String? = null,
    val words: List<String>? = null,
    val speed: Int = 1,
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
    fun spoken(tala: Tala, karvaiWord: String): String {
        // Written several to a beat (a swarajati), holds inside a beat aren't read out.
        val perBeat = maxOf(1, aksharas.size / tala.aksharas)
        return aksharas.mapIndexedNotNull { i, a ->
            val word = when (a) {
                Akshara.Karvai -> if (perBeat > 1) null else karvaiWord
                Akshara.Rest -> null
                is Akshara.Swaras -> a.spoken()
            }
            word?.let { i to it }
        }.mapIndexed { n, (i, word) ->
            when {
                n == 0 -> word
                tala.startsAngaAt(i, perBeat) -> ", $word"
                else -> " $word"
            }
        }.joinToString("")
    }
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

/** One section of a piece: its heading and its first and last avarta (inclusive). */
data class SectionSpan(val label: String, val firstAvarta: Int, val lastAvarta: Int)

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
    GITAM("Gitam", "First songs, with words: Purandara Dasa's gitams in raga Malahari, then others."),
    SWARAJATI("Swarajati", "Longer songs with several swaras to a beat: each charanam sung as swaras, then with words."),
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
    /**
     * Written aksharas (notation cells) per tala beat: 1, 2 or 4, or 5 for a
     * khanda-gati section (`beat:`). A `beat:` line inside a piece overrides
     * a section's gati, so [beat] is the piece's default — [cellsPerBeatIn]
     * gives each avarta's own.
     */
    val beat: Int = 1,
    /** A shorter [name] for the top bar (`short:`), e.g. "Rara Venu". */
    val shortName: String? = null,
    /**
     * Counts per tala beat (`kalai: 2`): a varnam's Adi tala is counted in
     * two kalai, 16 counts to the cycle. The tala strip and the click follow
     * these counts ([Tala.countsAt]); the notation itself stays written
     * [beat] cells to a beat.
     */
    val kalai: Int = 1,
) {
    /** Written aksharas per tala beat in [avarta]: the piece's [beat], or a section's override. */
    fun cellsPerBeatIn(avarta: Int): Int = avartas[avarta].aksharas.size / tala.aksharas

    /** Where avarta [avarta]'s notation starts a new row (cells), and the cells in its longest row. */
    fun rowStartsIn(avarta: Int): List<Int> = tala.rowStartsFor(cellsPerBeatIn(avarta))
    fun rowCellsIn(avarta: Int): Int = tala.rowCellsFor(cellsPerBeatIn(avarta))

    /** Whether notation cell [cell] of avarta [avarta] starts an anga (an anga bar goes before it). */
    fun startsAnga(avarta: Int, cell: Int): Boolean = tala.startsAngaAt(cell, cellsPerBeatIn(avarta))

    /**
     * Title short enough for a phone's top bar, e.g. "Alankaram 1" or
     * "Vakra Janta 2" (the screen shows the tala and raga separately).
     */
    val shortTitle: String
        get() = shortName ?: name ?: when (set) {
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

    /**
     * The Sa choices (MIDI, within [Shruti.SA_RANGE]) at which every note of
     * this piece is inside the harmonium samples, for any fine-tune and
     * either tuning. Every varisai and alankaram allows the whole range; a
     * gitam that climbs to upper Ma does not allow the highest Sa.
     */
    val playableSaMidis: IntRange by lazy {
        val notes = avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
            .map { Note(it.swaraIn(raga), it.octave) }.distinct()
        val ok = Shruti.SA_RANGE.filter { midi ->
            Shruti.FINE_TUNE_RANGE.let { listOf(it.first, it.last) }.all { cents ->
                val sa = Shruti.saHz(midi, cents)
                Tuning.entries.all { tuning -> notes.all { HarmoniumBank.playbackFor(tuning.frequency(it, sa)) != null } }
            }
        }
        if (ok.isEmpty()) IntRange.EMPTY else ok.first()..ok.last()
    }

    /**
     * The piece's sections in sung order, each by its first appearance (a
     * returning pallavi is listed once): what "practise one section" offers.
     * Empty for exercises without sections.
     */
    val sections: List<SectionSpan>
        get() {
            val starts = avartas.indices.filter { avartas[it].section != null }
            val seen = mutableSetOf<String>()
            return starts.mapIndexedNotNull { k, start ->
                val label = avartas[start].section!!
                val end = (starts.getOrNull(k + 1) ?: avartas.size) - 1
                if (seen.add(label)) SectionSpan(label, start, end) else null
            }
        }

    /** The playable Sa nearest to [saMidi] (itself when it is playable). */
    fun nearestPlayableSa(saMidi: Int): Int = saMidi.coerceIn(playableSaMidis)

    /** Written aksharas across all avartas. */
    val aksharaCount: Int get() = avartas.sumOf { it.aksharas.size }
}
