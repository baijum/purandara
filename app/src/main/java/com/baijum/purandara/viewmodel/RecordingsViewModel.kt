package com.baijum.purandara.viewmodel

import ai.onnxruntime.OrtException
import android.Manifest
import android.app.Application
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baijum.purandara.audio.MicInput
import com.baijum.purandara.audio.NeuralPitchSupervisor
import com.baijum.purandara.audio.PitchSupervisor
import com.baijum.purandara.audio.Recorder
import com.baijum.purandara.audio.RecordingDecoder
import com.baijum.purandara.audio.SyntheticSingerInput
import com.baijum.purandara.data.RecordingStore
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingLibrary
import com.baijum.purandara.recording.SwaraTranscriber
import com.baijum.purandara.recording.Transcription
import com.baijum.purandara.singalong.PitchLane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Which recordings screen is showing. */
enum class RecordingsScreen { LIST, RECORD, DETAIL }

/** The record screen: recording, then analysing. */
data class RecorderUi(
    val recording: Boolean = false,
    val seconds: Double = 0.0,
    /** Level of the last ~50 ms, 0..1 (RMS). */
    val level: Float = 0f,
    /** Analysis progress 0..1 after Stop, or null when not analysing. */
    val analysing: Float? = null,
    /** The last recording could not be saved. */
    val failed: Boolean = false,
    /** The Sa it is recorded against. */
    val saMidi: Int = Shruti.DEFAULT_SA_MIDI,
    val saCents: Int = 0,
)

/** One recording opened: its swaras, trace and playback. */
data class RecordingDetailUi(
    val recording: Recording,
    val track: PitchTrack?,
    val transcription: Transcription,
    val lane: PitchLane,
    val playing: Boolean = false,
    val positionSeconds: Double = 0.0,
)

data class RecordingsUiState(
    val open: Boolean = false,
    val screen: RecordingsScreen = RecordingsScreen.LIST,
    val library: RecordingLibrary = RecordingLibrary(),
    val recorder: RecorderUi = RecorderUi(),
    val detail: RecordingDetailUi? = null,
)

/**
 * Recordings with approximate swaras (docs/plan-recordings.md): record,
 * analyse after Stop, then list, open, play, rename, re-read against
 * another Sa or raga, and delete. Kept apart from [PracticeViewModel]; the
 * app stops practice audio before opening recordings.
 */
