package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotationLayoutTest {
    private val plain = VarisaiCatalog.inSet(VarisaiSet.PLAIN_SCALE).single()
    private fun varisai(id: String) = VarisaiCatalog.byId(id)!!

    /** Row widths of a block, e.g. [8, 8]. */
    private fun CycleBlock.widths() = rows.map { it.width }

    /** Akshara texts of a row. */
    private fun NotationLayout.text(row: LayoutRow) = row.groups.map { g -> g.cells.joinToString("") { akshara(it).toString() } }

    @Test
    fun firstSpeedLooksAsItAlwaysHas() {
        // At the first speed every catalog piece keeps one block per written
        // avarta, with today's rows and anga bars — unless a @2 section packs two
        // written avartas into one cycle.
        VarisaiCatalog.all.forEach { v ->
            if (v.avartas.any { it.speed != 1 }) return@forEach
            val layout = NotationLayout.of(v, 1)
            assertEquals(v.avartas.size, layout.blocks.size, v.id)
            layout.blocks.forEachIndexed { b, block ->
                assertEquals(v.rowStartsIn(b), block.rows.runningFold(0) { a, r -> a + r.width }.dropLast(1), "${v.id} avarta $b")
                block.cells.forEachIndexed { i, cell ->
                    assertEquals(CellRef(0, b, i), cell)
                    val group = block.rows.flatMap { it.groups }.first { cell in it.cells }
                    assertEquals(v.startsAnga(b, i), group.startsAnga && group.cells.first() == cell, "${v.id} $b:$i")
                }
            }
        }
    }

    @Test
    fun plainScaleAtSecondSpeedIsOneCycleOfPairs() {
        val layout = NotationLayout.of(plain, 2)
        val block = layout.blocks.single()
        assertEquals(listOf(8, 8), block.widths())
        assertTrue(block.mirrorsStrip)
        assertEquals(listOf("SR", "GM", "PD", "NS′"), layout.text(block.rows[0]))
        assertEquals(listOf("S′N", "DP", "MG", "RS"), layout.text(block.rows[1]))
        // The laghu is the first row; the drutams start the second.
        assertEquals(listOf("x", "1", "2", "3"), block.rows[0].groups.map { it.label })
        assertEquals(listOf("x", "v", "x", "v"), block.rows[1].groups.map { it.label })
    }

    @Test
    fun plainScaleAtThirdSpeedIsWrittenTwiceInOneCycle() {
        val block = NotationLayout.of(plain, 3).blocks.single()
        assertEquals(listOf(8, 8, 8, 8), block.widths())
        assertFalse(block.mirrorsStrip)
        assertEquals(listOf(0, 1), block.cells.map { it.repetition }.distinct())
        assertEquals(listOf("x", "2", "x", "x"), block.rows.map { it.gutterLabel })
    }

    @Test
    fun plainScaleAtFourthSpeedIsFourPassesInOneCycle() {
        val block = NotationLayout.of(plain, 4).blocks.single()
        assertEquals(List(8) { 8 }, block.widths())
        assertEquals(listOf(0, 1, 2, 3), block.cells.map { it.repetition }.distinct())
        assertEquals(64, block.cells.size)
    }

    @Test
    fun angaBarFollowsTheBeatAtSecondSpeed() {
        val layout = NotationLayout.of(varisai("sarali-1"), 2)
        val block = layout.blocks.first()
        // Written avartas 0 and 1 make one cycle; the anga bars come before beats 5 and 7, cells 8 and 12.
        val angaCells = block.rows.flatMap { it.groups }.filter { it.startsAnga }.map { block.cells.indexOf(it.cells.first()) }
        assertEquals(listOf(0, 8, 12), angaCells)
        // Written cell 4 (the old bar) is in the middle of the laghu now.
        val written4 = block.rows.flatMap { it.groups }.first { CellRef(0, 0, 4) in it.cells }
        assertEquals("2", written4.label)
    }

    @Test
    fun speechPutsCommasAtTheNewAngaBars() {
        val layout = NotationLayout.of(plain, 2)
        assertEquals("S R G M P D N S′, S′ N D P, M G R S", layout.spoken(0, "hold"))
    }

    @Test
    fun aMidCycleSectionIsNamedWithItsBeat() {
        val v = VarisaiParser().parse(
            """
            1
            [pallavi]
            S R G M P D N S′ ||
            [anupallavi]
            S′ N D P M G R S ||
            """.trimIndent(),
            VarisaiSet.SARALI,
        ).single()
        val block = NotationLayout.of(v, 2).blocks.single()
        assertEquals("Pallavi · Anupallavi from beat 5", block.heading)
    }

    @Test
    fun mixedGatiInOneCycleBreaksRowsAtBeats() {
        // A 16-cell avarta (two to a beat) and a 40-cell khanda one: at the second
        // speed both share a cycle, four cells to a beat then ten.
        val v = VarisaiParser().parse(
            "1\ntala: adi\nbeat: 2\n${List(16) { "S" }.joinToString(" ")} ||\n" +
                "[khanda]\nbeat: 5\n${List(40) { "R" }.joinToString(" ")} ||\n",
            VarisaiSet.SARALI,
        ).single()
        val block = NotationLayout.of(v, 2).blocks.single()
        val beats = block.rows.flatMap { it.groups }.groupBy { it.countInCycle }.mapValues { (_, g) -> g.sumOf { it.cells.size } }
        assertEquals(listOf(4, 4, 4, 4, 10, 10, 10, 10), beats.values.toList())
        assertTrue(block.rows.all { it.width <= Tala.ROW_AKSHARAS })
        assertTrue(block.rows.any { it.continuesBeat })
    }

    @Test
    fun twoKalaiVarnamKeepsItsSixteenCountsAndMirrorsAtFirstSpeed() {
        val v = VarisaiCatalog.inSet(VarisaiSet.VARNAM).first()
        val layout = NotationLayout.of(v, 1)
        val first = layout.blocks.first()
        assertEquals(16, first.rows.sumOf { it.groups.size })
        assertTrue(first.mirrorsStrip)
    }

    @Test
    fun everySoundedCellIsShownOnceAndCyclesAreWhole() = runTest {
        checkAll(Arb.element(VarisaiCatalog.all), Arb.int(1..4)) { v, kala ->
            val ranges = listOf(v.avartas.indices) + v.sections.map { it.firstAvarta..it.lastAvarta }
            ranges.forEach { range ->
                val layout = NotationLayout.of(v, range, kala)
                val settings = PerformanceSettings(saHz = Pitch.midiToHz(v.nearestPlayableSa(48)), kala = kala)
                val schedule = Schedule.of(v, settings).takeIf { range == v.avartas.indices }
                    ?: Schedule.ofSection(v, settings, v.sections.first { it.firstAvarta == range.first && it.lastAvarta == range.last })
                val shown = layout.blocks.flatMap { it.cells }
                assertEquals(shown.size, shown.toSet().size, "${v.id} kala $kala: a cell is shown twice")
                val cyclesPlayed = schedule.beats.size / (v.tala.aksharas * v.kalai)
                assertEquals(cyclesPlayed, layout.blocks.size, "${v.id} kala $kala $range")
                schedule.cursor.forEach { event ->
                    val at = assertNotNull(layout.locate(event.position), "${v.id} kala $kala ${event.position}")
                    // The cell is shown in the cycle and count it sounds in.
                    val countsIn = (event.startSeconds / settings.talaAksharaSeconds * v.kalai + 1e-6).toInt()
                    val countsPerCycle = v.tala.aksharas * v.kalai
                    assertEquals(countsIn / countsPerCycle, at.block, "${v.id} kala $kala ${event.position}")
                    val cell = CellRef(event.position.repetition, event.position.avarta, event.position.akshara)
                    val group = layout.blocks[at.block].rows[at.row].groups.first { cell in it.cells }
                    assertEquals(countsIn % countsPerCycle, group.countInCycle, "${v.id} kala $kala ${event.position}")
                }
                layout.blocks.forEach { block ->
                    assertEquals(v.tala.aksharas * v.kalai, block.rows.flatMap { it.groups }.map { it.countInCycle }.distinct().size)
                    block.rows.forEach { row ->
                        assertTrue(row.width <= maxOf(Tala.ROW_AKSHARAS, v.tala.rowAksharas) || row.groups.map { it.countInCycle / v.kalai }.distinct().size == 1, "${v.id} kala $kala row ${row.width}")
                    }
                }
            }
        }
    }
}
