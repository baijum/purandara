package com.baijum.purandara.audio

import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.PracticePolicy
import com.baijum.purandara.practice.RecoveryMode
import kotlin.math.min

/** UI phase is derived from heard frames, never the render-ahead position. */
enum class PracticePhase { COUNT_IN, PLAYING, LISTEN, YOUR_TURN, RELEASE, RECOVERY, WAITING, STOPPING, FINISHED }

enum class PracticeCompletion { NATURAL, REQUESTED_STOP, INTERRUPTION, FAILURE }

data class PracticePreparation(
    val runId: Long,
    val segmentId: Long,
    val attempt: Int,
    val response: Boolean,
    val scored: Boolean,
)

/** One finite model or singing attempt; all boundaries are in the global output timeline. */
@Suppress("LongParameterList") // Explicit immutable frame descriptor, also consumed by Swift.
data class PracticeSegment(
    val runId: Long,
    val segmentId: Long,
    val attempt: Int,
    val response: Boolean,
    val scored: Boolean,
    val listening: Boolean,
    val startFrame: Long,
    val countInFrames: Int,
    val performanceFrames: Int,
    val releaseFrames: Int,
) {
    val performanceStartFrame: Long get() = startFrame + countInFrames
    val performanceEndFrame: Long get() = performanceStartFrame + performanceFrames
    val endFrame: Long get() = performanceEndFrame + releaseFrames

    fun secondsInPerformance(heardFrame: Long, sampleRate: Int): Double =
        (heardFrame - performanceStartFrame).toDouble() / sampleRate
}

data class PracticeRecovery(
    val startFrame: Long,
    val endFrame: Long?,
    val deadlineFrame: Long?,
    val waitingForReady: Boolean,
    val waitingForContinue: Boolean,
)

/** Immutable, worker-produced value. Publish it rather than sharing a mutable renderer with UI. */
@Suppress("LongParameterList") // One immutable snapshot crosses the worker/UI boundary.
data class PracticeSnapshot(
    val runId: Long,
    val segments: List<PracticeSegment>,
    val recoveries: List<PracticeRecovery>,
    val renderedFrames: Long,
    val stopFrame: Long?,
    val endFrame: Long?,
    val pendingPreparation: PracticePreparation?,
    val recoverySeconds: Int,
    val completion: PracticeCompletion?,
) {
    fun segmentAt(heardFrame: Long): PracticeSegment? =
        segments.lastOrNull { heardFrame >= it.startFrame && heardFrame < it.endFrame }

    fun recoveryAt(heardFrame: Long): PracticeRecovery? =
        recoveries.lastOrNull { heardFrame >= it.startFrame && (it.endFrame == null || heardFrame < it.endFrame) }

    fun remainingFramesAt(heardFrame: Long): Long? =
        recoveryAt(heardFrame)?.deadlineFrame?.let { (it - heardFrame).coerceAtLeast(0) }

    fun phaseAt(heardFrame: Long): PracticePhase {
        if (endFrame != null && heardFrame >= endFrame) return PracticePhase.FINISHED
        if (stopFrame != null && heardFrame >= stopFrame) return PracticePhase.STOPPING
        val segment = segmentAt(heardFrame)
        if (segment != null) return when {
            heardFrame < segment.performanceStartFrame -> PracticePhase.COUNT_IN
            heardFrame >= segment.performanceEndFrame -> PracticePhase.RELEASE
            segment.listening -> PracticePhase.LISTEN
            segment.response -> PracticePhase.YOUR_TURN
            else -> PracticePhase.PLAYING
        }
        val recovery = recoveryAt(heardFrame)
        return if (recovery == null || recovery.waitingForContinue ||
            recovery.waitingForReady && (recovery.deadlineFrame == null || heardFrame >= recovery.deadlineFrame)
        ) PracticePhase.WAITING else PracticePhase.RECOVERY
    }
}

/**
 * Continuous drone plus finite attempts. All mutations, callbacks and rendering belong to one
 * render worker. Commands affect only unrendered audio: platform Stop also invalidates/flushes
 * queued output. A readiness failure leaves drone-only waiting, without blocking that worker.
 *
 * Normal continuous repeat deliberately uses SampleRenderer directly, not this transport.
 */
