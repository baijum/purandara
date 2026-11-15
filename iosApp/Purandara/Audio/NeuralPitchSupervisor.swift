// Ported from ukulele-companion (same author, MIT): NeuralPitchSupervisor.swift.
// Changes: the SwiftF0 v0.3.0 model (audio, fmin and fmax in; float64 pitch
// and confidence out), as the shared `SwiftF0Model`; chunking, the silence
// gate and frame reading are shared Kotlin (docs/plan-swiftf0-upgrade.md);
// bulk copies via FloatBridge; model from the bundle's models/ folder.
import Foundation
import onnxruntime
import shared

/// Runs the SwiftF0 v0.3.0 pitch model (ONNX Runtime C API) on 16 kHz audio.
///
/// Returns nil whenever anything is unexpected, so callers fall back to YIN
/// alone. One instance per stream, used from one thread at a time.
final class NeuralPitchSupervisor: NSObject, SwiftF0Model, @unchecked Sendable {
    static let modelName = "swift_f0_v0.3.0"
    private static let inputNames = ["audio", "fmin", "fmax"]
    private static let outputNames = ["pitch", "confidence"]

    private let api: UnsafePointer<OrtApi>
    private let env: OpaquePointer
    private let session: OpaquePointer
    private let memInfo: OpaquePointer
    private var samples16k: [Float] = []

    /// `threads`: 1 for live listening (it runs beside the audio), more for analysing a recording.
    init?(threads: Int = 1, bundle: Bundle = .main) {
        guard let modelPath = bundle.path(forResource: "models/\(Self.modelName)", ofType: "onnx"),
              let apiBase = OrtGetApiBase(),
              let apiPtr = apiBase.pointee.GetApi(UInt32(ORT_API_VERSION)) else { return nil }
        api = apiPtr

        var envPtr: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateEnv(ORT_LOGGING_LEVEL_WARNING, "purandara", &envPtr)), let envPtr else { return nil }
        env = envPtr

