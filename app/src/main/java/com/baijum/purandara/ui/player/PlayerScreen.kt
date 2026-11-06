package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.viewmodel.PlaybackState
import kotlin.math.roundToInt

/** Plays one exercise: notation on top, controls below, Play/Stop at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    varisai: Varisai,
    settings: PracticeSettings,
    playback: PlaybackState,
    canPlay: Boolean,
    actions: PlayerActions,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { PlayerTopBar(varisai.title, onBack = actions.onBack, onSettings = { showSettings = true }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = actions.onTogglePlay,
                    enabled = canPlay,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) {
                    Icon(
                        painterResource(if (playback.playing) R.drawable.ic_stop else R.drawable.ic_play),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (playback.playing) R.string.stop else R.string.play))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TalaStrip(
                beatInAvarta = playback.beatInAvarta,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            NotationView(
                varisai = varisai,
                position = playback.position,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            PlaybackStatus(playback)
            HorizontalDivider()
            QuickControls(settings, actions.onSettingsChange, Modifier.padding(16.dp))
        }
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            PracticeSettingsSheet(
                settings = settings,
                onSettingsChange = actions.onSettingsChange,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.practice_settings))
            }
        },
    )
}

/** "Count-in…" during the count-in, or "Pass n of m" when an exercise repeats within an avarta. */
@Composable
private fun PlaybackStatus(playback: PlaybackState) {
    val pass = playback.position?.repetition
    when {
        playback.countingIn -> StatusText(stringResource(R.string.counting_in), MaterialTheme.colorScheme.primary)
        playback.repetitions > 1 -> StatusText(
            stringResource(R.string.pass_of, (pass ?: 0) + 1, playback.repetitions),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** What changes while practising: tempo and speed. The rest is in [PracticeSettingsSheet]. */
@Composable
private fun QuickControls(
    settings: PracticeSettings,
    onSettingsChange: ((PracticeSettings) -> PracticeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TempoControl(settings.tempo) { tempo -> onSettingsChange { it.copy(tempo = tempo) } }
        Text(stringResource(R.string.kala), style = MaterialTheme.typography.titleSmall)
        val kalaNames = listOf(R.string.kala_1, R.string.kala_2, R.string.kala_3, R.string.kala_4).map { stringResource(it) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PerformanceSettings.KALA_RANGE.forEachIndexed { i, kala ->
                SegmentedButton(
                    selected = settings.kala == kala,
                    onClick = { onSettingsChange { it.copy(kala = kala) } },
                    shape = SegmentedButtonDefaults.itemShape(i, PerformanceSettings.KALA_RANGE.count()),
                    modifier = Modifier.semantics { contentDescription = kalaNames[i] },
                ) { Text("$kala", modifier = Modifier.clearAndSetSemantics {}) }
            }
        }
    }
}

/** Sa, tuning, drone, levels, click, count-in and repeat. */
@Composable
private fun PracticeSettingsSheet(
    settings: PracticeSettings,
    onSettingsChange: ((PracticeSettings) -> PracticeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.practice_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        SaPicker(settings.saMidi) { midi -> onSettingsChange { it.copy(saMidi = midi) } }

        Text(stringResource(R.string.tuning), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Tuning.JUST to R.string.tuning_just, Tuning.EQUAL to R.string.tuning_equal).forEachIndexed { i, (tuning, label) ->
                SegmentedButton(
                    selected = settings.tuning == tuning,
                    onClick = { onSettingsChange { it.copy(tuning = tuning) } },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(stringResource(label)) }
            }
        }

        ToggleRow(stringResource(R.string.drone), settings.drone) { on -> onSettingsChange { it.copy(drone = on) } }
        LevelSlider(stringResource(R.string.drone_volume), settings.droneLevel, enabled = settings.drone) { v ->
            onSettingsChange { it.copy(droneLevel = v) }
        }
        LevelSlider(stringResource(R.string.harmonium_volume), settings.harmoniumLevel, enabled = true) { v ->
            onSettingsChange { it.copy(harmoniumLevel = v) }
        }
        ToggleRow(stringResource(R.string.click), settings.click) { on -> onSettingsChange { it.copy(click = on) } }
        ToggleRow(stringResource(R.string.count_in), settings.countIn) { on -> onSettingsChange { it.copy(countIn = on) } }
        ToggleRow(stringResource(R.string.loop), settings.loop) { on -> onSettingsChange { it.copy(loop = on) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaPicker(saMidi: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = @Composable { midi: Int -> stringResource(R.string.sa_value, Shruti.noteName(midi), Shruti.kattai(midi)) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(saMidi),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sa)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Shruti.SA_RANGE.forEach { midi ->
                DropdownMenuItem(
                    text = { Text(label(midi)) },
                    onClick = {
                        expanded = false
                        onSelect(midi)
                    },
                )
            }
        }
    }
}

@Composable
private fun TempoControl(tempo: Int, onChange: (Int) -> Unit) {
    val range = PerformanceSettings.TEMPO_RANGE
    // Local while dragging; applied (and playback restarted) when the drag ends.
    var dragging by rememberSaveable { mutableStateOf(false) }
    var value by remember(tempo) { mutableFloatStateOf(tempo.toFloat()) }
    val shown = if (dragging) value.roundToInt() else tempo
    val description = stringResource(R.string.tempo_value, shown)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tempo), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.tempo_short, shown), style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.slower)) { onChange((tempo - TEMPO_STEP).coerceIn(range)) }
            Slider(
                value = value,
                onValueChange = {
                    dragging = true
                    value = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onChange(value.roundToInt())
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / TEMPO_STEP - 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { stateDescription = description },
            )
            StepButton("+", stringResource(R.string.faster)) { onChange((tempo + TEMPO_STEP).coerceIn(range)) }
        }
    }
}

/** A 0..1 level, applied when the drag ends so playback restarts only once. */
@Composable
private fun LevelSlider(label: String, level: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    var value by remember(level) { mutableFloatStateOf(level) }
    val percent = (value * PERCENT).roundToInt()
    val description = stringResource(R.string.percent, percent)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value) },
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = description
            },
        )
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) { Text(symbol, modifier = Modifier.clearAndSetSemantics {}) }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val TEMPO_STEP = 5
private const val PERCENT = 100
