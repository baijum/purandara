package com.baijum.purandara.viewmodel

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.onnxruntime.OrtException
import com.baijum.purandara.audio.AudioRecordMicSource
import com.baijum.purandara.audio.HarmoniumSamples
import com.baijum.purandara.audio.MicSource
import com.baijum.purandara.audio.NeuralPitchSupervisor
import com.baijum.purandara.audio.SampleBank
import com.baijum.purandara.audio.SampleRenderer
import com.baijum.purandara.audio.SingAlongEngine
import com.baijum.purandara.audio.SyntheticSingerMicSource
import com.baijum.purandara.audio.VarisaiAudioPlayer
import com.baijum.purandara.data.PracticePreferences
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.singalong.FrameOutcome
import com.baijum.purandara.singalong.NoteMark
import com.baijum.purandara.singalong.PassResult
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

/** What the player screen shows while an exercise sounds. */
data class PlaybackState(
    val playing: Boolean = false,
    /** Highlighted notation position, null when stopped. */
    val position: NotationPosition? = null,
    /** 0-based beat within the avarta, for the tala display. */
    val beatInAvarta: Int? = null,
    /** Passes through the exercise needed to fill whole avartas at this kala. */
    val repetitions: Int = 1,
    /** True during the count-in avarta before the exercise. */
    val countingIn: Boolean = false,
)

data class PracticeUiState(
    val settings: PracticeSettings = PracticeSettings(),
    val selected: Varisai? = null,
    val samplesLoaded: Boolean = false,
    val loadError: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
    val singAlong: SingAlongState = SingAlongState(),
)

