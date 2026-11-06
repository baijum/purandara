package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Kriya
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.schedule.Schedule
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Mix levels and articulation for [SampleRenderer]. */
data class RenderOptions(
    val harmoniumGain: Float = 0.6f,
    val drone: Boolean = true,
    val droneGain: Float = 0.2f,
    val click: Boolean = false,
    val clickGain: Float = 0.35f,
    /** Crossfade between consecutive notes, centred on the note boundary. */
    val legatoSeconds: Double = 0.03,
    /** Fade-out after the last note. */
    val releaseSeconds: Double = 0.25,
    /** Drone fade-in at the start and fade-out at the end. */
    val droneFadeSeconds: Double = 0.3,
)

/**
 * Renders a [Schedule] to mono float PCM at the bank's sample rate.
 *
 * Shared by Android and iOS so timing is identical, and testable on the JVM.
 * [render] can be called with any block size and gives the same result as
 * one big call. It allocates nothing, but it is not meant for a real-time
 * callback on iOS (Kotlin/Native GC); platforms render ahead on a worker thread.
 */
class SampleRenderer(
    private val bank: SampleBank,
    val schedule: Schedule,
    private val options: RenderOptions = RenderOptions(),
) {
    val sampleRate: Int = bank.sampleRate

    private val releaseFrames = framesOf(options.releaseSeconds, sampleRate)

    /** Frames of the performance itself, without the release tail. */
    val performanceFrames: Int = framesOf(schedule.durationSeconds, sampleRate)

    /** Total frames, including the release after the last note. */
    val totalFrames: Int = performanceFrames + releaseFrames

    /** Frames rendered so far. */
    var position: Int = 0
        private set

    private val voices: List<Voice> = buildVoices()
    private val drones: List<Voice> = if (options.drone) buildDrones() else emptyList()
    private val clickStarts: IntArray
    private val clickAccents: BooleanArray
    private val accentClick = clickWave(ACCENT_CLICK_HZ, 1f)
    private val plainClick = clickWave(PLAIN_CLICK_HZ, PLAIN_CLICK_LEVEL)

    private var firstActiveVoice = 0

    init {
        val beats = if (options.click) schedule.beats else emptyList()
        clickStarts = IntArray(beats.size) { framesOf(beats[it].startSeconds, sampleRate) }
        clickAccents = BooleanArray(beats.size) { beats[it].beat.kriya == Kriya.CLAP }
    }

    /**
     * Renders up to [length] frames into [out] starting at [offset].
     * @return frames written; 0 once the performance has finished.
     */
    fun render(out: FloatArray, offset: Int = 0, length: Int = out.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= out.size) { "bad range" }
        val n = min(length, totalFrames - position)
        if (n <= 0) return 0
        val blockStart = position
        val blockEnd = blockStart + n
        out.fill(0f, offset, offset + n)

        while (firstActiveVoice < voices.size && voices[firstActiveVoice].end <= blockStart) firstActiveVoice++
        var v = firstActiveVoice
        while (v < voices.size && voices[v].start < blockEnd) {
            voices[v].mixInto(out, offset, blockStart, blockEnd)
            v++
        }
        for (drone in drones) drone.mixInto(out, offset, blockStart, blockEnd)
        mixClicks(out, offset, blockStart, blockEnd)

        for (i in offset until offset + n) out[i] = out[i].coerceIn(-1f, 1f)
        position = blockEnd
        return n
    }

    /** Starts again from the beginning. */
    fun reset() {
        position = 0
        firstActiveVoice = 0
    }

    private fun buildVoices(): List<Voice> {
        val legato = framesOf(options.legatoSeconds, sampleRate)
        val notes = schedule.notes
        return notes.mapIndexed { i, note ->
            val start = framesOf(note.startSeconds, sampleRate)
            val end = framesOf(note.startSeconds + note.durationSeconds, sampleRate)
            val fade = min(legato, (end - start) / 2)
            val isLast = i == notes.lastIndex
            Voice(
                clip = bank.clip(note.playback.midi),
                rate = note.playback.rate,
                gain = options.harmoniumGain,
                // Centre the crossfade on the boundary; the first note starts at 0.
                start = max(0, start - fade / 2),
                fadeIn = if (i == 0) fade / 2 else fade,
                end = if (isLast) end + releaseFrames else end + fade / 2,
                fadeOut = if (isLast) releaseFrames else fade,
                sourceOffset = 0.0,
            )
        }
    }

    private fun buildDrones(): List<Voice> {
        val fade = framesOf(options.droneFadeSeconds, sampleRate)
        val settings = schedule.settings
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
                fadeOut = fade,
                // Different starting points so the three loops don't pulse together.
                sourceOffset = offsetSeconds * sampleRate,
            )
        }
    }

    private fun mixClicks(out: FloatArray, offset: Int, blockStart: Int, blockEnd: Int) {
        for (c in clickStarts.indices) {
            val wave = if (clickAccents[c]) accentClick else plainClick
            val s = clickStarts[c]
            if (s >= blockEnd) break
            if (s + wave.size <= blockStart) continue
            val from = max(s, blockStart)
            val to = min(s + wave.size, blockEnd)
            for (t in from until to) out[offset + t - blockStart] += wave[t - s] * options.clickGain
        }
    }

    private fun clickWave(hz: Double, level: Float): FloatArray {
        val n = framesOf(CLICK_SECONDS, sampleRate)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * PI * hz * t) * exp(-t / CLICK_DECAY_SECONDS)).toFloat() * level
        }
    }

    /** One sounding sample: a note or a drone string. */
    private class Voice(
        val clip: LoopedClip,
        val rate: Double,
        val gain: Float,
        val start: Int,
        val fadeIn: Int,
        val end: Int,
        val fadeOut: Int,
        val sourceOffset: Double,
    ) {
        fun mixInto(out: FloatArray, offset: Int, blockStart: Int, blockEnd: Int) {
            val from = max(start, blockStart)
            val to = min(end, blockEnd)
            for (t in from until to) {
                val sinceStart = t - start
                val untilEnd = end - t
                var env = 1f
                if (sinceStart < fadeIn) env = sinceStart.toFloat() / fadeIn
                if (untilEnd < fadeOut) env = min(env, untilEnd.toFloat() / fadeOut)
                out[offset + t - blockStart] += clip.value(sourceOffset + sinceStart * rate) * env * gain
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
    }
}
