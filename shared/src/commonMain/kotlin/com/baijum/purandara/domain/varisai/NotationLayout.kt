package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.TalaCount
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.schedule.Schedule

/** One written cell as sounded: which pass through the piece, which avarta and akshara. */
data class CellRef(val repetition: Int, val avarta: Int, val akshara: Int)

/**
 * The cells sung in one count of the tala: one beat, or one of the two
 * counts of a beat in a two-kalai varnam. [countInCycle] is 0-based.
 */
data class BeatGroup(val countInCycle: Int, val count: TalaCount, val cells: List<CellRef>) {
    /** Whether an anga starts here (an anga bar goes before it). */
    val startsAnga: Boolean get() = count.startsAnga

    /** How the count is written: x, 1, v, or `·` for a second count. */
    val label: String get() = count.label

    /** Columns the group takes: its cells, or one empty column if none starts in it. */
    val width: Int get() = maxOf(1, cells.size)
}

/**
 * One row of notation: whole counts, except that a beat wider than
 * [Tala.ROW_AKSHARAS] cells wraps onto rows of its own ([continuesBeat] on
 * all but the first).
 */
data class LayoutRow(val groups: List<BeatGroup>, val continuesBeat: Boolean) {
    /** Columns in the row. */
    val width: Int get() = groups.sumOf { it.width }

    /** The beat mark for the gutter: the first count's label, empty when the row continues a beat. */
    val gutterLabel: String get() = if (continuesBeat) "" else groups.first().label
}

/** A section heading that falls inside a cycle (at its first cell, or at count [countInCycle]). */
data class SectionStart(val label: String, val cell: CellRef, val countInCycle: Int, val beatInCycle: Int)

/** Where a cell sits on screen: block (tala cycle), row and column. */
data class Located(val block: Int, val row: Int, val column: Int)

/**
 * One tala cycle at the chosen speed: its rows, laid out on [columns]
 * columns, and the sections that start inside it.
 */
data class CycleBlock(val index: Int, val rows: List<LayoutRow>, val columns: Int, val sections: List<SectionStart>) {
    /**
     * Whether the tala strip can stand over this block as its column header:
     * at most two rows, none wrapping a beat. Otherwise each row carries its
     * beat mark in a gutter.
     */
    val mirrorsStrip: Boolean get() = rows.size <= MAX_STRIP_ROWS && rows.none { it.continuesBeat }

    /**
     * The heading above the block, e.g. "Pallavi", or "Charanam · Chitta
     * swaram 1 from beat 5" when a section starts inside the cycle; null when
     * none starts in it.
     */
    val heading: String?
        get() = sections.takeIf { it.isNotEmpty() }?.joinToString(" · ") { start ->
            if (start.countInCycle == 0) start.label else "${start.label} from beat ${start.beatInCycle + 1}"
        }

    /** The cells of the block, in order. */
    val cells: List<CellRef> get() = rows.flatMap { row -> row.groups.flatMap { it.cells } }

    private companion object {
        const val MAX_STRIP_ROWS = 2
    }
}

/**
 * The notation of [varisai] laid out by tala cycle at [kala]: one [CycleBlock]
 * per cycle of the performance, so the swaras under a beat are the ones sung
 * on it. Where a pass ends inside a cycle the next pass continues in it (the
 * plain scale is written twice in one cycle at the third speed), exactly as
 * [Schedule] plays it. At the first speed, one swara to a beat, each block is
 * one written avarta.
 */
