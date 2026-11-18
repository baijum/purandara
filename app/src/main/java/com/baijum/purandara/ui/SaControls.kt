package com.baijum.purandara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.CustomRaga
import com.baijum.purandara.domain.Melakartas
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.VoiceRange
import com.baijum.purandara.domain.varisai.SwaraNumbers
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.WordsDisplay
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

/**
 * The raga the varisais and alankarams are sung in (docs/plan-other-ragas.md):
 * a field that opens the list of ragas, each with its arohanam (read aloud in
 * full) and a button to hear its scale first (docs/plan-raga-followups.md F1).
 */
@Composable
fun RagaPicker(
    raga: Raga,
    previewKey: String?,
    onSelect: (Raga) -> Unit,
    onPreview: (Raga) -> Unit,
    onStopPreview: () -> Unit,
    /** The student's own ragas (docs/plan-own-raga-varisais.md), and opening the editor (null: a new one). */
    ownRagas: List<CustomRaga> = emptyList(),
    onEditOwn: (String?) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.varisai_raga)
    OutlinedCard(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "$label, ${raga.name}" },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(raga.name, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
    if (open) {
        val close = {
            open = false
            onStopPreview()
        }
        val actions = RagaListActions(
            select = { choice ->
                onSelect(choice)
                close()
            },
            preview = onPreview,
            edit = { key ->
                close()
                onEditOwn(key)
            },
        )
        RagaListDialog(raga, previewKey, ownRagas, actions, close)
    }
}

/** What the raga list can do: choose a raga (and close), hear one, edit an own raga (null: a new one). */
private class RagaListActions(val select: (Raga) -> Unit, val preview: (Raga) -> Unit, val edit: (String?) -> Unit)

/** The app's ragas for the varisais, then the student's own. */
@Composable
private fun RagaListDialog(
    current: Raga,
    previewKey: String?,
    ownRagas: List<CustomRaga>,
    actions: RagaListActions,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.varisai_raga), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.varisai_raga_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VarisaiCatalog.ragas.forEach { choice ->
                    RagaChoiceRow(choice, choice.key == current.key, choice.key == previewKey, { actions.select(choice) }, { actions.preview(choice) })
                }
                OwnRagaRows(ownRagas, current, previewKey, actions)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * "Your own ragas": the seven-swara ones to choose (with play and Edit), the
 * others shown with why they can't be, and Add your own raga….
 */
@Composable
private fun OwnRagaRows(ownRagas: List<CustomRaga>, current: Raga, previewKey: String?, actions: RagaListActions) {
    val onEdit = actions.edit
    Text(
        stringResource(R.string.own_ragas),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
    ownRagas.forEach { own ->
        val raga = Ragas.forVarisais(own.key)
        val editLabel = stringResource(R.string.raga_edit_named, own.name)
        val edit = @Composable {
            TextButton(onClick = { onEdit(own.key) }, modifier = Modifier.semantics { contentDescription = editLabel }) {
                Text(stringResource(R.string.raga_edit))
            }
        }
        if (raga != null) {
            RagaChoiceRow(raga, raga.key == current.key, raga.key == previewKey, { actions.select(raga) }, { actions.preview(raga) }, edit)
        } else {
            val reason = stringResource(R.string.own_raga_needs_seven)
            val line = CustomRaga.line(own.arohana, descending = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).padding(start = 48.dp, top = 4.dp, bottom = 4.dp)
                        .clearAndSetSemantics { contentDescription = "${own.name}: $reason" },
                ) {
                    Text(own.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$reason · $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                edit()
            }
        }
    }
    TextButton(onClick = { onEdit(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.raga_add))
    }
}

/** One raga in the list: choose it (the row), hear its scale (the button), and [trailing] (Edit, for the student's own). */
@Composable
private fun RagaChoiceRow(
    choice: Raga,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val swaras = choice.swaras.drop(1)
    val detail = if (choice == VarisaiCatalog.raga) {
        stringResource(R.string.varisai_raga_usual)
    } else {
        stringResource(R.string.raga_mela, choice.mela ?: Melakartas.numberOf(choice) ?: 0)
    }
    val spoken = "${choice.name}, $detail: ${CustomRaga.spokenLine(swaras, descending = false)}"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.RadioButton
                    this.selected = selected
                },
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Text(choice.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "$detail · ${CustomRaga.line(swaras, descending = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPreview) {
            Icon(
                painterResource(if (previewing) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = stringResource(if (previewing) R.string.raga_preview_stop else R.string.raga_preview_play, choice.name),
            )
        }
        trailing?.invoke()
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

/** Swaras / Words / Both: what a gitam's notation shows. */
@Composable
fun WordsPicker(words: WordsDisplay, onChange: (WordsDisplay) -> Unit) {
    Column {
        Text(stringResource(R.string.gitam_notation), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val choices = listOf(
                WordsDisplay.SWARAS to R.string.words_swaras,
                WordsDisplay.WORDS to R.string.words_words,
                WordsDisplay.BOTH to R.string.words_both,
            )
            choices.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = words == choice,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            stringResource(R.string.gitam_notation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Off / In other ragas / Always: swara numbers in the notation (docs/plan-raga-followups.md F2). */
@Composable
fun SwaraNumbersPicker(numbers: SwaraNumbers, onChange: (SwaraNumbers) -> Unit) {
    Column {
        Text(stringResource(R.string.swara_numbers), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val choices = listOf(
                SwaraNumbers.OFF to R.string.swara_numbers_off,
                SwaraNumbers.OTHER_RAGAS to R.string.swara_numbers_other,
                SwaraNumbers.ALWAYS to R.string.swara_numbers_always,
            )
            choices.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = numbers == choice,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            stringResource(R.string.swara_numbers_desc),
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
