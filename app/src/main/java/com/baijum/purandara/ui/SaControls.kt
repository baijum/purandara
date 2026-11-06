package com.baijum.purandara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.VoiceRange
import com.baijum.purandara.singalong.Strictness
import kotlin.math.roundToInt

/** "C3 · 1 kattai", with " +12¢" when fine-tuned. */
@Composable
fun saLabel(midi: Int, cents: Int = 0): String =
    stringResource(R.string.sa_value, Shruti.noteName(midi), Shruti.kattai(midi)) + Shruti.fineTuneSuffix(cents)

/** The Sa choice, grouped by the voices that usually use each (docs/plan-shruti.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaPicker(saMidi: Int, saCents: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = saLabel(saMidi, saCents),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sa)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VoiceRange.entries.forEach { range ->
                Text(
                    voiceRangeTitle(range),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                )
                range.midis.filter { it in Shruti.SA_RANGE }.forEach { midi ->
                    DropdownMenuItem(
                        text = { Text(saLabel(midi)) },
                        onClick = {
                            expanded = false
                            onSelect(midi)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun voiceRangeTitle(range: VoiceRange): String = stringResource(
    when (range) {
        VoiceRange.LOWER -> R.string.voice_lower
        VoiceRange.MEN -> R.string.voice_men
        VoiceRange.WOMEN -> R.string.voice_women
        VoiceRange.HIGHER -> R.string.voice_higher
    },
)

/** Sa fine-tune in cents: a slider plus one-cent steps for exact matching. */
@Composable
fun FineTuneControl(cents: Int, onChange: (Int) -> Unit) {
    val range = Shruti.FINE_TUNE_RANGE
    var value by remember(cents) { mutableFloatStateOf(cents.toFloat()) }
    val shown = value.roundToInt()
    val label = stringResource(R.string.fine_tune)
    val description = stringResource(R.string.fine_tune_value, shown)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange((cents - 1).coerceIn(range)) }) { Text("−1¢") }
            Text(
                if (shown == 0) stringResource(R.string.fine_tune_none) else "${if (shown > 0) "+" else "−"}${kotlin.math.abs(shown)}¢",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { onChange((cents + 1).coerceIn(range)) }) { Text("+1¢") }
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = description
            },
        )
        Text(
            stringResource(R.string.fine_tune_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Gentle / Steady / Precise. */
@Composable
fun StrictnessPicker(strictness: Strictness, onChange: (Strictness) -> Unit) {
    Column {
        Text(stringResource(R.string.strictness), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val choices = listOf(
                Strictness.GENTLE to R.string.strictness_gentle,
                Strictness.STEADY to R.string.strictness_steady,
                Strictness.PRECISE to R.string.strictness_precise,
            )
            choices.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = strictness == choice,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            stringResource(R.string.strictness_desc, strictness.inTuneCents.roundToInt(), strictness.closeCents.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
