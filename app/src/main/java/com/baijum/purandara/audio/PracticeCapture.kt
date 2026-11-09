package com.baijum.purandara.audio

import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.singalong.FrameOutcome
import com.baijum.purandara.singalong.OutputLevels
import com.baijum.purandara.singalong.PassResult
import com.baijum.purandara.singalong.PitchPipeline
import com.baijum.purandara.singalong.SingAlongSession
import com.baijum.purandara.singalong.Strictness

/** A fresh microphone/pipeline for one scored segment, armed before its count-in is queued. */
internal class PracticeCapture(
    private val mic: MicSource,
    private val player: VarisaiAudioPlayer,
    private val sampleRate: Int,
    private val supervisor: () -> PitchSupervisor?,
    private val onOutcome: (FrameOutcome) -> Unit,
) {
    private data class Binding(val segment: PracticeSegment, val session: SingAlongSession)
    @Volatile private var binding: Binding? = null
    private val pipeline = PitchPipeline(mic.sampleRate)
    private var ready = false

    /** Worker publishes the origin before rendering any of this segment's count-in. */
    fun bind(segment: PracticeSegment, schedule: Schedule, levels: OutputLevels, strictness: Strictness) {
        binding = Binding(segment, SingAlongSession.forSegment(schedule, segment, sampleRate, levels, strictness, 0.0, 0.0))
    }

    fun start(onReady: () -> Unit) {
        mic.start { window, nanos ->
            if (!ready) {
                ready = true
                onReady()
            }
            val current = binding ?: return@start
            val frame = player.outputFrameAt(nanos) ?: return@start
            // Reject windows overlapping the break; readiness itself never calibrates or scores.
            val halfWindow = window.size.toLong() * sampleRate / mic.sampleRate / 2
            if (frame - halfWindow < current.segment.startFrame || frame >= current.segment.performanceEndFrame) return@start
            if (pipeline.beginFrame()) pipeline.onNeuralResult(supervisor()?.estimate(window))
            onOutcome(current.session.onFrame(pipeline.process(window, frame.toDouble() / sampleRate)))
        }
    }

    /** Joining capture drains its in-flight window before exactly-once finalization. */
    fun stop(): PassResult? {
        mic.stop()
        val current = binding
        binding = null
        return current?.session?.finish()
    }
}
