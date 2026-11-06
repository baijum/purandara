// Ported from ukulele-companion (same author, MIT): NeuralPitchSupervisor.swift.
// Changes: takes Kotlin windows at the capture rate and resamples with the
// shared AudioResampler; bulk copies via FloatBridge instead of per-element
// bridging; model from the bundle's models/ folder.
import Foundation
import onnxruntime
import shared

/// Runs the SwiftF0 pitch model (ONNX Runtime C API) on microphone windows.
///
/// Returns nil whenever anything is unexpected, so callers fall back to YIN
/// alone. One instance per capture stream, used from one thread.
final class NeuralPitchSupervisor: @unchecked Sendable {
    static let minFreqHz = 46.875
    static let maxFreqHz = 2093.75
    static let minConfidence = 0.50

    private let api: UnsafePointer<OrtApi>
    private let env: OpaquePointer
    private let session: OpaquePointer
    private let inputName: String
    private let outputNames: [String]
    private let resampler: AudioResampler
    private var samples16k: [Float] = []

    init?(captureSampleRate: Int32, bundle: Bundle = .main) {
        guard let modelPath = bundle.path(forResource: "models/swift_f0_model", ofType: "onnx"),
              let apiBase = OrtGetApiBase(),
              let apiPtr = apiBase.pointee.GetApi(UInt32(ORT_API_VERSION)) else { return nil }
        api = apiPtr
        resampler = AudioResampler(sourceRate: captureSampleRate, targetRate: AudioResampler.companion.TARGET_RATE)

        var envPtr: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateEnv(ORT_LOGGING_LEVEL_WARNING, "purandara", &envPtr)), let envPtr else { return nil }
        env = envPtr

        var optionsPtr: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateSessionOptions(&optionsPtr)), let optionsPtr else {
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        _ = api.pointee.SetInterOpNumThreads(optionsPtr, 1)
        _ = api.pointee.SetIntraOpNumThreads(optionsPtr, 1)
        var sessionPtr: OpaquePointer?
        let created = Self.ok(api, api.pointee.CreateSession(envPtr, modelPath, optionsPtr, &sessionPtr))
        api.pointee.ReleaseSessionOptions(optionsPtr)
        guard created, let sessionPtr else {
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        session = sessionPtr

        var allocator: UnsafeMutablePointer<OrtAllocator>?
        _ = api.pointee.GetAllocatorWithDefaultOptions(&allocator)
        guard let allocator,
              let input = Self.name(apiPtr, allocator, { apiPtr.pointee.SessionGetInputName(sessionPtr, 0, allocator, &$0) }) else {
            api.pointee.ReleaseSession(sessionPtr)
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        inputName = input
        var count = 0
        _ = api.pointee.SessionGetOutputCount(sessionPtr, &count)
        outputNames = (0..<min(count, 2)).compactMap { i in
            Self.name(apiPtr, allocator) { apiPtr.pointee.SessionGetOutputName(sessionPtr, i, allocator, &$0) }
        }
        guard !outputNames.isEmpty else {
            api.pointee.ReleaseSession(sessionPtr)
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
    }

    deinit {
        api.pointee.ReleaseSession(session)
        api.pointee.ReleaseEnv(env)
    }

    /// SwiftF0's estimate for the middle of `window` (capture-rate samples), or nil.
    func estimate(_ window: KotlinFloatArray) -> NeuralPitchResult? {
        let down = resampler.downsample(input: window)
        let count = Int(down.size)
        guard count > 0 else { return nil }
        if samples16k.count != count { samples16k = [Float](repeating: 0, count: count) }
        samples16k.withUnsafeMutableBufferPointer {
            FloatBridge.shared.write(source: down, destination: UnsafeMutableRawPointer($0.baseAddress!), count: Int32(count))
        }

        var memInfo: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memInfo)), let memInfo else { return nil }
        defer { api.pointee.ReleaseMemoryInfo(memInfo) }

        return samples16k.withUnsafeMutableBufferPointer { buffer -> NeuralPitchResult? in
            var shape: [Int64] = [1, Int64(count)]
            var input: OpaquePointer?
            guard Self.ok(api, api.pointee.CreateTensorWithDataAsOrtValue(
                memInfo, buffer.baseAddress, count * MemoryLayout<Float>.size, &shape, 2,
                ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &input)), let input else { return nil }
            defer { api.pointee.ReleaseValue(input) }

            var outputs = [OpaquePointer?](repeating: nil, count: outputNames.count)
            let ran: Bool = withCStrings([inputName]) { inputNames in
                withCStrings(outputNames) { outNames in
                    var inputs: [OpaquePointer?] = [input]
                    return Self.ok(api, api.pointee.Run(session, nil, inputNames, &inputs, 1, outNames, outputNames.count, &outputs))
                }
            }
            defer { outputs.compactMap { $0 }.forEach { api.pointee.ReleaseValue($0) } }
            guard ran, let pitchValue = outputs[0], let pitch = floats(pitchValue), !pitch.isEmpty else { return nil }
            let confidence = outputs.count > 1 ? outputs[1].flatMap(floats) : nil

            let idx = pitch.count / 2
            let hz = Double(pitch[idx])
            let conf = confidence.flatMap { $0.isEmpty ? nil : Double($0[min(idx, $0.count - 1)]) } ?? 0
            guard hz >= Self.minFreqHz, hz <= Self.maxFreqHz, conf >= Self.minConfidence else { return nil }
            return NeuralPitchResult(frequencyHz: hz, confidence: conf)
        }
    }

    // MARK: - ONNX Runtime helpers

    private func floats(_ value: OpaquePointer) -> [Float]? {
        var data: UnsafeMutableRawPointer?
        guard Self.ok(api, api.pointee.GetTensorMutableData(value, &data)), let data else { return nil }
        var info: OpaquePointer?
        _ = api.pointee.GetTensorTypeAndShape(value, &info)
        guard let info else { return nil }
        defer { api.pointee.ReleaseTensorTypeAndShapeInfo(info) }
        var elements = 0
        _ = api.pointee.GetTensorShapeElementCount(info, &elements)
        guard elements > 0 else { return nil }
        return Array(UnsafeBufferPointer(start: data.assumingMemoryBound(to: Float.self), count: elements))
    }

    /// True when `status` is success; releases a failure status.
    private static func ok(_ api: UnsafePointer<OrtApi>, _ status: OpaquePointer?) -> Bool {
        guard let status else { return true }
        api.pointee.ReleaseStatus(status)
        return false
    }

    private static func name(
        _ api: UnsafePointer<OrtApi>, _ allocator: UnsafeMutablePointer<OrtAllocator>,
        _ get: (inout UnsafeMutablePointer<CChar>?) -> OpaquePointer?
    ) -> String? {
        var ptr: UnsafeMutablePointer<CChar>?
        guard ok(api, get(&ptr)), let ptr else { return nil }
        defer { _ = api.pointee.AllocatorFree(allocator, ptr) }
        return String(cString: ptr)
    }
}

/// Calls `body` with a C array of C strings that live for the call.
private func withCStrings<R>(_ strings: [String], _ body: (UnsafePointer<UnsafePointer<CChar>?>) -> R) -> R {
    let duplicated = strings.map { strdup($0) }
    defer { duplicated.forEach { free($0) } }
    let pointers = duplicated.map { UnsafePointer<CChar>($0) }
    return pointers.withUnsafeBufferPointer { body($0.baseAddress!) }
}
