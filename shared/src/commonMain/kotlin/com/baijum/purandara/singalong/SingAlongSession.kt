package com.baijum.purandara.singalong

import com.baijum.purandara.audio.PracticeSegment
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.platform.PlatformLock
import com.baijum.purandara.platform.withLock

/**
 * Levels of what the app has rendered, by rendered frame, so the mic side
 * can ask how loud the app was when a sound left the speaker.
 *
 * Written from the playback thread, read from the mic thread.
 */
class OutputLevels(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lock = PlatformLock()
    private val starts = LongArray(capacity)
    private val levels = FloatArray(capacity)
    private var count = 0
    private var next = 0

    /** Record that frames from [startFrame] were rendered at [rms]. Blocks must arrive in order. */
    fun record(startFrame: Long, rms: Float) = lock.withLock {
        starts[next] = startFrame
        levels[next] = rms
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** Level of the block containing [frame], or 0 if it was not (or is no longer) recorded. */
    fun rmsAt(frame: Long): Float = lock.withLock {
        var best = -1
        var bestStart = Long.MIN_VALUE
        for (i in 0 until count) {
            val s = starts[i]
            if (s <= frame && s > bestStart) {
                bestStart = s
                best = i
            }
        }
        if (best < 0) 0f else levels[best]
    }

    fun clear() = lock.withLock {
        count = 0
        next = 0
    }

    companion object {
        /** ~11 s of history at 44.1 kHz with 1024-frame blocks. */
        const val DEFAULT_CAPACITY = 512
    }
}

/** What the screen shows live while singing. */
data class LiveReading(val reading: SwaraReading?, val pass: Int?, val secondsInPass: Double?)

/**
 * Result of one mic frame: the live reading, notes whose marks are now
 * known (judged as soon as each note has ended), and a pass that this
 * frame completed, if any.
 */
data class FrameOutcome(val live: LiveReading, val newMarks: List<NoteMark>, val completedPass: PassResult?)

/**
 * Ties the sing-along pieces together for one playback: bleed calibration
 * during the count-in, voice detection, note-by-note scoring and the live
 * reading. Platforms feed it [PitchFrame]s (mic thread) and output levels
 * (playback thread, via [outputLevels]).
 *
 * Mic-thread methods ([onFrame], [finish]) must be called from one thread.
 */
class SingAlongSession(
    private val schedule: Schedule,
    private val clock: SingAlongClock,
    private val sampleRate: Int,
    val outputLevels: OutputLevels = OutputLevels(),
    strictness: Strictness = Strictness.PRECISE,
) {
    private val mapper = SwaraMapper(schedule.settings.saHz, schedule.raga, schedule.settings.tuning, strictness)
    private val scorer = NoteScorer(schedule, mapper, strictness)
    private val bleed = BleedModel()
    private val frames = mutableListOf<ScoredFrame>()
    private val marks = mutableListOf<NoteMark>()
    private var nextNote = 0
    private var currentPass = -1
    private val results = mutableListOf<PassResult>()
    private var finished = false
    private var finishedResult: PassResult? = null

    /** Passes scored so far, oldest first. */
    val passResults: List<PassResult> get() = results

    /** True once the count-in has been used to measure leakage. */
    val bleedCalibrated: Boolean get() = bleed.isCalibrated

    /** Mic level per unit of output level measured in the count-in (near 0 with headphones or echo cancellation). */
    val leakageRatio: Double get() = bleed.leakageRatio

    private val silent = LiveReading(null, null, null)

    fun onFrame(frame: PitchFrame): FrameOutcome {
        if (finished) return FrameOutcome(silent, emptyList(), null)
        val heardFrame = (clock.heardSeconds(frame.timeSeconds) * sampleRate).toLong()
        val outputRms = outputLevels.rmsAt(heardFrame)

        if (clock.inCountIn(frame.timeSeconds)) {
            bleed.calibrate(frame.rms, outputRms)
            return FrameOutcome(silent, emptyList(), null)
        }
        val position = clock.locate(frame.timeSeconds) ?: return FrameOutcome(silent, emptyList(), null)
        if (!bleed.isCalibrated) bleed.finishCalibration()

        var completed: PassResult? = null
        val newMarks = mutableListOf<NoteMark>()
        if (position.pass != currentPass) {
            if (currentPass >= 0) {
                val (rest, result) = completePass()
                newMarks += rest
                completed = result
            }
            currentPass = position.pass
        }
        val voice = frame.frequencyHz != null && bleed.isVoice(frame.rms, outputRms)
        frames += ScoredFrame(position.secondsInPass, frame.frequencyHz, voice)
        newMarks += judgeEndedNotes(position.secondsInPass)
        val reading = if (voice) mapper.read(frame.frequencyHz) else null
        return FrameOutcome(LiveReading(reading, position.pass, position.secondsInPass), newMarks, completed)
    }

    /**
     * Score the pass in progress after the platform drains relevant mic windows. Manual stops
     * deliberately retain a partial result. Repeated finish/late mic callbacks never score twice.
     */
    fun finish(): PassResult? {
        if (!finished) {
            finished = true
            finishedResult = if (currentPass >= 0) completePass().second else null
        }
        return finishedResult
    }

    private fun judgeEndedNotes(now: Double): List<NoteMark> {
        val out = mutableListOf<NoteMark>()
        val notes = schedule.notes
        while (nextNote < notes.size) {
            val e = notes[nextNote]
            if (e.startSeconds + e.durationSeconds + JUDGE_DELAY_SECONDS > now) break
            out += NoteMark(e, scorer.judge(e, frames)).also { marks += it }
            nextNote++
        }
        return out
    }

    private fun completePass(): Pair<List<NoteMark>, PassResult> {
        val rest = judgeEndedNotes(Double.MAX_VALUE)
        val result = PassResult(currentPass, marks.toList(), scorer.summarise(marks))
        results += result
        frames.clear()
        marks.clear()
        nextNote = 0
        return rest to result
    }

    companion object {
        /** A fresh scored attempt, retaining the transport's global mixed-output history. */
        fun forSegment(
            schedule: Schedule,
            segment: PracticeSegment,
            sampleRate: Int,
            outputLevels: OutputLevels,
            strictness: Strictness,
            outputLatencySeconds: Double,
            inputLatencySeconds: Double,
        ): SingAlongSession {
            require(segment.scored && !segment.listening) { "listening/unscored segments cannot create a scoring session" }
            return SingAlongSession(
                schedule,
                SingAlongClock.forSegment(segment, sampleRate, outputLatencySeconds, inputLatencySeconds),
                sampleRate, outputLevels, strictness,
            )
        }

        /** Wait this long after a note ends before judging it (smoothing and timing slack). */
        const val JUDGE_DELAY_SECONDS = 0.06
    }
}