class NotationLayout private constructor(
    val varisai: Varisai,
    val kala: Int,
    val blocks: List<CycleBlock>,
) {
    private val where: Map<CellRef, Located> = buildMap {
        blocks.forEachIndexed { b, block ->
            block.rows.forEachIndexed { r, row ->
                var column = 0
                row.groups.forEach { group ->
                    group.cells.forEach { cell -> put(cell, Located(b, r, column++)) }
                    if (group.cells.isEmpty()) column++
                }
            }
        }
    }

    /** The tala the blocks follow. */
    val tala: Tala get() = varisai.tala

    /** Where [position] is shown, or null if it isn't in this layout. */
    fun locate(position: NotationPosition): Located? =
        where[CellRef(position.repetition, position.avarta, position.akshara)]

    /** Where [cell] is shown, or null if it isn't in this layout. */
    fun locate(cell: CellRef): Located? = where[cell]

    /** The written akshara of [cell]. */
    fun akshara(cell: CellRef): Akshara = varisai.avartas[cell.avarta].aksharas[cell.akshara]

    /** The syllable under [cell], or null when the piece has no words there. */
    fun word(cell: CellRef): String? = varisai.avartas[cell.avarta].words?.get(cell.akshara)

    /** Whether the piece has words in block [block]. */
    fun hasWords(block: Int): Boolean = blocks[block].cells.any { word(it) != null }

    /**
     * Block [block] for screen readers: angas separated by commas, a karvai
     * spoken as [karvaiWord] (localised by the caller), e.g. "S R G M, P hold, S R".
     */
    fun spoken(block: Int, karvaiWord: String): String = spokenWith(block) { cell, akshara ->
        when (akshara) {
            Akshara.Karvai -> if (heldInsideBeat(cell)) null else karvaiWord
            Akshara.Rest -> null
            is Akshara.Swaras -> akshara.spoken()
        }
    }

    /**
     * Block [block] read with [text] for each cell (null to skip it), commas
     * at the anga bars. For the sing-along marks, which add a word per swara.
     */
    fun spokenWith(block: Int, text: (CellRef, Akshara) -> String?): String {
        val parts = mutableListOf<String>()
        blocks[block].rows.forEach { row ->
            row.groups.forEach { group ->
                group.cells.forEachIndexed { i, cell ->
                    val word = text(cell, akshara(cell)) ?: return@forEachIndexed
                    parts += when {
                        parts.isEmpty() -> word
                        i == 0 && group.startsAnga -> ", $word"
                        else -> " $word"
                    }
                }
            }
        }
        return parts.joinToString("")
    }

    /** The words of block [block] to read aloud, skipping held syllables; null without words. */
    fun spokenWords(block: Int): String? {
        if (!hasWords(block)) return null
        return blocks[block].cells.mapNotNull { word(it) }.filter { it != HELD }
            .joinToString(" ") { it.replace("·", " ") }
    }

    /** Written several to a beat (a swarajati), a hold inside a beat isn't read out. */
    fun heldInsideBeat(cell: CellRef): Boolean = varisai.cellsPerBeatIn(cell.avarta) > 1

    companion object {
        /** The whole piece at [kala]. */
        fun of(varisai: Varisai, kala: Int): NotationLayout = of(varisai, varisai.avartas.indices, kala)

        /** Avartas [range] of [varisai] (a section or phrase) at [kala], as [Schedule] plays them. */
        fun of(varisai: Varisai, range: IntRange, kala: Int): NotationLayout {
            require(kala in 1..MAX_KALA) { "kala $kala outside 1..$MAX_KALA" }
            require(!range.isEmpty() && range.first >= 0 && range.last < varisai.avartas.size) {
                "avartas $range outside the piece"
            }
            val tala = varisai.tala
            val kalai = varisai.kalai
            val perBeat = 1 shl (kala - 1)
            // Cells per tala beat in each avarta: its gati, the speed, and a @2 section's own speed.
            fun rate(avarta: Int): Int = varisai.cellsPerBeatIn(avarta) * perBeat * varisai.avartas[avarta].speed
            // Whole ticks, so cells never drift across a count: ticks per beat divide by every rate and by the kalai.
            var ticksPerBeat = range.map(::rate).fold(1, ::lcm)
            if (ticksPerBeat % kalai != 0) ticksPerBeat *= kalai
            val ticksPerCount = ticksPerBeat / kalai
            val countsPerCycle = tala.aksharas * kalai

            val byCount = mutableMapOf<Int, MutableList<CellRef>>()
            val sectionStarts = mutableListOf<Pair<Int, SectionStart>>() // global count → start
            var tick = 0L
            val reps = Schedule.repetitionsFor(varisai, range, kala)
            for (rep in 0 until reps) {
                for (a in range) {
                    val cellTicks = ticksPerBeat / rate(a)
                    varisai.avartas[a].aksharas.indices.forEach { k ->
                        val cell = CellRef(rep, a, k)
                        val count = (tick / ticksPerCount).toInt()
                        byCount.getOrPut(count) { mutableListOf() } += cell
                        val label = varisai.avartas[a].section
                        if (k == 0 && label != null) {
                            val inCycle = count % countsPerCycle
                            sectionStarts += count to SectionStart(label, cell, inCycle, inCycle / kalai)
                        }
                        tick += cellTicks
                    }
                }
            }
            val cycleTicks = ticksPerCount.toLong() * countsPerCycle
            check(tick % cycleTicks == 0L) { "${varisai.id} at kala $kala does not fill whole cycles" }
            val counts = tala.countsAt(kalai)
            val blocks = (0 until (tick / cycleTicks).toInt()).map { c ->
                val groups = (0 until countsPerCycle).map { i ->
                    BeatGroup(i, counts[i], byCount[c * countsPerCycle + i].orEmpty())
                }
                val rows = rowsFor(tala, kalai, groups)
                CycleBlock(
                    index = c,
                    rows = rows,
                    columns = rows.maxOf { it.width },
                    sections = sectionStarts.filter { it.first / countsPerCycle == c }.map { it.second },
                )
            }
            return NotationLayout(varisai, kala, blocks)
        }

        /**
         * What a practice of [varisai] plays at [kala]: the reviewed [phrase]
         * if one is chosen, else the [section], else the whole piece (as
         * `Schedule.ofPhrase` / `ofSection` / `of`).
         */
        fun forPractice(varisai: Varisai, section: SectionSpan?, phrase: PracticePhrase?, kala: Int): NotationLayout {
            val range = phrase?.let { it.firstAvarta..it.lastAvarta }
                ?: section?.let { it.firstAvarta..it.lastAvarta }
                ?: varisai.avartas.indices
            return of(varisai, range, kala)
        }

        /** For callers without default arguments (Swift): avartas [first]..[last] at [kala]. */
        fun create(varisai: Varisai, first: Int, last: Int, kala: Int): NotationLayout =
            of(varisai, first..last, kala)

        /**
         * The rows of one cycle. When every beat holds the same number of
         * cells, the shared rule [Tala.rowStartsFor] (so the first speed looks
         * as it always has); otherwise each anga splits at beats into rows of
         * at most [Tala.ROW_AKSHARAS] cells and short neighbours join. A beat
         * wider than a row wraps onto rows of its own.
         */
        private fun rowsFor(tala: Tala, kalai: Int, groups: List<BeatGroup>): List<LayoutRow> {
            val beatGroups = groups.chunked(kalai)
            val widths = beatGroups.map { beat -> beat.sumOf { it.width } }
            val beatStarts = if (widths.distinct().size == 1) {
                tala.rowStartsFor(widths.first()).map { it / widths.first() }
            } else {
                mixedRowStarts(tala, widths)
            }
            val ends = beatStarts.drop(1) + beatGroups.size
            return beatStarts.zip(ends).flatMap { (from, to) ->
                val rowGroups = beatGroups.subList(from, to).flatten()
                val width = rowGroups.sumOf { it.width }
                if (to - from == 1 && width > Tala.ROW_AKSHARAS) wrapBeat(rowGroups, width) else listOf(LayoutRow(rowGroups, false))
            }
        }

        private fun mixedRowStarts(tala: Tala, widths: List<Int>): List<Int> {
            val pieces = mutableListOf<Pair<Int, Int>>() // (first beat, cells)
            var beat = 0
            tala.angas.forEach { length ->
                var chunkStart = beat
                var cells = 0
                for (b in beat until beat + length) {
                    if (b > chunkStart && cells + widths[b] > Tala.ROW_AKSHARAS) {
                        pieces += chunkStart to cells
                        chunkStart = b
                        cells = 0
                    }
                    cells += widths[b]
                }
                pieces += chunkStart to cells
                beat += length
            }
            val rows = mutableListOf<Pair<Int, Int>>()
            pieces.forEach { piece ->
                val last = rows.lastOrNull()
                if (last != null && last.second + piece.second <= Tala.ROW_AKSHARAS) rows[rows.lastIndex] = last.first to last.second + piece.second
                else rows += piece
            }
            return rows.map { it.first }
        }

        /** One beat's cells over as few even rows as fit [Tala.ROW_AKSHARAS] each. */
        private fun wrapBeat(groups: List<BeatGroup>, width: Int): List<LayoutRow> {
            val rowCount = (width + Tala.ROW_AKSHARAS - 1) / Tala.ROW_AKSHARAS
            val sizes = List(rowCount) { width / rowCount + if (it < width % rowCount) 1 else 0 }
            // Each column with the count it belongs to (null cell: an empty count's column).
            val columns = groups.flatMap { g -> if (g.cells.isEmpty()) listOf(g to null) else g.cells.map { g to it } }
            var offset = 0
            return sizes.mapIndexed { r, size ->
                val slice = columns.subList(offset, offset + size)
                offset += size
                val rowGroups = mutableListOf<BeatGroup>()
                slice.forEach { (group, cell) ->
                    val last = rowGroups.lastOrNull()
                    if (last != null && last.countInCycle == group.countInCycle) {
                        rowGroups[rowGroups.lastIndex] = last.copy(cells = last.cells + listOfNotNull(cell))
                    } else {
                        rowGroups += group.copy(cells = listOfNotNull(cell))
                    }
                }
                LayoutRow(rowGroups, continuesBeat = r > 0)
            }
        }

        private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        private const val MAX_KALA = 4
    }
}
