package com.baijum.purandara.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.varisai.Akshara
import com.baijum.purandara.domain.varisai.Avarta
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.viewmodel.MarkKey
import com.baijum.purandara.ui.LocalReduceMotion

/**
 * The exercise as a grid: one row per avarta, eight akshara cells in the
 * same columns as the [TalaStrip] above (so the marks are not repeated on
 * every row). The sounding akshara and swara are highlighted, and the list
 * follows playback.
 */
@Composable
fun NotationView(
    varisai: Varisai,
    position: NotationPosition?,
    modifier: Modifier = Modifier,
    marks: Map<MarkKey, NoteResult> = emptyMap(),
    tala: Tala = Tala.ADI,
) {
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
            val spoken = if (rowMarks.isEmpty()) avarta.spoken(tala, karvaiWord) else markedSpoken(avarta, index, rowMarks, tala, karvaiWord)
            val description = stringResource(R.string.avarta_description, index + 1, spoken)
            AvartaRow(
                avarta = avarta,
                tala = tala,
                activeAkshara = position?.takeIf { it.avarta == index }?.akshara,
                activeSlot = position?.takeIf { it.avarta == index }?.slot,
                rowMarks = rowMarks,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clearAndSetSemantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun AvartaRow(
    avarta: Avarta,
    tala: Tala,
    activeAkshara: Int?,
    activeSlot: Int?,
    rowMarks: Map<MarkKey, NoteResult>,
    modifier: Modifier = Modifier,
) {
    // Taller rows with larger system text, so swaras grow instead of shrinking to fit.
    val rowHeight = ROW_HEIGHT_DP.dp * (1f + (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * ROW_GROWTH)
    Row(modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
        avarta.aksharas.forEachIndexed { i, akshara ->
            if (i > 0 && tala.beats[i].startsAnga) AngaBar()
            AksharaCell(
                akshara = akshara,
                active = i == activeAkshara,
                activeSlot = activeSlot.takeIf { i == activeAkshara },
                marks = rowMarks.filterKeys { it.akshara == i }.mapKeys { it.key.slot },
                modifier = Modifier.weight(1f),
            )
        }
        AngaBar(double = true)
    }
}

@Composable
private fun AksharaCell(
    akshara: Akshara,
    active: Boolean,
    activeSlot: Int?,
    marks: Map<Int, NoteResult>,
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
        val text = buildAnnotatedString {
            when (akshara) {
                Akshara.Karvai -> append(",")
                is Akshara.Swaras -> akshara.swaras.forEachIndexed { slot, swara ->
                    if (slot == activeSlot) {
                        withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(swara.toString()) }
                    } else {
                        append(swara.toString())
                    }
                }
            }
        }
        val swaraCount = (akshara as? Akshara.Swaras)?.swaras?.size ?: 1
        val baseSize = if (swaraCount > 2) 13.sp else if (swaraCount == 2) 17.sp else 20.sp
        // Shrink to fit rather than clip: with large system fonts a pair such
        // as "GM" would otherwise lose a swara.
        BasicText(
            text,
            style = TextStyle(
                textAlign = TextAlign.Center,
                color = if (active) colors.onPrimaryContainer else colors.onSurface,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_SWARA_SP.sp, maxFontSize = baseSize),
            modifier = Modifier.fillMaxWidth(),
        )
        if (marks.isNotEmpty() && akshara is Akshara.Swaras) {
            // One glyph per swara slot: ✓ in tune, ↑/↓ high/low, ⇣/⇡ octave, ✕ other swara, ○ not heard.
            val glyphs = buildAnnotatedString {
                akshara.swaras.indices.forEach { slot ->
                    val result = marks[slot]
                    withStyle(SpanStyle(color = markColor(result))) { append(markGlyph(result) ?: " ") }
                }
            }
            Text(glyphs, fontSize = MARK_SP.sp, maxLines = 1)
        }
    }
}

/** Row sentence with each swara's mark, e.g. "S in tune R high G in tune, …". */
@Composable
private fun markedSpoken(avarta: Avarta, index: Int, marks: Map<MarkKey, NoteResult>, tala: Tala, karvaiWord: String): String {
    val words = avarta.aksharas.mapIndexed { i, akshara ->
        val text = when (akshara) {
            Akshara.Karvai -> karvaiWord
            is Akshara.Swaras -> akshara.swaras.mapIndexed { slot, swara ->
                val mark = markWords(marks[MarkKey(index, i, slot)])
                if (mark == null) swara.toString() else "$swara $mark"
            }.joinToString(" ")
        }
        if (i > 0 && tala.beats[i].startsAnga) ", $text" else if (i > 0) " $text" else text
    }
    return words.joinToString("")
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
 * separate from the marks printed above the notation. One accessibility node
 * whose state is read only when focused, so nothing is announced during playback.
 */
@Composable
fun TalaStrip(beatInAvarta: Int?, modifier: Modifier = Modifier, tala: Tala = Tala.ADI) {
    val colors = MaterialTheme.colorScheme
    val clap = stringResource(R.string.clap)
    val wave = stringResource(R.string.wave)
    val talaLabel = stringResource(R.string.tala_label, tala.name, spokenBeats(tala, clap, wave))
    val beatText = beatInAvarta?.let { stringResource(R.string.beat_of, it + 1, tala.aksharas) } ?: ""
    Row(
        modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics {
            contentDescription = talaLabel
            stateDescription = beatText
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tala.beats.forEachIndexed { i, beat ->
            if (i > 0 && beat.startsAnga) AngaBar()
            val current = i == beatInAvarta
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(if (current) colors.primary else colors.surfaceVariant, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    beat.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MIN_SWARA_SP = 8
private const val MARK_SP = 11
private const val ROW_HEIGHT_DP = 48
private const val ROW_GROWTH = 0.75f

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
