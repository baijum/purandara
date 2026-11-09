package com.baijum.purandara.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.viewmodel.PlaybackState

/** Static, non-live countdown: no tick animations or repeated accessibility announcements. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecoveryBar(playback: PlaybackState, actions: PlayerActions) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.practice_rest), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val status = when {
                playback.pausedForContinue -> stringResource(R.string.practice_paused)
                playback.recoverySeconds > 0 -> stringResource(R.string.practice_rest_seconds, playback.recoverySeconds)
                else -> stringResource(R.string.practice_waiting)
            }
            Text(status)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(actions.onContinue, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.practice_continue)) }
                if (playback.recoverySeconds > 0) {
                    OutlinedButton(actions.onWaitForMe, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.practice_wait)) }
                }
                OutlinedButton(actions.onTogglePlay, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.stop)) }
            }
        }
    }
}
