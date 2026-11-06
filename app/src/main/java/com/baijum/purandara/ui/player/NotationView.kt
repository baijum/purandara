package com.baijum.purandara.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.varisai.Akshara
import com.baijum.purandara.domain.varisai.Avarta
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.ui.LocalReduceMotion

/**
 * The exercise as a grid: one row per avarta, eight akshara cells with the
 * tala marks above. The sounding akshara and swara are highlighted, and the
 * list follows playback.
 */
@Composable
fun NotationView(
    varisai: Varisai,
    position: NotationPosition?,
    modifier: Modifier = Modifier,
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
            val description = stringResource(R.string.avarta_description, index + 1, spoken(avarta, tala, karvaiWord))
            AvartaRow(
                avarta = avarta,
                tala = tala,
                activeAkshara = position?.takeIf { it.avarta == index }?.akshara,
                activeSlot = position?.takeIf { it.avarta == index }?.slot,
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
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        avarta.aksharas.forEachIndexed { i, akshara ->
            if (i > 0 && tala.beats[i].startsAnga) AngaBar()
            AksharaCell(
                akshara = akshara,
                mark = tala.beats[i].label,
                active = i == activeAkshara,
                activeSlot = activeSlot.takeIf { i == activeAkshara },
                modifier = Modifier.weight(1f),
            )
        }
        AngaBar(double = true)
    }
}

@Composable
private fun AksharaCell(
    akshara: Akshara,
    mark: String,
    active: Boolean,
    activeSlot: Int?,
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
        Text(mark, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
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
        Text(
            text,
            fontSize = if (swaraCount > 2) 13.sp else if (swaraCount == 2) 17.sp else 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (active) colors.onPrimaryContainer else colors.onSurface,
        )
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
 * The tala being kept: one mark per beat of the avarta, the current beat
 * filled. At higher kalas several written aksharas share one beat, so this is
 * separate from the marks printed above the notation. Visual only; screen
 * reader users follow the click and the music.
 */
@Composable
fun TalaStrip(beatInAvarta: Int?, modifier: Modifier = Modifier, tala: Tala = Tala.ADI) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics {},
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

/** Text a screen reader speaks for an avarta: angas separated by commas, karvai as a word. */
internal fun spoken(avarta: Avarta, tala: Tala, karvaiWord: String): String =
    avarta.aksharas.mapIndexed { i, a ->
        val word = if (a == Akshara.Karvai) karvaiWord else a.toString()
        if (i > 0 && tala.beats[i].startsAnga) ", $word" else if (i > 0) " $word" else word
    }.joinToString("")
