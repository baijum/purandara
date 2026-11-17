package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.TanpuraBank
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Akshara
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the drone sounds like. */
enum class DroneStyle {
    /** Sustained harmonium Sa, Pa and upper Sa: used only when the tanpura loops are not loaded. */
    HARMONIUM,

    /** A real tanpura plucking pa SA SA sa (bundled recording): the app's drone. */
    TANPURA,
}

/** How successive harmonium notes join; notation and scoring keep their written timing. */
enum class HarmoniumArticulation {
    /** The existing centred legato crossfade. */
    CONNECTED,

    /** A small gap before the next note, except after a karvai-held note. */
    LIGHTLY_SEPARATED,
}

/** Mix levels and articulation for [SampleRenderer]. */
data class RenderOptions(
    val harmoniumGain: Float = 0.6f,
    val drone: Boolean = true,
    val droneStyle: DroneStyle = DroneStyle.TANPURA,
    val droneGain: Float = 0.2f,
    val click: Boolean = false,
    val clickGain: Float = 0.35f,
    /**
     * Repeat the exercise on the beat, with the chosen articulation across
     * the seam and the drone continuing. [SampleRenderer.render]
     * then never ends on its own; call [SampleRenderer.fadeOut] to stop.
     */
    val loop: Boolean = false,
    /**
     * Before the exercise, one avarta of tala clicks (the drone already
     * sounding), as you would clap a cycle before singing. Played once, even
     * when looping, and regardless of [click].
     */
    val countIn: Boolean = false,
    /** Crossfade between consecutive notes, centred on the note boundary. */
    val legatoSeconds: Double = 0.03,
    /** Fade-out after the last note (not used when looping). */
    val releaseSeconds: Double = 0.25,
    /** Drone fade-in at the start and fade-out at the end. */
    val droneFadeSeconds: Double = 0.3,
    val articulation: HarmoniumArticulation = HarmoniumArticulation.CONNECTED,
) {
    companion object {
        private const val MAX_HARMONIUM_GAIN = 0.8f
        private const val MAX_DRONE_GAIN = 0.4f

        /** Default harmonium level (0..1) for [practice]. */
        const val DEFAULT_HARMONIUM_LEVEL = 0.75f

        /** Default drone level (0..1) for [practice]. */
        const val DEFAULT_DRONE_LEVEL = 0.5f

        /**
         * Options from the student's practice settings; levels are 0..1.
         * Also for callers without default arguments (Swift).
         */
        fun practice(
            drone: Boolean,
            click: Boolean,
            loop: Boolean,
            countIn: Boolean,
            harmoniumLevel: Float,
            droneLevel: Float,
            articulation: HarmoniumArticulation = HarmoniumArticulation.CONNECTED,
        ): RenderOptions = RenderOptions(
            harmoniumGain = MAX_HARMONIUM_GAIN * harmoniumLevel.coerceIn(0f, 1f),
            drone = drone,
            droneGain = MAX_DRONE_GAIN * droneLevel.coerceIn(0f, 1f),
            click = click,
            loop = loop,
            countIn = countIn,
            articulation = articulation,
        )

        /**
         * The same mix, looping: a practice run repeats the current kala
         * until its passes are done. For callers without default arguments
         * (Swift).
         */
        fun looping(options: RenderOptions): RenderOptions = options.copy(loop = true)

        /**
         * The tanpura alone, looping until faded out, for tuning the voice
         * before practice: render with any schedule at the student's Sa.
         */
        fun droneOnly(droneLevel: Float): RenderOptions = RenderOptions(
            harmoniumGain = 0f,
            drone = true,
            droneGain = MAX_DRONE_GAIN * droneLevel.coerceIn(0f, 1f),
            loop = true,
        )
    }
}

/**
 * Renders a [Schedule] to mono float PCM at the bank's sample rate.
 *
 * Shared by Android and iOS so timing is identical, and testable on the JVM.
 * [render] can be called with any block size and gives the same result as
 * one big call. It allocates nothing while rendering, but it is not
 * thread-safe and not meant for a real-time callback on iOS (Kotlin/Native
 * GC); platforms render ahead on one worker thread.
 */
