package com.baijum.purandara.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.varisai.Akshara
import com.baijum.purandara.domain.varisai.BeatGroup
import com.baijum.purandara.domain.varisai.CellRef
import com.baijum.purandara.domain.varisai.CycleBlock
import com.baijum.purandara.domain.varisai.LayoutRow
import com.baijum.purandara.domain.varisai.NotationLayout
import com.baijum.purandara.domain.varisai.HELD
import com.baijum.purandara.domain.varisai.WordsDisplay
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.viewmodel.MarkKey
import com.baijum.purandara.ui.LocalReduceMotion

/**
 * The exercise as a grid: one block per tala cycle at the chosen speed
 * ([NotationLayout]), so the swaras under a beat are the ones sung on it.
 * While a cycle takes at most two rows the [TalaStrip] above is its column
 * header; longer cycles (the third and fourth speeds, a swarajati) start each
 * row with its beat mark in a gutter. A piece's words go under the swaras (or
 * replace them, per [words]), and a section starts with a heading. The
 * sounding akshara and swara are highlighted, and the list follows playback.
 */
@Composable
fun NotationView(
    layout: NotationLayout,
    position: NotationPosition?,
    modifier: Modifier = Modifier,
    marks: Map<MarkKey, NoteResult> = emptyMap(),
    words: WordsDisplay = WordsDisplay.BOTH,
) {
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    val located = position?.let { layout.locate(it) }
    val currentBlock = located?.block

    LaunchedEffect(currentBlock) {
        val target = currentBlock ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == target && it.offset >= 0 &&
            it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        if (!fullyVisible) {
            if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    val karvaiWord = stringResource(R.string.karvai_spoken)
    LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(layout.blocks) { index, block ->
            val cells = block.cells.map { it.avarta to it.akshara }.toSet()
            val blockMarks = marks.filterKeys { (it.avarta to it.akshara) in cells }
            val swaras = if (blockMarks.isEmpty()) layout.spoken(index, karvaiWord) else markedSpoken(layout, index, blockMarks, karvaiWord)
            val spoken = layout.spokenWords(index)?.let { stringResource(R.string.avarta_words, swaras, it) } ?: swaras
            val description = stringResource(R.string.avarta_description, index + 1, spoken)
            Column {
                block.heading?.let { heading ->
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    )
                }
                CycleRows(
                    layout = layout,
                    block = block,
                    active = position.takeIf { currentBlock == index },
                    marks = blockMarks,
                    words = words,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clearAndSetSemantics { contentDescription = description },
                )
            }
        }
    }
}

/**
 * One row of a cycle, shared by the notation and the tala strip so their
 * columns line up: each count takes its width in columns ([BeatGroup.width]),
 * an anga bar goes before a new anga, a small gap between beats of several
 * cells, and a double bar ends the cycle. [gutter] adds the row's beat mark.
 */
@Composable
private fun BeatRow(
    row: LayoutRow,
    columns: Int,
    lastRow: Boolean,
    gutter: Boolean,
    modifier: Modifier = Modifier,
    sectionStarts: Set<CellRef> = emptySet(),
    group: @Composable RowScope.(BeatGroup) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (gutter) GutterMark(row.gutterLabel)
        row.groups.forEachIndexed { g, beatGroup ->
            if (g > 0) {
                when {
                    beatGroup.cells.firstOrNull() in sectionStarts -> AngaBar(double = true)
                    beatGroup.startsAnga -> AngaBar()
                    beatGroup.cells.size > 1 || row.groups[g - 1].cells.size > 1 -> Spacer(Modifier.width(BEAT_GAP_DP.dp))
                }
            }
            group(beatGroup)
        }
        if (lastRow) AngaBar(double = true)
        // Keep the columns of a shorter row in line with the longest.
        val missing = columns - row.width
        if (missing > 0) Spacer(Modifier.weight(missing.toFloat()))
        if (!lastRow) Spacer(Modifier.width(DOUBLE_BAR_DP.dp))
    }
}

/** A row's beat mark (x, 1, v) when the cycle is too long for the strip to head it. Decorative. */
@Composable
private fun GutterMark(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(GUTTER_DP.dp),
    )
}

