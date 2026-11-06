import AVFoundation
import shared

/// Decodes the bundled harmonium notes and tanpura loops (FLAC) into a shared `SampleBank`.
enum HarmoniumSamples {
    /// Sample rate of the prepared files (tools/samples/prepare_harmonium.py).
    static let sampleRate: Double = 44_100

    enum LoadError: Error {
        case missing(String)
        case unexpectedFormat(String)
    }

    /// Decodes every note. Call off the main thread; takes a fraction of a second.
    static func load(bundle: Bundle = .main) throws -> SampleBank {
        let bankInfo = HarmoniumBank.shared
        var samples: [KotlinInt: KotlinFloatArray] = [:]
        for midi in bankInfo.MIDI_LOW...bankInfo.MIDI_HIGH {
            samples[KotlinInt(int: midi)] = try decode(path: bankInfo.assetPath(midi: midi), bundle: bundle)
        }
        var tanpura: [KotlinInt: KotlinFloatArray] = [:]
        for midi in TanpuraBank.shared.SA_MIDIS {
            tanpura[midi] = try decode(path: TanpuraBank.shared.assetPath(saMidi: midi.int32Value), bundle: bundle)
        }
        return SampleBank(sampleRate: Int32(sampleRate), samples: samples, tanpura: tanpura)
    }

    static func decode(midi: Int32, bundle: Bundle = .main) throws -> KotlinFloatArray {
        try decode(path: HarmoniumBank.shared.assetPath(midi: midi), bundle: bundle)
    }

    /// Decodes `audio/<path>` from the bundle (paths as given by HarmoniumBank / TanpuraBank).
    static func decode(path: String, bundle: Bundle = .main) throws -> KotlinFloatArray {
        guard let url = bundle.url(forResource: "audio/" + path, withExtension: nil) else {
            throw LoadError.missing(path)
        }
        let file = try AVAudioFile(forReading: url, commonFormat: .pcmFormatFloat32, interleaved: false)
        let format = file.processingFormat
        guard format.sampleRate == sampleRate, format.channelCount == 1,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(file.length)) else {
            throw LoadError.unexpectedFormat(path)
        }
        try file.read(into: buffer)
        guard let channel = buffer.floatChannelData?[0], buffer.frameLength > 0 else {
            throw LoadError.unexpectedFormat(path)
        }
        return FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer(channel), count: Int32(buffer.frameLength))
    }
}
