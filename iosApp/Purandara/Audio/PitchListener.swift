import AVFoundation
import shared

/// Mic buffers → windows → (SwiftF0 every few windows) → YIN pipeline →
/// `onFrame`, for sing-along, Shruti practice and Find my Sa. Mirrors
/// Android's `PitchListener`.
///
/// Mic buffers arrive on an AVFoundation thread and are copied straight to a
/// serial queue, where all Kotlin work happens; `onFrame` runs on that queue.
/// `timeOf` maps each window's capture host time to the frame's time in
/// seconds (nil skips the window): for playback, the moment of the
/// performance the singer was hearing.
final class PitchListener: @unchecked Sendable {
    /// Debug builds: a synthetic singer replaces the mic.
    struct SyntheticSinger {
        let cents: Double
        /// What to sing at a host time (nil = silent).
        let frequency: (UInt64) -> Double?
    }

    let queue = DispatchQueue(label: "purandara.pitch", qos: .userInitiated)
    private let timeOf: (UInt64) -> Double?
    private let synthetic: SyntheticSinger?
    private let supervisorFactory: (Int32) -> NeuralPitchSupervisor?
    private let onFrame: (PitchFrame) -> Void

    // Queue-confined.
    private var pipeline: PitchPipeline?
    private var supervisor: NeuralPitchSupervisor?
    private var ring: [Float] = []
    private var ringStartHost: UInt64 = 0
    private var ringStartSeconds = 0.0
    private var micRate: Double = 44_100
    private var sinceLastWindow = 0
    private var timer: DispatchSourceTimer?
    private var phase = 0.0
    private var stopped = false

    #if DEBUG
    /// Extra text for the once-a-second `[singalong]` console line.
    var diagnostics: (() -> String)?
    private var diag = (buffers: 0, windows: 0, noTime: 0, last: 0.0)
    private func logDiagnostics() {
        let now = CACurrentMediaTime()
        guard now - diag.last >= 1 else { return }
        let route = AVAudioSession.sharedInstance().currentRoute
        print("[singalong] rate \(Int(micRate)) buffers \(diag.buffers) windows \(diag.windows) noTime \(diag.noTime) "
            + (diagnostics?() ?? "")
            + " in \(route.inputs.map(\.portType.rawValue)) out \(route.outputs.map(\.portType.rawValue))")
        diag = (0, 0, 0, now)
    }
    #endif

    private static let window = Int(PitchPipeline.companion.WINDOW_SIZE)
    private static let hop = Int(PitchPipeline.companion.HOP_SIZE)

    init(
        timeOf: @escaping (UInt64) -> Double?,
        synthetic: SyntheticSinger?,
        supervisorFactory: @escaping (Int32) -> NeuralPitchSupervisor?,
        onFrame: @escaping (PitchFrame) -> Void
    ) {
        self.timeOf = timeOf
        self.synthetic = synthetic
        self.supervisorFactory = supervisorFactory
        self.onFrame = onFrame
    }

    /// The mic request to pass to `VarisaiAudioPlayer.play`, or nil when a synthetic singer replaces the mic.
    func micRequest(echoCancellation: Bool) -> VarisaiAudioPlayer.MicRequest? {
        guard synthetic == nil else { return nil }
        return VarisaiAudioPlayer.MicRequest(echoCancellation: echoCancellation) { [weak self] buffer, when in
            guard let self, let channel = buffer.floatChannelData?[0] else { return }
            let host = when.isHostTimeValid ? when.hostTime : mach_absolute_time()
            let latency = AVAudioTime.hostTime(forSeconds: AVAudioSession.sharedInstance().inputLatency)
            let captureHost = host > latency ? host - latency : 0
            // Capture the heard timeline while the buffer is admitted, not when
            // a queued window is later processed after output has stopped.
            guard let seconds = self.timeOf(captureHost) else { return }
            let samples = Array(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
            let rate = buffer.format.sampleRate
            self.queue.async { self.append(samples, rate: rate, hostTime: captureHost, seconds: seconds) }
        }
    }

    /// Call right after playback starts.
    func start() {
        queue.async { [self] in
            if let synthetic { startSynthetic(synthetic) }
        }
    }

    /// Stops listening. Runs `then` on the queue after the last frame.
    func stop<T>(then: () -> T) -> T {
        queue.sync {
            stopped = true
            timer?.cancel()
            timer = nil
            return then()
        }
    }

    // MARK: - Queue

    private func append(_ samples: [Float], rate: Double, hostTime: UInt64, seconds: Double) {
        guard !stopped else { return }
        #if DEBUG
        diag.buffers += 1
        logDiagnostics()
        #endif
        if pipeline == nil || rate != micRate {
            micRate = rate
            pipeline = PitchPipeline(sampleRate: Int32(rate))
            supervisor = supervisorFactory(Int32(rate))
            ring.removeAll()
            sinceLastWindow = 0
        }
        if ring.isEmpty {
            ringStartHost = hostTime
            ringStartSeconds = seconds
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
            process(Array(ring[start..<end]), captureHost: captureHost, mappedTime: ringStartSeconds + centreOffset)
        }
        // Keep the history the next window needs.
        let excess = ring.count - Self.window - sinceLastWindow
        if excess > Self.hop * 4 {
            ring.removeFirst(excess)
            ringStartHost += AVAudioTime.hostTime(forSeconds: Double(excess) / micRate)
            ringStartSeconds += Double(excess) / micRate
        }
    }

    private func process(_ samples: [Float], captureHost: UInt64, mappedTime: Double? = nil) {
        #if DEBUG
        diag.windows += 1
        #endif
        guard let pipeline, let time = mappedTime ?? timeOf(captureHost) else {
            #if DEBUG
            diag.noTime += 1
            #endif
            return
        }
        let window = samples.withUnsafeBufferPointer {
            FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer(mutating: $0.baseAddress!), count: Int32(samples.count))
        }
        if pipeline.beginFrame() { pipeline.onNeuralResult(result: supervisor?.estimate(window)) }
        onFrame(pipeline.process(window: window, timeSeconds: time))
    }

    // MARK: - Debug synthetic singer

    private func startSynthetic(_ singer: SyntheticSinger) {
        micRate = 44_100
        pipeline = PitchPipeline(sampleRate: 44_100)
        supervisor = supervisorFactory(44_100)
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now(), repeating: Double(Self.hop) / micRate)
        var window = [Float](repeating: 0, count: Self.window)
        timer.setEventHandler { [weak self] in
            guard let self, !self.stopped else { return }
            let now = mach_absolute_time()
            // Stable transport capture can exist while this attempt is not
            // sounding: do not synthesize/copy/infer model or recovery audio.
            guard self.timeOf(now) != nil else { return }
            let hz = singer.frequency(now).map { $0 * pow(2, singer.cents / 1200) }
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
        self.timer = timer
    }
}
