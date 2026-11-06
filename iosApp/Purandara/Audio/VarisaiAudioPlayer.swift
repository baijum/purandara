import AVFoundation
import shared

/// Plays a shared `SampleRenderer` through `AVAudioEngine`.
///
/// Kotlin rendering never runs on the real-time audio thread: a serial queue
/// renders ~93 ms buffers ahead and schedules them on an `AVAudioPlayerNode`,
/// keeping a few queued. `positionFrames()` reports what has actually been
/// heard (play head minus output latency), so the highlight matches the sound.
///
/// Public methods are called from the main thread; renderer state is confined
/// to `queue`.
final class VarisaiAudioPlayer: @unchecked Sendable {
    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private let format: AVAudioFormat
    private let queue = DispatchQueue(label: "purandara.varisai-render", qos: .userInteractive)

    // Queue-confined.
    private var renderer: SampleRenderer?
    private var scratch: KotlinFloatArray
    private var pending = 0
    private var doneRendering = false
    private var stopRequested = false
    private var generation = 0
    private var onFinished: (@MainActor () -> Void)?

    // Main thread.
    private(set) var isPlaying = false

    private static let chunkFrames: AVAudioFrameCount = 4096
    private static let buffersAhead = 3

    init() {
        format = AVAudioFormat(standardFormatWithSampleRate: HarmoniumSamples.sampleRate, channels: 1)!
        scratch = KotlinFloatArray(size: Int32(Self.chunkFrames))
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        NotificationCenter.default.addObserver(
            self, selector: #selector(audioInterrupted), name: AVAudioSession.interruptionNotification, object: nil)
        NotificationCenter.default.addObserver(
            self, selector: #selector(audioInterrupted), name: .AVAudioEngineConfigurationChange, object: engine)
    }

    /// Starts `renderer` from the beginning. `onFinished` runs on the main
    /// thread when a non-looping performance has been heard, after a requested
    /// stop has faded out, or when audio is interrupted.
    func play(_ renderer: SampleRenderer, onFinished: @escaping @MainActor () -> Void) throws {
        stopImmediately()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default)
        try session.setActive(true)
        if !engine.isRunning { try engine.start() }
        queue.sync {
            generation += 1
            self.renderer = renderer
            self.onFinished = onFinished
            renderer.reset()
            pending = 0
            doneRendering = false
            stopRequested = false
            for _ in 0..<Self.buffersAhead { scheduleNext(generation) }
        }
        node.play()
        isPlaying = true
    }

    /// Fades out (about a quarter of a second, including audio already queued) and then finishes.
    func stop() {
        guard isPlaying else { return }
        queue.async { self.stopRequested = true }
    }

    /// Stops at once, without calling `onFinished`. Used when restarting.
    func stopImmediately() {
        queue.sync {
            generation += 1
            renderer = nil
            onFinished = nil
        }
        node.stop()
        isPlaying = false
    }

    /// Frames heard since `play`, or nil when idle.
    func positionFrames() -> Int64? {
        guard isPlaying,
              let nodeTime = node.lastRenderTime,
              let playerTime = node.playerTime(forNodeTime: nodeTime) else { return nil }
        let session = AVAudioSession.sharedInstance()
        let latency = Int64((session.outputLatency + session.ioBufferDuration) * format.sampleRate)
        return max(0, playerTime.sampleTime - latency)
    }

    // MARK: - Render queue

    private func scheduleNext(_ gen: Int) {
        guard gen == generation, let renderer else { return }
        if stopRequested { renderer.fadeOut(seconds: 0.08) }
        let frames = renderer.render(out: scratch, offset: 0, length: Int32(Self.chunkFrames))
        guard frames > 0 else {
            doneRendering = true
            finishIfDrained(gen)
            return
        }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: Self.chunkFrames),
              let channel = buffer.floatChannelData?[0] else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        FloatBridge.shared.write(source: scratch, destination: UnsafeMutableRawPointer(channel), count: frames)
        pending += 1
        node.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            guard let self else { return }
            self.queue.async {
                guard gen == self.generation else { return }
                self.pending -= 1
                if self.doneRendering { self.finishIfDrained(gen) } else { self.scheduleNext(gen) }
            }
        }
    }

    private func finishIfDrained(_ gen: Int) {
        guard gen == generation, pending == 0 else { return }
        let callback = onFinished
        generation += 1
        renderer = nil
        onFinished = nil
        DispatchQueue.main.async {
            self.node.stop()
            self.isPlaying = false
            callback?()
        }
    }

    @objc private func audioInterrupted(_ notification: Notification) {
        if notification.name == AVAudioSession.interruptionNotification,
           let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
           AVAudioSession.InterruptionType(rawValue: raw) != .began {
            return
        }
        DispatchQueue.main.async {
            let callback = self.queue.sync { self.onFinished }
            self.stopImmediately()
            callback?()
        }
    }
}
