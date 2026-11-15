package com.baijum.purandara.ui.recordings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.recording.Closeness
import com.baijum.purandara.recording.Insights
import com.baijum.purandara.recording.RecordingInsights
import com.baijum.purandara.recording.Scale
import com.baijum.purandara.recording.SwaraInsight
import com.baijum.purandara.ui.saLabel

/**
 * "What are you singing?" (docs/plan-recording-insights.md): a raga (recent
 * ones first) and Free singing / Arohana / Avarohana / Both. A scale needs a
 * raga, so the scale row only shows once one is chosen.
 */
@Composable
internal fun IntentPicker(
    ragaKey: String?,
    scale: Scale?,
    recent: List<String>,
    onChange: (String?, Scale?) -> Unit,
    own: CustomRagas = CustomRagas(),
    onEditRaga: (String?) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.intent_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        RagaPicker(ragaKey, recent, own, onAdd = { onEditRaga(null) }) { key -> onChange(key, if (key == null) null else scale) }
        val mine = ragaKey?.let { own[it] }
        if (mine != null) {
            val label = stringResource(R.string.raga_edit_named, mine.name)
            TextButton(onClick = { onEditRaga(mine.key) }, modifier = Modifier.semantics { contentDescription = label }) {
                Text(stringResource(R.string.raga_edit))
            }
        }
        if (ragaKey != null) {
            val choices = listOf(null) + Scale.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = scale == choice,
                        onClick = { onChange(ragaKey, choice) },
                        shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                        icon = {},
                    ) { Text(scaleName(choice), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun scaleName(scale: Scale?): String = stringResource(
    when (scale) {
        null -> R.string.scale_free
        Scale.AROHANA -> R.string.scale_arohana
        Scale.AVAROHANA -> R.string.scale_avarohana
        Scale.BOTH -> R.string.scale_both
    },
)

/** The raga choice: none, the recent ragas, the app's by name, the student's own, then "Add your own raga…". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RagaPicker(
    ragaKey: String?,
    recent: List<String>,
    own: CustomRagas = CustomRagas(),
    onAdd: (() -> Unit)? = null,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.recording_raga_none)
    val ownRagas = own.toRagas()
    val recentRagas = recent.mapNotNull { key -> Ragas.builtIn(key) ?: ownRagas.firstOrNull { it.key == key } }
    val others = Ragas.all.filter { it !in recentRagas }.sortedBy { it.name }
    val mine = ownRagas.filter { r -> recentRagas.none { it.key == r.key } }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = ragaKey?.let { key -> Ragas.builtIn(key)?.name ?: ownRagas.firstOrNull { it.key == key }?.name } ?: none,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.recording_raga)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun choose(key: String?) {
                expanded = false
                onSelect(key)
            }
            DropdownMenuItem(text = { Text(none) }, onClick = { choose(null) })
            recentRagas.forEach { raga -> DropdownMenuItem(text = { Text(raga.name) }, onClick = { choose(raga.key) }) }
            if (recentRagas.isNotEmpty()) HorizontalDivider()
            others.forEach { raga -> DropdownMenuItem(text = { Text(raga.name) }, onClick = { choose(raga.key) }) }
            if (mine.isNotEmpty() || onAdd != null) HorizontalDivider()
            mine.forEach { raga -> DropdownMenuItem(text = { Text(raga.name) }, onClick = { choose(raga.key) }) }
            onAdd?.let { add ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.raga_add)) },
                    onClick = {
                        expanded = false
                        add()
                    },
                )
            }
        }
    }
}

/** "Sing: S R₁ G₃ M₁ P D₁ N₃ S′", large, read as one sentence. */
@Composable
internal fun ReferenceLine(raga: Raga, scale: Scale) {
    val spoken = RecordingInsights.referenceSpoken(raga, scale)
    Text(
        RecordingInsights.referenceLabels(raga, scale).joinToString("  "),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
    )
}

/**
 * "How it went": the Sa the notes fit (with a button to use it), a chip per
 * swara, a sentence, the steadiness of held notes, and "no tanpura heard".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HowItWent(insights: Insights, onUseSa: (Int, Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.how_it_went), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (insights.tanpuraNotHeard) Text(stringResource(R.string.no_tanpura_heard), style = MaterialTheme.typography.bodyMedium)
        insights.suggestedSa?.let { (midi, cents) ->
            val sa = saLabel(midi, cents)
            Text(stringResource(R.string.recording_fitted_sa, sa), style = MaterialTheme.typography.bodyLarge)
            val description = stringResource(R.string.recording_fitted_sa_use, sa)
            OutlinedButton(onClick = { onUseSa(midi, cents) }, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.recording_tanpura_use))
            }
        }
        if (insights.swaras.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.swaras.forEach { SwaraChip(it) }
            }
        }
        Text(insights.summary, style = MaterialTheme.typography.bodyLarge)
        insights.steadiness.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** "G₃ +24¢" (coloured by closeness) or "P not heard", read as "Ga, 24 cents sharp". */
@Composable
private fun SwaraChip(s: SwaraInsight) {
    val colour = when (s.closeness) {
        Closeness.IN_TUNE -> MaterialTheme.colorScheme.primary
        Closeness.A_LITTLE_FLAT, Closeness.A_LITTLE_SHARP -> MaterialTheme.colorScheme.tertiary
        Closeness.FLAT, Closeness.SHARP, Closeness.MISSED -> MaterialTheme.colorScheme.error
    }
    val spoken = s.spoken()
    Row(
        Modifier
            .border(1.dp, colour, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(s.label, style = MaterialTheme.typography.titleMedium)
        if (s.closeness == Closeness.MISSED) {
            Text(stringResource(R.string.swara_missed), color = colour, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(RecordingInsights.centsText(s.cents ?: 0.0), color = colour, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
