package com.baijum.purandara.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.baijum.purandara.domain.schedule.NoteEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.singalong.Attention
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.singalong.PassSummary
import com.baijum.purandara.singalong.SwaraReading
import com.baijum.purandara.singalong.Tendency
import com.baijum.purandara.singalong.Zone
import com.baijum.purandara.viewmodel.TraceSample
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * The pitch lane: the current avarta's expected swaras as a staircase, the
 * sung pitch as a trace over them, and swara lines S R G M P D N S′. One
 * accessibility node whose state is the live reading (read when focused).
 */
@Composable
fun PitchLane(
    schedule: Schedule,
    avarta: Int?,
    pass: Int?,
    trace: List<TraceSample>,
    live: SwaraReading?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val avartaSeconds = schedule.settings.tala.aksharas * schedule.settings.writtenAksharaSeconds
    val reading = liveText(live)
    val label = stringResource(R.string.pitch_lane)
    val raga = schedule.settings.raga
    val lines = SwaraLetter.entries.map { Note(raga.swara(it)) } + Note(raga.swara(SwaraLetter.S), Octave.TARA)
    val sa = schedule.settings.saHz

    Column(modifier.clearAndSetSemantics {
        contentDescription = label
        stateDescription = reading
    }) {
        val laneColors = LaneColors(colors.outlineVariant, colors.onSurfaceVariant, colors.primary, colors.secondary, colors.error)
        Canvas(Modifier.fillMaxWidth().height(LANE_HEIGHT.dp)) {
            val lane = Lane(size.width, size.height, sa, avarta?.let { it * avartaSeconds } ?: 0.0, avartaSeconds)
            drawSwaraLines(lane, lines, laneColors, measurer)
            val a = avarta ?: return@Canvas
            drawStaircase(lane, schedule.notes.filter { it.position.avarta == a }, laneColors)
            drawTrace(lane, trace.filter { it.pass == (pass ?: 0) && lane.covers(it.secondsInPass) }, laneColors)
        }
        Text(reading, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

private class LaneColors(val line: Color, val label: Color, val inTune: Color, val close: Color, val off: Color)

/** Geometry of the pitch lane: time across (one avarta), cents from Sa up the side. */
private class Lane(val width: Float, val height: Float, val saHz: Double, val start: Double, val span: Double) {
    fun y(cents: Double) = (height * (1 - (cents - LOW_CENTS) / (HIGH_CENTS - LOW_CENTS))).toFloat()
    fun x(seconds: Double) = (LABEL_WIDTH + (width - LABEL_WIDTH) * ((seconds - start) / span)).toFloat()
    fun cents(note: Note) = CENTS_PER_OCTAVE * log2(note.frequency(saHz) / saHz)
    fun covers(seconds: Double) = seconds >= start && seconds < start + span
}

private fun DrawScope.drawSwaraLines(lane: Lane, lines: List<Note>, colors: LaneColors, measurer: TextMeasurer) {
    lines.forEach { note ->
        val y = lane.y(lane.cents(note))
        drawLine(colors.line, Offset(LABEL_WIDTH, y), Offset(lane.width, y), strokeWidth = 1f)
        val text = note.swara.symbol.take(1) + if (note.octave == Octave.TARA) "′" else ""
        drawText(measurer, text, Offset(0f, y - LABEL_RAISE), style = TextStyle(color = colors.label, fontSize = LABEL_SP.sp))
    }
}

private fun DrawScope.drawStaircase(lane: Lane, notes: List<NoteEvent>, colors: LaneColors) {
    notes.forEach { e ->
        val y = lane.y(lane.cents(e.note))
        drawLine(colors.inTune.copy(alpha = STAIRCASE_ALPHA), Offset(lane.x(e.startSeconds), y), Offset(lane.x(e.startSeconds + e.durationSeconds), y), strokeWidth = STAIRCASE_WIDTH)
    }
}

private fun DrawScope.drawTrace(lane: Lane, trace: List<TraceSample>, colors: LaneColors) {
    trace.forEach { t ->
        val c = when (t.zone) {
            Zone.IN_TUNE -> colors.inTune
            Zone.CLOSE -> colors.close
            Zone.OFF -> colors.off
        }
        drawCircle(c, radius = TRACE_RADIUS, center = Offset(lane.x(t.secondsInPass), lane.y(t.centsFromSa)))
    }
}

/** Carnatic display name with the variant as a subscript, e.g. "R₁"; tara with ′. */
fun swaraLabel(swara: com.baijum.purandara.domain.Swara, octave: Int = 0): String {
    val sub = swara.symbol.drop(1).map { SUBSCRIPTS[it - '0'] }.joinToString("")
    val mark = when {
        octave < 0 -> "̣"
        octave > 0 -> "′"
        else -> ""
    }
    return swara.symbol.take(1) + sub + mark
}

private val SUBSCRIPTS = listOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')

/** "P · in tune · +3¢ · G3", or "Not hearing you yet". */
@Composable
fun liveText(live: SwaraReading?): String {
    if (live == null) return stringResource(R.string.silent)
    val cents = live.centsOff.roundToInt()
    return "${swaraLabel(live.swara, live.octave)} · ${zoneWord(live.zone, cents.toDouble())} · ${if (cents >= 0) "+" else ""}$cents¢ · ${live.westernName}"
}

@Composable
private fun zoneWord(zone: Zone, cents: Double): String = when {
    zone == Zone.IN_TUNE -> stringResource(R.string.zone_in_tune)
    zone == Zone.CLOSE && cents > 0 -> stringResource(R.string.zone_slightly_high)
    zone == Zone.CLOSE -> stringResource(R.string.zone_slightly_low)
    cents > 0 -> stringResource(R.string.zone_high)
    else -> stringResource(R.string.zone_low)
}

/** Glyph shown under a judged swara in the notation, or null for no mark. */
fun markGlyph(result: NoteResult?): String? = when (result) {
    null, NoteResult.TooShort -> null
    NoteResult.NoVoice -> "○"
    is NoteResult.Judged -> when {
        result.sung != null -> "✕"
        result.octaveShift < 0 -> "⇣"
        result.octaveShift > 0 -> "⇡"
        result.zone == Zone.IN_TUNE -> "✓"
        result.high -> "↑"
        else -> "↓"
    }
}

/** Colour for a mark glyph. */
@Composable
fun markColor(result: NoteResult?): Color = when (result) {
    is NoteResult.Judged -> when {
        result.sung == null && result.octaveShift == 0 && result.zone == Zone.IN_TUNE -> MaterialTheme.colorScheme.primary
        result.sung == null && result.octaveShift == 0 && result.zone == Zone.CLOSE -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Words for a mark, for screen readers ("in tune", "high", "not heard"…). */
@Composable
fun markWords(result: NoteResult?): String? = when (result) {
    null, NoteResult.TooShort -> null
    NoteResult.NoVoice -> stringResource(R.string.mark_no_voice)
    is NoteResult.Judged -> when {
        result.sung != null -> stringResource(R.string.mark_sang, swaraLabel(result.sung!!.swara, result.sung!!.octave))
        result.octaveShift < 0 -> stringResource(R.string.mark_octave_low)
        result.octaveShift > 0 -> stringResource(R.string.mark_octave_high)
        else -> zoneWord(result.zone, result.cents)
    }
}

/** Card after each pass; a polite live region so screen readers hear it once. */
@Composable
fun PassSummaryCard(pass: Int, summary: PassSummary, modifier: Modifier = Modifier) {
    val headline = stringResource(R.string.pass_summary, pass, summary.inTuneOrClose, summary.judged)
    val attention = summary.attention.map { attentionText(it) }
    val needs = if (attention.isEmpty()) null else stringResource(R.string.needs_attention, attention.joinToString("; "))
    Card(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(12.dp)) {
            Text(headline, style = MaterialTheme.typography.titleSmall)
            if (needs != null) Text(needs, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun attentionText(a: Attention): String {
    val name = swaraLabel(a.note.swara, a.note.octave.offset)
    val res = when (a.tendency) {
        Tendency.HIGH -> R.string.attention_high
        Tendency.LOW -> R.string.attention_low
        Tendency.OCTAVE_LOW -> R.string.attention_octave_low
        Tendency.OCTAVE_HIGH -> R.string.attention_octave_high
        Tendency.WRONG_SWARA -> R.string.attention_wrong
    }
    return stringResource(res, name, a.times, a.of)
}

private const val LANE_HEIGHT = 120
private const val CENTS_PER_OCTAVE = 1200.0
private const val LOW_CENTS = -150.0 // a little below Sa…
private const val HIGH_CENTS = 1350.0 // …to a little above upper Sa
private const val LABEL_WIDTH = 36f
private const val LABEL_RAISE = 18f
private const val LABEL_SP = 11
private const val STAIRCASE_ALPHA = 0.35f
private const val STAIRCASE_WIDTH = 10f
private const val TRACE_RADIUS = 3.5f
