package com.baijum.purandara.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.viewmodel.PracticeViewModel

/** Top-level navigation: the exercise library, or the player for the selected exercise. */
@Composable
fun PurandaraApp(viewModel: PracticeViewModel) {
    val state by viewModel.state.collectAsState()
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
            settings = state.settings,
            playback = state.playback,
            canPlay = state.samplesLoaded,
            actions = PlayerActions(
                onBack = { viewModel.select(null) },
                onTogglePlay = viewModel::togglePlay,
                onSettingsChange = viewModel::updateSettings,
            ),
        )
    }
}
