import AVFoundation
import shared

/// Records the microphone into an AAC .m4a file (docs/plan-recordings.md),
/// on its own engine with the session in `.measurement` mode: no automatic
/// gain or voice processing, and no drone plays, so no echo cancellation.
/// Mirrors Android's `Recorder`. The tap writes on the audio thread;
/// [onLevel] and [onLimit] are called there (build them outside the main actor).
final class Recorder: @unchecked Sendable {
    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private let maxSeconds: Double
    private let onLevel: @Sendable (Float, Double) -> Void
    private let onLimit: @Sendable () -> Void
    // Guarded by `lock`.
    private var file: AVAudioFile?
    private var frames: AVAudioFramePosition = 0
    private var sampleRate: Double = 44_100
    private var limitReported = false
    private var synthetic: Thread?

    init(maxSeconds: Double, onLevel: @escaping @Sendable (Float, Double) -> Void, onLimit: @escaping @Sendable () -> Void) {
        self.maxSeconds = maxSeconds
        self.onLevel = onLevel
        self.onLimit = onLimit
    }

    static func fileSettings(sampleRate: Double) -> [String: Any] {
        [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: sampleRate, AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 96_000]
    }

    /// Starts recording the microphone into [url].
    func start(url: URL) throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement)
        try session.setActive(true)
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0,
              let mono = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: format.sampleRate, channels: 1, interleaved: false)
        else { throw RecorderError.noInput }
        let out = try AVAudioFile(forWriting: url, settings: Self.fileSettings(sampleRate: format.sampleRate),
                                  commonFormat: .pcmFormatFloat32, interleaved: false)
        lock.withLock {
            file = out
            sampleRate = format.sampleRate
        }
        input.installTap(onBus: 0, bufferSize: 4096, format: format) { [weak self] buffer, _ in
            self?.write(buffer, mono: mono)
        }
        engine.prepare()
        try engine.start()
    }

    #if DEBUG
    /// Debug only: sings Sarali Varisai 1 up and down at [saHz], [cents] off,
    /// half a second a note, in real time, instead of the microphone.
    func startSynthetic(url: URL, saHz: Double, cents: Double) throws {
        let rate = 44_100.0
        guard let mono = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, channels: 1, interleaved: false) else {
            throw RecorderError.noInput
        }
        let out = try AVAudioFile(forWriting: url, settings: Self.fileSettings(sampleRate: rate), commonFormat: .pcmFormatFloat32, interleaved: false)
        lock.withLock {
            file = out
            sampleRate = rate
        }
        let thread = Thread { [weak self] in self?.sing(mono: mono, saHz: saHz, cents: cents) }
        synthetic = thread
        thread.start()
    }

    private func sing(mono: AVAudioFormat, saHz: Double, cents: Double) {
        let up: [Double] = [1, 16.0 / 15, 5.0 / 4, 4.0 / 3, 1.5, 8.0 / 5, 15.0 / 8, 2]
        let sarali = up + up.reversed()
        let chunk = AVAudioFrameCount(2048)
        var phase = 0.0
        var frame = 0
        let start = Date()
        while !Thread.current.isCancelled {
            guard let buffer = AVAudioPCMBuffer(pcmFormat: mono, frameCapacity: chunk), let data = buffer.floatChannelData?[0] else { return }
            buffer.frameLength = chunk
            for i in 0..<Int(chunk) {
                let t = Double(frame) / mono.sampleRate
                let hz = saHz * sarali[Int(t / 0.5) % sarali.count] * pow(2, cents / 1200)
                phase += 2 * .pi * hz / mono.sampleRate
                var v = 0.0
                for h in 1...6 { v += sin(Double(h) * phase) / Double(h) }
                data[i] = Float(0.2 * v / 2.45)
                frame += 1
            }
            write(buffer, mono: mono)
            let due = Double(frame) / mono.sampleRate - Date().timeIntervalSince(start)
            if due > 0 { Thread.sleep(forTimeInterval: due) }
        }
    }
    #endif

    private func write(_ buffer: AVAudioPCMBuffer, mono: AVAudioFormat) {
        let n = buffer.frameLength
        guard n > 0, let channels = buffer.floatChannelData,
              let out = AVAudioPCMBuffer(pcmFormat: mono, frameCapacity: n), let dest = out.floatChannelData?[0] else { return }
        out.frameLength = n
        let count = Int(buffer.format.channelCount)
        var sum: Float = 0
        for i in 0..<Int(n) {
            var v: Float = 0
            for c in 0..<count { v += channels[c][i] }
            v /= Float(count)
            dest[i] = v
            sum += v * v
        }
        let rms = sqrt(sum / Float(n))
        let (seconds, reachedLimit): (Double, Bool) = lock.withLock {
            guard let file else { return (0, false) }
            do { try file.write(from: out) } catch { return (0, false) }
            frames += AVAudioFramePosition(n)
            let s = Double(frames) / sampleRate
            let reached = s >= maxSeconds && !limitReported
            if reached { limitReported = true }
            return (s, reached)
        }
        guard seconds > 0 else { return }
        onLevel(rms, seconds)
        if reachedLimit { onLimit() }
    }

    /// Stops and closes the file; the seconds recorded.
    func stop() -> Double {
        synthetic?.cancel()
        synthetic = nil
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        let seconds: Double = lock.withLock {
            let s = Double(frames) / sampleRate
            file = nil // closing the AVAudioFile finishes the .m4a
            return s
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return seconds
    }

    enum RecorderError: Error { case noInput }
}

/// Decodes a saved recording and analyses its pitch with the shared
/// `RecordingAnalyzer` (docs/plan-recordings.md). Blocking: call off the main thread.
enum RecordingDecoder {
    static func analyse(url: URL, onProgress: (Double) -> Void) throws -> PitchTrack {
        let file = try AVAudioFile(forReading: url)
        let format = file.processingFormat
        let rate = format.sampleRate
        let supervisor = NeuralPitchSupervisor(captureSampleRate: Int32(rate))
        let neural: ((KotlinFloatArray) -> NeuralPitchResult?)? = supervisor.map { s in { window in s.estimate(window) } }
        let analyzer = RecordingAnalyzer(
            sampleRate: Int32(rate), neuralInterval: RecordingAnalyzer.companion.NEURAL_EVERY_WINDOW, neural: neural
        )
        let chunk = AVAudioFrameCount(16_384)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: chunk) else { throw Recorder.RecorderError.noInput }
        var mono = [Float](repeating: 0, count: Int(chunk))
        let total = max(1, Double(file.length))
        while file.framePosition < file.length {
            try file.read(into: buffer, frameCount: chunk)
            let n = Int(buffer.frameLength)
            guard n > 0, let channels = buffer.floatChannelData else { break }
            let count = Int(format.channelCount)
            for i in 0..<n {
                var v: Float = 0
                for c in 0..<count { v += channels[c][i] }
                mono[i] = v / Float(count)
            }
            let samples = mono.withUnsafeMutableBufferPointer {
                FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer($0.baseAddress!), count: Int32(n))
            }
            analyzer.feed(samples: samples, count: Int32(n))
            onProgress(Double(file.framePosition) / total)
        }
        return analyzer.finish()
    }
}