class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RecordingStore(application)
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(RecordingsUiState(library = store.load()))
    val state: StateFlow<RecordingsUiState> = _state.asStateFlow()

    private var tuning: Tuning = Tuning.JUST
    private var saMidi: Int = Shruti.DEFAULT_SA_MIDI
    private var saCents: Int = 0
    private var recorder: Recorder? = null
    private var recordingId: String? = null
    private var recordingStarted = 0L
    private var player: MediaPlayer? = null
    private var playerFor: String? = null
    private var ticker: Job? = null

    /** Debug builds only: record a synthetic singer this many cents off instead of the mic. */
    var debugSyntheticSingerCents: Double? = null

    /** Opens the list; new recordings use [saMidi] / [saCents] and swaras are read in [tuning]. */
    fun open(tuning: Tuning, saMidi: Int, saCents: Int) {
        this.tuning = tuning
        this.saMidi = saMidi
        this.saCents = saCents
        _state.update { it.copy(open = true, screen = RecordingsScreen.LIST, detail = null) }
    }

    /** Back: from a recording or the record screen to the list, from the list out. */
    fun back() {
        when (_state.value.screen) {
            RecordingsScreen.DETAIL -> {
                releasePlayer()
                _state.update { it.copy(screen = RecordingsScreen.LIST, detail = null) }
            }
            RecordingsScreen.RECORD -> {
                if (_state.value.recorder.recording) stopRecording() else showList()
            }
            RecordingsScreen.LIST -> close()
        }
    }

    fun close() {
        recorder?.stop()
        releasePlayer()
        _state.update { it.copy(open = false, detail = null, screen = RecordingsScreen.LIST) }
    }

    /** The app left the screen: recording stops and keeps what it has; playback pauses. */
    fun onBackgrounded() {
        if (_state.value.recorder.recording) stopRecording()
        pause()
    }

    // --- Recording ---

    fun showRecorder() {
        releasePlayer()
        _state.update {
            it.copy(screen = RecordingsScreen.RECORD, detail = null, recorder = RecorderUi(saMidi = saMidi, saCents = saCents))
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (recorder != null || _state.value.recorder.analysing != null) return
        val ui = _state.value.recorder
        val id = UUID.randomUUID().toString()
        val file = store.audioFile(id)
        val synthetic = debugSyntheticSingerCents
        val source = if (synthetic != null) {
            SyntheticSingerInput(Shruti.saHz(ui.saMidi, ui.saCents), synthetic)
        } else {
            MicInput(audioManager)
        }
        val r = Recorder(
            source, file, RecordingLibrary.MAX_SECONDS,
            onLevel = { rms, seconds -> _state.update { it.copy(recorder = it.recorder.copy(level = rms, seconds = seconds)) } },
            onFinished = { seconds -> mainHandler.post { onRecorded(id, seconds) } },
        )
        recorder = r
        recordingId = id
        recordingStarted = System.currentTimeMillis()
        _state.update { it.copy(recorder = it.recorder.copy(recording = true, seconds = 0.0, level = 0f, failed = false)) }
        r.start()
    }

    fun stopRecording() {
        recorder?.stop()
    }

    private fun onRecorded(id: String, seconds: Double?) {
        if (recordingId != id) return
        recorder = null
        recordingId = null
        val ui = _state.value.recorder
        if (seconds == null) {
            store.deleteFiles(id)
            _state.update { it.copy(recorder = it.recorder.copy(recording = false, level = 0f, failed = true)) }
            return
        }
        _state.update { it.copy(recorder = it.recorder.copy(recording = false, level = 0f, analysing = 0f)) }
        val created = recordingStarted
        viewModelScope.launch {
            val track = withContext(Dispatchers.Default) {
                try {
                    RecordingDecoder.analyse(store.audioFile(id), ::loadSupervisor) { p ->
                        _state.update { it.copy(recorder = it.recorder.copy(analysing = p)) }
                    }.also { store.writePitch(id, it) }
                } catch (e: IOException) {
                    Log.e(TAG, "could not analyse $id", e)
                    null
                }
            }
            if (track == null) {
                store.deleteFiles(id)
                _state.update { it.copy(recorder = it.recorder.copy(analysing = null, failed = true)) }
                return@launch
            }
            val recording = Recording(id, created, seconds, ui.saMidi, ui.saCents, analysisVersion = RecordingLibrary.ANALYSIS_VERSION)
            updateLibrary { it.add(recording) }
            _state.update { it.copy(recorder = it.recorder.copy(analysing = null)) }
            // Still on the record screen (not closed or backed out meanwhile): show the result.
            if (_state.value.open && _state.value.screen == RecordingsScreen.RECORD) openRecording(id)
        }
    }

    private fun loadSupervisor(sampleRate: Int): PitchSupervisor? = try {
        NeuralPitchSupervisor(getApplication<Application>().assets, sampleRate)
    } catch (e: OrtException) {
        Log.w(TAG, "SwiftF0 unavailable; analysing with YIN alone", e)
        null
    } catch (e: IOException) {
        Log.w(TAG, "SwiftF0 model missing; analysing with YIN alone", e)
        null
    }

    // --- One recording ---

    fun openRecording(id: String) {
        val recording = _state.value.library[id] ?: return
        releasePlayer()
        val track = store.readPitch(id)
        _state.update { it.copy(screen = RecordingsScreen.DETAIL, detail = read(recording, track)) }
    }

    private fun read(recording: Recording, track: PitchTrack?): RecordingDetailUi {
        val transcription = track?.let { SwaraTranscriber.transcribe(it, recording.saHz, recording.raga, tuning) } ?: Transcription(emptyList())
        return RecordingDetailUi(recording, track, transcription, PitchLane.forRecording(transcription, recording.raga))
    }

    fun rename(id: String, title: String) = updateLibrary { it.rename(id, title) }

    fun changeSa(id: String, midi: Int, cents: Int) = updateLibrary { it.changeSa(id, midi, cents) }

    fun changeRaga(id: String, ragaKey: String?) = updateLibrary { it.changeRaga(id, ragaKey) }

    fun delete(id: String) {
        if (playerFor == id) releasePlayer()
        store.deleteFiles(id)
        updateLibrary { it.delete(id) }
        _state.update { if (it.detail?.recording?.id == id) it.copy(screen = RecordingsScreen.LIST, detail = null) else it }
    }

    private fun updateLibrary(transform: (RecordingLibrary) -> RecordingLibrary) {
        val next = transform(_state.value.library)
        store.save(next)
        _state.update { s ->
            val detail = s.detail
            val updated = detail?.let { next[it.recording.id] }
            s.copy(
                library = next,
                detail = when {
                    detail == null || updated == null -> detail
                    updated.saHz != detail.recording.saHz || updated.ragaKey != detail.recording.ragaKey ->
                        read(updated, detail.track).copy(playing = detail.playing, positionSeconds = detail.positionSeconds)
                    else -> detail.copy(recording = updated)
                },
            )
        }
    }

    // --- Playback ---

    fun togglePlay() {
        if (_state.value.detail?.playing == true) pause() else play()
    }

    private fun play() {
        val detail = _state.value.detail ?: return
        val id = detail.recording.id
        val p = player.takeIf { playerFor == id } ?: try {
            releasePlayer()
            MediaPlayer().apply {
                setDataSource(store.audioFile(id).path)
                prepare()
                setOnCompletionListener { onPlaybackEnded() }
            }.also {
                player = it
                playerFor = id
            }
        } catch (e: IOException) {
            Log.e(TAG, "could not play $id", e)
            return
        }
        if (detail.positionSeconds >= detail.recording.durationSeconds - END_SLACK_SECONDS) p.seekTo(0)
        p.start()
        _state.update { it.copy(detail = it.detail?.copy(playing = true)) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val seconds = player?.currentPosition?.div(MILLIS) ?: break
                _state.update { it.copy(detail = it.detail?.copy(positionSeconds = seconds)) }
                delay(TICK_MS)
            }
        }
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        ticker?.cancel()
        _state.update { it.copy(detail = it.detail?.copy(playing = false)) }
    }

    /** Moves playback to [seconds] (tapping a swara or the trace, or the position slider). */
    fun seekTo(seconds: Double) {
        val detail = _state.value.detail ?: return
        val clamped = seconds.coerceIn(0.0, detail.recording.durationSeconds)
        player?.takeIf { playerFor == detail.recording.id }?.seekTo((clamped * MILLIS).toInt())
        _state.update { it.copy(detail = it.detail?.copy(positionSeconds = clamped)) }
    }

    private fun onPlaybackEnded() {
        ticker?.cancel()
        _state.update { it.copy(detail = it.detail?.copy(playing = false, positionSeconds = it.detail.recording.durationSeconds)) }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
        player?.release()
        player = null
        playerFor = null
        _state.update { it.copy(detail = it.detail?.copy(playing = false)) }
    }

    private fun showList() {
        _state.update { it.copy(screen = RecordingsScreen.LIST, detail = null) }
    }

    override fun onCleared() {
        recorder?.stopAndWait()
        releasePlayer()
    }

    private companion object {
        const val TAG = "RecordingsViewModel"
        const val TICK_MS = 50L
        const val MILLIS = 1000.0
        const val END_SLACK_SECONDS = 0.1
    }
}
