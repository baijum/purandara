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
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
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

    val selected = state.selected
    if (selected == null) {
        LibraryScreen(
            catalog = viewModel.catalog,
            samplesLoaded = state.samplesLoaded,
            loadError = state.loadError,
            onSelect = viewModel::select,
        )
    } else {
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
                        else -> requestMic.launch(Manifest.permission.RECORD_AUDIO)
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
}