/**
 * Holds practice settings, the selected exercise, playback and sing-along.
 *
 * Settings changes while playing restart the exercise with the new settings.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PracticePreferences(application)
    private val player = VarisaiAudioPlayer(application)
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(PracticeUiState(settings = preferences.load()))
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    val catalog: List<Varisai> = VarisaiCatalog.all

    private var bank: SampleBank? = null
    private var renderer: SampleRenderer? = null
    private var schedule: Schedule? = null
    private var ticker: Job? = null
    private var engine: SingAlongEngine? = null
    private var supervisor: NeuralPitchSupervisor? = null
    private var supervisorRequested = false

    /** Debug builds only: a synthetic singer offset by this many cents replaces the mic. */
    var debugSyntheticSingerCents: Double? = null

    init {
        viewModelScope.launch {
            try {
                bank = HarmoniumSamples.load(application.assets)
                _state.update { it.copy(samplesLoaded = true) }
            } catch (e: IOException) {
                onLoadFailed(e)
            } catch (e: IllegalStateException) {
                onLoadFailed(e) // includes MediaCodec.CodecException
            }
        }
        if (_state.value.settings.singAlong) loadSupervisor()
    }

    private fun onLoadFailed(e: Exception) {
        Log.e(TAG, "could not decode samples", e)
        _state.update { it.copy(loadError = true) }
    }

    /** Loads SwiftF0 in the background; sing-along uses YIN alone until (or if it never) succeeds. */
    private fun loadSupervisor() {
        if (supervisorRequested) return
        supervisorRequested = true
        viewModelScope.launch {
            supervisor = withContext(Dispatchers.Default) {
                try {
                    NeuralPitchSupervisor(getApplication<Application>().assets, MIC_SAMPLE_RATE)
                } catch (e: OrtException) {
                    Log.w(TAG, "SwiftF0 unavailable; using YIN alone", e)
                    null
                } catch (e: IOException) {
                    Log.w(TAG, "SwiftF0 model missing; using YIN alone", e)
                    null
                }
            }
        }
    }

    fun select(varisai: Varisai?) {
        stop()
        _state.update {
            it.copy(selected = varisai, playback = PlaybackState(), singAlong = it.singAlong.copy(marks = emptyMap(), trace = emptyList(), lastSummary = null, live = null))
        }
    }

    fun updateSettings(transform: (PracticeSettings) -> PracticeSettings) {
        val wasPlaying = _state.value.playback.playing
        val next = transform(_state.value.settings)
        if (next == _state.value.settings) return
        preferences.save(next)
        _state.update { it.copy(settings = next) }
        if (next.singAlong) loadSupervisor()
        if (wasPlaying) play()
    }

    fun setMicPermission(granted: Boolean) {
        _state.update { it.copy(singAlong = it.singAlong.copy(micPermitted = granted)) }
    }

    fun togglePlay() {
        if (_state.value.playback.playing) stop() else play()
    }

    fun play() {
        val varisai = _state.value.selected ?: return
        val bank = bank ?: return
        stop()
        val settings = _state.value.settings
        val schedule = Schedule.of(varisai, settings.performance())
        val r = SampleRenderer(bank, schedule, settings.renderOptions())
        renderer = r
        this.schedule = schedule
        val onSpeaker = !headphonesConnected()
        _state.update {
            it.copy(
                playback = PlaybackState(playing = true, repetitions = schedule.repetitions),
                singAlong = it.singAlong.copy(onSpeaker = onSpeaker, live = null, trace = emptyList(), marks = emptyMap(), lastSummary = null),
            )
        }
        val singAlong = settings.singAlong && (_state.value.singAlong.micPermitted || debugSyntheticSingerCents != null)
        val newEngine = if (singAlong) {
            SingAlongEngine(micSource(onSpeaker, r, schedule), player, r, schedule, supervisor) { outcome ->
                mainHandler.post { onOutcome(r, outcome) }
            }.also { it.attach() }
        } else {
            null
        }
        engine = newEngine
        player.play(r) { mainHandler.post { onPlaybackFinished(r) } }
        newEngine?.start()
        ticker = viewModelScope.launch {
            while (isActive) {
                val frames = player.outputFrameAt(System.nanoTime())
                if (frames != null && frames >= 0) {
                    val countInBeat = r.countInBeat(frames)
                    val seconds = r.passSeconds(frames)
                    _state.update {
                        it.copy(
                            playback = it.playback.copy(
                                countingIn = countInBeat != null,
                                position = if (countInBeat != null) null else schedule.cursorAt(seconds),
                                beatInAvarta = countInBeat ?: schedule.beatAt(seconds)?.beatInAvarta,
                            ),
                        )
                    }
                }
                delay(FRAME_MS)
            }
        }
    }

    private fun micSource(onSpeaker: Boolean, r: SampleRenderer, schedule: Schedule): MicSource {
        val synthetic = debugSyntheticSingerCents
        return if (synthetic != null) {
            SyntheticSingerMicSource(synthetic) { nanos ->
                val frame = player.outputFrameAt(nanos) ?: return@SyntheticSingerMicSource null
                if (r.countInBeat(frame) != null || frame < 0) return@SyntheticSingerMicSource null
                val t = r.passSeconds(frame)
                schedule.notes.lastOrNull { it.startSeconds <= t && t < schedule.durationSeconds }?.frequencyHz
            }
        } else {
            AudioRecordMicSource(echoCancellation = onSpeaker)
        }
    }

    private fun onOutcome(r: SampleRenderer, outcome: FrameOutcome) {
        if (renderer !== r) return
        val sa = schedule?.settings?.saHz ?: return
        _state.update { s ->
            val live = outcome.live
            var trace = s.singAlong.trace
            if (live.reading != null && live.pass != null && live.secondsInPass != null) {
                trace = (trace + TraceSample(live.pass!!, live.secondsInPass!!, Pitch.cents(sa, live.reading!!.frequencyHz), live.reading!!.zone))
                    .takeLast(MAX_TRACE)
            }
            s.copy(
                singAlong = s.singAlong.copy(
                    live = live.reading,
                    trace = trace,
                    marks = s.singAlong.marks + outcome.newMarks.toMarkEntries(),
                    lastSummary = outcome.completedPass?.let { it.pass + 1 to it.summary } ?: s.singAlong.lastSummary,
                ),
            )
        }
    }

    private fun List<NoteMark>.toMarkEntries(): List<Pair<MarkKey, com.baijum.purandara.singalong.NoteResult>> =
        map { MarkKey(it.event.position.avarta, it.event.position.akshara, it.event.position.slot) to it.result }

    private fun onFinalPass(result: PassResult?) {
        result ?: return
        _state.update { s ->
            s.copy(
                singAlong = s.singAlong.copy(
                    live = null,
                    marks = s.singAlong.marks + result.marks.toMarkEntries(),
                    lastSummary = result.pass + 1 to result.summary,
                ),
            )
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        val finalPass = engine?.stop()
        engine = null
        player.stop()
        renderer = null
        _state.update {
            it.copy(playback = it.playback.copy(playing = false, position = null, beatInAvarta = null, countingIn = false))
        }
        onFinalPass(finalPass)
    }

    private fun onPlaybackFinished(finished: SampleRenderer) {
        if (renderer === finished) stop()
    }

    private fun headphonesConnected(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }

    override fun onCleared() {
        stop()
        supervisor?.close()
    }

    private companion object {
        const val TAG = "PracticeViewModel"
        const val FRAME_MS = 16L
        const val MAX_TRACE = 1500
        const val MIC_SAMPLE_RATE = 44_100
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
