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
            options: RenderOptions.companion.practice(drone: true, click: true, loop: false))
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
        XCTAssertEqual(vm.catalog.count, 37)
    }
}
