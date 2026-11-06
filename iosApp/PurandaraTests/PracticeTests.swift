import XCTest
import shared
@testable import Purandara

final class HarmoniumSamplesTests: XCTestCase {
    func testDecodesEveryNoteFromTheAppBundle() throws {
        let bank = try HarmoniumSamples.load()
        for midi in HarmoniumBank.shared.MIDI_LOW...HarmoniumBank.shared.MIDI_HIGH {
            XCTAssertTrue(bank.contains(midi: midi), "missing MIDI \(midi)")
        }
        let c3 = try HarmoniumSamples.decode(midi: 48)
        XCTAssertEqual(Double(c3.size) / HarmoniumSamples.sampleRate, 2.5, accuracy: 0.01)
    }

    func testRendersAudibleUnclippedAudioFromRealSamples() throws {
        let bank = try HarmoniumSamples.load()
        let varisai = try XCTUnwrap(VarisaiCatalog.shared.byId(id: "plain-scale-1"))
        let settings = PracticeSettings().performance()
        let renderer = SampleRenderer(
            bank: bank,
            schedule: Schedule.companion.of(varisai: varisai, settings: settings),
            options: PracticeSettings().renderOptions())
        let out = KotlinFloatArray(size: Int32(HarmoniumSamples.sampleRate))
        XCTAssertEqual(renderer.render(out: out, offset: 0, length: out.size), out.size)
        var peak: Float = 0
        for i in 0..<out.size { peak = max(peak, abs(out.get(index: i))) }
        XCTAssertGreaterThan(peak, 0.05)
        XCTAssertLessThan(peak, 1.0)
    }

    func testFloatBridgeRoundTrip() {
        var values: [Float] = [0.25, -0.5, 1.0]
        let array = values.withUnsafeMutableBufferPointer {
            FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer($0.baseAddress!), count: 3)
        }
        XCTAssertEqual(array.get(index: 1), -0.5)
        var out = [Float](repeating: 0, count: 3)
        out.withUnsafeMutableBufferPointer {
            FloatBridge.shared.write(source: array, destination: UnsafeMutableRawPointer($0.baseAddress!), count: 3)
        }
        XCTAssertEqual(out, values)
        values.removeAll()
    }
}

final class PracticeSettingsStoreTests: XCTestCase {
    private var defaults: UserDefaults!
    private let suite = "PracticeSettingsStoreTests"

    override func setUp() {
        defaults = UserDefaults(suiteName: suite)
        defaults.removePersistentDomain(forName: suite)
    }

    func testDefaultsMatchTheWorkbook() {
        let s = PracticeSettingsStore(defaults: defaults).load()
        XCTAssertEqual(s.saMidi, 48) // C3
        XCTAssertEqual(s.tempo, 60)
        XCTAssertEqual(s.kala, 1)
        XCTAssertTrue(s.drone)
        XCTAssertEqual(s.tuning, .just)
        XCTAssertTrue(s.countIn)
        XCTAssertEqual(s.harmoniumLevel, 0.75)
        XCTAssertEqual(s.droneLevel, 0.5)
    }

    func testRoundTrip() {
        let store = PracticeSettingsStore(defaults: defaults)
        var s = PracticeSettings()
        s.saMidi = 57
        s.tempo = 75
        s.kala = 3
        s.click = true
        s.loop = true
        s.tuning = .equal
        s.countIn = false
        s.harmoniumLevel = 0.3
        s.droneLevel = 0.9
        s.singAlong = true
        s.harmoniumGuide = false
        s.echoCancellation = false
        s.strictness = .gentle
        s.saCents = -12
        store.save(s)
        XCTAssertEqual(store.load(), s)
    }

    func testOutOfRangeValuesFallBack() {
        defaults.set(99, forKey: "practice.saMidi")
        defaults.set(500, forKey: "practice.tempo")
        defaults.set(9, forKey: "practice.kala")
        defaults.set("bogus", forKey: "practice.tuning")
        XCTAssertEqual(PracticeSettingsStore(defaults: defaults).load(), PracticeSettings())
    }

    func testTanpuraLoopsAreDecodedAndUsed() throws {
        let bank = try HarmoniumSamples.load()
        XCTAssertTrue(bank.hasTanpura)
        XCTAssertEqual(PracticeSettings().renderOptions().droneStyle, .tanpura)
    }

    func testSingAlongForcesCountInAndGuideOffSilencesHarmonium() {
        var s = PracticeSettings()
        s.countIn = false
        s.singAlong = true
        s.harmoniumGuide = false
        let options = s.renderOptions()
        XCTAssertTrue(options.countIn)
        XCTAssertEqual(options.harmoniumGain, 0)
    }

    func testPerformanceSettingsUseSaFrequency() {
        var s = PracticeSettings()
        s.saMidi = 57
        XCTAssertEqual(s.performance().saHz, 220.0, accuracy: 1e-9)
    }
}

@MainActor
final class PracticeViewModelTests: XCTestCase {
    func testSettingsChangesArePersisted() {
        let defaults = UserDefaults(suiteName: "PracticeViewModelTests")!
        defaults.removePersistentDomain(forName: "PracticeViewModelTests")
        let store = PracticeSettingsStore(defaults: defaults)
        let vm = PracticeViewModel(store: store, loadSamples: false)
        vm.settings.tempo = 90
        XCTAssertEqual(store.load().tempo, 90)
        XCTAssertFalse(vm.canPlay)
        XCTAssertEqual(vm.catalog.count, 48)
    }
}

final class NeuralPitchSupervisorTests: XCTestCase {
    private func voice(_ f0: Double, rate: Double, count: Int = 4096) -> KotlinFloatArray {
        var samples = [Float](repeating: 0, count: count)
        for i in 0..<count {
            var v = 0.0
            for h in 1...8 { v += sin(2 * Double.pi * Double(h) * f0 * Double(i) / rate) / Double(h) }
            samples[i] = Float(0.3 * v / 2.7)
        }
        return samples.withUnsafeMutableBufferPointer {
            FloatBridge.shared.toFloatArray(source: UnsafeMutableRawPointer($0.baseAddress!), count: Int32(count))
        }
    }

    func testRunsTheBundledModelAcrossTheRange() throws {
        for rate in [44_100.0, 48_000.0] {
            let supervisor = try XCTUnwrap(NeuralPitchSupervisor(captureSampleRate: Int32(rate)), "model failed to load")
            for f0 in [98.0, 146.83, 220.0, 392.0, 659.26] {
                let result = try XCTUnwrap(supervisor.estimate(voice(f0, rate: rate)), "\(f0) Hz at \(rate)")
                let cents = 1200 * log2(result.frequencyHz / f0)
                XCTAssertLessThan(abs(cents), 100, "\(f0) Hz read \(result.frequencyHz)")
                XCTAssertGreaterThanOrEqual(result.confidence, NeuralPitchSupervisor.minConfidence)
            }
        }
    }

    func testSilenceGivesNoEstimate() throws {
        let supervisor = try XCTUnwrap(NeuralPitchSupervisor(captureSampleRate: 44_100))
        XCTAssertNil(supervisor.estimate(KotlinFloatArray(size: 4096)))
    }
}
