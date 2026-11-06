package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.TanpuraBank
import com.baijum.purandara.domain.schedule.Schedule
import kotlin.math.PI
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

/** Mix levels and articulation for [SampleRenderer]. */
data class RenderOptions(
    val harmoniumGain: Float = 0.6f,
    val drone: Boolean = true,
    val droneStyle: DroneStyle = DroneStyle.TANPURA,
    val droneGain: Float = 0.2f,
    val click: Boolean = false,
    val clickGain: Float = 0.35f,
    /**
     * Repeat the exercise without a break: the last note crossfades into the
     * first on the beat and the drone continues. [SampleRenderer.render]
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
        ): RenderOptions = RenderOptions(
            harmoniumGain = MAX_HARMONIUM_GAIN * harmoniumLevel.coerceIn(0f, 1f),
            drone = drone,
            droneGain = MAX_DRONE_GAIN * droneLevel.coerceIn(0f, 1f),
            click = click,
            loop = loop,
            countIn = countIn,
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
) {
    val sampleRate: Int = bank.sampleRate

    private val releaseFrames = framesOf(options.releaseSeconds, sampleRate)

    /** Frames of one pass of the performance, without any release tail. */
    val performanceFrames: Int = framesOf(schedule.durationSeconds, sampleRate)

    /** Frames per tala akshara (beat). */
    private val beatFrames: Int = framesOf(schedule.settings.talaAksharaSeconds, sampleRate)

    /** Frames of count-in before the exercise starts (0 without a count-in). */
    val countInFrames: Int = if (options.countIn) schedule.settings.tala.aksharas * beatFrames else 0

    /**
     * Total frames when not looping: count-in, performance and release.
     * Unbounded ([Long.MAX_VALUE]) when looping.
     */
    val totalFrames: Long =
        if (options.loop) Long.MAX_VALUE else (countInFrames + performanceFrames + releaseFrames).toLong()

    /** Frames rendered so far. */
    var position: Long = 0
        private set

    private val voices: List<Voice> = buildVoices()

    /** Looping only: notes of the previous pass still sounding after the boundary. */
    private val tailVoices = if (options.loop) voices.filter { it.end > performanceFrames } else emptyList()

    /** Looping only: notes of the next pass that start before the boundary. */
    private val headVoices = if (options.loop) voices.filter { it.start < 0 } else emptyList()

    private val drones: List<Voice> = if (options.drone) buildDrones() else emptyList()
    private val clickStarts: IntArray
    private val clickAccents: BooleanArray
    private val accentClick = clickWave(ACCENT_CLICK_HZ, 1f)
    private val plainClick = clickWave(PLAIN_CLICK_HZ, PLAIN_CLICK_LEVEL)

    private var firstActiveVoice = 0
    private var fadeOutStart = Long.MAX_VALUE
    private var fadeOutFrames = 0

    init {
        val beats = if (options.click) schedule.beats else emptyList()
        clickStarts = IntArray(beats.size) { framesOf(beats[it].startSeconds, sampleRate) }
        clickAccents = BooleanArray(beats.size) { beats[it].beat.kriya == Kriya.CLAP }
    }

    /**
     * Renders up to [length] frames into [out] starting at [offset].
     * @return frames written; 0 once the performance (or a [fadeOut]) has finished.
     */
    fun render(out: FloatArray, offset: Int = 0, length: Int = out.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= out.size) { "bad range" }
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
            val absolute = frame - countInFrames
            val pass = absolute / performanceFrames
            val inPass = (absolute % performanceFrames).toInt()
            // Non-looping: one pass that runs on into the release tail.
            val chunk = if (options.loop) min(n - done, performanceFrames - inPass) else n - done
            val from = if (options.loop) inPass else absolute.toInt()
            val to = from + chunk
            val at = offset + done
            if (options.loop && inPass == 0) firstActiveVoice = 0
            mixPassVoices(out, at, from, to)
            if (options.loop) {
                if (pass > 0) for (v in tailVoices) v.mixInto(out, at, from + performanceFrames.toLong(), to + performanceFrames.toLong())
                for (v in headVoices) v.mixInto(out, at, from - performanceFrames.toLong(), to - performanceFrames.toLong())
            }
            mixClicks(out, at, from, to)
            done += chunk
        }
        for (drone in drones) drone.mixInto(out, offset, position, position + n)

        for (i in 0 until n) {
            val t = position + i
            var sample = out[offset + i]
            if (t >= fadeOutStart) sample *= (fadeOutStart + fadeOutFrames - t).toFloat() / fadeOutFrames
            out[offset + i] = sample.coerceIn(-1f, 1f)
        }
        position += n
        return n
    }

    /** Fades everything out over [seconds] from the current position; [render] then finishes. */
    fun fadeOut(seconds: Double = DEFAULT_STOP_FADE_SECONDS) {
        if (fadeOutStart != Long.MAX_VALUE) return
        fadeOutFrames = max(1, framesOf(seconds, sampleRate))
        fadeOutStart = position
    }

    /** Starts again from the beginning. */
    fun reset() {
        position = 0
        firstActiveVoice = 0
        fadeOutStart = Long.MAX_VALUE
    }

    /**
     * Seconds into the current pass of the exercise for a frame since the
     * start (e.g. the audio device's play head). Negative during the count-in.
     */
    fun passSeconds(frame: Long): Double {
        val sinceStart = frame - countInFrames
        if (sinceStart < 0) return sinceStart.toDouble() / sampleRate
        val inPass = if (options.loop) sinceStart % performanceFrames else sinceStart
        return inPass.toDouble() / sampleRate
    }

    /** 0-based beat of the count-in avarta at [frame], or null outside the count-in. */
    fun countInBeat(frame: Long): Int? =
        if (frame in 0 until countInFrames) (frame / beatFrames).toInt() else null

    private fun mixPassVoices(out: FloatArray, at: Int, from: Int, to: Int) {
        while (firstActiveVoice < voices.size && voices[firstActiveVoice].end <= from) firstActiveVoice++
        var v = firstActiveVoice
        while (v < voices.size && voices[v].start < to) {
            voices[v].mixInto(out, at, from.toLong(), to.toLong())
            v++
        }
    }

    private fun buildVoices(): List<Voice> {
        val legato = framesOf(options.legatoSeconds, sampleRate)
        val notes = schedule.notes
        return notes.mapIndexed { i, note ->
            val start = framesOf(note.startSeconds, sampleRate)
            val end = framesOf(note.startSeconds + note.durationSeconds, sampleRate)
            val fade = min(legato, (end - start) / 2)
            val first = i == 0
            val last = i == notes.lastIndex
            // Centre each crossfade on the boundary. Without looping, the first
            // note starts at 0 and the last one ends with a release.
            val voiceStart = if (first && !options.loop) 0 else start - fade / 2
            Voice(
                clip = bank.clip(note.playback.midi),
                rate = note.playback.rate,
                gain = options.harmoniumGain,
                start = voiceStart.toLong(),
                fadeIn = if (first && !options.loop) fade / 2 else fade,
                end = (if (last && !options.loop) end + releaseFrames else end + fade / 2).toLong(),
                fadeOut = if (last && !options.loop) releaseFrames else fade,
                sourceOffset = 0.0,
            )
        }
    }

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
        val beats = schedule.settings.tala.beats
        for (b in beats.indices) {
            val wave = if (beats[b].kriya == Kriya.CLAP) accentClick else plainClick
            val s = b * beatFrames
            if (s >= to) break
            if (s + wave.size <= from) continue
            for (t in max(s, from) until min(s + wave.size, to)) out[at + t - from] += wave[t - s] * COUNT_IN_CLICK_GAIN
        }
    }

    private fun mixClicks(out: FloatArray, at: Int, from: Int, to: Int) {
        for (c in clickStarts.indices) {
            val wave = if (clickAccents[c]) accentClick else plainClick
            val s = clickStarts[c]
            if (s >= to) break
            if (s + wave.size <= from) continue
            for (t in max(s, from) until min(s + wave.size, to)) out[at + t - from] += wave[t - s] * options.clickGain
        }
    }

    private fun clickWave(hz: Double, level: Float): FloatArray {
        val n = framesOf(CLICK_SECONDS, sampleRate)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * PI * hz * t) * exp(-t / CLICK_DECAY_SECONDS)).toFloat() * level
        }
    }

    /** One sounding sample: a note or a drone string. Coordinates are frames. */
    private class Voice(
        val clip: Clip,
        val rate: Double,
        val gain: Float,
        val start: Long,
        val fadeIn: Int,
        val end: Long,
        val fadeOut: Int,
        val sourceOffset: Double,
    ) {
        /** Mixes the part of this voice inside [from, to) into [out] at [at]. */
        fun mixInto(out: FloatArray, at: Int, from: Long, to: Long) {
            val lo = max(start, from)
            val hi = min(end, to)
            var t = lo
            while (t < hi) {
                val sinceStart = t - start
                val untilEnd = end - t
                var env = 1f
                if (sinceStart < fadeIn) env = sinceStart.toFloat() / fadeIn
                if (untilEnd < fadeOut) env = min(env, untilEnd.toFloat() / fadeOut)
                out[at + (t - from).toInt()] += clip.value(sourceOffset + sinceStart * rate) * env * gain
                t++
            }
        }
    }

    private data class DroneString(val note: Note, val level: Float, val offsetSeconds: Double)

    private companion object {
        /** Tanpura-like drone: Sa, Pa and upper Sa. */
        val DRONE = listOf(
            DroneString(Note(Swara.S), 1f, 0.0),
            DroneString(Note(Swara.P), 0.7f, 0.37),
            DroneString(Note(Swara.S, Octave.TARA), 0.5f, 0.71),
        )
        const val CLICK_SECONDS = 0.03
        const val CLICK_DECAY_SECONDS = 0.006
        const val ACCENT_CLICK_HZ = 2000.0
        const val PLAIN_CLICK_HZ = 1400.0
        const val PLAIN_CLICK_LEVEL = 0.6f
        const val DEFAULT_STOP_FADE_SECONDS = 0.08
        const val COUNT_IN_CLICK_GAIN = 0.4f
    }
}

/** The tanpura loop is one voice; this matches its level to the three harmonium drone voices. */
internal const val TANPURA_LEVEL = 1.6f
