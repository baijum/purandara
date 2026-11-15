package com.baijum.purandara.ui.recordings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.CustomRaga
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Swara

/**
 * "Your own raga" (docs/plan-recording-insights.md, step 3): a name and, for
 * the arohana and the avarohana, one swara per letter or none. The checks
 * show as one sentence; Save waits for them.
 */
@Composable
fun RagaEditorScreen(
    editing: CustomRaga?,
    ragas: CustomRagas,
    onSave: (String, List<Swara>, List<Swara>) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var up by rememberSaveable { mutableStateOf(editing?.arohana.orEmpty().map { it.name }) }
    var down by rememberSaveable { mutableStateOf(editing?.avarohana.orEmpty().map { it.name }) }
    var same by rememberSaveable { mutableStateOf(editing == null || editing.arohana == editing.avarohana) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val arohana = up.map { Swara.valueOf(it) }.sortedBy { it.semitone }
    val avarohana = if (same) arohana else down.map { Swara.valueOf(it) }.sortedBy { it.semitone }
    val problem = ragas.problem(editing?.key, name, arohana, avarohana)
    Scaffold(topBar = { EditorBar(editing == null, enabled = problem == null, onClose) { onSave(name, arohana, avarohana) } }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(CustomRaga.MAX_NAME) },
                label = { Text(stringResource(R.string.raga_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.raga_help), style = MaterialTheme.typography.bodyMedium)
            Heading(R.string.raga_arohana)
            LetterChoices(arohana) { up = it.map { s -> s.name } }
            SameSwitch(same) {
                if (!it) down = up
                same = it
            }
            if (!same) {
                Heading(R.string.raga_avarohana)
                LetterChoices(avarohana) { down = it.map { s -> s.name } }
            }
            ReadBack(R.string.raga_read_up, CustomRaga.line(arohana, descending = false), CustomRaga.spokenLine(arohana, descending = false))
            ReadBack(R.string.raga_read_down, CustomRaga.line(avarohana, descending = true), CustomRaga.spokenLine(avarohana, descending = true))
            if (problem != null) {
                Text(
                    problem,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (editing != null) {
                OutlinedButton(onClick = { deleting = true }) {
                    Text(stringResource(R.string.raga_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (deleting && editing != null) {
        DeleteRagaDialog(editing.name, onDismiss = { deleting = false }) {
            deleting = false
            onDelete()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBar(new: Boolean, enabled: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(if (new) R.string.raga_new_title else R.string.raga_edit_title), maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        },
        actions = { TextButton(onClick = onSave, enabled = enabled) { Text(stringResource(R.string.save)) } },
    )
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SameSwitch(same: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = same, role = Role.Switch, onValueChange = onChange).defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.raga_same_down), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = same, onCheckedChange = null)
    }
}

@Composable
private fun DeleteRagaDialog(name: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.raga_delete_title)) },
        text = { Text(stringResource(R.string.raga_delete_body, name)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.raga_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Up: S R₂ G₃ P D₂ S′", read as "Up: Sa, Ri 2, …". */
@Composable
private fun ReadBack(label: Int, line: String, spoken: String) {
    val description = stringResource(label, spoken)
    Text(
        stringResource(label, line),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    )
}

/** A row per letter (Ri … Ni): None or one of its swaras, as radio chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterChoices(chosen: List<Swara>, onChange: (List<Swara>) -> Unit) {
    val none = stringResource(R.string.raga_none)
    CustomRaga.LETTERS.forEach { letter ->
        val current = chosen.firstOrNull { CustomRaga.letterOf(it) == letter }
        fun choose(swara: Swara?) = onChange(chosen.filter { CustomRaga.letterOf(it) != letter } + listOfNotNull(swara))
        val letterName = CustomRaga.spoken(letter)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(letterName, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(none, stringResource(R.string.raga_letter_choice, letterName, none), current == null) { choose(null) }
                CustomRaga.choices(letter).forEach { swara ->
                    Chip(CustomRaga.label(swara), CustomRaga.spokenSwara(swara), current == swara) { choose(swara) }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, spoken: String, selected: Boolean, onClick: () -> Unit) {
    val colour = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .border(if (selected) 2.dp else 1.dp, colour, RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
    ) {
        Box(Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