@Composable
private fun CycleRows(
    layout: NotationLayout,
    block: CycleBlock,
    active: NotationPosition?,
    marks: Map<MarkKey, NoteResult>,
    words: WordsDisplay,
    modifier: Modifier = Modifier,
) {
    val activeCell = active?.let { CellRef(it.repetition, it.avarta, it.akshara) }
    val activeSlot = active?.slot
    val hasWords = words.showsWords && block.cells.any { layout.word(it) != null }
    val showSwaras = !hasWords || words.showsSwaras
    // Taller rows with larger system text, so swaras grow instead of shrinking to fit.
    val grow = 1f + (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * ROW_GROWTH
    val rowHeight = (ROW_HEIGHT_DP + if (hasWords && showSwaras) WORDS_HEIGHT_DP else 0).dp * grow
    val sectionStarts = block.sections.filter { it.countInCycle > 0 }.map { it.cell }.toSet()
    Column(modifier.fillMaxWidth()) {
        block.rows.forEachIndexed { r, row ->
            BeatRow(
                row = row,
                columns = block.columns,
                lastRow = r == block.rows.lastIndex,
                gutter = !block.mirrorsStrip,
                sectionStarts = sectionStarts,
                modifier = Modifier.height(rowHeight),
            ) { group ->
                // Several swaras to a beat: a light band shows which ones share it.
                val band = if (group.cells.size > 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = BAND_ALPHA) else Color.Transparent
                Row(
                    Modifier.weight(group.width.toFloat()).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
                        .background(band, RoundedCornerShape(8.dp)),
                ) {
                    if (group.cells.isEmpty()) Spacer(Modifier.weight(1f))
                    group.cells.forEach { cell ->
                        AksharaCell(
                            akshara = layout.akshara(cell),
                            active = cell == activeCell,
                            activeSlot = activeSlot.takeIf { cell == activeCell },
                            marks = marks.filterKeys { it.avarta == cell.avarta && it.akshara == cell.akshara }.mapKeys { it.key.slot },
                            word = layout.word(cell).takeIf { hasWords },
                            showSwaras = showSwaras,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AksharaCell(
    akshara: Akshara,
    active: Boolean,
    activeSlot: Int?,
    marks: Map<Int, NoteResult>,
    word: String? = null,
    showSwaras: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .background(if (active) colors.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val swaraCount = (akshara as? Akshara.Swaras)?.swaras?.size ?: 1
        val baseSize = if (swaraCount > 2) 13.sp else if (swaraCount == 2) 17.sp else 20.sp
        val textColor = if (active) colors.onPrimaryContainer else colors.onSurface
        if (showSwaras) SwaraText(akshara, activeSlot, textColor, baseSize)
        if (word != null) WordText(word, underSwaras = showSwaras, textColor, if (showSwaras) WORD_SP.sp else baseSize)
        if (marks.isNotEmpty() && akshara is Akshara.Swaras) MarkGlyphs(akshara, marks)
    }
}

/** The akshara's swaras, the sounding one in bold. */
@Composable
private fun SwaraText(akshara: Akshara, activeSlot: Int?, color: Color, size: TextUnit) {
    val accent = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        when (akshara) {
            Akshara.Karvai -> append(",")
            Akshara.Rest -> append("_")
            is Akshara.Swaras -> akshara.swaras.forEachIndexed { slot, swara ->
                val main = if (slot == activeSlot) SpanStyle(color = accent, fontWeight = FontWeight.Bold) else SpanStyle(color = color)
                withStyle(main) { append(swara.baseText) }
                // A variant digit (D1, N3) is written as a subscript.
                swara.variantDigit?.let { digit ->
                    withStyle(main.copy(fontSize = size * VARIANT_DIGIT_SCALE)) { append(digit) }
                }
            }
        }
    }
    // Shrink to fit rather than clip: with large system fonts a pair such
    // as "GM" would otherwise lose a swara.
    BasicText(
        text,
        style = TextStyle(textAlign = TextAlign.Center, color = color),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_SWARA_SP.sp, maxFontSize = size),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A syllable of the words: the main text on its own, smaller and italic under swaras. */
@Composable
private fun WordText(word: String, underSwaras: Boolean, color: Color, size: TextUnit) {
    // A held syllable ("-") is drawn as a bold en dash, as on iOS: a thin hyphen is hard to see.
    val held = word == HELD
    BasicText(
        if (held) "–" else word,
        style = TextStyle(
            textAlign = TextAlign.Center,
            color = color,
            fontStyle = if (underSwaras && !held) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (held) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_SWARA_SP.sp, maxFontSize = size),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One glyph per swara slot: ✓ in tune, ↑/↓ high/low, ⇣/⇡ octave, ✕ other swara, ○ not heard. */
@Composable
private fun MarkGlyphs(akshara: Akshara.Swaras, marks: Map<Int, NoteResult>) {
    val glyphs = buildAnnotatedString {
        akshara.swaras.indices.forEach { slot ->
            val result = marks[slot]
            withStyle(SpanStyle(color = markColor(result))) { append(markGlyph(result) ?: " ") }
        }
    }
    Text(glyphs, fontSize = MARK_SP.sp, maxLines = 1)
}

/** Cycle sentence with each swara's mark, e.g. "S in tune R high G in tune, …". */
@Composable
private fun markedSpoken(layout: NotationLayout, block: Int, marks: Map<MarkKey, NoteResult>, karvaiWord: String): String {
    val markWords = marks.mapValues { markWords(it.value) }
    return layout.spokenWith(block) { cell, akshara ->
        when (akshara) {
            Akshara.Karvai -> if (layout.heldInsideBeat(cell)) null else karvaiWord
            Akshara.Rest -> null // silence: nothing to read
            is Akshara.Swaras -> akshara.swaras.mapIndexed { slot, swara ->
                val mark = markWords[MarkKey(cell.avarta, cell.akshara, slot)]
                if (mark == null) swara.spoken() else "${swara.spoken()} $mark"
            }.joinToString(" ")
        }
    }
}

@Composable
private fun AngaBar(double: Boolean = false) {
    val color = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxHeight().padding(vertical = 10.dp)) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(color))
        if (double) {
            Box(Modifier.width(2.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(color))
        }
    }
}

/**
 * The tala being kept, the current count filled. While [block] (the cycle
 * being sung) takes at most two rows, the strip has its rows and columns, so
 * each mark stands over the swaras sung on it; otherwise one compact row (and
 * the notation rows carry their own marks). A two-kalai piece ([kalai] 2)
 * splits each beat into its kriya and a `"·"` second count. One accessibility
 * node whose state is read only when focused, so nothing is announced during
 * playback.
 */
@Composable
fun TalaStrip(beatInAvarta: Int?, tala: Tala, kalai: Int = 1, block: CycleBlock? = null, modifier: Modifier = Modifier) {
    val clap = stringResource(R.string.clap)
    val wave = stringResource(R.string.wave)
    val counts = tala.countsAt(kalai)
    val kalaiLabel = if (kalai > 1) stringResource(R.string.kalai_label, kalai) else ""
    val perBeat = block?.swarasPerBeat(kalai)
    val densityLabel = if (perBeat != null && perBeat > 1) stringResource(R.string.swaras_per_beat, perBeat) else ""
    val talaLabel = stringResource(
        R.string.tala_label,
        tala.name,
        listOf(kalaiLabel, densityLabel, spokenBeats(tala, clap, wave)).filter { it.isNotEmpty() }.joinToString(" "),
    )
    val beatText = beatInAvarta?.let {
        if (kalai > 1) stringResource(R.string.count_of, it + 1, counts.size)
        else stringResource(R.string.beat_of, it + 1, tala.aksharas)
    } ?: ""
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = talaLabel
            stateDescription = beatText
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (block != null && block.mirrorsStrip) {
            block.rows.forEachIndexed { r, row ->
                BeatRow(row, block.columns, lastRow = r == block.rows.lastIndex, gutter = false, modifier = Modifier.height(36.dp)) { group ->
                    TalaChip(group.label, group.countInCycle == beatInAvarta, Modifier.weight(group.width.toFloat()).padding(horizontal = 3.dp))
                }
            }
        } else {
            val starts = tala.rowStartsFor(kalai) // one count per beat at kalai 1: the same rows as before
            val ends = starts.drop(1) + counts.size
            starts.zip(ends).forEach { (from, to) ->
                Row(
                    Modifier.fillMaxWidth().height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in from until to) {
                        val count = counts[i]
                        if (i > from && count.startsAnga) AngaBar()
                        TalaChip(count.label, i == beatInAvarta, Modifier.weight(1f))
                    }
                    val missing = tala.rowCellsFor(kalai) - (to - from)
                    if (missing > 0) Spacer(Modifier.weight(missing.toFloat()))
                }
            }
        }
    }
}

/** Swaras sung to each beat of this cycle, or null when the beats differ (a khanda section joins it). */
private fun CycleBlock.swarasPerBeat(kalai: Int): Int? =
    rows.flatMap { it.groups }.groupBy { it.countInCycle / kalai }.values.map { beat -> beat.sumOf { it.cells.size } }
        .distinct().singleOrNull()

@Composable
private fun TalaChip(label: String, current: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(36.dp)
            .background(if (current) colors.primary else colors.surfaceVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (current) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

private const val MIN_SWARA_SP = 8
private const val BEAT_GAP_DP = 4
private const val DOUBLE_BAR_DP = 4
private const val GUTTER_DP = 20
private const val BAND_ALPHA = 0.5f
private const val MARK_SP = 11
private const val ROW_HEIGHT_DP = 48
private const val ROW_GROWTH = 0.75f
private const val WORDS_HEIGHT_DP = 22
private const val WORD_SP = 15

/** A variant digit (the 1 of D1) is drawn as a subscript at this fraction of the swara's size. */
private const val VARIANT_DIGIT_SCALE = 0.6f

/** "clap 1 2 3, clap wave, clap wave": the tala marks as words. */
internal fun spokenBeats(tala: Tala, clap: String, wave: String): String =
    tala.beats.mapIndexed { i, beat ->
        val word = when (beat.kriya) {
            Kriya.CLAP -> clap
            Kriya.WAVE -> wave
            Kriya.COUNT -> beat.label
        }
        when {
            i == 0 -> word
            beat.startsAnga -> ", $word"
            else -> " $word"
        }
    }.joinToString("")
