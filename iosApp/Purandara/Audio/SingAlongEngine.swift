import AVFoundation
import shared

/// Sing-along for one playback: a `PitchListener` feeding the shared
/// `SingAlongSession`. Mirrors Android's `SingAlongEngine`.
///
/// Each window's capture host time is mapped through
/// `VarisaiAudioPlayer.outputFrame(atHostTime:)` to the rendered frame the
/// singer was hearing, so the shared clock runs with zero extra latency.
/// `onOutcome` runs on the listener's queue.
final class SingAlongEngine: @unchecked Sendable {
    private let player: VarisaiAudioPlayer
    private let session: SingAlongSession
    private var listener: PitchListener!

    init(
        player: VarisaiAudioPlayer,
        renderer: SampleRenderer,
        schedule: Schedule,
        strictness: Strictness,
        syntheticCents: Double?,
        supervisorFactory: @escaping (Int32) -> NeuralPitchSupervisor?,
        onOutcome: @escaping (FrameOutcome) -> Void
    ) {
        self.player = player
        let rate = Double(renderer.sampleRate)
        let session = SingAlongSession(
            schedule: schedule,
            clock: SingAlongClock(
                countInSeconds: Double(renderer.countInFrames) / rate,
                passSeconds: Double(renderer.performanceFrames) / rate,
                loop: renderer.totalFrames == Int64.max,
                outputLatencySeconds: 0,
                inputLatencySeconds: 0),
            sampleRate: renderer.sampleRate,
            outputLevels: OutputLevels(capacity: 512),
            strictness: strictness)
        self.session = session
        // The debug synthetic singer sings the note being heard, silent in the count-in.
        let synthetic = syntheticCents.map { cents in
            PitchListener.SyntheticSinger(cents: cents) { [weak player] host in
                guard let frame = player?.outputFrame(atHostTime: host), frame >= 0,
                      renderer.countInBeat(frame: frame) == nil else { return nil }
                let t = renderer.passSeconds(frame: frame)
                guard t < schedule.durationSeconds else { return nil }
                return schedule.notes.last { $0.startSeconds <= t }?.frequencyHz
            }
        }
        listener = PitchListener(
            timeOf: { [weak player] host in player?.outputFrame(atHostTime: host).map { Double($0) / rate } },
            synthetic: synthetic,
            supervisorFactory: supervisorFactory
        ) { frame in onOutcome(session.onFrame(frame: frame)) }
        #if DEBUG
        listener.diagnostics = { [weak player] in
            "leak \(String(format: "%.3f", session.leakageRatio)) calibrated \(session.bleedCalibrated) onSpeaker \(player?.micOnSpeaker ?? false)"
        }
        #endif
        player.onRendered = { start, rms in session.outputLevels.record(startFrame: start, rms: rms) }
    }

    /// The mic request to pass to `VarisaiAudioPlayer.play`, or nil when a synthetic singer replaces the mic.
    func micRequest(echoCancellation: Bool) -> VarisaiAudioPlayer.MicRequest? {
        listener.micRequest(echoCancellation: echoCancellation)
    }

    /// Call right after playback starts.
    func start() { listener.start() }

    /// Stops listening; returns the scored final pass, if one was in progress.
    private var finalized = false

    func stop() -> PassResult? {
        listener.stop { [self] in
            guard !finalized else { return nil }
            finalized = true
            player.onRendered = nil
            return session.finish()
        }
    }
}

/// One stable recording graph, with a fresh listener/pipeline/session for each
/// scored segment. The router rejects recovery/model audio before copying it.
final class TransportSingAlongEngine: @unchecked Sendable {
    private struct Attempt {
        let segment: PracticeSegment
        let listener: PitchListener
        let session: SingAlongSession
        let request: VarisaiAudioPlayer.MicRequest?
    }

    private let lock = NSLock()
    private var attempts: [Attempt] = []
    private var stopped = false
    private var captureReady = false
    private let player: VarisaiAudioPlayer
    private let schedule: Schedule
    private let strictness: Strictness
    private let syntheticCents: Double?
    private let rate: Int32
    private let onOutcome: (PracticeSegment, FrameOutcome) -> Void
    let outputLevels = OutputLevels(capacity: 512)

