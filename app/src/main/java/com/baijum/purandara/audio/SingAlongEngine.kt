package com.baijum.purandara.audio

import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.singalong.FrameOutcome
import com.baijum.purandara.singalong.LivePitch
import com.baijum.purandara.singalong.SingAlongClock
import com.baijum.purandara.singalong.SingAlongSession
import com.baijum.purandara.singalong.Strictness
import com.baijum.purandara.domain.schedule.Schedule

/**
 * Sing-along for one playback: mic windows → (SwiftF0 every few windows) →
 * YIN pipeline → the shared [SingAlongSession].
 *
 * Timing: each mic window's capture time is mapped through
 * [VarisaiAudioPlayer.outputFrameAt] to the rendered frame that was leaving
 * the speaker at that moment, which is exactly what the singer was
 * responding to, with output latency included by the hardware timestamp.
 * The shared clock therefore runs with zero extra latency.
 *
 * [onOutcome] runs on the mic thread; post to the main thread to use it.
 *
 * [model] is read for every run, not captured at construction: it loads
 * asynchronously, so an engine started before it finishes (sing-along enabled
 * during playback) must still pick it up when it arrives (issue #14). SwiftF0
 * searches around the session's Sa.
 */
class SingAlongEngine(
    private val mic: MicSource,
    private val player: VarisaiAudioPlayer,
    renderer: SampleRenderer,
    private val session: SingAlongSession,
    model: () -> SwiftF0Model?,
    private val onOutcome: (FrameOutcome) -> Unit,
) {
    private val sampleRate = renderer.sampleRate
    private val live = LivePitch(mic.sampleRate, model, SwiftF0.range(session.saHz))

    /** Call before [VarisaiAudioPlayer.play] so output levels are recorded from the first block. */
    fun attach() {
        player.onRendered = { start, rms -> session.outputLevels.record(start, rms) }
    }

    fun start() {
        mic.start { window, captureNanos ->
            live.listen(window)
            val heardFrame = player.outputFrameAt(captureNanos) ?: return@start
            val frame = live.process(window, heardFrame.toDouble() / sampleRate)
            onOutcome(session.onFrame(frame))
        }
    }

    companion object {
        /** The session for [renderer]'s playback of [schedule]: count-in, pass length, looping and repeated cycles from the renderer. */
        fun sessionFor(renderer: SampleRenderer, schedule: Schedule, strictness: Strictness): SingAlongSession =
            SingAlongSession(
                schedule = schedule,
                clock = SingAlongClock.forRenderer(renderer),
                sampleRate = renderer.sampleRate,
                strictness = strictness,
            )
    }

    /** Stops the mic; returns the scored final pass, if one was in progress. */
    fun stop(): com.baijum.purandara.singalong.PassResult? {
        mic.stop()
        player.onRendered = null
        return session.finish()
    }
}
