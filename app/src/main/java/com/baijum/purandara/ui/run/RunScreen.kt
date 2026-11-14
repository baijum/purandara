package com.baijum.purandara.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.practice.RunEntry
import com.baijum.purandara.practice.RunStep
import com.baijum.purandara.ui.player.TalaStrip
import com.baijum.purandara.viewmodel.PracticeUiState
import com.baijum.purandara.viewmodel.RunPhase
import com.baijum.purandara.viewmodel.RunUiState
import kotlin.math.ceil

/**
 * One set practised as a run (docs/plan-practice-run.md): plan it, sing it,
 * then see it complete. Mirrors iOS's `RunView`.
 */
@Composable
fun RunScreen(state: PracticeUiState, actions: RunActions) {
    val run = state.run ?: return
    when (run.phase) {
        RunPhase.SETUP -> RunSetupScreen(run, state, actions)
        // The normal player screen plays it, with its tempo, room check and feedback.
        RunPhase.PLAYING -> Unit
        RunPhase.COMPLETE -> RunCompleteScreen(run, actions)
    }
}

// --- Setup ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunSetupScreen(run: RunUiState, state: PracticeUiState, actions: RunActions) {
    Scaffold(
        topBar = { RunTopBar(run.set.shortTitle, actions.onBack) },
        bottomBar = { StartBar(run, state.samplesLoaded, actions) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.outputChanged) {
                Text(
                    stringResource(R.string.audio_output_changed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Text(
                stringResource(R.string.run_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            BulkRunControls(actions.onApplySpeed, actions.onApplyRepeats)
            HorizontalDivider()
            run.steps.forEachIndexed { index, step ->
                RunStepRow(step, index, actions.onSetSpeed, actions.onSetRepeats)
                HorizontalDivider()
            }
        }
    }
}

/** Speed for every varisai at once, then repeats for every varisai at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkRunControls(onApplySpeed: (Int) -> Unit, onApplyRepeats: (Int) -> Unit) {
    var bulkRepeats by rememberSaveable { mutableIntStateOf(3) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.run_all_speed),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceSettings.KALA_RANGE.forEach { kala ->
                val description = stringResource(R.string.run_all_speed_desc, kala)
                OutlinedButton(
                    onClick = { onApplySpeed(kala) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
                ) {
                    Text("$kala", modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        Text(
            stringResource(R.string.run_all_repeats),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.run_fewer), enabled = bulkRepeats > 1) {
                bulkRepeats = (bulkRepeats - 1).coerceAtLeast(1)
                onApplyRepeats(bulkRepeats)
            }
            Text(
                repeatText(bulkRepeats),
                modifier = Modifier.widthIn(min = 72.dp),
                textAlign = TextAlign.Center,
            )
            StepButton("+", stringResource(R.string.run_more), enabled = bulkRepeats < RunEntry.MAX_REPEATS) {
                bulkRepeats = (bulkRepeats + 1).coerceAtMost(RunEntry.MAX_REPEATS)
                onApplyRepeats(bulkRepeats)
            }
        }
    }
}

@Composable
private fun RunStepRow(
    step: RunStep,
    index: Int,
    onSpeed: (Int, Int) -> Unit,
    onRepeats: (Int, Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(step.varisai.title, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.run_speed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PerformanceSettings.KALA_RANGE.forEachIndexed { i, kala ->
                val name = kalaName(kala)
                SegmentedButton(
                    selected = step.speed == kala,
                    onClick = { onSpeed(index, kala) },
                    shape = SegmentedButtonDefaults.itemShape(i, PerformanceSettings.KALA_RANGE.count()),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = name },
                ) {
                    Text("$kala", modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        Text(
            speedHint(step.speed, step.repeats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.run_sing), modifier = Modifier.weight(1f))
            StepButton("−", stringResource(R.string.run_fewer), enabled = step.repeats > 1) {
                onRepeats(index, step.repeats - 1)
            }
            Text(
                repeatText(step.repeats),
                modifier = Modifier.widthIn(min = 72.dp),
                textAlign = TextAlign.Center,
            )
            StepButton("+", stringResource(R.string.run_more), enabled = step.repeats < RunEntry.MAX_REPEATS) {
                onRepeats(index, step.repeats + 1)
            }
        }
    }
}

@Composable
private fun StartBar(run: RunUiState, samplesLoaded: Boolean, actions: RunActions) {
    val minutes = ceil(run.totalSeconds / 60.0).toInt()
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (minutes > 0) {
                Text(
                    stringResource(R.string.run_estimate, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (run.startStep > 0) {
                val next = run.steps.getOrNull(run.startStep)?.varisai?.title.orEmpty()
                Button(
                    onClick = { actions.onStart(run.startStep) },
                    enabled = samplesLoaded,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.run_start_at, next))
                }
                OutlinedButton(
                    onClick = { actions.onStart(0) },
                    enabled = samplesLoaded,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.run_start_beginning))
                }
            } else {
                Button(
                    onClick = { actions.onStart(0) },
                    enabled = samplesLoaded,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.run_start))
                }
            }
        }
    }
}

// --- Complete ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunCompleteScreen(run: RunUiState, actions: RunActions) {
    Scaffold(topBar = { RunTopBar(run.set.shortTitle, actions.onDone) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.run_complete_title, run.set.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.run_complete_body, run.steps.size),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = actions.onAgain,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.run_practise_again))
            }
            OutlinedButton(
                onClick = actions.onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.run_done))
            }
        }
    }
}

// --- Shared pieces ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.run_title, title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        },
    )
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Text(symbol, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun speedHint(speed: Int, repeats: Int): String {
    val range = if (speed == 1) stringResource(R.string.run_kala_only) else stringResource(R.string.run_kalas, speed)
    val times = repeatText(repeats)
    return if (speed == 1) "$range · $times" else "$range · ${stringResource(R.string.run_each, times)}"
}

@Composable
private fun repeatText(repeats: Int): String =
    if (repeats == 1) stringResource(R.string.run_once) else stringResource(R.string.run_times, repeats)

@Composable
private fun kalaName(kala: Int): String = stringResource(
    when (kala) {
        1 -> R.string.kala_1
        2 -> R.string.kala_2
        3 -> R.string.kala_3
        else -> R.string.kala_4
    },
)