        var optionsPtr: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateSessionOptions(&optionsPtr)), let optionsPtr else {
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        _ = api.pointee.SetInterOpNumThreads(optionsPtr, 1)
        _ = api.pointee.SetIntraOpNumThreads(optionsPtr, Int32(threads))
        var sessionPtr: OpaquePointer?
        let created = Self.ok(api, api.pointee.CreateSession(envPtr, modelPath, optionsPtr, &sessionPtr))
        api.pointee.ReleaseSessionOptions(optionsPtr)
        guard created, let sessionPtr else {
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        session = sessionPtr

        var info: OpaquePointer?
        guard Self.ok(api, api.pointee.CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &info)), let info,
              Self.names(apiPtr, sessionPtr, input: true).isSuperset(of: Self.inputNames),
              Self.names(apiPtr, sessionPtr, input: false).isSuperset(of: Self.outputNames) else {
            api.pointee.ReleaseSession(sessionPtr)
            api.pointee.ReleaseEnv(envPtr)
            return nil
        }
        memInfo = info
        super.init()
    }

    deinit {
        api.pointee.ReleaseMemoryInfo(memInfo)
        api.pointee.ReleaseSession(session)
        api.pointee.ReleaseEnv(env)
    }

    /// The model's pitch (Hz) and confidence per 16 ms frame of `audio16k`, or nil.
    func run(audio16k: KotlinFloatArray, range: PitchRange) -> SwiftF0Output? {
        let count = Int(audio16k.size)
        guard count > 0 else { return nil }
        if samples16k.count != count { samples16k = [Float](repeating: 0, count: count) }
        samples16k.withUnsafeMutableBufferPointer {
            FloatBridge.shared.write(source: audio16k, destination: UnsafeMutableRawPointer($0.baseAddress!), count: Int32(count))
        }
        var fmin = range.minHz
        var fmax = range.maxHz
        return samples16k.withUnsafeMutableBufferPointer { buffer -> SwiftF0Output? in
            guard let audio = tensor(buffer.baseAddress!, count: count, shape: [1, Int64(count)]) else { return nil }
            defer { api.pointee.ReleaseValue(audio) }
            guard let low = tensor(&fmin, count: 1, shape: []) else { return nil }
            defer { api.pointee.ReleaseValue(low) }
            guard let high = tensor(&fmax, count: 1, shape: []) else { return nil }
            defer { api.pointee.ReleaseValue(high) }

            var outputs = [OpaquePointer?](repeating: nil, count: Self.outputNames.count)
            let ran: Bool = withCStrings(Self.inputNames) { inputNames in
                withCStrings(Self.outputNames) { outNames in
                    var inputs: [OpaquePointer?] = [audio, low, high]
                    return Self.ok(api, api.pointee.Run(session, nil, inputNames, &inputs, inputs.count, outNames, outputs.count, &outputs))
                }
            }
            defer { outputs.compactMap { $0 }.forEach { api.pointee.ReleaseValue($0) } }
            guard ran, let pitchValue = outputs[0], let confidenceValue = outputs[1],
                  let pitch = floats(pitchValue), let confidence = floats(confidenceValue),
                  pitch.size == confidence.size else { return nil }
            return SwiftF0Output(hz: pitch, confidence: confidence)
        }
    }

    // MARK: - ONNX Runtime helpers

    private func tensor(_ data: UnsafeMutablePointer<Float>, count: Int, shape: [Int64]) -> OpaquePointer? {
        var value: OpaquePointer?
        var dims = shape
        let status = dims.withUnsafeMutableBufferPointer { dimsBuffer in
            api.pointee.CreateTensorWithDataAsOrtValue(
                memInfo, data, count * MemoryLayout<Float>.size, dimsBuffer.baseAddress, shape.count,
                ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &value)
        }
        return Self.ok(api, status) ? value : nil
    }

    /// A tensor's values as Kotlin floats: pitch is float64, confidence float32.
    private func floats(_ value: OpaquePointer) -> KotlinFloatArray? {
        var data: UnsafeMutableRawPointer?
        guard Self.ok(api, api.pointee.GetTensorMutableData(value, &data)), let data else { return nil }
        var info: OpaquePointer?
        _ = api.pointee.GetTensorTypeAndShape(value, &info)
        guard let info else { return nil }
        defer { api.pointee.ReleaseTensorTypeAndShapeInfo(info) }
        var elements = 0
        var type = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED
        _ = api.pointee.GetTensorShapeElementCount(info, &elements)
        _ = api.pointee.GetTensorElementType(info, &type)
        switch type {
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT:
            return FloatBridge.shared.toFloatArray(source: data, count: Int32(elements))
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE:
            return FloatBridge.shared.doublesToFloatArray(source: data, count: Int32(elements))
        default:
            return nil
        }
    }

    /// True when `status` is success; releases a failure status.
    private static func ok(_ api: UnsafePointer<OrtApi>, _ status: OpaquePointer?) -> Bool {
        guard let status else { return true }
        api.pointee.ReleaseStatus(status)
        return false
    }

    /// The session's input or output names.
    private static func names(_ api: UnsafePointer<OrtApi>, _ session: OpaquePointer, input: Bool) -> Set<String> {
        var allocator: UnsafeMutablePointer<OrtAllocator>?
        guard ok(api, api.pointee.GetAllocatorWithDefaultOptions(&allocator)), let allocator else { return [] }
        var count = 0
        _ = input ? api.pointee.SessionGetInputCount(session, &count) : api.pointee.SessionGetOutputCount(session, &count)
        var result = Set<String>()
        for i in 0..<count {
            var ptr: UnsafeMutablePointer<CChar>?
            let status = input
                ? api.pointee.SessionGetInputName(session, i, allocator, &ptr)
                : api.pointee.SessionGetOutputName(session, i, allocator, &ptr)
            guard ok(api, status), let ptr else { continue }
            result.insert(String(cString: ptr))
            _ = api.pointee.AllocatorFree(allocator, ptr)
        }
        return result
    }
}

/// Calls `body` with a C array of C strings that live for the call.
private func withCStrings<R>(_ strings: [String], _ body: (UnsafePointer<UnsafePointer<CChar>?>) -> R) -> R {
    let duplicated = strings.map { strdup($0) }
    defer { duplicated.forEach { free($0) } }
    let pointers = duplicated.map { UnsafePointer<CChar>($0) }
    return pointers.withUnsafeBufferPointer { body($0.baseAddress!) }
}
