package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baijum.purandara.R
import com.baijum.purandara.domain.varisai.SectionSpan

/**
 * "Section: Whole piece": practise one section of a gitam or swarajati alone
 * (it plays once, or loops with Repeat on). Only shown for pieces with sections.
 */
@Composable
fun SectionPicker(sections: List<SectionSpan>, selected: SectionSpan?, onSelect: (SectionSpan?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val whole = stringResource(R.string.section_whole)
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.section_button, selected?.label ?: whole))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(whole) }, onClick = { open = false; onSelect(null) })
            sections.forEach { section ->
                DropdownMenuItem(text = { Text(section.label) }, onClick = { open = false; onSelect(section) })
            }
        }
    }
}
