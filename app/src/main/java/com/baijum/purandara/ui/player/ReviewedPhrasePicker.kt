package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.varisai.PracticePhrase
import com.baijum.purandara.domain.varisai.PracticePhrases
import com.baijum.purandara.domain.varisai.Varisai

/** No guessed boundaries: an empty reviewed catalog exposes no phrase control. */
@Composable
internal fun ReviewedPhrasePicker(varisai: Varisai, selected: PracticePhrase?, onSelect: (PracticePhrase?) -> Unit) {
    val phrases = remember(varisai) { PracticePhrases.forVarisai(varisai) }
    if (phrases.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup()) {
        Text(stringResource(R.string.phrase_heading), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        PhraseRow(stringResource(R.string.section_whole), selected == null) { onSelect(null) }
        phrases.forEach { phrase ->
            PhraseRow(stringResource(R.string.phrase_choice, phrase.label, phrase.firstAvarta + 1, phrase.lastAvarta + 1), selected == phrase) { onSelect(phrase) }
        }
    }
}

@Composable
private fun PhraseRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(label, modifier = Modifier.weight(1f))
    }
}
