package com.baijum.purandara.audio

import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.singalong.LivePitch
import com.baijum.purandara.singalong.PitchFrame

/**
 * Mic windows → (SwiftF0 every few windows) → YIN pipeline → [onFrame], for
 * Shruti practice and Find my Sa. [timeOf] maps a window's capture time to
 * the frame's time in seconds (null skips the window); [onFrame] runs on the
 * mic thread.
 *
 * [model] is read for every run, not captured at construction: it loads
 * asynchronously, so a session started before it finishes must still pick it
 * up when it arrives (issue #14). [range] is SwiftF0's band around the Sa
 * (`SwiftF0.range`; the full range for Find my Sa).
 */
class PitchListener(
    private val mic: MicSource,
    model: () -> SwiftF0Model?,
    range: PitchRange,
    private val timeOf: (captureNanos: Long) -> Double?,
    private val onFrame: (PitchFrame) -> Unit,
) {
    private val live = LivePitch(mic.sampleRate, model, range)

    fun start() {
        mic.start { window, captureNanos ->
            live.listen(window)
            val time = timeOf(captureNanos) ?: return@start
            onFrame(live.process(window, time))
        }
    }

    fun stop() = mic.stop()
}