class SampleRenderer(
    private val bank: SampleBank,
    val schedule: Schedule,
    private val options: RenderOptions = RenderOptions(),
) : PcmSource {
    override val sampleRate: Int = bank.sampleRate

    private val releaseFrames = framesOf(options.releaseSeconds, sampleRate)

    /** Frames of one pass of the performance, without any release tail. */
    val performanceFrames: Int = framesOf(schedule.durationSeconds, sampleRate)

    /** Frames per tala count (a beat at one kalai, its half at two). */
    private val countFrames: Int = framesOf(schedule.settings.talaAksharaSeconds / schedule.varisai.kalai, sampleRate)

    /** Frames of count-in before the exercise starts (0 without a count-in). */
    val countInFrames: Int = if (options.countIn) schedule.tala.aksharas * schedule.varisai.kalai * countFrames else 0

    /**
     * Where repeating a tala cycle has taken the performance back
     * (docs/plan-repeat-cycle.md); empty unless [repeatCycle] was used.
     * Safe to read from any thread.
     */
    val repeats = CycleRepeats()

    /**
     * Total frames when not looping: count-in, performance, any repeated
     * cycles and release. Unbounded ([Long.MAX_VALUE]) when looping.
     */
    val totalFrames: Long
        get() = if (options.loop) Long.MAX_VALUE else countInFrames + performanceFrames + releaseFrames + repeats.totalShift

    /** Frames rendered so far. */
    override var position: Long = 0
        private set

    /** In note order, so by [Voice.noteStart]. */
    private val voices: List<Voice> = buildVoices()
    private val voiceNoteStarts = LongArray(voices.size) { voices[it].noteStart }

    /** Most frames a voice sounds before its note starts (a legato crossfade). */
    private val maxPreRoll: Long = voices.maxOfOrNull { it.noteStart - it.start } ?: 0L

    /** Most frames a voice sounds from its note's start (a held note and its release). */
    private val maxVoiceFrames: Long = voices.maxOfOrNull { it.end - it.noteStart } ?: 0L

    private val drones: List<Voice> = if (options.drone) buildDrones() else emptyList()

    /** Frames over which notes cut off by a repeat jump fade out. */
    private val seamFrames: Int = max(framesOf(options.legatoSeconds, sampleRate) / 2, framesOf(SEAM_SECONDS, sampleRate))

    /** One tala beat: a press heard within the first beat of a cycle repeats the one before. */
    private val beatFrames: Long = framesOf(schedule.settings.talaAksharaSeconds, sampleRate).toLong()

    /**
     * Stretches of output, oldest first, each playing the performance with
     * one shift; a new one starts at each repeat jump. The last is current.
     */
    private val spans = ArrayList<Span>().apply { add(Span(0, Long.MAX_VALUE, 0)) }

    /** The span that starts at the next repeat jump, once one is decided. */
    private var pending: Span? = null

    /** The cycle being repeated (counted across passes), or null. Render worker only. */
    var repeatingCycle: Long? = null
        private set

    /** Frames the drone has been lengthened by for repeats (not looping). */
    private var droneExtension = 0L
    private val clickStarts: IntArray
    private val clickKriyas: Array<Kriya>
    private val clickSoft: BooleanArray

    /** One click sound per hand action, so a laghu's counts sound unlike its clap. */
    private val clickWaves: Map<Kriya, FloatArray> = Kriya.entries.associateWith { clickWave(CLICK_TONES.getValue(it)) }
    private val clickFrames: Int = framesOf(CLICK_SECONDS, sampleRate)

    private var fadeOutStart = Long.MAX_VALUE
    private var fadeOutFrames = 0

    init {
        val beats = if (options.click) schedule.beats else emptyList()
        clickStarts = IntArray(beats.size) { framesOf(beats[it].startSeconds, sampleRate) }
        clickKriyas = Array(beats.size) { beats[it].beat.kriya }
        clickSoft = BooleanArray(beats.size) { beats[it].countInKriya > 0 }
    }

    /**
     * Renders up to [length] frames into [out] starting at [offset].
     * @return frames written; 0 once the performance (or a [fadeOut]) has finished.
     */
    override fun render(out: FloatArray, offset: Int, length: Int): Int = renderSamples(out, offset, length, clip = true)

    /** Transport mixes the drone and exercise before a single final clip. */
    internal fun renderUnclipped(out: FloatArray, offset: Int, length: Int): Int = renderSamples(out, offset, length, clip = false)

    private fun renderSamples(out: FloatArray, offset: Int, length: Int, clip: Boolean): Int {
        require(offset >= 0 && length >= 0 && offset <= out.size - length) { "bad range" }
        val end = min(totalFrames, fadeOutStart + fadeOutFrames)
        val n = min(length.toLong(), end - position).toInt()
        if (n <= 0) return 0
        out.fill(0f, offset, offset + n)

        var done = 0
        while (done < n) {
            val frame = position + done
            if (frame < countInFrames) {
                val chunk = min(n - done, (countInFrames - frame).toInt())
                mixCountIn(out, offset + done, frame.toInt(), frame.toInt() + chunk)
                done += chunk
                continue
            }
            val since = frame - countInFrames
            commitJump(since)
            // Stop exactly at a decided jump, so the next span starts on its frame.
            val chunk = pending?.let { min((n - done).toLong(), it.outputStart - since).toInt() } ?: (n - done)
            for (span in spans) mixSpan(span, out, offset + done, since, since + chunk)
            pending?.let { mixSpan(it, out, offset + done, since, since + chunk) }
            pruneSpans(since + chunk)
            done += chunk
        }
        for (drone in drones) drone.mixInto(out, offset, position, position + n)

        for (i in 0 until n) {
            val t = position + i
            var sample = out[offset + i]
            if (t >= fadeOutStart) sample *= (fadeOutStart + fadeOutFrames - t).toFloat() / fadeOutFrames
            out[offset + i] = if (clip) sample.coerceIn(-1f, 1f) else sample
        }
        position += n
        return n
    }

    /** Fades everything out over [seconds] from the current position; [render] then finishes. */
    override fun fadeOut(seconds: Double) {
        if (fadeOutStart != Long.MAX_VALUE) return
        fadeOutFrames = max(1, framesOf(seconds, sampleRate))
        fadeOutStart = position
    }

    /** Starts again from the beginning. */
    override fun reset() {
        position = 0
        fadeOutStart = Long.MAX_VALUE
        spans.clear()
        spans += Span(0, Long.MAX_VALUE, 0)
        pending = null
        repeatingCycle = null
        extendDrones(-droneExtension)
        repeats.clear()
    }

    /**
     * Seconds into the current pass of the exercise for a frame since the
     * start (e.g. the audio device's play head). Negative during the count-in.
     */
    fun passSeconds(frame: Long): Double {
        val sinceStart = frame - countInFrames
        if (sinceStart < 0) return sinceStart.toDouble() / sampleRate
        val u = repeats.performanceFrame(sinceStart)
        val inPass = if (options.loop) u % performanceFrames else u
        return inPass.toDouble() / sampleRate
    }

    /**
     * Frames into the performance, counted across passes, heard at [frame]
     * (frames since the start), after any repeated cycles; null in the count-in.
     */
    fun performanceFrame(frame: Long): Long? =
        (frame - countInFrames).takeIf { it >= 0 }?.let { repeats.performanceFrame(it) }

    /** The pass (0-based) heard at [frame]: the attempt, less one. 0 in the count-in. */
    fun passAt(frame: Long): Int = ((performanceFrame(frame) ?: 0L) / performanceFrames).toInt()

    /** The tala cycle within its pass (a notation block) heard at [frame], or null in the count-in or after the end. */
    fun cycleInPassAt(frame: Long): Int? {
        val u = performanceFrame(frame) ?: return null
        if (!options.loop && u >= performanceFrames) return null
        return (cycleIndexAt(u) % schedule.cycles).toInt()
    }

    /**
     * The cycle (counted across passes) that Repeat this cycle should repeat
     * when pressed while [frame] is heard: the cycle playing, or the one just
     * finished if the press comes within its first beat (you notice a slip as
     * the next cycle begins). Never reaches back into the previous pass,
     * whose sing-along result is already complete. Null in the count-in or
     * after the end. Safe to call from any thread.
     */
    fun cycleToRepeat(frame: Long): Long? {
        val u = performanceFrame(frame) ?: return null
        if (!options.loop && u >= performanceFrames) return null
        val k = cycleIndexAt(u)
        val early = u - cycleStartFrame(k) < beatFrames
        return if (early && k % schedule.cycles != 0L) k - 1 else k
    }

    /**
     * Render worker: from the end of the cycle being rendered, play [cycle]
     * (from [cycleToRepeat]) again and again until [continueAfterRepeat].
     * Audio already rendered is never changed, so the cycle playing finishes
     * first. A cycle not yet reached, or in an earlier pass than the one
     * being rendered, repeats the cycle being rendered instead.
     * @return false in the count-in, after the end or while fading out.
     */
    fun repeatCycle(cycle: Long): Boolean {
        if (fadeOutStart != Long.MAX_VALUE) return false
        val since = position - countInFrames
        if (since < 0) return false
        val current = spans.last()
        val u = since - current.shift
        if (!options.loop && u >= performanceFrames) return false
        val rendering = cycleIndexAt(u)
        val samePass = cycle >= 0 && cycle / schedule.cycles == rendering / schedule.cycles
        val target = if (samePass && cycle <= rendering) cycle else rendering
        repeatingCycle = target
        decideJump(cycleStartFrame(rendering + 1), cycleStartFrame(target))
        return true
    }

    /**
     * Render worker: stop repeating. The pass of the cycle being played
     * finishes and the performance carries on into the next cycle.
     */
    fun continueAfterRepeat() {
        repeatingCycle = null
        if (pending != null) {
            pending = null
            spans.last().end = Long.MAX_VALUE
            extendDrones(repeats.totalShift - droneExtension)
        }
    }

    /** The current span ends at performance frame [from]; then the performance goes back to [to]. */
    private fun decideJump(from: Long, to: Long) {
        val current = spans.last()
        current.end = from
        pending = Span(to, Long.MAX_VALUE, from + current.shift - to)
        // Without looping the drone ends with the performance: keep it going for the repeat.
        extendDrones(from - to + repeats.totalShift - droneExtension)
    }

    /** Take a decided jump once rendering reaches it, and decide the next while repeating. */
    private fun commitJump(since: Long) {
        val next = pending ?: return
        if (since < next.outputStart) return
        spans += next
        pending = null
        repeats.add(next.outputStart, next.shift)
        repeatingCycle?.let { decideJump(cycleStartFrame(it + 1), cycleStartFrame(it)) }
    }

    /** Old spans are kept only while their cut-off notes are still fading. */
    private fun pruneSpans(since: Long) {
        while (spans.size > 1 && spans[0].end != Long.MAX_VALUE && spans[0].end + spans[0].shift + seamFrames <= since) {
            spans.removeAt(0)
        }
    }

    private fun extendDrones(frames: Long) {
        if (options.loop || frames == 0L) return
        droneExtension += frames
        for (d in drones) d.end += frames
    }

    /** Start frame (counted across passes) of tala cycle [k] (counted across passes). */
    private fun cycleStartFrame(k: Long): Long {
        val cycles = schedule.cycles.toLong()
        val pass = k.floorDiv(cycles)
        val c = k - pass * cycles
        val inPass = if (c == 0L) 0L else framesOf(c * schedule.cycleSeconds, sampleRate).toLong()
        return pass * performanceFrames + inPass
    }

    /** The tala cycle (counted across passes) containing performance frame [u]. */
    private fun cycleIndexAt(u: Long): Long {
        val cycles = schedule.cycles
        val pass = u.floorDiv(performanceFrames.toLong())
        val inPass = u - pass * performanceFrames
        var c = schedule.cycleAt(inPass.toDouble() / sampleRate).toLong()
        while (c + 1 < cycles && cycleStartFrame(c + 1) <= inPass) c++
        while (c > 0 && cycleStartFrame(c) > inPass) c--
        return pass * cycles + c
    }

    /**
     * Mixes the notes and clicks of [span] that fall in output frames
     * [from, to) (since the count-in) into [out] at [at]. A span plays the
     * notes starting in its stretch of the performance, whole: a legato
     * crossfade into its first note sounds before the span starts, and
     * notes still sounding when it ends fade out over [seamFrames].
     */
    private fun mixSpan(span: Span, out: FloatArray, at: Int, from: Long, to: Long) {
        val x0 = from - span.shift
        val x1 = to - span.shift
        if (span.end != Long.MAX_VALUE && x0 >= span.end + seamFrames) return
        val reach = max(maxVoiceFrames, clickFrames.toLong())
        val passes = if (options.loop) {
            (x0 - reach).floorDiv(performanceFrames.toLong())..(x1 - 1 + maxPreRoll).floorDiv(performanceFrames.toLong())
        } else {
            0L..0L
        }
        for (p in passes) {
            val base = p * performanceFrames
            val cut = if (span.end == Long.MAX_VALUE) Long.MAX_VALUE else span.end - base
            val lo = max(span.start, x0 - maxVoiceFrames) - base
            val hi = min(span.end, x1 + maxPreRoll) - base
            var v = lowerBound(voiceNoteStarts, lo)
            while (v < voices.size && voices[v].noteStart < hi) {
                voices[v].mixInto(out, at, x0 - base, x1 - base, cut, seamFrames)
                v++
            }
            mixClicks(out, at, x0 - base, x1 - base, max(span.start, x0 - clickFrames) - base, min(span.end, x1) - base)
        }
    }

    /** 0-based count of the count-in cycle at [frame], or null outside the count-in. */
    fun countInBeat(frame: Long): Int? =
        if (frame in 0 until countInFrames) (frame / countFrames).toInt() else null

    private fun buildVoices(): List<Voice> {
        // A silent harmonium (guide off, drone only) needs no voices at all.
        if (options.harmoniumGain == 0f) return emptyList()
        val notes = schedule.notes
        val held = heldNotes()
        val windows = notes.mapIndexed { i, note ->
            NoteWindow(
                framesOf(note.startSeconds, sampleRate),
                framesOf(note.startSeconds + note.durationSeconds, sampleRate),
                held[i],
                framesOf(min(note.durationSeconds * LIGHT_GAP_FRACTION, LIGHT_MAX_GAP_SECONDS), sampleRate),
            )
        }
        // Adjacency is musical time, not consecutive indices in the note list:
        // rests have cursor events but no notes. Include the seam only when both
        // ends of the pass sound, so neither leading nor trailing rests are filled.
        val joins = BooleanArray(notes.size) { i ->
            val next = notes.getOrNull(i + 1)
            val end = notes[i].startSeconds + notes[i].durationSeconds
            if (next != null) abs(end - next.startSeconds) < TIME_EPSILON
            else options.loop && abs(end - schedule.durationSeconds) < TIME_EPSILON && notes.first().startSeconds == 0.0
        }
        val legato = framesOf(options.legatoSeconds, sampleRate)
        return notes.mapIndexed { i, note ->
            val before = if (i == 0) joins.last() else joins[i - 1]
            val after = if (joins[i]) windows[(i + 1) % windows.size] else null
            val finalRelease = !options.loop && i == notes.lastIndex &&
                abs(note.startSeconds + note.durationSeconds - schedule.durationSeconds) < TIME_EPSILON
            val envelope = when (options.articulation) {
                HarmoniumArticulation.CONNECTED -> connectedEnvelope(windows[i], before, after != null, finalRelease, legato)
                HarmoniumArticulation.LIGHTLY_SEPARATED -> lightEnvelope(windows[i], after, finalRelease)
            }
            Voice(
                clip = bank.clip(note.playback.midi), rate = note.playback.rate, gain = options.harmoniumGain,
                start = envelope.start.toLong(), fadeIn = envelope.fadeIn,
                end = envelope.end.toLong(), fadeOut = envelope.fadeOut, sourceOffset = 0.0,
                noteStart = windows[i].start.toLong(),
            )
        }
    }

    /** Read written karvais from the cursor, not a duration threshold (gati/speed vary). */
    private fun heldNotes(): BooleanArray {
        val held = BooleanArray(schedule.notes.size)
        var note = -1
        for (cursor in schedule.cursor) {
            val p = cursor.position
            when (schedule.varisai.avartas[p.avarta].aksharas[p.akshara]) {
                is Akshara.Swaras -> note++
                Akshara.Karvai -> if (note >= 0) held[note] = true
                Akshara.Rest -> Unit
            }
        }
        return held
    }

    private fun connectedEnvelope(note: NoteWindow, before: Boolean, after: Boolean, finalRelease: Boolean, legato: Int): Envelope {
        val fade = min(legato, note.duration / 2)
        return Envelope(
            start = if (before) note.start - fade / 2 else note.start,
            fadeIn = if (before) fade else fade / 2,
            end = when {
                finalRelease -> note.end + releaseFrames
                after -> note.end + fade / 2
                else -> note.end
            },
            fadeOut = when {
                finalRelease -> releaseFrames
                after -> fade
                else -> fade / 2
            },
        )
    }

    private fun lightFade(note: NoteWindow): Int = min(framesOf(LIGHT_FADE_SECONDS, sampleRate), note.duration / 4)

    private fun lightEnvelope(note: NoteWindow, after: NoteWindow?, finalRelease: Boolean): Envelope {
        val fade = lightFade(note)
        // A held note keeps its whole written sustain, then overlaps the next
        // on-time attack. An explicit rest never receives that release tail.
        val holdRelease = if (note.held && after != null) lightFade(after) else 0
        val end = when {
            finalRelease -> note.end + releaseFrames
            holdRelease > 0 -> note.end + holdRelease
            after != null && !note.held -> note.end - note.gap
            else -> note.end
        }
        return Envelope(
            start = note.start, fadeIn = fade, end = end,
            fadeOut = when {
                finalRelease -> releaseFrames
                holdRelease > 0 -> holdRelease
                else -> fade
            },
        )
    }

    private data class NoteWindow(val start: Int, val end: Int, val held: Boolean, val gap: Int) {
        val duration: Int get() = end - start
    }

    private data class Envelope(val start: Int, val fadeIn: Int, val end: Int, val fadeOut: Int)

    private fun buildDrones(): List<Voice> {
        val fade = framesOf(options.droneFadeSeconds, sampleRate)
        val settings = schedule.settings
        if (options.droneStyle == DroneStyle.TANPURA && bank.hasTanpura) {
            val playback = requireNotNull(TanpuraBank.playbackFor(settings.saHz)) { "Sa ${settings.saHz} Hz outside the tanpura range" }
            return listOf(
                Voice(
                    clip = bank.tanpuraClip(playback.saMidi),
                    rate = playback.rate,
                    gain = options.droneGain * TANPURA_LEVEL,
                    start = 0,
                    fadeIn = fade,
                    end = totalFrames,
                    fadeOut = if (options.loop) 0 else fade,
                    sourceOffset = 0.0,
                ),
            )
        }
        return DRONE.map { (note, level, offsetSeconds) ->
            val hz = settings.tuning.frequency(note, settings.saHz)
            val playback = requireNotNull(HarmoniumBank.playbackFor(hz)) { "drone $note outside the bank" }
            Voice(
                clip = bank.clip(playback.midi),
                rate = playback.rate,
                gain = options.droneGain * level,
                start = 0,
                fadeIn = fade,
                end = totalFrames,
                fadeOut = if (options.loop) 0 else fade,
                // Different starting points so the three loops don't pulse together.
                sourceOffset = offsetSeconds * sampleRate,
            )
        }
    }

    private fun mixCountIn(out: FloatArray, at: Int, from: Int, to: Int) {
        val counts = schedule.tala.countsAt(schedule.varisai.kalai)
        for (c in counts.indices) {
            val wave = clickWaves.getValue(counts[c].kriya.kriya)
            val s = c * countFrames
            if (s >= to) break
            if (s + wave.size <= from) continue
            val gain = if (counts[c].countInKriya > 0) SECOND_COUNT_GAIN else 1f
            for (t in max(s, from) until min(s + wave.size, to)) out[at + t - from] += wave[t - s] * COUNT_IN_CLICK_GAIN * gain
        }
    }

    /** Clicks starting in [first, last) of the pass, over pass frames [from, to). */
    private fun mixClicks(out: FloatArray, at: Int, from: Long, to: Long, first: Long, last: Long) {
        if (first >= last) return
        for (c in clickStarts.indices) {
            val wave = clickWaves.getValue(clickKriyas[c])
            val s = clickStarts[c].toLong()
            if (s >= to || s >= last) break
            if (s + wave.size <= from || s < first) continue
            val gain = options.clickGain * if (clickSoft[c]) SECOND_COUNT_GAIN else 1f
            for (t in max(s, from) until min(s + wave.size, to)) out[at + (t - from).toInt()] += wave[(t - s).toInt()] * gain
        }
    }

    /** A decaying tone with a touch of seeded noise: the same samples every time. */
    private fun clickWave(tone: ClickTone): FloatArray {
        val n = framesOf(CLICK_SECONDS, sampleRate)
        var seed = NOISE_SEED
        return FloatArray(n) { i ->
            seed = seed * LCG_MULTIPLIER + LCG_INCREMENT
            val noise = ((seed ushr NOISE_SHIFT).toDouble() / NOISE_SCALE) * 2 - 1
            val t = i.toDouble() / sampleRate
            val body = (1 - tone.noise) * sin(2 * PI * tone.hz * t) + tone.noise * noise
            (body * exp(-t / tone.decaySeconds)).toFloat() * tone.level
        }
    }

    /** How one hand action's click sounds. */
    private data class ClickTone(val hz: Double, val decaySeconds: Double, val level: Float, val noise: Double)

    /** One sounding sample: a note or a drone string. Coordinates are frames. */
    @Suppress("LongParameterList") // A plain frame descriptor.
    private class Voice(
        val clip: Clip,
        val rate: Double,
        val gain: Float,
        val start: Long,
        val fadeIn: Int,
        var end: Long,
        val fadeOut: Int,
        val sourceOffset: Double,
        /** Where the written note starts (the envelope may start a little before). */
        val noteStart: Long = start,
    ) {
        /**
         * Mixes the part of this voice inside [from, to) into [out] at [at],
         * fading out over [cutFrames] from [cutFrom] if it is still sounding then.
         */
        fun mixInto(out: FloatArray, at: Int, from: Long, to: Long, cutFrom: Long = Long.MAX_VALUE, cutFrames: Int = 1) {
            val lo = max(start, from)
            val hi = if (cutFrom == Long.MAX_VALUE) min(end, to) else min(min(end, to), cutFrom + cutFrames)
            var t = lo
            while (t < hi) {
                val sinceStart = t - start
                val untilEnd = end - t
                var env = 1f
                if (sinceStart < fadeIn) env = sinceStart.toFloat() / fadeIn
                if (untilEnd < fadeOut) env = min(env, untilEnd.toFloat() / fadeOut)
                if (t >= cutFrom) env = min(env, (cutFrom + cutFrames - t).toFloat() / cutFrames)
                out[at + (t - from).toInt()] += clip.value(sourceOffset + sinceStart * rate) * env * gain
                t++
            }
        }
    }

    /**
     * Output frames from [start] (since the count-in, less [shift]) play the
     * performance from [start] up to [end] (performance frames counted
     * across passes; [Long.MAX_VALUE] while no jump is decided).
     */
    private class Span(val start: Long, var end: Long, val shift: Long) {
        val outputStart: Long get() = start + shift
    }

    private data class DroneString(val note: Note, val level: Float, val offsetSeconds: Double)

    private companion object {
        /** Tanpura-like drone: Sa, Pa and upper Sa. */
        val DRONE = listOf(
            DroneString(Note(Swara.S), 1f, 0.0),
            DroneString(Note(Swara.P), 0.7f, 0.37),
            DroneString(Note(Swara.S, Octave.TARA), 0.5f, 0.71),
        )
        const val CLICK_SECONDS = 0.04

        /** The shortest fade for notes cut off where a repeated cycle goes back. */
        const val SEAM_SECONDS = 0.01

        /** Index of the first value >= [key] in sorted [values]. */
        fun lowerBound(values: LongArray, key: Long): Int {
            var lo = 0
            var hi = values.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (values[mid] < key) lo = mid + 1 else hi = mid
            }
            return lo
        }

        /**
         * Clap, finger count and wave, after the author's ThaaLam app: a dry,
         * bright clap; a shorter, higher finger tick; a lower, longer, airier wave.
         */
        val CLICK_TONES = mapOf(
            Kriya.CLAP to ClickTone(hz = 2000.0, decaySeconds = 0.0057, level = 1f, noise = 0.10),
            Kriya.COUNT to ClickTone(hz = 1700.0, decaySeconds = 0.0039, level = 0.5f, noise = 0.0),
            Kriya.WAVE to ClickTone(hz = 950.0, decaySeconds = 0.0078, level = 0.55f, noise = 0.18),
        )
        const val NOISE_SEED = 0x2545F491L
        const val LCG_MULTIPLIER = 6364136223846793005L
        const val LCG_INCREMENT = 1442695040888963407L
        const val NOISE_SHIFT = 11
        const val NOISE_SCALE = 9007199254740992.0 // 2^53
        const val COUNT_IN_CLICK_GAIN = 0.4f
        const val LIGHT_GAP_FRACTION = 0.05
        const val LIGHT_MAX_GAP_SECONDS = 0.025
        const val LIGHT_FADE_SECONDS = 0.005
        const val TIME_EPSILON = 1e-9

        /** A two-kalai second count clicks softer than the kriya it follows. */
        const val SECOND_COUNT_GAIN = 0.5f
    }
}

/** The tanpura loop is one voice; this matches its level to the three harmonium drone voices. */
internal const val TANPURA_LEVEL = 1.6f
