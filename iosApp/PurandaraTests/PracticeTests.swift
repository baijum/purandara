import AVFoundation
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

private final class TestOutputRoute: @unchecked Sendable {
    // Tests mutate on the main actor and post notifications synchronously there.
    var outputs = ["speaker"]
}

final class PlaybackRecoveryTests: XCTestCase {
    func testPlainPlaybackExplicitlySelectsPlaybackInsteadOfTheDefaultCategory() {
        XCTAssertEqual(VarisaiAudioPlayer.sessionCategory(recording: false), .playback)
        XCTAssertNotEqual(VarisaiAudioPlayer.sessionCategory(recording: false), .soloAmbient)
        XCTAssertEqual(VarisaiAudioPlayer.sessionCategory(recording: true), .playAndRecord)
    }

    func testOutputIdentityChangeStopsRegardlessOfDeviceTypeOrReason() {
        for reason: AVAudioSession.RouteChangeReason in [.newDeviceAvailable, .oldDeviceUnavailable, .override, .routeConfigurationChange] {
            XCTAssertTrue(PlaybackRecovery.routeNeedsStop(reason: reason, outputsChanged: true))
        }
    }

    func testCategoryAndInputOnlyChangesDoNotStop() {
        XCTAssertFalse(PlaybackRecovery.routeNeedsStop(reason: .categoryChange, outputsChanged: true))
        for reason: AVAudioSession.RouteChangeReason in [.newDeviceAvailable, .oldDeviceUnavailable, .routeConfigurationChange] {
            XCTAssertFalse(PlaybackRecovery.routeNeedsStop(reason: reason, outputsChanged: false))
        }
    }

    func testRelatedRequestsAreCoalesced() throws {
        var recovery = PlaybackRecovery()
        recovery.start()
        let ticket = try XCTUnwrap(recovery.request())
        XCTAssertNil(recovery.request())
        XCTAssertTrue(recovery.take(ticket))
        XCTAssertFalse(recovery.take(ticket))
        XCTAssertNil(recovery.request())
    }

    private func drone() throws -> SampleRenderer {
        let varisai = try XCTUnwrap(VarisaiCatalog.shared.byId(id: "plain-scale-1"))
        return SampleRenderer(
            bank: try HarmoniumSamples.load(),
            schedule: Schedule.companion.of(varisai: varisai, settings: PracticeSettings().performance()),
            options: RenderOptions.companion.droneOnly(droneLevel: 0))
    }

