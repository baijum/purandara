package com.baijum.purandara.audio

import com.baijum.purandara.singalong.FrameOutcome
import com.baijum.purandara.singalong.PitchPipeline
import com.baijum.purandara.singalong.SingAlongClock
import com.baijum.purandara.singalong.SingAlongSession
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
 */
class SingAlongEngine(
    private val mic: MicSource,
    private val player: VarisaiAudioPlayer,
    private val renderer: SampleRenderer,
    schedule: Schedule,
    private val supervisor: NeuralPitchSupervisor?,
    private val onOutcome: (FrameOutcome) -> Unit,
) {
    private val sampleRate = renderer.sampleRate
    private val session = SingAlongSession(
        schedule = schedule,
        clock = SingAlongClock(
            countInSeconds = renderer.countInFrames.toDouble() / sampleRate,
            passSeconds = renderer.performanceFrames.toDouble() / sampleRate,
            loop = renderer.totalFrames == Long.MAX_VALUE,
            outputLatencySeconds = 0.0,
            inputLatencySeconds = 0.0,
        ),
        sampleRate = sampleRate,
    )
    private val pipeline = PitchPipeline(mic.sampleRate)

    /** Call before [VarisaiAudioPlayer.play] so output levels are recorded from the first block. */
    fun attach() {
        player.onRendered = { start, rms -> session.outputLevels.record(start, rms) }
    }

    fun start() {
        mic.start { window, captureNanos ->
            val heardFrame = player.outputFrameAt(captureNanos) ?: return@start
            if (pipeline.beginFrame()) pipeline.onNeuralResult(supervisor?.estimate(window))
            val frame = pipeline.process(window, heardFrame.toDouble() / sampleRate)
            onOutcome(session.onFrame(frame))
        }
    }

    /** Stops the mic; returns the scored final pass, if one was in progress. */
    fun stop(): com.baijum.purandara.singalong.PassResult? {
        mic.stop()
        player.onRendered = null
        return session.finish()
    }
}
