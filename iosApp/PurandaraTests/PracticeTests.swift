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

    @MainActor func testPlaybackAfterRecordingWithoutEchoCancellationStarts() throws {
        let player = VarisaiAudioPlayer()
        defer { player.stopImmediately() }
        let mic = VarisaiAudioPlayer.MicRequest(echoCancellation: false) { _, _ in }
        do {
            try player.play(drone(), mic: mic) {}
        } catch {
            throw XCTSkip("no microphone input here: \(error)")
        }
        // The engine that recorded must not be reused for `.playback` (it fails to start).
        XCTAssertNoThrow(try player.play(drone()) {})
        XCTAssertTrue(player.isPlaying)
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback)
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
        XCTAssertEqual(s.articulation, .connected)
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
        s.articulation = .lightlySeparated
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

    func testRecoveryAndModeDefaultsRoundTripAndFallback() {
        let store = PracticeSettingsStore(defaults: defaults)
        XCTAssertEqual(store.load().practiceMode, .normal)
        XCTAssertEqual(store.load().recoveryMode, .continuous)
        XCTAssertEqual(store.load().recoverySeconds, 5)
        XCTAssertFalse(store.load().responseGuide)
        for mode in PracticeSettings.ModeChoice.allCases {
            for recovery in PracticeSettings.RecoveryChoice.allCases {
                var s = PracticeSettings()
                s.practiceMode = mode
                s.recoveryMode = recovery
                s.recoverySeconds = 30
                s.responseGuide = true
                store.save(s)
                XCTAssertEqual(store.load(), s)
            }
        }
        defaults.set("unknown", forKey: "practice.mode")
        defaults.set("unknown", forKey: "practice.recoveryMode")
        defaults.set(31, forKey: "practice.recoverySeconds")
        XCTAssertEqual(store.load().practiceMode, .normal)
        XCTAssertEqual(store.load().recoveryMode, .continuous)
        XCTAssertEqual(store.load().recoverySeconds, 5)
    }

    func testTransportSelectionAndModelGuideAreIndependentOfFeedback() {
        var s = PracticeSettings()
        XCTAssertFalse(s.usesTransport)
        s.recoveryMode = .whenReady
        XCTAssertFalse(s.usesTransport, "recovery has no effect with repeat off")
        s.loop = true
        XCTAssertTrue(s.usesTransport)
        s.loop = false
        s.practiceMode = .listenRepeat
        s.singAlong = true
        s.harmoniumGuide = false
        XCTAssertTrue(s.usesTransport)
        XCTAssertEqual(s.transportOptions().harmoniumGain,
                       RenderOptions.companion.practice(drone: s.drone, click: s.click, loop: false,
                           countIn: s.countIn, harmoniumLevel: s.harmoniumLevel, droneLevel: s.droneLevel,
                           articulation: s.articulation.kotlin).harmoniumGain)
        XCTAssertFalse(s.policy(feedback: true).responseGuide)
        XCTAssertFalse(s.policy(feedback: true).repeat)
        s.responseGuide = true
        XCTAssertTrue(s.policy(feedback: false).responseGuide)
        XCTAssertFalse(s.policy(feedback: false).feedback)
    }

    func testArticulationRoundTrip() {
        let store = PracticeSettingsStore(defaults: defaults)
        for choice in PracticeSettings.ArticulationChoice.allCases {
            var s = PracticeSettings()
            s.articulation = choice
            store.save(s)
            XCTAssertEqual(defaults.string(forKey: "practice.articulation"), choice.rawValue)
            XCTAssertEqual(store.load().articulation, choice)
        }
    }

    func testInvalidArticulationFallsBackToConnected() {
        defaults.set("unknown", forKey: "practice.articulation")
        XCTAssertEqual(PracticeSettingsStore(defaults: defaults).load().articulation, .connected)
    }

    func testRenderOptionsUseArticulation() {
        var s = PracticeSettings()
        XCTAssertEqual(s.renderOptions().articulation, .connected)
        s.articulation = .lightlySeparated
        XCTAssertEqual(s.renderOptions().articulation, .lightlySeparated)
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

    private func waitForRecovery(_ vm: PracticeViewModel) async throws {
        let deadline = Date().addingTimeInterval(15)
        while !vm.playback.recovering && Date() < deadline {
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertTrue(vm.playback.recovering)
    }

    private func recoveryModel(_ route: TestOutputRoute) throws -> (PracticeViewModel, VarisaiAudioPlayer, Varisai) {
        let player = VarisaiAudioPlayer(routeOutputs: { route.outputs })
        let vm = model(player, bank: try HarmoniumSamples.load())
        vm.settings.tempo = PracticeSettings.tempoRange.upperBound
        vm.settings.kala = 4
        vm.settings.countIn = false
        vm.settings.loop = true
        vm.settings.recoveryMode = .whenReady
        return (vm, player, try XCTUnwrap(vm.catalog.first { $0.id == "sarali-1" }))
    }

    func testRecoveryBackgroundRequiresContinueAndStopCancelsIt() async throws {
        let (vm, player, piece) = try recoveryModel(TestOutputRoute())
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        vm.onBackgrounded()
        XCTAssertFalse(player.isPlaying)
        vm.onForegrounded()
        XCTAssertTrue(vm.practicePaused)
        XCTAssertFalse(player.isPlaying)
        vm.onBackgrounded()
        vm.onForegrounded()
        XCTAssertTrue(vm.practicePaused, "another background trip preserves explicit Continue")
        XCTAssertFalse(player.isPlaying)
        vm.continuePractice()
        XCTAssertTrue(player.isPlaying)
        XCTAssertFalse(vm.practicePaused)
        try await waitForRecovery(vm)
        vm.onBackgrounded()
        vm.stopImmediately()
        vm.onForegrounded()
        vm.continuePractice()
        XCTAssertFalse(player.isPlaying)
    }

    func testRecoveryOutputChangeAndSaChangeCancelContinuation() async throws {
        let route = TestOutputRoute()
        let (vm, player, piece) = try recoveryModel(route)
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        try await changeOutput(route)
        vm.continuePractice()
        XCTAssertFalse(player.isPlaying)
        XCTAssertTrue(vm.outputChanged)
        vm.play(piece)
        try await waitForRecovery(vm)
        vm.settings.saCents = 1
        vm.continuePractice()
        XCTAssertFalse(player.isPlaying)
        XCTAssertFalse(vm.practicePaused)
    }

    func testSyntheticRecoveryScoresTwoAttemptsWithFreshCountInAndOneHistoryCount() async throws {
        UserDefaults.standard.set("0", forKey: "singer")
        defer { UserDefaults.standard.removeObject(forKey: "singer") }
        let (vm, player, piece) = try recoveryModel(TestOutputRoute())
        vm.settings.singAlong = true
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        XCTAssertEqual(vm.singAlong.lastSummary?.pass, 1)
        let first = try XCTUnwrap(player.practiceSnapshot()?.segments.first)
        XCTAssertTrue(first.scored)
        XCTAssertGreaterThan(first.countInFrames, 0, "scoring forces calibration even when count-in setting is off")
        vm.continuePractice()
        let deadline = Date().addingTimeInterval(20)
        while (vm.singAlong.lastSummary?.pass ?? 0) < 2 && Date() < deadline {
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertEqual(vm.singAlong.lastSummary?.pass, 2)
        let segments = try XCTUnwrap(player.practiceSnapshot()).segments
        XCTAssertEqual(segments.count, 2)
        XCTAssertGreaterThan(segments[1].countInFrames, 0)
        XCTAssertGreaterThan(segments[1].startFrame, first.endFrame)
        XCTAssertEqual(vm.history.get(varisaiId: piece.id)?.timesPracticed, 1)
        XCTAssertTrue(player.isPlaying, "tanpura transport stays alive during recovery")
    }

    func testListenRepeatWithoutFeedbackRunsOnePairAndNeverRequestsCapture() async throws {
        let route = TestOutputRoute()
        let (vm, player, piece) = try recoveryModel(route)
        vm.settings.loop = false
        vm.settings.practiceMode = .listenRepeat
        vm.settings.recoveryMode = .continuous
        vm.play(piece)
        defer { vm.stopImmediately() }
        let deadline = Date().addingTimeInterval(20)
        var lastSegments: [PracticeSegment] = []
        while player.isPlaying && Date() < deadline {
            if let snapshot = player.practiceSnapshot() { lastSegments = snapshot.segments }
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertFalse(player.isPlaying)
        XCTAssertEqual(lastSegments.count, 2)
        XCTAssertTrue(lastSegments[0].listening)
        XCTAssertTrue(lastSegments[1].response)
        XCTAssertFalse(lastSegments.contains { $0.scored })
        XCTAssertEqual(lastSegments[1].countInFrames, 0, "unscored response honors count-in off")
        XCTAssertNil(vm.singAlong.lastSummary)
        XCTAssertEqual(vm.history.get(varisaiId: piece.id)?.timesPracticed, 1)
    }

    func testRepeatOffDuringNormalRecoveryCancelsFutureAttempts() async throws {
        let (vm, player, piece) = try recoveryModel(TestOutputRoute())
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        vm.settings.loop = false
        vm.continuePractice()
        XCTAssertFalse(player.isPlaying)
        XCTAssertFalse(vm.practicePaused)
        vm.onBackgrounded()
        vm.onForegrounded()
        XCTAssertFalse(player.isPlaying)
    }

    func testRecoverySwitchToContinuousWaitsThenUsesSeamlessRenderer() async throws {
        let (vm, player, piece) = try recoveryModel(TestOutputRoute())
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        vm.settings.recoveryMode = .continuous
        XCTAssertTrue(vm.practicePaused)
        XCTAssertFalse(player.isPlaying)
        vm.continuePractice()
        XCTAssertTrue(player.isPlaying)
        XCTAssertNil(player.practiceSnapshot(), "continuous repeat uses the existing seamless renderer")
    }

    func testRecoveryScheduleChangeWaitsForExplicitFreshContinue() async throws {
        let (vm, player, piece) = try recoveryModel(TestOutputRoute())
        vm.play(piece)
        defer { vm.stopImmediately() }
        try await waitForRecovery(vm)
        vm.settings.tempo -= 5
        XCTAssertTrue(vm.practicePaused)
        XCTAssertFalse(player.isPlaying)
        vm.continuePractice()
        XCTAssertTrue(player.isPlaying)
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

final class PitchListenerDrainTests: XCTestCase {
    private final class Clock: @unchecked Sendable {
        let lock = NSLock()
        var active = true
        var frames = 0

        func time() -> Double? { lock.lock(); defer { lock.unlock() }; return active ? 1 : nil }
        func stopped() { lock.lock(); active = false; lock.unlock() }
        func record() { lock.lock(); frames += 1; lock.unlock() }
    }

    func testAdmittedMicWindowsDrainAfterOutputClockStops() throws {
        let clock = Clock()
        let listener = PitchListener(timeOf: { _ in clock.time() }, synthetic: nil,
                                     supervisorFactory: { _ in nil }) { _ in clock.record() }
        let request = try XCTUnwrap(listener.micRequest(echoCancellation: false))
        let format = try XCTUnwrap(AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1))
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 8192))
        buffer.frameLength = 8192
        buffer.floatChannelData![0].initialize(repeating: 0, count: 8192)
        listener.queue.suspend()
        request.onBuffer(buffer, AVAudioTime(hostTime: mach_absolute_time()))
        clock.stopped()
        listener.queue.resume()
        listener.stop {}
        XCTAssertGreaterThan(clock.frames, 0, "admitted windows keep their mapped time after audio teardown")
        let count = clock.frames
        request.onBuffer(buffer, AVAudioTime(hostTime: mach_absolute_time()))
        listener.stop {}
        XCTAssertEqual(clock.frames, count, "stale/new buffers after output stop are not analyzed")
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
