package com.baijum.purandara.ui.shruti

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.shruti.SaSuggestion
import com.baijum.purandara.ui.saLabel
import com.baijum.purandara.shruti.FindSaProgress
import com.baijum.purandara.shruti.FindSaStep

/** Find my Sa: catch the lowest and highest comfortable notes, then suggest a Sa (docs/plan-shruti.md). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FindSaScreen(state: FindSaProgress, micPermitted: Boolean, actions: FindSaActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_my_sa)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.find_sa_intro), style = MaterialTheme.typography.bodyLarge)
            if (!micPermitted) {
                Button(onClick = actions.onAllowMic) { Text(stringResource(R.string.allow_mic)) }
                return@Column
            }
            val suggestion = state.suggestion
            if (state.step != FindSaStep.DONE || suggestion == null) {
                Text(
                    stringResource(if (state.step == FindSaStep.LOWEST) R.string.find_sa_lowest else R.string.find_sa_highest),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                LinearProgressIndicator(progress = { state.holding.toFloat() }, modifier = Modifier.fillMaxWidth())
                state.hearingHz?.let { Text(stringResource(R.string.find_sa_hearing, noteName(it)), style = MaterialTheme.typography.bodyLarge) }
                if (state.notHigher) {
                    Text(stringResource(R.string.find_sa_not_higher), color = MaterialTheme.colorScheme.error)
                }
            } else {
                FindSaResult(state, suggestion, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindSaResult(state: FindSaProgress, suggestion: SaSuggestion, actions: FindSaActions) {
            Text(
                stringResource(R.string.find_sa_result, saLabel(suggestion.saMidi)),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            val low = state.lowestHz
            val high = state.highestHz
            if (low != null && high != null) {
                Text(stringResource(R.string.find_sa_range, noteName(low), noteName(high)))
            }
            if (suggestion.narrowRange) {
                Text(stringResource(R.string.find_sa_narrow), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onUse) { Text(stringResource(R.string.use_this)) }
                OutlinedButton(onClick = actions.onRetry) { Text(stringResource(R.string.try_again)) }
            }
}

private fun noteName(hz: Double) = Shruti.noteName(Pitch.nearestMidi(hz))
