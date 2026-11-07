package com.baijum.purandara.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.baijum.purandara.domain.varisai.Avarta
import com.baijum.purandara.domain.varisai.HELD
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.WordsDisplay
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.viewmodel.MarkKey
import com.baijum.purandara.ui.LocalReduceMotion

/**
 * The exercise as a grid: one block per avarta, its akshara cells in the
 * same columns as the [TalaStrip] above (so the marks are not repeated on
 * every row). Talas longer than eight beats wrap onto two rows at an anga
 * boundary ([Tala.rowStarts]). A gitam's words go under the swaras (or
 * replace them, per [words]), and each section starts with a heading. The
 * sounding akshara and swara are highlighted, and the list follows playback.
 */
@Composable
fun NotationView(
    varisai: Varisai,
    position: NotationPosition?,
    modifier: Modifier = Modifier,
    marks: Map<MarkKey, NoteResult> = emptyMap(),
    words: WordsDisplay = WordsDisplay.BOTH,
) {
    val tala = varisai.tala
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    val currentAvarta = position?.avarta

    LaunchedEffect(currentAvarta) {
        val target = currentAvarta ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == target && it.offset >= 0 &&
            it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        if (!fullyVisible) {
            if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    val karvaiWord = stringResource(R.string.karvai_spoken)
    LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(varisai.avartas) { index, avarta ->
            val rowMarks = marks.filterKeys { it.avarta == index }
            val swaras = if (rowMarks.isEmpty()) avarta.spoken(tala, karvaiWord) else markedSpoken(avarta, index, rowMarks, varisai, karvaiWord)
            val spoken = avarta.spokenWords()?.let { stringResource(R.string.avarta_words, swaras, it) } ?: swaras
            val description = stringResource(R.string.avarta_description, index + 1, spoken)
            Column {
                avarta.section?.let { section ->
                    Text(
                        section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    )
                }
                AvartaRow(
                    avarta = avarta,
                    layout = AvartaLayout(varisai, index),
                    active = position?.takeIf { it.avarta == index },
                    rowMarks = rowMarks,
                    words = words,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clearAndSetSemantics { contentDescription = description },
                )
            }
        }
    }
}

/** One avarta's row layout: where its rows start and the longest row's cells. */
private data class AvartaLayout(private val varisai: Varisai, private val avarta: Int) {
    val rowStarts: List<Int> get() = varisai.rowStartsIn(avarta)
    val rowCells: Int get() = varisai.rowCellsIn(avarta)

    /** Whether cell [cell] starts an anga (an anga bar goes before it). */
    fun startsAnga(cell: Int): Boolean = varisai.startsAnga(avarta, cell)
}

@Composable
private fun AvartaRow(
    avarta: Avarta,
    layout: AvartaLayout,
    active: NotationPosition?,
    rowMarks: Map<MarkKey, NoteResult>,
    words: WordsDisplay,
    modifier: Modifier = Modifier,
) {
    val activeAkshara = active?.akshara
    val activeSlot = active?.slot
    val rowWords = avarta.words?.takeIf { words.showsWords }
    val showSwaras = rowWords == null || words.showsSwaras
    // Taller rows with larger system text, so swaras grow instead of shrinking to fit.
    val grow = 1f + (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * ROW_GROWTH
    val rowHeight = (ROW_HEIGHT_DP + if (rowWords != null && showSwaras) WORDS_HEIGHT_DP else 0).dp * grow
    val starts = layout.rowStarts
    val ends = starts.drop(1) + avarta.aksharas.size
    Column(modifier.fillMaxWidth()) {
        starts.zip(ends).forEachIndexed { row, (from, to) ->
            Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                for (i in from until to) {
                    if (i > from && layout.startsAnga(i)) AngaBar()
                    AksharaCell(
                        akshara = avarta.aksharas[i],
                        active = i == activeAkshara,
                        activeSlot = activeSlot.takeIf { i == activeAkshara },
                        marks = rowMarks.filterKeys { it.akshara == i }.mapKeys { it.key.slot },
                        word = rowWords?.get(i),
                        showSwaras = showSwaras,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row == starts.lastIndex) AngaBar(double = true)
                // Keep the columns of a shorter row in line with the longest.
                val missing = layout.rowCells - (to - from)
                if (missing > 0) Spacer(Modifier.weight(missing.toFloat()))
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

/** Row sentence with each swara's mark, e.g. "S in tune R high G in tune, …". */
@Composable
private fun markedSpoken(avarta: Avarta, index: Int, marks: Map<MarkKey, NoteResult>, varisai: Varisai, karvaiWord: String): String {
    val words = avarta.aksharas.mapIndexedNotNull { i, akshara ->
        val text = when (akshara) {
            // Written several to a beat (a swarajati), holds inside a beat aren't read out.
            Akshara.Karvai -> if (varisai.cellsPerBeatIn(index) > 1) null else karvaiWord
            Akshara.Rest -> null // silence: nothing to read
            is Akshara.Swaras -> akshara.swaras.mapIndexed { slot, swara ->
                val mark = markWords(marks[MarkKey(index, i, slot)])
                if (mark == null) swara.spoken() else "${swara.spoken()} $mark"
            }.joinToString(" ")
        }
        text?.let { i to it }
    }
    return words.mapIndexed { n, (i, text) ->
        if (n > 0 && varisai.startsAnga(index, i)) ", $text" else if (n > 0) " $text" else text
    }.joinToString("")
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
 * The tala being kept: one mark per beat of the avarta, the current beat
 * filled. At higher kalas several written aksharas share one beat, so this is
 * separate from the marks printed above the notation. A two-kalai piece
 * ([kalai] 2) splits each beat into its kriya and a `"·"` second count, 16
 * columns for Adi. One accessibility node whose state is read only when
 * focused, so nothing is announced during playback.
 */
@Composable
fun TalaStrip(beatInAvarta: Int?, tala: Tala, kalai: Int = 1, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clap = stringResource(R.string.clap)
    val wave = stringResource(R.string.wave)
    val counts = tala.countsAt(kalai)
    val kalaiLabel = if (kalai > 1) stringResource(R.string.kalai_label, kalai) else ""
    val talaLabel = stringResource(R.string.tala_label, tala.name, "$kalaiLabel ${spokenBeats(tala, clap, wave)}".trim())
    val beatText = beatInAvarta?.let {
        if (kalai > 1) stringResource(R.string.count_of, it + 1, counts.size)
        else stringResource(R.string.beat_of, it + 1, tala.aksharas)
    } ?: ""
    val starts = tala.rowStartsFor(kalai) // one count per beat at kalai 1: the same rows as before
    val ends = starts.drop(1) + counts.size
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = talaLabel
            stateDescription = beatText
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        starts.zip(ends).forEach { (from, to) ->
            Row(
                Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in from until to) {
                    val count = counts[i]
                    if (i > from && count.startsAnga) AngaBar()
                    val current = i == beatInAvarta
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(if (current) colors.primary else colors.surfaceVariant, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            count.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (current) colors.onPrimary else colors.onSurfaceVariant,
                        )
                    }
                }
                val missing = tala.rowCellsFor(kalai) - (to - from)
                if (missing > 0) Spacer(Modifier.weight(missing.toFloat()))
            }
        }
    }
}

private const val MIN_SWARA_SP = 8
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
