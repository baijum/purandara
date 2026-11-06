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
    func play(_ renderer: SampleRenderer, mic: MicRequest? = nil, onFinished: @escaping @MainActor () -> Void) throws {
        stopImmediately()
        try configure(mic: mic)
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

    /// Microphone capture on this same engine (needed for echo cancellation).
    struct MicRequest {
        /// Cancel the app's own output from the mic (use on the built-in speaker).
        let voiceProcessing: Bool
        /// Called on an AVFoundation thread with each captured buffer and its time.
        let onBuffer: (AVAudioPCMBuffer, AVAudioTime) -> Void
    }

    /// Called on the render queue after each rendered block: its first frame and RMS level.
    var onRendered: ((Int64, Float) -> Void)?

    private var micTapInstalled = false
    private var reconfiguring = false

    private func configure(mic: MicRequest?) throws {
        reconfiguring = true
        defer { reconfiguring = false }
        let session = AVAudioSession.sharedInstance()
        let wantVoiceProcessing = mic?.voiceProcessing ?? false
        if micTapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            micTapInstalled = false
        }
        if engine.inputNode.isVoiceProcessingEnabled != wantVoiceProcessing || (mic != nil) != (session.category == .playAndRecord) {
            engine.stop()
            if mic != nil {
                try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothA2DP])
            } else {
                try session.setCategory(.playback, mode: .default)
            }
            try session.setActive(true)
            if engine.inputNode.isVoiceProcessingEnabled != wantVoiceProcessing {
                try engine.inputNode.setVoiceProcessingEnabled(wantVoiceProcessing)
                if wantVoiceProcessing { engine.inputNode.isVoiceProcessingAGCEnabled = false }
            }
        } else {
            try session.setActive(true)
        }
        if let mic {
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, when in mic.onBuffer(buffer, when) }
            micTapInstalled = true
        }
        if !engine.isRunning { try engine.start() }
    }

    /// Sample rate of the microphone tap (valid after `play` with a mic request).
    var micSampleRate: Double { engine.inputNode.outputFormat(forBus: 0).sampleRate }

    /// The rendered frame that was leaving the speaker at `hostTime`, or nil when idle.
    /// Extrapolates from the node's last render time and subtracts the output latency.
    func outputFrame(atHostTime hostTime: UInt64) -> Int64? {
        guard isPlaying,
              let nodeTime = node.lastRenderTime, nodeTime.isHostTimeValid,
              let playerTime = node.playerTime(forNodeTime: nodeTime) else { return nil }
        let session = AVAudioSession.sharedInstance()
        let rate = format.sampleRate
        let elapsed = AVAudioTime.seconds(forHostTime: hostTime) - AVAudioTime.seconds(forHostTime: nodeTime.hostTime)
        return playerTime.sampleTime + Int64((elapsed - session.outputLatency - session.ioBufferDuration) * rate)
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
        if micTapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            micTapInstalled = false
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
        let startFrame = renderer.position
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
        if let onRendered {
            var sum: Float = 0
            for i in 0..<Int(frames) { sum += channel[i] * channel[i] }
            onRendered(startFrame, (sum / Float(frames)).squareRoot())
        }
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
            if self.micTapInstalled {
                self.engine.inputNode.removeTap(onBus: 0)
                self.micTapInstalled = false
            }
            callback?()
        }
    }

    @objc private func audioInterrupted(_ notification: Notification) {
        // Our own category / voice-processing changes post configuration changes too.
        if notification.name == .AVAudioEngineConfigurationChange && (reconfiguring || !isPlaying) { return }
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
