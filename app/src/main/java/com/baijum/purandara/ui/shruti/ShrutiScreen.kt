package com.baijum.purandara.ui.shruti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldTarget
import com.baijum.purandara.singalong.Zone
import com.baijum.purandara.ui.player.liveText
import com.baijum.purandara.ui.saLabel
import com.baijum.purandara.viewmodel.FreeTracePoint
import com.baijum.purandara.viewmodel.PracticeUiState
import kotlin.math.roundToInt

/**
 * Shruti practice (docs/plan-shruti.md): the tanpura plays, the student's
 * pitch is traced against the swara lines, and hold practice times a held
 * Sa, Pa or upper Sa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrutiScreen(state: PracticeUiState, actions: ShrutiActions, micAllowed: Boolean = state.singAlong.micPermitted) {
    val shruti = state.shruti
    val settings = state.settings
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.shruti_title))
                        Text(
                            stringResource(R.string.sa_playing, saLabel(settings.saMidi, settings.saCents)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            if (state.outputChanged) {
                OutputChangedNotice(actions.onStartAgain)
                HoldPanel(state, actions.onHoldChange)
            } else if (!micAllowed) {
                Text(stringResource(R.string.mic_needed_shruti), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = actions.onAllowMic, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.allow_mic))
                }
            } else {
                val reading = if (shruti.listeningToRoom) stringResource(R.string.room_check) else liveText(shruti.reading)
                val label = stringResource(R.string.pitch_lane)
                Column(Modifier.clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = reading
                }) {
                    if (!large) FreePitchTrace(shruti.trace, settings.saHz, Modifier.fillMaxWidth().height(TRACE_HEIGHT))
                    Text(reading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
                if (state.singAlong.onSpeaker) {
                    Text(
                        stringResource(R.string.headphones_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HoldPanel(state, actions.onHoldChange)
            }
        }
    }
}

/** Shared stopped message, announced once, with an explicit accessible restart action. */
@Composable
internal fun OutputChangedNotice(onStartAgain: () -> Unit) {
    Text(
        stringResource(R.string.audio_output_changed),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Button(onClick = onStartAgain, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.start_again))
    }
}

@Composable
private fun HoldPanel(state: PracticeUiState, onChange: (HoldTarget, HoldGoal) -> Unit) {
    val shruti = state.shruti
    val hold = shruti.hold
    val targetName = stringResource(targetLabel(shruti.target))
    val goalSeconds = shruti.goal.seconds.roundToInt()
    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.hold_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.hold_target), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            ChoiceRow(HoldTarget.entries, shruti.target, { onChange(it, shruti.goal) }) { stringResource(targetLabel(it)) }
            Text(stringResource(R.string.hold_goal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            ChoiceRow(HoldGoal.entries, shruti.goal, { onChange(shruti.target, it) }) {
                stringResource(R.string.seconds_short, it.seconds.roundToInt())
            }
            if (state.outputChanged) return@Column
            val current = hold?.currentSeconds ?: 0.0
            LinearProgressIndicator(
                progress = { (current / shruti.goal.seconds).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                if (current > 0) {
                    stringResource(R.string.hold_progress, targetName, current, goalSeconds)
                } else {
                    stringResource(R.string.hold_waiting, targetName)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (hold != null && hold.bestSeconds > 0) {
                Text(stringResource(R.string.hold_best, hold.bestSeconds), style = MaterialTheme.typography.bodyMedium)
            }
            if (hold?.reached == true) {
                Text(
                    stringResource(R.string.hold_reached, targetName, goalSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    // Announced once when it appears.
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(choices: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { i, choice ->
            SegmentedButton(
                selected = selected == choice,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
            ) { Text(label(choice)) }
        }
    }
}

private fun targetLabel(target: HoldTarget) = when (target) {
    HoldTarget.SA -> R.string.hold_sa
    HoldTarget.PA -> R.string.hold_pa
    HoldTarget.UPPER_SA -> R.string.hold_upper_sa
}

/** The last ten seconds of the student's pitch against the raga's swara lines (decorative; the reading is spoken). */
@Composable
private fun FreePitchTrace(trace: List<FreeTracePoint>, saHz: Double, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val raga = VarisaiCatalog.raga
    val lines = listOf(Octave.MANDRA to listOf(SwaraLetter.P, SwaraLetter.D, SwaraLetter.N), Octave.MADHYA to SwaraLetter.entries)
        .flatMap { (octave, letters) -> letters.map { Note(raga.swara(it), octave) } } + Note(raga.swara(SwaraLetter.S), Octave.TARA)
    Canvas(modifier) {
        fun y(cents: Double) = (size.height * (1 - (cents - LOW_CENTS) / (HIGH_CENTS - LOW_CENTS))).toFloat()
        val labelWidth = LABEL_WIDTH.dp.toPx()
        lines.forEach { note ->
            val yy = y(Pitch.cents(saHz, note.frequency(saHz)))
            val isSa = note.swara == raga.swara(SwaraLetter.S)
            drawLine(if (isSa) colors.primary else colors.outlineVariant, Offset(labelWidth, yy), Offset(size.width, yy), strokeWidth = if (isSa) 2f else 1f)
            val name = note.swara.symbol.take(1) + when (note.octave) {
                Octave.TARA -> "′"
                Octave.MANDRA -> "."
                Octave.MADHYA -> ""
            }
            drawText(measurer, name, Offset(0f, yy - LABEL_RAISE), style = TextStyle(color = colors.onSurfaceVariant, fontSize = LABEL_SP.sp))
        }
        val now = trace.lastOrNull()?.seconds ?: return@Canvas
        trace.forEach { p ->
            val x = labelWidth + (size.width - labelWidth) * (1 - ((now - p.seconds) / WINDOW_SECONDS)).toFloat()
            val c: Color = when (p.zone) {
                Zone.IN_TUNE -> colors.primary
                Zone.CLOSE -> colors.secondary
                Zone.OFF -> colors.error
            }
            drawCircle(c, radius = DOT_RADIUS, center = Offset(x, y(p.centsFromSa)))
        }
    }
}

private val TRACE_HEIGHT = 220.dp
private const val LOW_CENTS = -800.0
private const val HIGH_CENTS = 1350.0
private const val WINDOW_SECONDS = 10.0
private const val LABEL_WIDTH = 22
private const val LABEL_SP = 11
private const val LABEL_RAISE = 18f
private const val DOT_RADIUS = 3.5f
private const val LARGE_FONT_SCALE = 1.5f
