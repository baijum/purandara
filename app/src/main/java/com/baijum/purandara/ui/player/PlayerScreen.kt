package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.viewmodel.PlaybackState
import com.baijum.purandara.viewmodel.PracticeUiState
import kotlin.math.roundToInt

/** Plays one exercise: notation on top, controls below, Play/Stop at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    varisai: Varisai,
    state: PracticeUiState,
    actions: PlayerActions,
    micDenied: Boolean = false,
) {
    val settings = state.settings
    val playback = state.playback
    val canPlay = state.samplesLoaded
    var showSettings by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            PlayerTopBar(
                varisai.title,
                singAlong = settings.singAlong,
                onBack = actions.onBack,
                onSingAlong = actions.onToggleSingAlong,
                onSettings = { showSettings = true },
            )
        },
        bottomBar = { PlayButtonBar(playback.playing, canPlay, actions.onTogglePlay) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TalaStrip(
                beatInAvarta = playback.beatInAvarta,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            // Also shown after a refused permission, so the student learns why nothing happened.
            if (settings.singAlong || (micDenied && !state.singAlong.micPermitted)) {
                SingAlongPanel(varisai, state, micDenied, actions.onOpenAppSettings)
            }
            NotationView(
                varisai = varisai,
                position = playback.position,
                marks = if (settings.singAlong) state.singAlong.marks else emptyMap(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            PlaybackStatus(playback, settings.saMidi)
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

@Composable
private fun PlayButtonBar(playing: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onToggle,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        ) {
            Icon(painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (playing) R.string.stop else R.string.play))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(title: String, singAlong: Boolean, onBack: () -> Unit, onSingAlong: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            val state = stringResource(if (singAlong) R.string.sing_along_on else R.string.sing_along_off)
            IconToggleButton(
                checked = singAlong,
                onCheckedChange = { onSingAlong() },
                modifier = Modifier.semantics { stateDescription = state },
            ) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.sing_along))
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.practice_settings))
            }
        },
    )
}

/** Pitch lane, live reading, headphone hint, permission message and the last pass summary. */
@Composable
private fun SingAlongPanel(varisai: Varisai, state: PracticeUiState, micDenied: Boolean, onOpenAppSettings: () -> Unit) {
    val sing = state.singAlong
    val schedule = remember(varisai, state.settings) { Schedule.of(varisai, state.settings.performance()) }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (micDenied && !sing.micPermitted) {
            Text(stringResource(R.string.mic_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.open_settings)) }
            return@Column
        }
        PitchLane(
            schedule = schedule,
            avarta = state.playback.position?.avarta,
            pass = state.playback.position?.repetition,
            trace = sing.trace,
            live = sing.live,
            countingIn = state.playback.countingIn,
        )
        if (sing.onSpeaker) {
            Text(stringResource(R.string.headphones_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sing.lastSummary?.let { (pass, summary) -> PassSummaryCard(pass, summary) }
    }
}

/**
 * While playing: the Sa in use, then "Count-in…" during the count-in or
 * "Pass n of m" when an exercise repeats within an avarta. Wraps onto two
 * lines at large text sizes rather than clipping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackStatus(playback: PlaybackState, saMidi: Int) {
    if (!playback.playing) return
    val pass = playback.position?.repetition
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatusText(
            stringResource(R.string.sa_playing, stringResource(R.string.sa_value, Shruti.noteName(saMidi), Shruti.kattai(saMidi))),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            playback.countingIn -> StatusText(stringResource(R.string.counting_in), MaterialTheme.colorScheme.primary)
            playback.repetitions > 1 -> StatusText(
                stringResource(R.string.pass_of, (pass ?: 0) + 1, playback.repetitions),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Text(stringResource(R.string.harmonium_guide), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true to R.string.guide_all, false to R.string.guide_off).forEachIndexed { i, (guide, label) ->
                SegmentedButton(
                    selected = settings.harmoniumGuide == guide,
                    onClick = { onSettingsChange { it.copy(harmoniumGuide = guide) } },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(stringResource(label)) }
            }
        }
        ToggleRow(stringResource(R.string.echo_cancellation), settings.echoCancellation) { on ->
            onSettingsChange { it.copy(echoCancellation = on) }
        }
        Text(
            stringResource(R.string.echo_cancellation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