    private func postRouteChange() {
        NotificationCenter.default.post(
            name: AVAudioSession.routeChangeNotification, object: nil,
            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.newDeviceAvailable.rawValue])
    }

    @MainActor func testPlaybackCorrectsTheActualDefaultSessionCategory() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setActive(false)
        try session.setCategory(.soloAmbient)
        let player = VarisaiAudioPlayer()
        defer { player.stopImmediately() }
        try player.play(drone()) {}
        XCTAssertEqual(session.category, .playback)
    }

    @MainActor func testOutputAndEngineNotificationsFinishOnceWithoutRestartInEitherOrder() async throws {
        let renderer = try drone()
        for routeFirst in [true, false] {
            let engine = AVAudioEngine()
            let route = TestOutputRoute()
            let player = VarisaiAudioPlayer(engine: engine, routeOutputs: { route.outputs })
            var restarts = 0
            var finishes = 0
            player.onRestartNeeded = { restarts += 1 }
            try player.play(renderer) { finishes += 1 }
            defer { player.stopImmediately() }
            // The old configuration handler stopped plain playback outside its
            // two-second own-category-change window.
            try await Task.sleep(for: .milliseconds(2100))
            engine.stop()
            route.outputs = ["bluetooth"]
            if routeFirst { postRouteChange() }
            NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: engine)
            if !routeFirst { postRouteChange() }
            try await Task.sleep(for: .milliseconds(350))
            XCTAssertEqual(restarts, 0, "route first: \(routeFirst)")
            XCTAssertEqual(finishes, 1)
            XCTAssertFalse(player.isPlaying)
            postRouteChange()
            try await Task.sleep(for: .milliseconds(200))
            XCTAssertEqual(finishes, 1)
        }
    }

    @MainActor func testStopAndOutputChangeFinishOnlyOnce() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        var restarts = 0
        let finished = expectation(description: "Stop finishes")
        player.onRestartNeeded = { restarts += 1 }
        try player.play(drone()) { finished.fulfill() }
        defer { player.stopImmediately() }
        route.outputs = ["bluetooth"]
        postRouteChange()
        await Task.yield()
        player.stop()
        await fulfillment(of: [finished], timeout: 3)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(restarts, 0)
        XCTAssertFalse(player.isPlaying)
    }

    @MainActor func testEngineStoppingAfterUserStopStillFinishes() async throws {
        let engine = AVAudioEngine()
        let player = VarisaiAudioPlayer(engine: engine)
        var restarts = 0
        let finished = expectation(description: "stopped engine finishes the fade")
        player.onRestartNeeded = { restarts += 1 }
        try player.play(drone()) { finished.fulfill() }
        defer { player.stopImmediately() }
        player.stop()
        engine.stop()
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: engine)
        await fulfillment(of: [finished], timeout: 3)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertFalse(player.isPlaying)
        XCTAssertEqual(restarts, 0)
    }

    @MainActor func testInternalConfigurationRecoversButOutputChangeCancelsPendingRecovery() async throws {
        let renderer = try drone()
        let engine = AVAudioEngine()
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(engine: engine, routeOutputs: { route.outputs })
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.soloAmbient)
        var restarts = 0
        var finishes = 0
        player.onRestartNeeded = { restarts += 1 }
        try player.play(renderer) { finishes += 1 }
        defer { player.stopImmediately() }
        engine.stop()
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: engine)
        try await Task.sleep(for: .milliseconds(350))
        XCTAssertEqual(restarts, 1, "an internal category rebuild still recovers")
        try session.setCategory(.soloAmbient)
        try player.play(renderer) { finishes += 1 }
        engine.stop()
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: engine)
        try await Task.sleep(for: .milliseconds(30))
        route.outputs = ["headphones-2"]
        postRouteChange()
        try await Task.sleep(for: .milliseconds(350))
        XCTAssertEqual(restarts, 1, "output change cancels the queued internal restart")
        XCTAssertEqual(finishes, 1)
        XCTAssertFalse(player.isPlaying)
    }

    @MainActor func testARebuildThatChangedNothingRecoversWithoutAnOutputChange() async throws {
        let engine = AVAudioEngine()
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(engine: engine, routeOutputs: { route.outputs })
        var restarts = 0
        var finishes = 0
        var outputChanges = 0
        player.onRestartNeeded = { restarts += 1 }
        player.onOutputChanged = { outputChanges += 1 }
        try player.play(drone()) { finishes += 1 }
        defer { player.stopImmediately() }
        try await Task.sleep(for: .milliseconds(2100)) // past the own-reconfiguration window
        engine.stop()
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: engine)
        try await Task.sleep(for: .milliseconds(350))
        XCTAssertEqual(restarts, 1)
        XCTAssertEqual(outputChanges, 0)
        XCTAssertEqual(finishes, 0)
    }

    func testRebuildRecoveriesAreLimitedWithinTheirWindow() {
        var limiter = RebuildLimiter()
        for t in 0..<RebuildLimiter.maxRecoveries { XCTAssertTrue(limiter.allow(now: CFTimeInterval(t))) }
        XCTAssertFalse(limiter.allow(now: 5), "a rebuild loop stops")
        XCTAssertTrue(limiter.allow(now: RebuildLimiter.window + 0.5), "the oldest has left the window")
    }

    @MainActor func testAnIdleRouteChangeOnlyNotifies() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        var outputChanges = 0
        player.onOutputChanged = { outputChanges += 1 }
        route.outputs = ["bluetooth"]
        postRouteChange()
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(outputChanges, 1, "the view model decides whether a pending resume is cancelled")
        XCTAssertFalse(player.isPlaying)
    }

    func testIdleAndStopDuringRecoveryDoNotRestart() throws {
        var recovery = PlaybackRecovery()
        XCTAssertNil(recovery.request())
        recovery.start()
        let ticket = try XCTUnwrap(recovery.request())
        recovery.stop()
        XCTAssertFalse(recovery.take(ticket))
        XCTAssertNil(recovery.request())
    }

    func testOldRecoveryCannotAffectANewPlay() throws {
        var recovery = PlaybackRecovery()
        recovery.start()
        let old = try XCTUnwrap(recovery.request())
        recovery.start()
        let current = try XCTUnwrap(recovery.request())
        XCTAssertFalse(recovery.take(old))
        XCTAssertTrue(recovery.take(current))
    }

    @MainActor func testRealInterruptionAndOutputChangeFinishOnce() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        let bank = try HarmoniumSamples.load()
        let varisai = try XCTUnwrap(VarisaiCatalog.shared.byId(id: "plain-scale-1"))
        let renderer = SampleRenderer(
            bank: bank,
            schedule: Schedule.companion.of(varisai: varisai, settings: PracticeSettings().performance()),
            options: RenderOptions.companion.droneOnly(droneLevel: 0))
        let finished = expectation(description: "interruption finishes playback")
        var restarts = 0
        player.onRestartNeeded = { restarts += 1 }
        try player.play(renderer) { finished.fulfill() }
        defer { player.stopImmediately() }
        route.outputs = ["bluetooth"]
        NotificationCenter.default.post(
            name: AVAudioSession.routeChangeNotification, object: nil,
            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.newDeviceAvailable.rawValue])
        NotificationCenter.default.post(
            name: AVAudioSession.interruptionNotification, object: nil,
            userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        await fulfillment(of: [finished], timeout: 3)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertFalse(player.isPlaying)
        XCTAssertEqual(restarts, 0)
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
        XCTAssertEqual(vm.catalog.count, 59)
    }

    private func model(_ player: VarisaiAudioPlayer, bank: SampleBank) -> PracticeViewModel {
        let defaults = UserDefaults(suiteName: "PracticeRouteTests")!
        defaults.removePersistentDomain(forName: "PracticeRouteTests")
        return PracticeViewModel(store: PracticeSettingsStore(defaults: defaults), loadSamples: false, player: player, bank: bank)
    }

    private func changeOutput(_ route: TestOutputRoute) async throws {
        route.outputs = ["bluetooth"]
        NotificationCenter.default.post(
            name: AVAudioSession.routeChangeNotification, object: nil,
            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.newDeviceAvailable.rawValue])
        try await Task.sleep(for: .milliseconds(250))
    }

    func testShrutiAndFindSaStayStoppedUntilExplicitRestart() async throws {
        let bank = try HarmoniumSamples.load()
        for shruti in [true, false] {
            let route = TestOutputRoute()
            let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
            let vm = model(player, bank: bank)
            if shruti { vm.openShruti() } else { vm.openFindSa() }
            defer { vm.stopImmediately() }
            // Find my Sa without mic permission has no stream; suspend it to
            // cover a pending listening destination as well.
            if !player.isPlaying { vm.onBackgrounded() }
            try await changeOutput(route)
            XCTAssertTrue(vm.outputChanged)
            XCTAssertFalse(player.isPlaying)
            vm.settings.tempo = 90
            if shruti { vm.openShruti() } else { vm.openFindSa() }
            XCTAssertTrue(vm.outputChanged, "repeated appearance is not an explicit restart")
            vm.onBackgrounded()
            vm.onForegrounded()
            player.onRestartNeeded?()
            XCTAssertTrue(vm.outputChanged)
            XCTAssertFalse(player.isPlaying)
            vm.restartAfterOutputChange()
            XCTAssertFalse(vm.outputChanged)
            if shruti { XCTAssertTrue(player.isPlaying) }
        }
    }

    func testOutputChangeWhileBackgroundedCancelsResumeAndManualPlayWorks() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        let vm = model(player, bank: try HarmoniumSamples.load())
        let piece = try XCTUnwrap(vm.catalog.first)
        vm.play(piece)
        defer { vm.stopImmediately() }
        vm.onBackgrounded()
        try await changeOutput(route)
        vm.onForegrounded()
        XCTAssertTrue(vm.outputChanged)
        XCTAssertFalse(vm.playback.playing)
        XCTAssertFalse(player.isPlaying)
        vm.play(piece)
        XCTAssertFalse(vm.outputChanged)
        XCTAssertTrue(player.isPlaying)
        vm.onBackgrounded()
        vm.onForegrounded()
        XCTAssertTrue(player.isPlaying, "normal background resume remains supported after an explicit restart")
    }

    func testOutputChangeWhileIdleInTheBackgroundIsNotAnInterruption() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        let vm = model(player, bank: try HarmoniumSamples.load())
        vm.onBackgrounded()
        try await changeOutput(route)
        vm.onForegrounded()
        XCTAssertFalse(vm.outputChanged, "nothing was sounding, so nothing was stopped")
    }

    func testQueuedOutputChangeWinsOverSettingsRestartAndImmediateForeground() async throws {
        let bank = try HarmoniumSamples.load()
        for background in [false, true] {
            let route = TestOutputRoute()
            let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
            let vm = model(player, bank: bank)
            let piece = try XCTUnwrap(vm.catalog.first)
            vm.play(piece)
            defer { vm.stopImmediately() }
            if background { vm.onBackgrounded() }
            route.outputs = ["bluetooth"]
            NotificationCenter.default.post(
                name: AVAudioSession.routeChangeNotification, object: nil,
                userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.newDeviceAvailable.rawValue])
            // Do not yield before automatic starts: this is the notification race.
            if background { vm.onForegrounded() } else { vm.settings.tempo = 90 }
            try await Task.sleep(for: .milliseconds(250))
            XCTAssertTrue(vm.outputChanged)
            XCTAssertFalse(player.isPlaying)
            vm.play(piece)
            XCTAssertTrue(player.isPlaying)
        }
    }

    func testTanpuraReturnsToPlayAndDoesNotResumeAfterOutputChange() async throws {
        let route = TestOutputRoute()
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        let vm = model(player, bank: try HarmoniumSamples.load())
        vm.toggleTanpura()
        defer { vm.stopImmediately() }
        try await changeOutput(route)
        XCTAssertFalse(vm.tanpuraPlaying)
        vm.onBackgrounded()
        vm.onForegrounded()
        XCTAssertFalse(player.isPlaying)
        vm.toggleTanpura()
        XCTAssertTrue(vm.tanpuraPlaying)
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
