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
    // Replaced (main thread, while stopped) when voice processing goes off.
    private var engine: AVAudioEngine
    private var node = AVAudioPlayerNode()
    private let format: AVAudioFormat
    private let queue = DispatchQueue(label: "purandara.varisai-render", qos: .userInteractive)

    // Queue-confined.
    private var renderer: SampleRenderer?
    private var scratch: KotlinFloatArray
    private var pending = 0
    private var doneRendering = false
    private var stopRequested = false
    private var generation = 0
    private var playGeneration = 0
    private var outputRevision = 0
    private var outputAcknowledgement = 0
    private var notifiedOutputs: [String] = []
    private var onFinished: (@MainActor () -> Void)?

    // Main thread.
    private(set) var isPlaying = false
    private var recovery = PlaybackRecovery()
    private var playbackOutputs: [String] = []
    private let routeOutputs: @Sendable () -> [String]
    private var stopping = false

    private static let chunkFrames: AVAudioFrameCount = 4096
    private static let buffersAhead = 3

    init(engine: AVAudioEngine = AVAudioEngine(), routeOutputs: @escaping @Sendable () -> [String] = { VarisaiAudioPlayer.outputIDs() }) {
        self.engine = engine
        self.routeOutputs = routeOutputs
        format = AVAudioFormat(standardFormatWithSampleRate: HarmoniumSamples.sampleRate, channels: 1)!
        scratch = KotlinFloatArray(size: Int32(Self.chunkFrames))
        wireEngine()
        notifiedOutputs = routeOutputs()
        NotificationCenter.default.addObserver(
            self, selector: #selector(audioInterrupted), name: AVAudioSession.interruptionNotification, object: nil)
        NotificationCenter.default.addObserver(
            self, selector: #selector(routeChanged), name: AVAudioSession.routeChangeNotification, object: nil)
    }

    private func wireEngine() {
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        NotificationCenter.default.addObserver(
            self, selector: #selector(engineConfigurationChanged), name: .AVAudioEngineConfigurationChange, object: engine)
    }

    /// A fresh engine and player node. Switching voice processing off on an
    /// engine that had it leaves the output silent, so build a new one instead.
    private func replaceEngine() {
        engine.stop()
        NotificationCenter.default.removeObserver(self, name: .AVAudioEngineConfigurationChange, object: engine)
        engine = AVAudioEngine()
        node = AVAudioPlayerNode()
        wireEngine()
        voiceProcessingOn = false
        lastReconfigured = CACurrentMediaTime()
    }

    /// Starts `renderer` from the beginning. `onFinished` runs on the main
    /// thread when a non-looping performance has been heard, after a requested
    /// stop has faded out, or when audio is interrupted.
    func play(_ renderer: SampleRenderer, mic: MicRequest? = nil, onFinished: @escaping @MainActor () -> Void) throws {
        stopImmediately()
        try configure(mic: mic)
        queue.sync {
            generation += 1
            playGeneration += 1
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
        playbackOutputs = routeOutputs()
        recovery.start()
    }

    /// Microphone capture on this same engine (needed for echo cancellation).
    /// Echo cancellation (voice processing) is switched on when the sound goes
    /// to the built-in speaker; that is decided after the session enters
    /// record mode, because the route can change then (a paired Bluetooth
    /// device may play `.playback` audio but not `.playAndRecord`).
    struct MicRequest {
        /// The student's setting: cancel the app's own sound when on the speaker.
        let echoCancellation: Bool
        /// Called on an AVFoundation thread with each captured buffer and its time.
        let onBuffer: (AVAudioPCMBuffer, AVAudioTime) -> Void
    }

    /// Called on the render queue after each rendered block: its first frame and RMS level.
    var onRendered: ((Int64, Float) -> Void)?

    private var micTapInstalled = false
    private var reconfiguring = false
    private var voiceProcessingOn = false
    private var lastReconfigured: CFTimeInterval = 0
    /// The route `configure` found and left, and when: a notification shortly
    /// after that reports this very move is the app's own doing.
    private var configuredFrom: [String] = []
    private var configuredOutputs: [String] = []
    private var configuredAt: CFTimeInterval = -.infinity

    static func sessionCategory(recording: Bool) -> AVAudioSession.Category {
        recording ? .playAndRecord : .playback
    }

    private func configure(mic: MicRequest?) throws {
        reconfiguring = true
        let outputsBefore = routeOutputs()
        defer {
            configuredFrom = outputsBefore
            reconfiguring = false
            configuredOutputs = routeOutputs()
            configuredAt = CACurrentMediaTime()
        }
        let session = AVAudioSession.sharedInstance()
        if micTapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            micTapInstalled = false
        }
        let wantRecord = mic != nil
        if !wantRecord && voiceProcessingOn { replaceEngine() }
        if session.category != Self.sessionCategory(recording: wantRecord) {
            engine.stop()
            if wantRecord {
                try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothA2DP])
            } else {
                try session.setCategory(.playback, mode: .default)
            }
            lastReconfigured = CACurrentMediaTime()
        }
        try session.setActive(true)
        let onSpeaker = wantRecord && Self.outputIsBuiltInSpeaker()
        let wantVoiceProcessing = onSpeaker && mic?.echoCancellation == true
        if voiceProcessingOn && !wantVoiceProcessing { replaceEngine() }
        if wantVoiceProcessing && !voiceProcessingOn {
            engine.stop()
            try engine.inputNode.setVoiceProcessingEnabled(true)
            voiceProcessingOn = true
            lastReconfigured = CACurrentMediaTime()
            engine.inputNode.isVoiceProcessingAGCEnabled = false
        }
        micOnSpeaker = onSpeaker
        #if DEBUG
        defer {
            print("[player] configure record \(wantRecord) echoCancel \(wantVoiceProcessing) running \(engine.isRunning) "
                + "out \(session.currentRoute.outputs.map { $0.portType.rawValue })")
        }
        #endif
        // The input must be part of the engine before it starts: creating it on
        // a running engine (first `inputNode` access) leaves the engine silent.
        if wantRecord { _ = engine.inputNode }
        if !engine.isRunning { try engine.start() }
        // After starting: switching voice processing changes the input format,
        // and a tap installed with the stale format never receives buffers.
        if let mic {
            engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: nil) { buffer, when in mic.onBuffer(buffer, when) }
            micTapInstalled = true
        }
    }

    /// Internal session / voice-processing rebuilds can stop the engine just
    /// after configuration. The caller restarts with the same settings.
    var onRestartNeeded: (@MainActor () -> Void)?

    /// External output changes stop practice, even while suspended in the background.
    var onOutputChanged: (@MainActor () -> Void)?

    /// True when the last `play` with a mic sent sound to the built-in speaker.
    private(set) var micOnSpeaker = false

    /// Whether the current route plays through the built-in speaker rather than headphones.
    static func outputIsBuiltInSpeaker() -> Bool {
        let headphones: Set<AVAudioSession.Port> = [.headphones, .bluetoothA2DP, .bluetoothLE, .bluetoothHFP, .usbAudio]
        return !AVAudioSession.sharedInstance().currentRoute.outputs.contains { headphones.contains($0.portType) }
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
    @MainActor func stop() {
        recovery.stop()
        guard isPlaying else { return }
        stopping = true
        guard engine.isRunning else { finishImmediately(); return }
        queue.async { self.stopRequested = true }
    }

    /// Stops at once, without calling `onFinished`. Used when restarting.
    func stopImmediately() {
        recovery.stop()
        queue.sync {
            generation += 1
            playGeneration += 1
            renderer = nil
            onFinished = nil
        }
        if micTapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            micTapInstalled = false
        }
        node.stop()
        isPlaying = false
        stopping = false
    }

    @MainActor private func finishImmediately() {
        let callback = queue.sync { onFinished }
        stopImmediately()
        callback?()
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
        // Refill as soon as the engine has taken a buffer. Waiting until it has
        // been heard (.dataPlayedBack) includes the output latency, which over
        // Bluetooth (150–250 ms) drains the queue and breaks the sound up.
        node.scheduleBuffer(buffer, completionCallbackType: .dataConsumed) { [weak self] _ in
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
        generation += 1
        renderer = nil
        // Keep the completion until delivery: Stop or an interruption can
        // finish during the output-latency tail, before the delayed closure.
        // The last buffer has been consumed but is still on its way to the
        // speaker or headphones: finish once it has been heard.
        let session = AVAudioSession.sharedInstance()
        let tail = session.outputLatency + session.ioBufferDuration
        let finishedGeneration = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + tail) {
            // A new play (or stopImmediately) since then owns the node now.
            guard self.queue.sync(execute: { self.generation == finishedGeneration }) else { return }
            self.finishImmediately()
        }
    }

    struct OutputSnapshot: Equatable {
        let outputs: [String]
        let revision: Int
    }

    func outputSnapshot() -> OutputSnapshot {
        OutputSnapshot(outputs: routeOutputs(), revision: queue.sync { outputRevision })
    }

    /// Only an explicit user start may acknowledge a previously queued change.
    func acknowledgeOutputChange() {
        let outputs = routeOutputs()
        queue.sync {
            outputAcknowledgement += 1
            notifiedOutputs = outputs
        }
    }

    private static func outputIDs() -> [String] {
        AVAudioSession.sharedInstance().currentRoute.outputs.map { $0.uid }
    }

    @objc private func routeChanged(_ notification: Notification) {
        let acknowledgement = queue.sync { outputAcknowledgement }
        let raw = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt ?? 0
        let reason = AVAudioSession.RouteChangeReason(rawValue: raw) ?? .unknown
        // Record hardware connections before the main-thread handoff, so a
        // foreground resume cannot overtake a pending route notification.
        let routeBeforeChange = (notification.userInfo?[AVAudioSessionRouteChangePreviousRouteKey] as? AVAudioSessionRouteDescription)?
            .outputs.map { $0.uid }
        let outputs = routeOutputs()
        let changedAtDelivery = queue.sync {
            let changed = outputs != (routeBeforeChange ?? notifiedOutputs)
            notifiedOutputs = outputs
            if reason != .categoryChange && changed { outputRevision += 1 }
            return changed
        }
        #if DEBUG
        let session = AVAudioSession.sharedInstance()
        print("[player] route change \(raw) category \(session.category.rawValue) "
            + "out \(session.currentRoute.outputs.map { $0.portType.rawValue })")
        #endif
        #if DEBUG
        let simulated = UserDefaults.standard.bool(forKey: "simulateOutputChange")
            && notification.userInfo?["purandara.testOutputChange"] as? Bool == true
        #else
        let simulated = false
        #endif
        DispatchQueue.main.async {
            guard self.queue.sync(execute: { self.outputAcknowledgement == acknowledgement }) else { return }
            // `configure` runs synchronously on this thread, so a notification it
            // caused arrives after it returns: recognise it by the move it made.
            // A configure that found the route already changed (a restart racing an
            // external change) made no move, so that change still stops practice.
            let ownChange = !simulated && CACurrentMediaTime() - self.configuredAt < 2
                && self.configuredFrom != self.configuredOutputs
                && self.routeOutputs() == self.configuredOutputs
            guard !ownChange,
                  PlaybackRecovery.routeNeedsStop(reason: reason, outputsChanged: changedAtDelivery || simulated) else { return }
            self.stopForOutputChange()
        }
    }

    @objc private func engineConfigurationChanged(_ notification: Notification) {
        guard let source = notification.object as? AVAudioEngine else { return }
        let gen = queue.sync { playGeneration }
        DispatchQueue.main.async {
            guard source === self.engine, self.queue.sync(execute: { self.playGeneration == gen }),
                  self.isPlaying, !self.reconfiguring, !self.engine.isRunning else { return }
            if self.stopping { self.finishImmediately(); return }
            if self.routeOutputs() != self.playbackOutputs {
                self.stopForOutputChange()
            } else if CACurrentMediaTime() - self.lastReconfigured < 2 {
                self.requestRecovery()
            } else {
                // An unexplained hardware rebuild must not restart practice.
                self.stopForOutputChange()
            }
        }
    }

    @MainActor private func stopForOutputChange() {
        let callback = queue.sync { onFinished }
        stopImmediately()
        engine.stop()
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("[player] session release on output change failed: \(error)")
        }
        // Notify before completion so no settings/background path can restart
        // a stopped listening screen. The normal completion still runs once.
        onOutputChanged?()
        callback?()
    }

    /// Give a related external-route notification time to cancel an internal
    /// rebuild. Stop or a new play also invalidates the recovery ticket.
    private func requestRecovery() {
        guard let ticket = recovery.request() else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            guard self.recovery.take(ticket) else { return }
            guard self.routeOutputs() == self.playbackOutputs else { self.stopForOutputChange(); return }
            let callback = self.queue.sync { self.onFinished }
            self.stopImmediately()
            self.engine.stop()
            if let restart = self.onRestartNeeded { restart() } else { callback?() }
        }
    }

    @objc private func audioInterrupted(_ notification: Notification) {
        guard let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              AVAudioSession.InterruptionType(rawValue: raw) == .began else { return }
        let gen = queue.sync { playGeneration }
        DispatchQueue.main.async {
            guard self.queue.sync(execute: { self.playGeneration == gen }) else { return }
            self.finishImmediately()
        }
    }
}

/// Main-thread recovery tickets invalidate delayed work when playback changes.
struct PlaybackRecovery {
    static func routeNeedsStop(reason: AVAudioSession.RouteChangeReason, outputsChanged: Bool) -> Bool {
        guard reason != .categoryChange else { return false }
        return outputsChanged
    }

    private var generation = 0
    private var active = false
    private var pending = false

    mutating func start() {
        generation += 1
        active = true
        pending = false
    }

    mutating func stop() {
        generation += 1
        active = false
        pending = false
    }

    mutating func request() -> Int? {
        guard active, !pending else { return nil }
        pending = true
        return generation
    }

    mutating func take(_ ticket: Int) -> Bool {
        guard active, pending, ticket == generation else { return false }
        pending = false
        active = false
        return true
    }
}
