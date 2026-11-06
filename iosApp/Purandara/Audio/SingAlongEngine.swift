import AVFoundation
import shared

/// Sing-along for one playback: mic buffers → windows → (SwiftF0 every few
/// windows) → YIN pipeline → the shared `SingAlongSession`. Mirrors Android's
/// `SingAlongEngine`.
///
/// Mic buffers arrive on an AVFoundation thread and are copied straight to a
/// serial queue, where all Kotlin work happens. Each window's capture host
/// time is mapped through `VarisaiAudioPlayer.outputFrame(atHostTime:)` to the
/// rendered frame the singer was hearing, so the shared clock runs with zero
/// extra latency. `onOutcome` runs on that queue.
final class SingAlongEngine: @unchecked Sendable {
    private let player: VarisaiAudioPlayer
    private let renderer: SampleRenderer
    private let session: SingAlongSession
    private let supervisorFactory: (Int32) -> NeuralPitchSupervisor?
    private let syntheticCents: Double?
    private let schedule: Schedule
    private let onOutcome: (FrameOutcome) -> Void
    private let queue = DispatchQueue(label: "purandara.sing-along", qos: .userInitiated)

    // Queue-confined.
    private var pipeline: PitchPipeline?
    private var supervisor: NeuralPitchSupervisor?
    private var ring: [Float] = []
    private var ringStartHost: UInt64 = 0
    private var micRate: Double = 44_100
    private var sinceLastWindow = 0
    private var synthetic: DispatchSourceTimer?
    private var phase = 0.0
    private var stopped = false

    private static let window = Int(PitchPipeline.companion.WINDOW_SIZE)
    private static let hop = Int(PitchPipeline.companion.HOP_SIZE)

    init(
        player: VarisaiAudioPlayer,
        renderer: SampleRenderer,
        schedule: Schedule,
        syntheticCents: Double?,
        supervisorFactory: @escaping (Int32) -> NeuralPitchSupervisor?,
        onOutcome: @escaping (FrameOutcome) -> Void
    ) {
        self.player = player
        self.renderer = renderer
        self.schedule = schedule
        self.syntheticCents = syntheticCents
        self.supervisorFactory = supervisorFactory
        self.onOutcome = onOutcome
        let rate = Double(renderer.sampleRate)
        session = SingAlongSession(
            schedule: schedule,
            clock: SingAlongClock(
                countInSeconds: Double(renderer.countInFrames) / rate,
                passSeconds: Double(renderer.performanceFrames) / rate,
                loop: renderer.totalFrames == Int64.max,
                outputLatencySeconds: 0,
                inputLatencySeconds: 0),
            sampleRate: renderer.sampleRate,
            outputLevels: OutputLevels(capacity: 512))
        player.onRendered = { [session] start, rms in session.outputLevels.record(startFrame: start, rms: rms) }
    }

    /// The mic request to pass to `VarisaiAudioPlayer.play`, or nil when a synthetic singer replaces the mic.
    func micRequest() -> VarisaiAudioPlayer.MicRequest? {
        guard syntheticCents == nil else { return nil }
        return VarisaiAudioPlayer.MicRequest { [weak self] buffer, when in
            guard let self, let channel = buffer.floatChannelData?[0] else { return }
            let samples = Array(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
            let rate = buffer.format.sampleRate
            let host = when.isHostTimeValid ? when.hostTime : mach_absolute_time()
            self.queue.async { self.append(samples, rate: rate, hostTime: host) }
        }
    }

    /// Call right after playback starts.
    func start() {
        queue.async { [self] in
            if let cents = syntheticCents { startSynthetic(cents: cents) }
        }
    }

    /// Stops listening; returns the scored final pass, if one was in progress.
    func stop() -> PassResult? {
        queue.sync {
            stopped = true
            synthetic?.cancel()
            synthetic = nil
            player.onRendered = nil
            return session.finish()
        }
    }

    // MARK: - Queue

    private func append(_ samples: [Float], rate: Double, hostTime: UInt64) {
        guard !stopped else { return }
        if pipeline == nil || rate != micRate {
            micRate = rate
            pipeline = PitchPipeline(sampleRate: Int32(rate))
            supervisor = supervisorFactory(Int32(rate))
            ring.removeAll()
            sinceLastWindow = 0
        }
        if ring.isEmpty {
            ringStartHost = hostTime - AVAudioTime.hostTime(forSeconds: AVAudioSession.sharedInstance().inputLatency)
        }
        ring.append(contentsOf: samples)
        // Windows can only end where a full window of history precedes them,
        // so audio that arrived before the first full window doesn't count.
        sinceLastWindow = min(sinceLastWindow + samples.count, max(0, ring.count - Self.window))
        while ring.count >= Self.window && sinceLastWindow >= Self.hop {
            sinceLastWindow -= Self.hop
            // Window ends `sinceLastWindow` samples before the newest sample.
            let end = ring.count - sinceLastWindow
            let start = end - Self.window
            let centreOffset = Double(start + Self.window / 2) / micRate
            let captureHost = ringStartHost + AVAudioTime.hostTime(forSeconds: centreOffset)
            process(Array(ring[start..<end]), captureHost: captureHost)
        }
        // Keep the history the next window needs.
        let excess = ring.count - Self.window - sinceLastWindow
        if excess > Self.hop * 4 {
            ring.removeFirst(excess)
            ringStartHost += AVAudioTime.hostTime(forSeconds: Double(excess) / micRate)
        }
    }

    private func process(_ samples: [Float], captureHost: UInt64) {
        guard let pipeline, let heard = player.outputFrame(atHostTime: captureHost) else { return }
        let window = samples.withUnsafeBufferPointer {
            FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer(mutating: $0.baseAddress!), count: Int32(samples.count))
        }
        if pipeline.beginFrame() { pipeline.onNeuralResult(result: supervisor?.estimate(window)) }
        let frame = pipeline.process(window: window, timeSeconds: Double(heard) / Double(renderer.sampleRate))
        onOutcome(session.onFrame(frame: frame))
    }

    // MARK: - Debug synthetic singer

    private func startSynthetic(cents: Double) {
        micRate = 44_100
        pipeline = PitchPipeline(sampleRate: 44_100)
        supervisor = supervisorFactory(44_100)
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now(), repeating: Double(Self.hop) / micRate)
        var window = [Float](repeating: 0, count: Self.window)
        timer.setEventHandler { [weak self] in
            guard let self, !self.stopped else { return }
            let now = mach_absolute_time()
            let hz = self.syntheticFrequency(atHostTime: now).map { $0 * pow(2, cents / 1200) }
            window.removeFirst(Self.hop)
            for _ in 0..<Self.hop {
                guard let hz else { window.append(0); continue }
                self.phase += 2 * Double.pi * hz / self.micRate
                var v = 0.0
                for h in 1...6 { v += sin(Double(h) * self.phase) / Double(h) }
                window.append(Float(0.2 * v / 2.45))
            }
            self.process(window, captureHost: now)
        }
        timer.resume()
        synthetic = timer
    }

    private func syntheticFrequency(atHostTime host: UInt64) -> Double? {
        guard let frame = player.outputFrame(atHostTime: host), frame >= 0,
              renderer.countInBeat(frame: frame) == nil else { return nil }
        let t = renderer.passSeconds(frame: frame)
        guard t < schedule.durationSeconds else { return nil }
        return schedule.notes.last { $0.startSeconds <= t }?.frequencyHz
    }
}
