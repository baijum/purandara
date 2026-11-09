package com.baijum.purandara.audio

import android.os.Handler
import android.os.Looper
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.PracticePolicy
import com.baijum.purandara.singalong.FrameOutcome
import com.baijum.purandara.singalong.OutputLevels
import com.baijum.purandara.singalong.PassResult
import kotlin.math.ceil

/** Heard state and capture coordination; only the output worker mutates the shared transport. */
class PracticePlayback(
    private val player: VarisaiAudioPlayer,
    private val supervisor: () -> PitchSupervisor?,
    private val makeMic: ((Long) -> Double?) -> MicSource,
    private val callbacks: Callbacks,
) {
    class Callbacks(
        val onProgress: (PracticeSnapshot, Long, Int) -> Unit,
        val onOutcome: (Int, FrameOutcome) -> Unit,
        val onResult: (Int, PassResult) -> Unit,
        val onCompleted: () -> Unit,
        val onAttemptStarted: (Int) -> Unit = {},
    )
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0L
    @Volatile private var transport: PracticeTransport? = null
    private var schedule: Schedule? = null
    private var settings = PracticeSettings()
    @Volatile private var capture: Capture? = null
    @Volatile private var latest: PracticeSnapshot? = null
    private var completedSegments = mutableSetOf<Long>()
    private var preparedId: Long? = null
    private var visibleSegment: Long? = null
    private var desiredFeedback = false
    @Volatile private var configuration = 0L
    private var workerConfiguration = 0L
    val hasCapture: Boolean get() = capture != null

    private data class Capture(val id: Long, val attempt: Int, val engine: PracticeCapture, val strictness: com.baijum.purandara.singalong.Strictness)

    fun start(bank: SampleBank, schedule: Schedule, settings: PracticeSettings, feedback: Boolean, automaticRestart: Boolean) {
        stop()
        val token = ++generation
        this.schedule = schedule
        this.settings = settings
        desiredFeedback = feedback
        workerConfiguration = configuration
        val outputLevels = OutputLevels()
        completedSegments.clear()
        val next = PracticeTransport.create(bank, schedule, options(settings), policy(settings, feedback), token)
        transport = next
        next.onSegmentStarted = { segment ->
            if (generation == token) capture?.takeIf { it.id == segment.segmentId }?.let { it.engine.bind(segment, schedule, outputLevels, it.strictness) }
        }
        player.onRendered = { frame, rms -> outputLevels.record(frame, rms) }
        player.onProgress = progress@{ heard ->
            if (generation != token) return@progress
            val snapshot = next.snapshot()
            val revision = workerConfiguration
            latest = snapshot
            main.post { if (generation == token && transport === next && revision == configuration) progress(next, snapshot, heard) }
        }
        player.play(next, automaticRestart) {
            main.post {
                if (generation == token && transport === next) callbacks.onCompleted()
            }
        }
    }

    private fun progress(source: PracticeTransport, snapshot: PracticeSnapshot, heard: Long) {
        val finished = snapshot.segments.filter { heard >= it.endFrame && it.segmentId !in completedSegments }
        for (segment in finished) {
            completedSegments += segment.segmentId
            if (capture?.id == segment.segmentId) finishCapture()
        }
        if (transport !== source) return // a result observer may Stop or navigate
        val heardSegment = snapshot.segmentAt(heard)
        if (heardSegment != null && !heardSegment.listening && visibleSegment != heardSegment.segmentId) {
            visibleSegment = heardSegment.segmentId
            callbacks.onAttemptStarted(heardSegment.attempt)
        }
        val preparation = snapshot.pendingPreparation
        // Do not even start capturing while a model or previous attempt is still heard.
        if (shouldPrepareCapture(desiredFeedback, preparation, preparedId) && captureMayStart(snapshot, heard)) {
            prepareCapture(source, requireNotNull(preparation))
        }
        val remaining = snapshot.remainingFramesAt(heard)?.let { ceil(it.toDouble() / source.sampleRate).toInt() } ?: 0
        callbacks.onProgress(snapshot, heard, remaining)
    }

    private fun prepareCapture(source: PracticeTransport, preparation: PracticePreparation) {
        finishCapture()
        preparedId = preparation.segmentId
        val token = generation
        val engine = PracticeCapture(makeMic(::targetAt), player, source.sampleRate, supervisor) { outcome ->
            main.post {
                if (generation == token && capture?.id == preparation.segmentId) callbacks.onOutcome(preparation.attempt, outcome)
            }
        }
        capture = Capture(preparation.segmentId, preparation.attempt, engine, settings.strictness)
        val revision = configuration
        try {
            engine.start {
                player.enqueue(source) {
                    if (revision == configuration) source.setReady(token, preparation.segmentId, true)
                }
            }
        } catch (_: IllegalStateException) {
            finishCapture() // leave drone-only waiting: explicit Stop/Continue remain available
        } catch (_: SecurityException) {
            finishCapture()
        }
    }

    private fun targetAt(nanos: Long): Double? {
        val heard = player.outputFrameAt(nanos) ?: return null
        val segment = latest?.segmentAt(heard)?.takeIf { it.scored && heard >= it.performanceStartFrame && heard < it.performanceEndFrame } ?: return null
        val rate = transport?.sampleRate ?: return null
        val time = segment.secondsInPerformance(heard, rate)
        return schedule?.notes?.lastOrNull { it.startSeconds <= time }?.frequencyHz
    }

    private fun finishCapture() {
        val previous = capture ?: return
        capture = null
        previous.engine.stop()?.let { callbacks.onResult(previous.attempt, it) }
    }

    fun continuePractice() {
        if (capture == null) preparedId = null // an explicit retry may prepare a failed microphone again
        transport?.let { source -> player.enqueue(source) { source.continuePractice(source.runId) } }
    }

    fun waitForMe() {
        val revision = ++configuration
        val prepared = preparedId
        finishCapture()
        preparedId = null
        transport?.let { source ->
            player.enqueue(source) {
                prepared?.let { source.setReady(source.runId, it, false) }
                source.waitForMe(source.runId)
                workerConfiguration = revision
            }
        }
    }

    /** Waiting settings never restart output; tuning/selection changes are handled by the owner. */
    fun update(settings: PracticeSettings, feedback: Boolean) {
        this.settings = settings
        desiredFeedback = feedback
        val revision = ++configuration
        val prepared = preparedId
        finishCapture()
        preparedId = null
        transport?.let { source ->
            player.enqueue(source) {
                prepared?.let { source.setReady(source.runId, it, false) }
                source.updatePolicy(source.runId, policy(settings, feedback))
                source.updateOptions(source.runId, options(settings))
                workerConfiguration = revision
            }
        }
    }

    /** Invalidates preparation replies before stopping/joining capture or output. */
    fun stop() {
        generation++
        configuration++
        desiredFeedback = false
        transport = null
        latest = null
        preparedId = null
        visibleSegment = null
        finishCapture()
        player.onProgress = null
        player.onRendered = null
    }

    companion object {
        internal fun captureMayStart(snapshot: PracticeSnapshot, heard: Long): Boolean {
            if ((snapshot.segments.lastOrNull()?.endFrame ?: 0L) > heard) return false
            val recovery = snapshot.recoveryAt(heard) ?: return false
            val deadline = recovery.deadlineFrame ?: return false
            return !recovery.waitingForContinue && heard >= deadline
        }

        internal fun shouldPrepareCapture(feedback: Boolean, preparation: PracticePreparation?, preparedId: Long?): Boolean =
            feedback && preparation?.scored == true && preparedId != preparation.segmentId

        fun policy(settings: PracticeSettings, feedback: Boolean): PracticePolicy = PracticePolicy.create(
            settings.practiceMode, settings.recoveryMode, settings.recoverySeconds,
            settings.loop, feedback, settings.responseGuide,
        )

        private fun options(settings: PracticeSettings): RenderOptions = RenderOptions.practice(
            drone = settings.drone, click = settings.click, loop = false, countIn = settings.countIn,
            harmoniumLevel = if (settings.practiceMode == PracticeMode.LISTEN_REPEAT || !settings.singAlong || settings.harmoniumGuide) settings.harmoniumLevel else 0f,
            droneLevel = settings.droneLevel, articulation = settings.articulation,
        )
    }
}