    init(player: VarisaiAudioPlayer, schedule: Schedule, strictness: Strictness,
         syntheticCents: Double?, sampleRate: Int32,
         onOutcome: @escaping (PracticeSegment, FrameOutcome) -> Void) {
        self.player = player
        self.schedule = schedule
        self.strictness = strictness
        self.syntheticCents = syntheticCents
        self.rate = sampleRate
        self.onOutcome = onOutcome
    }

    var ready: Bool {
        lock.lock()
        defer { lock.unlock() }
        return !stopped && (syntheticCents != nil || captureReady)
    }

    func micRequest(echoCancellation: Bool) -> VarisaiAudioPlayer.MicRequest? {
        guard syntheticCents == nil else { return nil }
        return VarisaiAudioPlayer.MicRequest(echoCancellation: echoCancellation) { [weak self] buffer, time in
            guard let self, buffer.frameLength > 0, buffer.format.sampleRate > 0,
                  buffer.floatChannelData?[0] != nil else { return }
            // A real usable tap buffer is the capture readiness handshake. No
            // samples need copying or inference just to prove the graph is live.
            self.lock.lock()
            self.captureReady = true
            self.lock.unlock()
            let host = time.isHostTimeValid ? time.hostTime : mach_absolute_time()
            let latency = AVAudioTime.hostTime(forSeconds: AVAudioSession.sharedInstance().inputLatency)
            guard let frame = self.player.outputFrame(atHostTime: host > latency ? host - latency : 0) else { return }
            let endFrame = frame + Int64(ceil(Double(buffer.frameLength) / buffer.format.sampleRate * Double(self.rate)))
            self.lock.lock()
            // A boundary-straddling buffer is discarded too: no recovery/model
            // samples enter an attempt's window history or neural inference.
            let request = self.stopped ? nil : self.attempts.last {
                frame >= $0.segment.startFrame && endFrame <= $0.segment.performanceEndFrame
            }?.request
            // Keep admission and enqueue atomic with removing an attempt.
            request?.onBuffer(buffer, time)
            self.lock.unlock()
        }
    }

    /// Render worker only; called before the segment's count-in is rendered.
    func begin(_ segment: PracticeSegment) {
        guard segment.scored else { return }
        let session = SingAlongSession.companion.forSegment(
            schedule: schedule, segment: segment, sampleRate: rate,
            outputLevels: outputLevels, strictness: strictness,
            outputLatencySeconds: 0, inputLatencySeconds: 0)
        let rate = Double(rate)
        let synthetic = syntheticCents.map { cents in
            PitchListener.SyntheticSinger(cents: cents) { [weak player, schedule] host in
                guard let frame = player?.outputFrame(atHostTime: host),
                      frame >= segment.performanceStartFrame, frame < segment.performanceEndFrame else { return nil }
                let seconds = Double(frame - segment.performanceStartFrame) / rate
                return schedule.notes.last { $0.startSeconds <= seconds }?.frequencyHz
            }
        }
        let listener = PitchListener(
            timeOf: { [weak player] host in
                guard let frame = player?.outputFrame(atHostTime: host),
                      frame >= segment.startFrame, frame < segment.performanceEndFrame else { return nil }
                return Double(frame) / rate
            }, synthetic: synthetic,
            supervisorFactory: { NeuralPitchSupervisor(captureSampleRate: $0) }
        ) { [onOutcome] frame in onOutcome(segment, session.onFrame(frame: frame)) }
        let attempt = Attempt(segment: segment, listener: listener, session: session,
                              request: listener.micRequest(echoCancellation: false))
        lock.lock()
        guard !stopped else { lock.unlock(); return }
        attempts.append(attempt)
        lock.unlock()
        listener.start()
    }

    /// Heard clock, not render-ahead. Removing routing first and draining the
    /// listener queue retains all accepted windows before exactly-once finish.
    func finalize(heardFrame: Int64) -> [(PracticeSegment, PassResult)] {
        lock.lock()
        let ended = attempts.filter { heardFrame >= $0.segment.endFrame }
        attempts.removeAll { heardFrame >= $0.segment.endFrame }
        lock.unlock()
        return finish(ended)
    }

    func stop() -> [(PracticeSegment, PassResult)] {
        lock.lock()
        stopped = true
        let ended = attempts
        attempts.removeAll()
        lock.unlock()
        return finish(ended)
    }

    private func finish(_ ended: [Attempt]) -> [(PracticeSegment, PassResult)] {
        ended.compactMap { attempt in
            attempt.listener.stop(then: { attempt.session.finish() }).map { (attempt.segment, $0) }
        }
    }
}
