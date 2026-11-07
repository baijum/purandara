package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.Talas

/** A notation error, with a 1-based [line] and [column] in the source text. */
class VarisaiParseException(val line: Int, val column: Int, detail: String) :
    IllegalArgumentException("line $line, column $column: $detail")

/**
 * Parses exercise notation (grammar: docs/plan.md, section 3.2).
 *
 * ```
 * 4
 * S R G M | P , | S R || S R G M | P D | N S′
 * ```
 *
 * A number on its own line starts an exercise. Header lines may follow it:
 * `tala: <key>` ([Talas]), `raga: <key>` ([Ragas]), `title: <text>` and
 * `short: <text>` (a shorter title for the top bar).
 * Gitams add sections and words: `[pallavi]` starts a named section,
 * `(pallavi)` sings that section again (expanded here, so playback and
 * scoring follow the sung order), and a line starting with `"` gives the
 * words for the notation line above it, one token per akshara.
 * A section or a repeat may carry a playback speed — `[charanam @2]` or
 * `(pallavi @2)`: its avartas play speed-times as fast while the tala keeps
 * its beat, so a @2 section packs speed written avartas into one tala cycle
 * (its avarta count must fill whole cycles; a varnam's purvanga is sung @1
 * then repeated @2, its uttaranga written @2). A repeat without its own @
 * keeps the section's speed.
 * `beat: <n>` writes n aksharas (cells) to each tala beat, as swarajatis are
 * written; `[charanam 1 +swaram]` sings that section first as swaras, then
 * with its words. A `beat:` line inside a piece (after notation has started)
 * overrides the gati from there on — a khanda-gati charanam writes
 * `beat: 5` — and the next `[section]` header restores the piece's own beat.
 * `kalai: 2` counts each tala beat twice (a varnam's two-kalai Adi): the
 * tala strip and the click follow the counts.
 * Each whitespace-separated token is one akshara; `||` ends an avarta (a
 * closing `||` at the end of a line is optional); `|` is layout only. `′` after a letter is the upper octave, `ˌ` (U+02CC) the
 * lower. `_` is a rest: silence, unlike a karvai's held sound (a passage that
 * starts after the beat writes rests until its first swara). The parser never
 * guesses: unknown characters, letters the raga
 * doesn't use, avartas of the wrong length, and a leading karvai or rest are errors.
 */
