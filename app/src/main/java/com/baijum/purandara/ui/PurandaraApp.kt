package com.baijum.purandara.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.baijum.purandara.R
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.run.RunActions
import com.baijum.purandara.ui.run.RunScreen
import com.baijum.purandara.ui.shruti.FindSaActions
import com.baijum.purandara.ui.shruti.FindSaScreen
import com.baijum.purandara.ui.shruti.ShrutiActions
import com.baijum.purandara.ui.shruti.ShrutiScreen
import com.baijum.purandara.viewmodel.PracticeUiState
import com.baijum.purandara.viewmodel.RunPhase
import com.baijum.purandara.viewmodel.PracticeViewModel

/** Top-level navigation: the exercise library, or the player for the selected exercise. */
@Composable
fun PurandaraApp(viewModel: PracticeViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var micDenied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.setMicPermission(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setMicPermission(granted)
        micDenied = !granted
        if (granted) viewModel.updateSettings { it.copy(singAlong = true) }
    }
    // Shruti practice and Find my Sa ask for the mic themselves and restart once allowed.
    val requestMicForShruti = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setMicPermission(granted, restartPractice = true)
    }
    val allowMic = { requestMicForShruti.launch(Manifest.permission.RECORD_AUDIO) }

    AnnounceOutputChange(state)

    when {
        state.shruti.open -> ShrutiRoute(viewModel, state, allowMic)
        state.findSa.open -> FindSaRoute(viewModel, state, allowMic)
        state.run?.phase == RunPhase.PLAYING -> PlayerRoute(viewModel, state, micDenied, runMode = true) { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
        state.run != null -> RunRoute(viewModel, state)
        state.selected == null -> LibraryRoute(viewModel, state)
        else -> PlayerRoute(viewModel, state, micDenied) { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/**
 * Shruti practice and an unfinished Find my Sa show a notice (a live region);
 * elsewhere (the player, the landing page's tanpura) playback just stops, so
 * TalkBack is told, as VoiceOver is on iOS.
 */
@Composable
private fun AnnounceOutputChange(state: PracticeUiState) {
    val hasNotice = state.shruti.open || (state.findSa.open && state.findSa.progress.suggestion == null)
    val announce = state.outputChanged && !hasNotice
    val view = LocalView.current
    val message = stringResource(R.string.audio_output_changed)
    LaunchedEffect(announce) {
        @Suppress("DEPRECATION") // no live region to attach to: nothing stays on screen
        if (announce) view.announceForAccessibility(message)
    }
}

@Composable
private fun ShrutiRoute(viewModel: PracticeViewModel, state: PracticeUiState, allowMic: () -> Unit) {
    BackHandler { viewModel.closeShruti() }
    ShrutiScreen(
        state,
        ShrutiActions(onBack = viewModel::closeShruti, onHoldChange = viewModel::setHold, onAllowMic = allowMic, onStartAgain = viewModel::openShruti),
        micAllowed = state.singAlong.micPermitted || viewModel.debugSyntheticSingerCents != null,
    )
}

@Composable
private fun FindSaRoute(viewModel: PracticeViewModel, state: PracticeUiState, allowMic: () -> Unit) {
    BackHandler { viewModel.closeFindSa() }
    FindSaScreen(
        state.findSa.progress,
        micPermitted = state.singAlong.micPermitted || viewModel.debugSyntheticSingerCents != null,
        actions = FindSaActions(
            onBack = viewModel::closeFindSa,
            onUse = viewModel::useSuggestedSa,
            onRetry = viewModel::openFindSa,
            onAllowMic = allowMic,
        ),
        outputChanged = state.outputChanged,
    )
}

@Composable
private fun LibraryRoute(viewModel: PracticeViewModel, state: PracticeUiState) {
    LibraryScreen(
        catalog = viewModel.catalog,
        state = LibraryState(
            history = state.history,
            saMidi = state.settings.saMidi,
            saCents = state.settings.saCents,
            tanpuraPlaying = state.tanpuraPlaying,
            samplesLoaded = state.samplesLoaded,
            loadError = state.loadError,
            runPlans = state.runPlans,
        ),
        actions = LibraryActions(
            onSelect = { varisai -> viewModel.select(varisai) },
            onToggleTanpura = viewModel::toggleTanpura,
            onSaChange = { midi -> viewModel.updateSettings { it.copy(saMidi = midi) } },
            onOpenShruti = viewModel::openShruti,
            onFindSa = viewModel::openFindSa,
            onOpenRun = viewModel.runs::open,
        ),
    )
}

@Composable
private fun RunRoute(viewModel: PracticeViewModel, state: PracticeUiState) {
    BackHandler { viewModel.runs.back() }
    RunScreen(
        state = state,
        actions = RunActions(
            onBack = viewModel.runs::back,
            onSetSpeed = viewModel.runs::setSpeed,
            onSetRepeats = viewModel.runs::setRepeats,
            onApplySpeed = viewModel.runs::applySpeed,
            onApplyRepeats = viewModel.runs::applyRepeats,
            onStart = viewModel.runs::start,
            onAgain = viewModel.runs::again,
            onDone = viewModel.runs::close,
        ),
    )
}

@Composable
private fun PlayerRoute(viewModel: PracticeViewModel, state: PracticeUiState, micDenied: Boolean, runMode: Boolean = false, requestMic: () -> Unit) {
    val context = LocalContext.current
    val selected = state.selected ?: return
    BackHandler { if (runMode) viewModel.runs.back() else viewModel.select(null) }
    PlayerScreen(
        varisai = selected,
        state = state,
        micDenied = micDenied,
        actions = PlayerActions(
            onBack = { if (runMode) viewModel.runs.back() else viewModel.select(null) },
            onTogglePlay = { if (runMode) viewModel.runs.stop() else viewModel.togglePlay() },
            onSettingsChange = viewModel::updateSettings,
            onContinue = { viewModel.continuePractice() },
            onWaitForMe = { viewModel.continuePractice(waitForMe = true) },
            onSelectSection = { section -> if (!runMode) viewModel.select(selected, section) },
            onSelectPhrase = { phrase -> if (!runMode) viewModel.select(selected, phrase = phrase) },
            onSkipRun = { if (runMode) viewModel.runs.skip() },
            onToggleSingAlong = {
                when {
                    state.settings.singAlong -> viewModel.updateSettings { it.copy(singAlong = false) }
                    state.singAlong.micPermitted || viewModel.debugSyntheticSingerCents != null ->
                        viewModel.updateSettings { it.copy(singAlong = true) }
                    else -> requestMic()
                }
            },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        ),
    )
}
