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
import androidx.core.content.ContextCompat
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.shruti.FindSaActions
import com.baijum.purandara.ui.shruti.FindSaScreen
import com.baijum.purandara.ui.shruti.ShrutiActions
import com.baijum.purandara.ui.shruti.ShrutiScreen
import com.baijum.purandara.viewmodel.PracticeUiState
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
        viewModel.setMicPermission(granted)
        if (granted && state.shruti.open) viewModel.openShruti()
        if (granted && state.findSa.open) viewModel.openFindSa()
    }
    val allowMic = { requestMicForShruti.launch(Manifest.permission.RECORD_AUDIO) }

    when {
        state.shruti.open -> ShrutiRoute(viewModel, state, allowMic)
        state.findSa.open -> FindSaRoute(viewModel, state, allowMic)
        state.selected == null -> LibraryRoute(viewModel, state)
        else -> PlayerRoute(viewModel, state, micDenied) { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

@Composable
private fun ShrutiRoute(viewModel: PracticeViewModel, state: PracticeUiState, allowMic: () -> Unit) {
    BackHandler { viewModel.closeShruti() }
    ShrutiScreen(
        state,
        ShrutiActions(onBack = viewModel::closeShruti, onHoldChange = viewModel::setHold, onAllowMic = allowMic),
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
        ),
        actions = LibraryActions(
            onSelect = viewModel::select,
            onToggleTanpura = viewModel::toggleTanpura,
            onSaChange = { midi -> viewModel.updateSettings { it.copy(saMidi = midi) } },
            onOpenShruti = viewModel::openShruti,
            onFindSa = viewModel::openFindSa,
        ),
    )
}

@Composable
private fun PlayerRoute(viewModel: PracticeViewModel, state: PracticeUiState, micDenied: Boolean, requestMic: () -> Unit) {
    val context = LocalContext.current
    val selected = state.selected ?: return
    BackHandler { viewModel.select(null) }
    PlayerScreen(
        varisai = selected,
        state = state,
        micDenied = micDenied,
        actions = PlayerActions(
            onBack = { viewModel.select(null) },
            onTogglePlay = viewModel::togglePlay,
            onSettingsChange = viewModel::updateSettings,
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
