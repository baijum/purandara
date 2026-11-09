package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.RecoveryMode
import kotlin.math.roundToInt

/** Full-width selected choices remain reachable and wrap at accessibility text sizes. */
@Composable
internal fun PracticeModeControls(settings: PracticeSettings, onChange: ((PracticeSettings) -> PracticeSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PracticeChoices(
            stringResource(R.string.practice_mode), settings.practiceMode,
            listOf(PracticeMode.NORMAL to R.string.practice_normal, PracticeMode.LISTEN_REPEAT to R.string.practice_listen_repeat),
        ) { mode -> onChange { it.copy(practiceMode = mode) } }
        Explanation(R.string.practice_mode_desc)
        if (settings.practiceMode == PracticeMode.LISTEN_REPEAT) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(settings.responseGuide, role = Role.Switch) { on -> onChange { it.copy(responseGuide = on) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.response_guide), modifier = Modifier.weight(1f))
                Switch(settings.responseGuide, onCheckedChange = null)
            }
            Explanation(R.string.response_guide_desc)
        }
        PracticeChoices(
            stringResource(R.string.between_repeats), settings.recoveryMode,
            listOf(
                RecoveryMode.CONTINUOUS to R.string.recovery_continuous,
                RecoveryMode.TIMED to R.string.recovery_timed,
                RecoveryMode.WHEN_READY to R.string.recovery_ready,
            ),
        ) { mode -> onChange { it.copy(recoveryMode = mode) } }
        if (settings.recoveryMode == RecoveryMode.TIMED) {
            RecoveryDuration(settings.recoverySeconds) { seconds -> onChange { it.copy(recoverySeconds = seconds) } }
        }
        Explanation(R.string.recovery_desc)
    }
}

@Composable
private fun <T> PracticeChoices(title: String, selected: T, choices: List<Pair<T, Int>>, onChange: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            choices.forEach { (choice, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected == choice, role = Role.RadioButton, onClick = { onChange(choice) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected == choice, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(label), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecoveryDuration(seconds: Int, onChange: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val title = stringResource(R.string.recovery_duration)
    val description = stringResource(R.string.recovery_seconds, value.roundToInt())
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(description)
        Slider(
            value, onValueChange = { value = it }, onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 1f..30f, steps = 28,
            modifier = Modifier.semantics { contentDescription = title; stateDescription = description },
        )
    }
}

@Composable
private fun Explanation(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
