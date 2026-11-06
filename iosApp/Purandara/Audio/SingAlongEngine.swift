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
    func stop() -> PassResult? {
        listener.stop { [self] in
            player.onRendered = nil
            return session.finish()
        }
    }
}