class VarisaiParser(
    private val defaultTala: Tala = Tala.ADI,
    private val defaultRaga: Raga = Raga.MAYAMALAVAGOWLA,
) {

    /** The exercise being read. */
    private class Draft(val number: Int, val line: Int, var tala: Tala, var raga: Raga) {
        var name: String? = null

        /** The active gati: [pieceBeat], or a section's `beat:` override. */
        var beat: Int = 1

        /** The piece's own gati (`beat:` before any notation); a section header restores it. */
        var pieceBeat: Int = 1

        /** Counts per tala beat (`kalai: 2` for a varnam's two-kalai Adi). */
        var kalai: Int = 1
        var shortName: String? = null
        val avartas = mutableListOf<Avarta>()

        /** Avartas of the last notation line, until its words are given. */
        var lastLine: IntRange? = null

        /** The open section's key and first avarta, and the label for its first avarta. */
        var openSection: Pair<String, Int>? = null
        var openSwaramFirst = false
        var openLine: Int = 0

        /** The open section's playback speed (`[charanam @2]`): its avartas play speed-times as fast. */
        var openSpeed: Int = 1
        var pendingLabel: String? = null
        val sections = mutableMapOf<String, IntRange>()

        fun closeSection() {
            val (key, start) = openSection ?: return
            val range = start until avartas.size
            if (openSpeed > 1) {
                if (range.count() % openSpeed != 0) {
                    throw VarisaiParseException(
                        openLine,
                        2,
                        "section [$key] @${openSpeed} needs its ${range.count()} avartas to fill whole cycles",
                    )
                }
                for (i in range) avartas[i] = avartas[i].copy(speed = openSpeed)
            }
            if (openSwaramFirst && start < avartas.size) {
                // Sung first as swaras (no words), then with the words.
                val withWords = avartas.subList(start, avartas.size).toList()
                val label = withWords.first().section
                val swaram = withWords.mapIndexed { k, a -> a.copy(words = null, section = if (k == 0) "$label · swaram" else null) }
                avartas.subList(start, avartas.size).clear()
                avartas += swaram
                avartas += withWords.mapIndexed { k, a -> if (k == 0) a.copy(section = "$label · sahityam") else a }
                sections[key] = start + swaram.size until avartas.size
            } else {
                sections[key] = start until avartas.size
            }
            openSection = null
            openSwaramFirst = false
            openSpeed = 1
        }
    }

    fun parse(text: String, set: VarisaiSet): List<Varisai> {
        val result = mutableListOf<Varisai>()
        var draft: Draft? = null

        fun finish() {
            val d = draft ?: return
            d.closeSection()
            if (d.avartas.isEmpty()) throw VarisaiParseException(d.line, 1, "exercise ${d.number} has no notation")
            result += Varisai(set, d.number, d.avartas.toList(), d.tala, d.raga, d.name, d.pieceBeat, d.shortName, d.kalai)
        }

        text.lines().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val line = raw.trim()
            val header = HEADER.matchEntire(line)
            val section = SECTION.matchEntire(line)
            val repeat = REPEAT.matchEntire(line)
            when {
                line.isEmpty() -> Unit
                section != null || repeat != null || line.startsWith(WORDS_MARK) -> {
                    val d = draft ?: throw VarisaiParseException(lineNo, 1, "notation before the first exercise number")
                    when {
                        section != null -> startSection(d, section.groupValues[1].trim(), lineNo)
                        repeat != null -> repeatSection(d, repeat.groupValues[1].trim(), lineNo)
                        else -> words(d, raw, lineNo)
                    }
                    return@forEachIndexed
                }
                line.all { it.isDigit() } -> {
                    finish()
                    draft = Draft(line.toInt(), lineNo, defaultTala, defaultRaga)
                }
                header != null -> {
                    val d = draft ?: throw VarisaiParseException(lineNo, 1, "header before the first exercise number")
                    // `beat:` may also appear inside a piece, to override a section's gati.
                    if (d.avartas.isNotEmpty() && header.groupValues[1] != "beat") {
                        throw VarisaiParseException(lineNo, 1, "header after notation")
                    }
                    header(d, header.groupValues[1], header.groupValues[2].trim(), lineNo)
                }
                else -> {
                    val d = draft ?: throw VarisaiParseException(lineNo, 1, "notation before the first exercise number")
                    val line = parseLine(raw, lineNo, d, isFirstLine = d.avartas.isEmpty()).toMutableList()
                    d.pendingLabel?.let { label -> line[0] = line[0].copy(section = label) }
                    d.pendingLabel = null
                    d.lastLine = d.avartas.size until d.avartas.size + line.size
                    d.avartas += line
                }
            }
        }
        finish()
        return result
    }

    private fun startSection(d: Draft, spec: String, lineNo: Int) {
        val swaramFirst = spec.endsWith(SWARAM_FIRST)
        val rest = spec.removeSuffix(SWARAM_FIRST).trim()
        val (key, speed) = speedSpec(rest, lineNo)
        if (key in d.sections || d.openSection?.first == key) {
            throw VarisaiParseException(lineNo, 2, "section [$key] appears twice")
        }
        d.closeSection()
        d.beat = d.pieceBeat // a section's `beat:` override ends with the section
        d.openSection = key to d.avartas.size
        d.openSwaramFirst = swaramFirst
        d.openSpeed = speed
        d.openLine = lineNo
        d.pendingLabel = label(key)
        d.lastLine = null
    }

    private fun repeatSection(d: Draft, spec: String, lineNo: Int) {
        d.closeSection()
        val (key, stated) = speedSpec(spec, lineNo)
        val range = d.sections[key] ?: throw VarisaiParseException(lineNo, 2, "no section [$key] to repeat")
        if (range.isEmpty()) throw VarisaiParseException(lineNo, 2, "section [$key] has no notation")
        // A repeat without its own @ keeps the section's speed: `(charanam)`
        // after a `[charanam @2]` sings it at @2 again.
        val speed = stated ?: d.avartas[range.first].speed
        requireWholeCycles(key, range, speed, lineNo)
        range.forEachIndexed { k, i ->
            d.avartas += d.avartas[i].copy(section = if (k == 0) label(key) else null, speed = speed)
        }
        d.pendingLabel = null
        d.lastLine = null
    }

    /** `"charanam 1 @2"` → key "charanam 1" and speed 2; `"charanam 1"` → speed 1. */
    private fun speedSpec(spec: String, lineNo: Int): Pair<String, Int> {
        val match = SPEED.matchEntire(spec) ?: throw VarisaiParseException(lineNo, 2, "bad section \"[$spec]\"")
        val key = match.groupValues[1].trim()
        val speed = match.groupValues[2].takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull()?.takeIf { n -> n in 1..4 }
                ?: throw VarisaiParseException(lineNo, spec.indexOf('@') + 3, "speed must be 1..4")
        } ?: 1
        return key to speed
    }

    /** A section at @s packs s written avartas into one tala cycle; a partial cycle can't be played. */
    private fun requireWholeCycles(key: String, range: IntRange, speed: Int, lineNo: Int) {
        if (speed > 1 && range.count() % speed != 0) {
            throw VarisaiParseException(
                lineNo,
                2,
                "section [$key] @${speed} needs its ${range.count()} avartas to fill whole cycles",
            )
        }
    }

    private fun words(d: Draft, raw: String, lineNo: Int) {
        val target = d.lastLine ?: throw VarisaiParseException(lineNo, 1, "words must follow a line of notation")
        val start = raw.indexOf(WORDS_MARK) + 1
        val tokens = tokens(raw.substring(start).replace("||", " "), start)
        val needed = target.sumOf { d.avartas[it].aksharas.size }
        if (tokens.size != needed) {
            throw VarisaiParseException(lineNo, start + 1, "${tokens.size} words for $needed aksharas")
        }
        var next = 0
        for (i in target) {
            val n = d.avartas[i].aksharas.size
            d.avartas[i] = d.avartas[i].copy(words = tokens.subList(next, next + n).map { it.second })
            next += n
        }
        d.lastLine = null
    }

    /** "charanam 1" → "Charanam 1". */
    private fun label(key: String): String = key.replaceFirstChar { it.uppercaseChar() }

    private fun header(d: Draft, field: String, value: String, lineNo: Int) {
        val column = field.length + 2
        when (field) {
            "tala" -> d.tala = Talas.byKey(value) ?: throw VarisaiParseException(lineNo, column, "unknown tala \"$value\"")
            "raga" -> d.raga = Ragas.byKey(value) ?: throw VarisaiParseException(lineNo, column, "unknown raga \"$value\"")
            "title" -> d.name = value
            "short" -> d.shortName = value
            "beat" -> {
                val value = value.toIntOrNull()?.takeIf { it in BEATS }
                    ?: throw VarisaiParseException(lineNo, column, "beat must be one of $BEATS")
                d.beat = value
                if (d.avartas.isEmpty()) d.pieceBeat = value
            }
            "kalai" -> {
                val value = value.toIntOrNull()?.takeIf { it in 1..2 }
                    ?: throw VarisaiParseException(lineNo, column, "kalai must be 1 or 2")
                d.kalai = value
            }
        }
    }

    private fun parseLine(raw: String, lineNo: Int, d: Draft, isFirstLine: Boolean): List<Avarta> {
        val avartas = mutableListOf<Avarta>()
        var segmentStart = 0
        val segments = mutableListOf<Pair<Int, String>>()
        var i = raw.indexOf("||")
        while (i >= 0) {
            segments += segmentStart to raw.substring(segmentStart, i)
            segmentStart = i + 2
            i = raw.indexOf("||", segmentStart)
        }
        // A closing `||` at the end of the line, as books write it, leaves nothing after it.
        if (segments.isEmpty() || raw.substring(segmentStart).isNotBlank()) segments += segmentStart to raw.substring(segmentStart)

        for ((offset, segment) in segments) {
            val aksharas = mutableListOf<Akshara>()
            for ((column, token) in tokens(segment, offset)) {
                val akshara = parseToken(token, lineNo, column, d.raga)
                if (isFirstLine && avartas.isEmpty() && aksharas.isEmpty() &&
                    (akshara == Akshara.Karvai || akshara == Akshara.Rest)
                ) {
                    val what = if (akshara == Akshara.Karvai) "a karvai" else "a rest"
                    throw VarisaiParseException(lineNo, column, "an exercise cannot start with $what")
                }
                aksharas += akshara
            }
            val needed = d.tala.aksharas * d.beat
            if (aksharas.size != needed) {
                throw VarisaiParseException(
                    lineNo,
                    offset + 1,
                    "avarta has ${aksharas.size} aksharas; ${d.tala.name} tala needs $needed",
                )
            }
            avartas += Avarta(aksharas)
        }
        return avartas
    }

    /** Whitespace-separated tokens with their 1-based columns, skipping `|`. */
    private fun tokens(segment: String, offset: Int): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var start = -1
        for (j in 0..segment.length) {
            val c = segment.getOrNull(j)
            val separator = c == null || c.isWhitespace() || c == '|'
            if (separator && start >= 0) {
                out += (offset + start + 1) to segment.substring(start, j)
                start = -1
            } else if (!separator && start < 0) {
                start = j
            }
        }
        return out
    }

    private fun parseToken(token: String, lineNo: Int, column: Int, raga: Raga): Akshara {
        if (token == ",") return Akshara.Karvai
        if (token == "_") return Akshara.Rest
        val swaras = mutableListOf<WrittenSwara>()
        token.forEachIndexed { k, c ->
            val octave = when (c) {
                TARA_MARK -> Octave.TARA
                MANDRA_MARK -> Octave.MANDRA
                else -> null
            }
            if (octave != null) {
                val last = swaras.lastOrNull()
                    ?: throw VarisaiParseException(lineNo, column + k, "octave mark without a swara")
                if (last.octave != Octave.MADHYA) {
                    throw VarisaiParseException(lineNo, column + k, "repeated octave mark")
                }
                swaras[swaras.lastIndex] = last.copy(octave = octave)
            } else if (c.isDigit()) {
                // A variant digit (D1, N3): the raga decides which letters take which.
                val last = swaras.lastOrNull()
                    ?: throw VarisaiParseException(lineNo, column + k, "variant digit without a swara")
                if (last.variant != null) {
                    throw VarisaiParseException(lineNo, column + k, "repeated variant digit")
                }
                val variant = Swara.entries.firstOrNull {
                    it.name == "${last.letter.symbol}$c"
                } ?: throw VarisaiParseException(lineNo, column + k, "no swara ${last.letter.symbol}$c")
                if (!raga.allows(last.letter, variant)) {
                    throw VarisaiParseException(lineNo, column + k, "${raga.name} does not allow ${last.letter.symbol}$c")
                }
                swaras[swaras.lastIndex] = last.copy(variant = variant)
            } else {
                val letter = SwaraLetter.fromSymbol(c)
                    ?: throw VarisaiParseException(lineNo, column + k, "unexpected character '$c' in \"$token\"")
                if (!raga.uses(letter)) {
                    throw VarisaiParseException(lineNo, column + k, "${raga.name} has no ${letter.symbol}")
                }
                swaras += WrittenSwara(letter)
            }
        }
        return Akshara.Swaras(swaras)
    }

    private companion object {
        /** U+2032 PRIME, the tara (upper octave) mark. */
        const val TARA_MARK = '′'

        /** U+02CC MODIFIER LETTER LOW VERTICAL LINE, the mandra (lower octave) mark; shown as a dot below. */
        const val MANDRA_MARK = 'ˌ'

        val HEADER = Regex("""(tala|raga|title|short|beat|kalai):(.*)""")
        val BEATS = listOf(1, 2, 4, 5)
        const val SWARAM_FIRST = "+swaram"
        val SECTION = Regex("""\[([^\]]+)]""")
        val REPEAT = Regex("""\(([^)]+)\)""")

        /** A section key with an optional playback speed: "pallavi" or "pallavi @2". */
        val SPEED = Regex("""([^@]+?)(?:\s*@(\d+))?${'$'}""")

        /** Starts a line of words (sahityam). */
        const val WORDS_MARK = '"'

    }
}