class PracticeTransport private constructor(
    private val bank: SampleBank,
    val schedule: Schedule,
    private var options: RenderOptions,
    policy: PracticePolicy,
    val runId: Long,
) : PcmSource {
    override val sampleRate: Int = bank.sampleRate
    override var position: Long = 0
        private set
    var policy: PracticePolicy = policy
        private set

    private val drone = SampleRenderer(bank, schedule, options.copy(harmoniumGain = 0f, click = false, countIn = false, loop = true))
    private val scratch = FloatArray(SCRATCH_FRAMES)
    private val history = mutableListOf<PracticeSegment>()
    private val recoveryHistory = mutableListOf<PracticeRecovery>()
    private var nextId = 0L
    private var active: SampleRenderer? = null
    private var activeSegment: PracticeSegment? = null
    private var candidate: SampleRenderer? = null
    private var candidateListening = false
    private var candidateReady = false
    private var preparation: PracticePreparation? = null
    private var stopStart: Long? = null
    private var end: Long? = null
    private var completion: PracticeCompletion? = null
    private var forceWait = false

    /** Called on the render worker before any frame of the committed segment is rendered. */
    var onSegmentStarted: ((PracticeSegment) -> Unit)? = null

    val pendingPreparation: PracticePreparation? get() = preparation
    val segments: List<PracticeSegment> get() = history.toList()

    init {
        prepare(attempt = 1, listening = policy.mode == PracticeMode.LISTEN_REPEAT, response = false)
        openRecovery(initial = true)
    }

    fun snapshot(): PracticeSnapshot = PracticeSnapshot(
        runId, history.toList(), recoveryHistory.toList(), position, stopStart, end,
        preparation, policy.recoverySeconds, completion,
    )

    /** A stale run or preparation token cannot release a later attempt. */
    fun setReady(runId: Long, segmentId: Long, ready: Boolean): Boolean {
        val p = preparation ?: return false
        if (!accepts(runId) || p.segmentId != segmentId || !p.scored) return false
        candidateReady = ready
        refreshRecovery()
        return true
    }

    /** Continue starts at the first unrendered frame, after capture readiness if needed. */
    fun continuePractice(runId: Long): Boolean {
        if (!accepts(runId) || candidate == null || active != null) return false
        forceWait = false
        replaceRecovery { it.copy(deadlineFrame = position, waitingForContinue = false) }
        return true
    }

    /** Cancels the deadline. Already buffered notes cannot be recalled by this command. */
    fun waitForMe(runId: Long): Boolean {
        if (!accepts(runId)) return false
        forceWait = true
        if (active == null) replaceRecovery { it.copy(deadlineFrame = null, waitingForContinue = true) }
        return true
    }

    /** Changed deadlines are relative to the original break, never an immediate surprise restart. */
    fun updatePolicy(runId: Long, policy: PracticePolicy): Boolean {
        if (!accepts(runId)) return false
        this.policy = policy
        if (active == null && candidate != null) {
            val p = requireNotNull(preparation)
            val completed = history.lastOrNull()
            if (!policy.repeat && completed != null && p.attempt > completed.attempt) {
                terminate(PracticeCompletion.NATURAL, STOP_FADE_SECONDS)
                return true
            }
            val listening = !p.response && policy.mode == PracticeMode.LISTEN_REPEAT
            prepare(p.attempt, listening, p.response, p.segmentId)
            replaceRecovery { old ->
                val initial = history.isEmpty()
                val deadline = if (initial) old.startFrame else deadlineAt(old.startFrame)
                val elapsed = !initial && deadline != null && deadline <= position
                old.copy(
                    deadlineFrame = if (forceWait || elapsed) null else deadline,
                    waitingForContinue = forceWait || elapsed || !initial && policy.recoveryMode == RecoveryMode.WHEN_READY,
                    waitingForReady = !candidateReady,
                )
            }
        }
        return true
    }

    /** Next-attempt mix/count-in preferences; the continuous drone is intentionally unchanged. */
    fun updateOptions(runId: Long, options: RenderOptions): Boolean {
        if (!accepts(runId)) return false
        this.options = options
        if (active == null && candidate != null) {
            val p = requireNotNull(preparation)
            prepare(p.attempt, candidateListening, p.response, p.segmentId)
            refreshRecovery()
        }
        return true
    }

    override fun render(out: FloatArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= out.size - length) { "bad range" }
        var written = 0
        while (written < length && (end == null || position < requireNotNull(end))) {
            if (stopStart == null) advance()
            var chunk = min(length - written, scratch.size)
            end?.let { chunk = min(chunk.toLong(), it - position).toInt() }
            val segment = activeSegment
            if (active != null && segment != null && position < segment.endFrame) {
                chunk = min(chunk.toLong(), segment.endFrame - position).toInt()
            } else if (stopStart == null) {
                val r = recoveryHistory.lastOrNull()
                if (r?.deadlineFrame != null && !r.waitingForContinue && candidateReady && r.deadlineFrame > position) {
                    chunk = min(chunk.toLong(), r.deadlineFrame - position).toInt()
                }
            }
            if (chunk <= 0) continue
            drone.renderUnclipped(out, offset + written, chunk)
            val exerciseFrames = active?.renderUnclipped(scratch, 0, chunk) ?: 0
            for (i in 0 until chunk) {
                var mixed = out[offset + written + i] + if (i < exerciseFrames) scratch[i] else 0f
                val stop = stopStart
                if (stop != null) mixed *= (requireNotNull(end) - position - i).toFloat() / (requireNotNull(end) - stop)
                out[offset + written + i] = mixed.coerceIn(-1f, 1f)
            }
            position += chunk
            written += chunk
        }
        // Commit a boundary even when the caller's block ends exactly there.
        if (stopStart == null) advance()
        return written
    }

    /** Requested stop cancels all future attempts immediately, before the worker renders a fade. */
    override fun fadeOut(seconds: Double) {
        terminate(PracticeCompletion.REQUESTED_STOP, seconds)
    }

    fun terminate(reason: PracticeCompletion, seconds: Double) {
        require(seconds.isFinite() && seconds >= 0.0) { "invalid stop fade" }
        if (stopStart != null) return
        preparation = null
        candidate = null
        stopStart = position
        end = position + maxOf(1, (seconds * sampleRate).toLong())
        completion = reason
        closeRecovery(position)
    }

    override fun reset() {
        position = 0
        drone.reset()
        history.clear()
        recoveryHistory.clear()
        active = null
        activeSegment = null
        candidate = null
        preparation = null
        stopStart = null
        end = null
        completion = null
        forceWait = false
        // IDs are never reused, so a readiness reply from before reset stays stale.
        prepare(1, policy.mode == PracticeMode.LISTEN_REPEAT, response = false)
        openRecovery(initial = true)
    }

    private fun accepts(id: Long): Boolean = id == runId && stopStart == null

    private fun advance() {
        val current = activeSegment
        if (active != null && current != null && position >= current.endFrame) {
            active = null
            activeSegment = null
            if (current.listening) prepare(current.attempt, listening = false, response = true)
            else if (policy.repeat) prepare(current.attempt + 1, policy.mode == PracticeMode.LISTEN_REPEAT, response = false)
            else {
                terminate(PracticeCompletion.NATURAL, STOP_FADE_SECONDS)
                return
            }
            openRecovery(initial = false)
        }
        if (active != null || candidate == null) return
        val recovery = recoveryHistory.last()
        if (!candidateReady || recovery.waitingForContinue || recovery.deadlineFrame == null || position < recovery.deadlineFrame) return
        val renderer = requireNotNull(candidate)
        val p = requireNotNull(preparation)
        val segment = PracticeSegment(
            runId, p.segmentId, p.attempt, p.response, p.scored, candidateListening, position,
            renderer.countInFrames, renderer.performanceFrames,
            (renderer.totalFrames - renderer.countInFrames - renderer.performanceFrames).toInt(),
        )
        closeRecovery(position)
        history += segment
        active = renderer
        activeSegment = segment
        candidate = null
        preparation = null
        onSegmentStarted?.invoke(segment)
    }

    private fun prepare(attempt: Int, listening: Boolean, response: Boolean, id: Long = ++nextId) {
        val scored = !listening && policy.feedback
        val previous = preparation
        val captureChanged = previous != null && previous.segmentId == id &&
            (previous.scored != scored || previous.response != response || candidateListening != listening)
        val token = if (captureChanged) ++nextId else id
        val previouslyReady = previous?.takeIf { it.segmentId == token && it.scored == scored } != null && candidateReady
        candidateListening = listening
        preparation = PracticePreparation(runId, token, attempt, response, scored)
        candidateReady = !scored || previouslyReady
        candidate = SampleRenderer(
            bank, schedule,
            options.copy(
                drone = false, loop = false, countIn = !listening && (options.countIn || scored),
                harmoniumGain = if (response && !policy.responseGuide) 0f else options.harmoniumGain,
            ),
        )
    }

    private fun openRecovery(initial: Boolean) {
        recoveryHistory += PracticeRecovery(
            position, null, if (initial) position else if (forceWait) null else deadlineAt(position), !candidateReady,
            !initial && (forceWait || policy.recoveryMode == RecoveryMode.WHEN_READY),
        )
    }

    private fun deadlineAt(start: Long): Long? = when (policy.recoveryMode) {
        RecoveryMode.CONTINUOUS -> start
        RecoveryMode.TIMED -> start + policy.recoverySeconds.toLong() * sampleRate
        RecoveryMode.WHEN_READY -> null
    }

    private fun refreshRecovery() = replaceRecovery { it.copy(waitingForReady = !candidateReady) }

    private fun replaceRecovery(change: (PracticeRecovery) -> PracticeRecovery) {
        if (recoveryHistory.lastOrNull()?.endFrame == null && recoveryHistory.isNotEmpty()) {
            recoveryHistory[recoveryHistory.lastIndex] = change(recoveryHistory.last())
        }
    }

    private fun closeRecovery(frame: Long) = replaceRecovery { it.copy(endFrame = frame) }

    companion object {
        private const val SCRATCH_FRAMES = 4096
        private const val STOP_FADE_SECONDS = 0.08

        fun create(
            bank: SampleBank,
            schedule: Schedule,
            options: RenderOptions,
            policy: PracticePolicy,
            runId: Long,
        ): PracticeTransport = PracticeTransport(bank, schedule, options, policy, runId)
    }
}
