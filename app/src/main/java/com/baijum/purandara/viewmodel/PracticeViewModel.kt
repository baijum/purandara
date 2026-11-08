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
import com.baijum.purandara.audio.PitchListener
import com.baijum.purandara.audio.RenderOptions
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
import com.baijum.purandara.domain.varisai.SectionSpan
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.shruti.FindSaGuide
import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldTarget
import com.baijum.purandara.shruti.ShrutiOutcome
import com.baijum.purandara.shruti.ShrutiSession
import com.baijum.purandara.singalong.BleedModel
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
    /** One section of [selected] being practised alone, or null for the whole piece. */
    val section: SectionSpan? = null,
    val samplesLoaded: Boolean = false,
    val loadError: Boolean = false,
    val playback: PlaybackState = PlaybackState(),
    val singAlong: SingAlongState = SingAlongState(),
    /** What has been practised (landing page). */
    val history: PracticeHistory = PracticeHistory(),
    /** The landing page's tanpura-only drone is sounding. */
    val tanpuraPlaying: Boolean = false,
    val shruti: ShrutiUiState = ShrutiUiState(),
    val findSa: FindSaUiState = FindSaUiState(),
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

    private val _state = MutableStateFlow(PracticeUiState(settings = preferences.load(), history = preferences.loadHistory()))
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    val catalog: List<Varisai> = VarisaiCatalog.all

    private var bank: SampleBank? = null
    private var renderer: SampleRenderer? = null
    private var schedule: Schedule? = null
    private var ticker: Job? = null
    private var engine: SingAlongEngine? = null
    private var supervisor: NeuralPitchSupervisor? = null
    private var supervisorRequested = false

    /** The exercise playing now has been heard through one full pass (recorded once per play). */
    private var practisedThisPlay = false

    /** Shruti practice or Find my Sa listening. */
    private var listener: PitchListener? = null
    private var shrutiSession: ShrutiSession? = null

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

    /** Opens [varisai] (null: back to the landing page), optionally to practise one [section] alone. */
    fun select(varisai: Varisai?, section: SectionSpan? = null) {
        stop()
        _state.update {
            it.copy(selected = varisai, section = section, playback = PlaybackState(), singAlong = it.singAlong.copy(marks = emptyMap(), trace = emptyList(), lastSummary = null, live = null))
        }
    }

    fun updateSettings(transform: (PracticeSettings) -> PracticeSettings) {
        val wasPlaying = _state.value.playback.playing
        val wasTanpura = _state.value.tanpuraPlaying
        val next = transform(_state.value.settings)
        if (next == _state.value.settings) return
        preferences.save(next)
        _state.update { it.copy(settings = next) }
        if (next.singAlong) loadSupervisor()
        if (wasPlaying) play()
        if (wasTanpura) playTanpura()
        if (_state.value.shruti.open) startShruti()
    }

    fun setMicPermission(granted: Boolean) {
        _state.update { it.copy(singAlong = it.singAlong.copy(micPermitted = granted)) }
    }

    /** The landing page's drone alone, at the current Sa, until stopped. */
    fun toggleTanpura() {
        if (_state.value.tanpuraPlaying) stop() else playTanpura()
    }

    private fun playTanpura() {
        val bank = bank ?: return
        stop()
        val settings = _state.value.settings
        val r = SampleRenderer(bank, Schedule.of(catalog.first(), settings.performance()), RenderOptions.droneOnly(settings.droneLevel))
        renderer = r
        player.play(r) { mainHandler.post { onPlaybackFinished(r) } }
        _state.update { it.copy(tanpuraPlaying = true) }
    }

    // --- Shruti practice (docs/plan-shruti.md) ---

    fun openShruti() {
        _state.update { it.copy(shruti = ShrutiUiState(open = true, target = it.shruti.target, goal = it.shruti.goal)) }
        startShruti()
    }

    fun closeShruti() {
        stop()
        _state.update { it.copy(shruti = it.shruti.copy(open = false, reading = null, trace = emptyList(), hold = null)) }
    }

    fun setHold(target: HoldTarget, goal: HoldGoal) {
        shrutiSession?.startHold(target, goal)
        _state.update { it.copy(shruti = it.shruti.copy(target = target, goal = goal, hold = null)) }
    }

    /** The tanpura plus, with the mic allowed, a [ShrutiSession] listening to the student. */
    private fun startShruti() {
        val bank = bank ?: return
        stop()
        val settings = _state.value.settings
        val r = SampleRenderer(bank, Schedule.of(catalog.first(), settings.performance()), RenderOptions.droneOnly(settings.droneLevel))
        renderer = r
        val listening = _state.value.singAlong.micPermitted || debugSyntheticSingerCents != null
        val session = ShrutiSession(settings.saHz, VarisaiCatalog.raga, settings.tuning, settings.strictness, r.sampleRate)
        val shruti = _state.value.shruti
        session.startHold(shruti.target, shruti.goal)
        if (listening) {
            shrutiSession = session
            player.onRendered = { start, rms -> session.outputLevels.record(start, rms) }
            loadSupervisor()
        }
        player.play(r) { mainHandler.post { onPlaybackFinished(r) } }
        if (listening) {
            val onSpeaker = !headphonesConnected()
            listener = PitchListener(
                // The debug synthetic singer sings Sa after the room check, like a student would.
                micSource(onSpeaker && settings.echoCancellation) { nanos ->
                    player.outputFrameAt(nanos)?.takeIf { it >= ShrutiSession.DEFAULT_ROOM_CHECK_SECONDS * r.sampleRate }?.let { settings.saHz }
                },
                supervisor,
                timeOf = { nanos -> player.outputFrameAt(nanos)?.takeIf { it >= 0 }?.let { it.toDouble() / r.sampleRate } },
            ) { frame ->
                val outcome = session.onFrame(frame)
                mainHandler.post { onShrutiOutcome(r, frame.timeSeconds, outcome) }
            }.also { it.start() }
            _state.update { it.copy(singAlong = it.singAlong.copy(onSpeaker = onSpeaker)) }
        }
        _state.update { it.copy(shruti = it.shruti.copy(listeningToRoom = listening, reading = null, trace = emptyList(), hold = null)) }
    }

    private fun onShrutiOutcome(r: SampleRenderer, seconds: Double, outcome: ShrutiOutcome) {
        if (renderer !== r) return
        val sa = _state.value.settings.saHz
        _state.update { s ->
            val reading = outcome.reading
            val trace = if (reading == null) s.shruti.trace else s.shruti.trace + FreeTracePoint(seconds, Pitch.cents(sa, reading.frequencyHz), reading.zone)
            s.copy(
                shruti = s.shruti.copy(
                    listeningToRoom = outcome.listeningToRoom,
                    reading = reading,
                    trace = trace.dropWhile { it.seconds < seconds - TRACE_SECONDS },
                    hold = outcome.hold,
                ),
            )
        }
    }

    // --- Find my Sa ---

    fun openFindSa() {
        stop()
        val guide = FindSaGuide()
        val start = System.nanoTime()
        _state.update { it.copy(findSa = FindSaUiState(open = true)) }
        if (!_state.value.singAlong.micPermitted && debugSyntheticSingerCents == null) return
        loadSupervisor()
        listener = PitchListener(
            micSource(echoCancellation = false) { nanos -> if ((nanos - start) / NANOS < FAKE_LOW_SECONDS) LOW_DEMO_HZ else HIGH_DEMO_HZ },
            supervisor,
            timeOf = { nanos -> (nanos - start) / NANOS },
        ) { frame ->
            val hz = frame.frequencyHz?.takeIf { frame.rms >= BleedModel.MIN_VOICE_RMS }
            val next = guide.onFrame(frame.timeSeconds, hz)
            mainHandler.post { if (_state.value.findSa.open) _state.update { it.copy(findSa = FindSaUiState(open = true, progress = next)) } }
        }.also { it.start() }
    }

    fun closeFindSa() {
        stop()
        _state.update { it.copy(findSa = FindSaUiState()) }
    }

    fun useSuggestedSa() {
        val suggestion = _state.value.findSa.progress.suggestion ?: return
        closeFindSa()
        updateSettings { it.copy(saMidi = suggestion.saMidi, saCents = 0) }
    }

    private fun updateHistory(transform: (PracticeHistory) -> PracticeHistory) {
        val next = transform(_state.value.history)
        if (next == _state.value.history) return
        preferences.saveHistory(next)
        _state.update { it.copy(history = next) }
    }

    fun togglePlay() {
        if (_state.value.playback.playing) stop() else play()
    }

    fun play() {
        val varisai = _state.value.selected ?: return
        val bank = bank ?: return
        val settings = _state.value.settings
        // The player offers a playable Sa instead; never build a schedule the samples can't play.
        if (settings.saMidi !in varisai.playableSaMidis) return
        stop()
        val section = _state.value.section
        val schedule = section?.let { Schedule.ofSection(varisai, settings.performance(), it) } ?: Schedule.of(varisai, settings.performance())
        val r = SampleRenderer(bank, schedule, settings.renderOptions())
        renderer = r
        this.schedule = schedule
        practisedThisPlay = false
        val onSpeaker = !headphonesConnected()
        _state.update {
            it.copy(
                playback = PlaybackState(playing = true, repetitions = schedule.repetitions),
                singAlong = it.singAlong.copy(onSpeaker = onSpeaker, live = null, pass = null, trace = emptyList(), marks = emptyMap(), lastSummary = null),
            )
        }
        val singAlong = settings.singAlong && (_state.value.singAlong.micPermitted || debugSyntheticSingerCents != null)
        val newEngine = if (singAlong) {
            SingAlongEngine(micSource(onSpeaker && settings.echoCancellation, exerciseTarget(r, schedule)), player, r, SingAlongEngine.sessionFor(r, schedule, settings.strictness), supervisor) { outcome ->
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
                onTick(r, schedule, varisai)
                delay(FRAME_MS)
            }
        }
    }

    /** Follows the heard position: highlight, count-in, and marking the exercise practised. */
    private fun onTick(r: SampleRenderer, schedule: Schedule, varisai: Varisai) {
        val frames = player.outputFrameAt(System.nanoTime())
        if (frames == null || frames < 0) return
        if (!practisedThisPlay && frames >= r.countInFrames + r.performanceFrames) {
            practisedThisPlay = true
            // A section alone doesn't count as practising the whole piece.
            if (schedule.section == null) updateHistory { it.practised(varisai.id, System.currentTimeMillis()) }
        }
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

    /** Debug synthetic singer for an exercise: the note being heard, silent in the count-in. */
    private fun exerciseTarget(r: SampleRenderer, schedule: Schedule): (Long) -> Double? = { nanos ->
        player.outputFrameAt(nanos)?.takeIf { it >= 0 && r.countInBeat(it) == null }?.let { frame ->
            val t = r.passSeconds(frame)
            schedule.notes.lastOrNull { it.startSeconds <= t && t < schedule.durationSeconds }?.frequencyHz
        }
    }

    /** The microphone, or in debug builds a synthetic singer singing [syntheticTarget]. */
    private fun micSource(echoCancellation: Boolean, syntheticTarget: (Long) -> Double?): MicSource {
        val synthetic = debugSyntheticSingerCents
        return if (synthetic != null) {
            SyntheticSingerMicSource(synthetic, syntheticTarget)
        } else {
            AudioRecordMicSource(echoCancellation = echoCancellation)
        }
    }

    private fun onOutcome(r: SampleRenderer, outcome: FrameOutcome) {
        if (renderer !== r) return
        val sa = schedule?.settings?.saHz ?: return
        outcome.completedPass?.let { recordScore(it) }
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
                    // Same counter the trace is tagged with: the whole-performance
                    // loop, reported even while no voice is heard (issue #12).
                    pass = live.pass,
                    trace = trace,
                    marks = s.singAlong.marks + outcome.newMarks.toMarkEntries(),
                    lastSummary = outcome.completedPass?.let { it.pass + 1 to it.summary } ?: s.singAlong.lastSummary,
                ),
            )
        }
    }

    private fun List<NoteMark>.toMarkEntries(): List<Pair<MarkKey, com.baijum.purandara.singalong.NoteResult>> =
        map { MarkKey(it.event.position.avarta, it.event.position.akshara, it.event.position.slot) to it.result }

    private fun recordScore(result: PassResult) {
        if (_state.value.section != null) return // only whole-piece passes are a best result
        val id = _state.value.selected?.id ?: return
        updateHistory { it.scored(id, result, System.currentTimeMillis()) }
    }

    private fun onFinalPass(result: PassResult?) {
        result ?: return
        recordScore(result)
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
        listener?.stop()
        listener = null
        if (shrutiSession != null) player.onRendered = null
        shrutiSession = null
        val finalPass = engine?.stop()
        engine = null
        player.stop()
        renderer = null
        _state.update {
            it.copy(
                playback = it.playback.copy(playing = false, position = null, beatInAvarta = null, countingIn = false),
                tanpuraPlaying = false,
            )
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
        const val TRACE_SECONDS = 10.0
        const val NANOS = 1e9

        /** Debug synthetic singer in Find my Sa: a low note, then a high one. */
        const val FAKE_LOW_SECONDS = 4.0
        const val LOW_DEMO_HZ = 98.0
        const val HIGH_DEMO_HZ = 392.0
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
