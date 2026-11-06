package com.baijum.purandara.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baijum.purandara.audio.HarmoniumSamples
import com.baijum.purandara.audio.RenderOptions
import com.baijum.purandara.audio.SampleBank
import com.baijum.purandara.audio.SampleRenderer
import com.baijum.purandara.audio.VarisaiAudioPlayer
import com.baijum.purandara.data.PracticePreferences
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.schedule.NotationPosition
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
)

data class PracticeUiState(
    val settings: PracticeSettings = PracticeSettings(),
    val selected: Varisai? = null,
    val samplesLoaded: Boolean = false,
    val loadError: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
)

/**
 * Holds practice settings, the selected exercise and playback.
 *
 * Settings changes while playing restart the exercise with the new settings.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PracticePreferences(application)
    private val player = VarisaiAudioPlayer(application)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(PracticeUiState(settings = preferences.load()))
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    val catalog: List<Varisai> = VarisaiCatalog.all

    private var bank: SampleBank? = null
    private var renderer: SampleRenderer? = null
    private var ticker: Job? = null

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
    }

    private fun onLoadFailed(e: Exception) {
        Log.e(TAG, "could not decode samples", e)
        _state.update { it.copy(loadError = true) }
    }

    fun select(varisai: Varisai?) {
        stop()
        _state.update { it.copy(selected = varisai, playback = PlaybackState()) }
    }

    fun updateSettings(transform: (PracticeSettings) -> PracticeSettings) {
        val wasPlaying = _state.value.playback.playing
        val next = transform(_state.value.settings)
        if (next == _state.value.settings) return
        preferences.save(next)
        _state.update { it.copy(settings = next) }
        if (wasPlaying) play()
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
        val r = SampleRenderer(
            bank,
            schedule,
            RenderOptions(drone = settings.drone, click = settings.click, loop = settings.loop),
        )
        renderer = r
        _state.update { it.copy(playback = PlaybackState(playing = true, repetitions = schedule.repetitions)) }
        player.play(r) { mainHandler.post { onPlaybackFinished(r) } }
        ticker = viewModelScope.launch {
            while (isActive) {
                val frames = player.positionFrames()
                if (frames != null) {
                    val seconds = r.passSeconds(frames)
                    _state.update {
                        it.copy(
                            playback = it.playback.copy(
                                position = schedule.cursorAt(seconds),
                                beatInAvarta = schedule.beatAt(seconds)?.beatInAvarta,
                            ),
                        )
                    }
                }
                delay(FRAME_MS)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player.stop()
        renderer = null
        _state.update { it.copy(playback = it.playback.copy(playing = false, position = null, beatInAvarta = null)) }
    }

    private fun onPlaybackFinished(finished: SampleRenderer) {
        if (renderer === finished) stop()
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val TAG = "PracticeViewModel"
        const val FRAME_MS = 16L
    }
}
