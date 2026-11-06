package com.baijum.purandara.audio

import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.PitchPipeline

/**
 * Mic windows → (SwiftF0 every few windows) → YIN pipeline → [onFrame], for
 * Shruti practice and Find my Sa. [timeOf] maps a window's capture time to
 * the frame's time in seconds (null skips the window); [onFrame] runs on the
 * mic thread.
 */
class PitchListener(
    private val mic: MicSource,
    private val supervisor: NeuralPitchSupervisor?,
    private val timeOf: (captureNanos: Long) -> Double?,
    private val onFrame: (PitchFrame) -> Unit,
) {
    private val pipeline = PitchPipeline(mic.sampleRate)

    fun start() {
        mic.start { window, captureNanos ->
            val time = timeOf(captureNanos) ?: return@start
            if (pipeline.beginFrame()) pipeline.onNeuralResult(supervisor?.estimate(window))
            onFrame(pipeline.process(window, time))
        }
    }

    fun stop() = mic.stop()
}
