package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tala

/** A notation error, with a 1-based [line] and [column] in the source text. */
class VarisaiParseException(val line: Int, val column: Int, detail: String) :
    IllegalArgumentException("line $line, column $column: $detail")

/**
 * Parses varisai notation (grammar: docs/plan.md, section 3.2).
 *
 * ```
 * 4
 * S R G M | P , | S R || S R G M | P D | N S′
 * ```
 *
 * A number on its own line starts an exercise. Each whitespace-separated
 * token is one akshara; `||` ends an avarta; `|` is layout only. The parser
 * never guesses: unknown characters, avartas of the wrong length, and a
 * leading karvai are errors.
 */
class VarisaiParser(private val tala: Tala = Tala.ADI) {

    fun parse(text: String, set: VarisaiSet): List<Varisai> {
        val result = mutableListOf<Varisai>()
        var number: Int? = null
        var numberLine = 0
        val avartas = mutableListOf<Avarta>()

        fun finish() {
            val n = number ?: return
            if (avartas.isEmpty()) throw VarisaiParseException(numberLine, 1, "exercise $n has no notation")
            result += Varisai(set, n, avartas.toList())
            avartas.clear()
        }

        text.lines().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.all { it.isDigit() } -> {
                    finish()
                    number = line.toInt()
                    numberLine = lineNo
                }
                else -> {
                    if (number == null) {
                        throw VarisaiParseException(lineNo, 1, "notation before the first exercise number")
                    }
                    avartas += parseLine(raw, lineNo, isFirstLine = avartas.isEmpty())
                }
            }
        }
        finish()
        return result
    }

    private fun parseLine(raw: String, lineNo: Int, isFirstLine: Boolean): List<Avarta> {
        val avartas = mutableListOf<Avarta>()
        var segmentStart = 0
        val segments = mutableListOf<Pair<Int, String>>()
        var i = raw.indexOf("||")
        while (i >= 0) {
            segments += segmentStart to raw.substring(segmentStart, i)
            segmentStart = i + 2
            i = raw.indexOf("||", segmentStart)
        }
        segments += segmentStart to raw.substring(segmentStart)

        for ((offset, segment) in segments) {
            val aksharas = mutableListOf<Akshara>()
            for ((column, token) in tokens(segment, offset)) {
                val akshara = parseToken(token, lineNo, column)
                if (akshara == Akshara.Karvai && isFirstLine && avartas.isEmpty() && aksharas.isEmpty()) {
                    throw VarisaiParseException(lineNo, column, "an exercise cannot start with a karvai")
                }
                aksharas += akshara
            }
            if (aksharas.size != tala.aksharas) {
                throw VarisaiParseException(
                    lineNo,
                    offset + 1,
                    "avarta has ${aksharas.size} aksharas; ${tala.name} tala needs ${tala.aksharas}",
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

    private fun parseToken(token: String, lineNo: Int, column: Int): Akshara {
        if (token == ",") return Akshara.Karvai
        val swaras = mutableListOf<WrittenSwara>()
        token.forEachIndexed { k, c ->
            if (c == TARA_MARK) {
                val last = swaras.lastOrNull()
                    ?: throw VarisaiParseException(lineNo, column + k, "octave mark without a swara")
                if (last.octave != Octave.MADHYA) {
                    throw VarisaiParseException(lineNo, column + k, "repeated octave mark")
                }
                swaras[swaras.lastIndex] = last.copy(octave = Octave.TARA)
            } else {
                val letter = SwaraLetter.fromSymbol(c)
                    ?: throw VarisaiParseException(lineNo, column + k, "unexpected character '$c' in \"$token\"")
                swaras += WrittenSwara(letter)
            }
        }
        return Akshara.Swaras(swaras)
    }

    private companion object {
        /** U+2032 PRIME, the tara (upper octave) mark. */
        const val TARA_MARK = '′'
    }
}
