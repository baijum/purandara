package com.baijum.purandara.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import com.baijum.purandara.BuildConfig
import com.baijum.purandara.R
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.navigation.NavDestination
import com.baijum.purandara.ui.about.AboutScreen
import com.baijum.purandara.ui.navigation.MenuScaffold
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.HomeScreen
import com.baijum.purandara.ui.library.SetScreen
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.recordings.RagaEditorScreen
import com.baijum.purandara.ui.recordings.RecordScreen
import com.baijum.purandara.ui.recordings.RecordingDetailScreen
import com.baijum.purandara.ui.recordings.RecordingsActions
import com.baijum.purandara.ui.recordings.RecordingsListScreen
import com.baijum.purandara.ui.run.RunActions
import com.baijum.purandara.ui.run.RunScreen
import com.baijum.purandara.ui.shruti.FindSaActions
import com.baijum.purandara.ui.shruti.FindSaScreen
import com.baijum.purandara.ui.shruti.ShrutiActions
import com.baijum.purandara.ui.shruti.ShrutiScreen
import com.baijum.purandara.viewmodel.PracticeUiState
import com.baijum.purandara.viewmodel.RunPhase
import com.baijum.purandara.viewmodel.PracticeViewModel
import com.baijum.purandara.viewmodel.RecordingsScreen
import com.baijum.purandara.viewmodel.RecordingsUiState
import com.baijum.purandara.viewmodel.RecordingsViewModel

/**
 * Top-level navigation (docs/plan-navigation.md): the menu's destinations in a
 * drawer, with the player, a practice run, the recorder and a recording pushed
 * above them.
 */
@Composable
fun PurandaraApp(viewModel: PracticeViewModel, recordings: RecordingsViewModel) {
    val state by viewModel.state.collectAsState()
    val recordingsState by recordings.state.collectAsState()
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
    val requestMicForRecording = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setMicPermission(granted)
    }

    AnnounceOutputChange(state)

    var pageId by rememberSaveable { mutableStateOf(NavDestination.Home.id) }
    val navigator = AppNavigator(viewModel, recordings, state, recordingsState, pageId) { pageId = it.id }
    val allowRecordingMic = { requestMicForRecording.launch(Manifest.permission.RECORD_AUDIO) }

    when {
        recordingsState.open && recordingsState.screen != RecordingsScreen.LIST || recordingsState.ragaEditor != null ->
            RecordingsRoute(recordings, recordingsState, micForRecording(state, recordings), allowRecordingMic) { raga ->
                // "Choose Kalyani instead", from an editor opened on the varisais' raga list.
                recordings.ragas.close()
                viewModel.updateSettings { it.copy(ragaKey = raga.key) }
            }
        state.run?.phase == RunPhase.PLAYING -> PlayerRoute(viewModel, state, micDenied, runMode = true) { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
        state.run != null -> RunRoute(viewModel, state)
        state.selected != null -> PlayerRoute(viewModel, state, micDenied) { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
        else -> MenuScaffold(navigator.current, navigator::navigate) {
            MenuRoutes(navigator, allowMic, allowRecordingMic)
        }
    }
}

/**
 * Where the menu is (docs/plan-navigation.md): the page destinations (Home, a
 * set, About) are UI state ([pageId]); Shruti practice, Find my Sa and
 * Recordings are open while their view-model state says so, and close back to
 * Home, so the menu can't disagree with what is showing.
 */
private class AppNavigator(
    val viewModel: PracticeViewModel,
    val recordings: RecordingsViewModel,
    val state: PracticeUiState,
    val recordingsState: RecordingsUiState,
    pageId: String,
    private val setPage: (NavDestination) -> Unit,
) {
    val current: NavDestination = when {
        recordingsState.open -> NavDestination.Recordings
        state.shruti.open -> NavDestination.ShrutiPractice
        state.findSa.open -> NavDestination.FindSa
        else -> NavDestination.fromId(pageId) ?: NavDestination.Home
    }

    fun navigate(target: NavDestination) {
        if (target == current) return
        when (current) {
            NavDestination.ShrutiPractice -> viewModel.closeShruti()
            NavDestination.FindSa -> viewModel.closeFindSa()
            NavDestination.Recordings -> recordings.close()
            else -> {}
        }
        when (target) {
            NavDestination.ShrutiPractice -> viewModel.openShruti()
            NavDestination.FindSa -> viewModel.openFindSa()
            NavDestination.Recordings -> {
                viewModel.stop()
                val settings = state.settings
                recordings.open(settings.tuning, settings.saMidi, settings.saCents, settings.strictness)
            }
            else -> {}
        }
        // Leaving Shruti practice, Find my Sa or Recordings lands Home.
        setPage(if (target.isPage) target else NavDestination.Home)
    }

    fun openPage(page: NavDestination) = setPage(page)

    private val NavDestination.isPage: Boolean
        get() = this == NavDestination.Home || this == NavDestination.About || this is NavDestination.SetPage
}

@Composable
private fun MenuRoutes(navigator: AppNavigator, allowMic: () -> Unit, allowRecordingMic: () -> Unit) {
    val viewModel = navigator.viewModel
    val recordings = navigator.recordings
    val state = navigator.state
    val current = navigator.current
    val goHome = { navigator.navigate(NavDestination.Home) }
    // Back from a destination other than Home goes Home; from Home the app leaves.
    BackHandler(enabled = current != NavDestination.Home, onBack = goHome)
    when (current) {
        NavDestination.Recordings -> RecordingsRoute(recordings, navigator.recordingsState, micForRecording(state, recordings), allowRecordingMic)
        NavDestination.ShrutiPractice -> ShrutiRoute(viewModel, state, allowMic)
        NavDestination.FindSa -> FindSaRoute(viewModel, state, allowMic)
        NavDestination.About -> AboutScreen(BuildConfig.VERSION_NAME, onBack = goHome)
        is NavDestination.SetPage -> SetScreen(
            current.set, viewModel.catalog, libraryState(state, navigator.recordingsState), libraryActions(viewModel, recordings, navigator::openPage),
        )
        NavDestination.Home -> HomeScreen(
            viewModel.catalog, libraryState(state, navigator.recordingsState), libraryActions(viewModel, recordings, navigator::openPage),
        )
    }
}

private fun micForRecording(state: PracticeUiState, recordings: RecordingsViewModel): Boolean =
    state.singAlong.micPermitted || recordings.debugSyntheticSingerCents != null

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
    ShrutiScreen(
        state,
        ShrutiActions(onBack = viewModel::closeShruti, onHoldChange = viewModel::setHold, onAllowMic = allowMic, onStartAgain = viewModel::openShruti),
        micAllowed = state.singAlong.micPermitted || viewModel.debugSyntheticSingerCents != null,
    )
}

@Composable
private fun FindSaRoute(viewModel: PracticeViewModel, state: PracticeUiState, allowMic: () -> Unit) {
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

private fun libraryState(state: PracticeUiState, recordings: RecordingsUiState) = LibraryState(
    history = state.history,
    saMidi = state.settings.saMidi,
    saCents = state.settings.saCents,
    tanpuraPlaying = state.tanpuraPlaying,
    samplesLoaded = state.samplesLoaded,
    loadError = state.loadError,
    runPlans = state.runPlans,
    raga = state.settings.raga,
    previewRagaKey = state.previewRagaKey,
    ownRagas = recordings.customRagas.ragas,
)

private fun libraryActions(viewModel: PracticeViewModel, recordings: RecordingsViewModel, openPage: (NavDestination) -> Unit) = LibraryActions(
    onSelect = { varisai -> viewModel.select(varisai) },
    onToggleTanpura = viewModel::toggleTanpura,
    onSaChange = { midi -> viewModel.updateSettings { it.copy(saMidi = midi) } },
    onRagaChange = { raga -> viewModel.updateSettings { it.copy(ragaKey = raga.key) } },
    onPreviewRaga = { raga -> viewModel.preview(raga) },
    onStopPreview = { viewModel.preview(null) },
    onEditOwnRaga = { key -> recordings.ragas.edit(key, forVarisais = true) },
    onOpenRun = viewModel.runs::open,
    onOpenSet = { set -> openPage(NavDestination.SetPage(set)) },
    onBack = { openPage(NavDestination.Home) },
)

@SuppressLint("MissingPermission") // startRecording is only offered once the mic is allowed
@Composable
private fun RecordingsRoute(
    recordings: RecordingsViewModel,
    state: RecordingsUiState,
    micAllowed: Boolean,
    allowMic: () -> Unit,
    onChooseVarisaiRaga: ((Raga) -> Unit)? = null,
) {
    BackHandler { recordings.back() }
    val actions = RecordingsActions(
        onBack = recordings::back,
        onNew = recordings::showRecorder,
        onOpen = recordings::openRecording,
        onStart = recordings::startRecording,
        onStop = recordings::stopRecording,
        onAllowMic = allowMic,
        onTogglePlay = recordings::togglePlay,
        onSeek = recordings::seekTo,
        onRename = recordings::rename,
        onChangeSa = recordings::changeSa,
        onChangeRaga = recordings::changeRaga,
        onDelete = recordings::delete,
        onTanpura = recordings::setTanpura,
        onIntent = recordings::setIntent,
        onChangeIntent = recordings::changeIntent,
        onChangeTanpura = recordings::changeTanpura,
        onReanalyse = if (BuildConfig.DEBUG) recordings::reanalyse else null,
        onEditRaga = recordings.ragas::edit,
    )
    val detail = state.detail
    val editor = state.ragaEditor
    when {
        editor != null -> key(editor.key) {
            RagaEditorScreen(
                editing = editor.key?.let { state.customRagas[it] },
                ragas = state.customRagas,
                onSave = recordings.ragas::save,
                onDelete = recordings.ragas::delete,
                onClose = recordings.ragas::close,
                onChooseBuiltIn = onChooseVarisaiRaga.takeIf { editor.forVarisais },
            )
        }
        state.screen == RecordingsScreen.RECORD -> RecordScreen(state.recorder, micAllowed, actions, state.recentRagas, state.customRagas)
        state.screen == RecordingsScreen.DETAIL && detail != null -> RecordingDetailScreen(detail, actions, state.recentRagas, state.customRagas)
        else -> RecordingsListScreen(state.library, actions)
    }
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
            onToggleRepeatCycle = viewModel.cycleRepeat::toggle,
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
