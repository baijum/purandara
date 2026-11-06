import AVFoundation
import Foundation
import shared
import UIKit

/// What the player shows while an exercise sounds.
struct PlaybackState: Equatable {
    var playing = false
    var repetition: Int?
    var avarta: Int?
    var akshara: Int?
    var slot: Int?
    /// 0-based beat within the avarta, for the tala strip.
    var beatInAvarta: Int?
    /// Passes through the exercise needed to fill whole avartas at this kala.
    var repetitions = 1
    /// True during the count-in avarta before the exercise.
    var countingIn = false
}

/// Where a mark goes in the notation.
struct MarkKey: Hashable {
    let avarta: Int
    let akshara: Int
    let slot: Int
}

/// One point of the pitch lane.
struct TraceSample {
    let pass: Int
    let secondsInPass: Double
    let centsFromSa: Double
    let zone: Zone
}

/// What the player shows for sing-along. Mirrors Android's `SingAlongState`.
struct SingAlongUI {
    var micPermitted = false
    var micDenied = false
    var onSpeaker = true
    var live: SwaraReading?
    var trace: [TraceSample] = []
    var marks: [MarkKey: NoteResult] = [:]
    /// Last completed pass (1-based) and its summary.
    var lastSummary: (pass: Int, summary: PassSummary)?
}

/// Holds practice settings, the samples and playback. Mirrors Android's `PracticeViewModel`.
/// Changing a setting while playing restarts the exercise with the new settings.
@MainActor
final class PracticeViewModel: ObservableObject {
    @Published var settings: PracticeSettings {
        didSet {
            guard settings != oldValue else { return }
            store.save(settings)
            if playback.playing, let current { play(current) }
            if tanpuraPlaying { playTanpura() }
        }
    }
    @Published private(set) var samplesLoaded = false
    @Published private(set) var loadError = false
    @Published private(set) var playback = PlaybackState()
    @Published private(set) var singAlong = SingAlongUI()
    /// What has been practised (landing page).
    @Published private(set) var history: PracticeHistory
    /// The landing page's tanpura-only drone is sounding.
    @Published private(set) var tanpuraPlaying = false

    let catalog: [Varisai] = VarisaiCatalog.shared.all

    private let store: PracticeSettingsStore
    private let player = VarisaiAudioPlayer()
    private var bank: SampleBank?
    private var current: Varisai?
    private var recentRestarts: [Date] = []
    private var renderer: SampleRenderer?
    private var schedule: Schedule?
    private var timer: Timer?
    private var engine: SingAlongEngine?
    /// The exercise playing now has been heard through one full pass (recorded once per play).
    private var practisedThisPlay = false

    /// Debug builds: launch argument `-singer <cents>` replaces the mic with a synthetic singer.
    let syntheticSingerCents: Double?

    init(store: PracticeSettingsStore = .standard, loadSamples: Bool = true) {
        self.store = store
        settings = store.load()
        history = store.loadHistory()
        #if DEBUG
        syntheticSingerCents = UserDefaults.standard.object(forKey: "singer") != nil
            ? UserDefaults.standard.double(forKey: "singer") : nil
        #else
        syntheticSingerCents = nil
        #endif
        singAlong.micPermitted = Self.micAuthorized()
        player.onRestartNeeded = { [weak self] in self?.restartPlayback() }
        guard loadSamples else { return }
        Task.detached(priority: .userInitiated) {
            do {
                let bank = try HarmoniumSamples.load()
                await MainActor.run {
                    self.bank = bank
                    self.samplesLoaded = true
                }
            } catch {
                await MainActor.run { self.loadError = true }
            }
        }
    }

    var canPlay: Bool { samplesLoaded }

    func togglePlay(_ varisai: Varisai) {
        if playback.playing { stop() } else { play(varisai) }
    }

    func play(_ varisai: Varisai) {
        guard let bank else { return }
        stopTimer()
        let schedule = Schedule.companion.of(varisai: varisai, settings: settings.performance())
        let renderer = SampleRenderer(
            bank: bank,
            schedule: schedule,
            options: settings.renderOptions())
        current = varisai
        tanpuraPlaying = false
        practisedThisPlay = false
        self.schedule = schedule
        self.renderer = renderer
        singAlong.live = nil
        singAlong.trace = []
        singAlong.marks = [:]
        singAlong.lastSummary = nil
        let listening = settings.singAlong && (singAlong.micPermitted || syntheticSingerCents != nil)
        let sa = schedule.settings.saHz
        let newEngine = listening ? SingAlongEngine(
            player: player, renderer: renderer, schedule: schedule, syntheticCents: syntheticSingerCents,
            supervisorFactory: { NeuralPitchSupervisor(captureSampleRate: $0) },
            onOutcome: { [weak self] outcome in
                DispatchQueue.main.async { self?.apply(outcome, from: renderer, saHz: sa) }
            }) : nil
        engine = newEngine
        do {
            let mic = newEngine?.micRequest(echoCancellation: settings.echoCancellation)
            try player.play(renderer, mic: mic) { [weak self] in self?.finished(renderer) }
            // Decided by the player once the session is in record mode (the route can change then).
            singAlong.onSpeaker = mic != nil ? player.micOnSpeaker : VarisaiAudioPlayer.outputIsBuiltInSpeaker()
        } catch {
            engine = nil
            stop()
            return
        }
        newEngine?.start()
        playback = PlaybackState(playing: true, repetitions: Int(schedule.repetitions))
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    // MARK: - Tanpura and history (landing page)

    /// The drone alone, at the current Sa, until stopped.
    func toggleTanpura() {
        if tanpuraPlaying { stop() } else { playTanpura() }
    }

    func stopTanpura() {
        if tanpuraPlaying { stop() }
    }

    private func playTanpura() {
        guard let bank, let first = catalog.first else { return }
        stopTimer()
        _ = engine?.stop()
        engine = nil
        current = nil
        schedule = nil
        let renderer = SampleRenderer(
            bank: bank,
            schedule: Schedule.companion.of(varisai: first, settings: settings.performance()),
            options: RenderOptions.companion.droneOnly(droneLevel: settings.droneLevel))
        self.renderer = renderer
        do {
            try player.play(renderer, mic: nil) { [weak self] in self?.finished(renderer) }
        } catch {
            finished(renderer)
            return
        }
        tanpuraPlaying = true
    }

    #if DEBUG
    /// UI tests start from an empty history (`-resetSettings YES`).
    func resetHistory() {
        history = PracticeHistory(records: [])
        store.saveHistory(history)
    }
    #endif

    private func updateHistory(_ transform: (PracticeHistory) -> PracticeHistory) {
        let next = transform(history)
        guard next != history else { return }
        history = next
        store.saveHistory(next)
    }

    private static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Fades out; the state returns to stopped once the fade has played.
    func stop() {
        if player.isPlaying {
            player.stop()
        } else {
            finished(renderer)
        }
    }

    /// The player can't continue as configured (route change while listening,
    /// or iOS rebuilt the audio engine): start again so echo cancellation and
    /// the count-in leakage measurement match. At most a few times in quick succession.
    private func restartPlayback() {
        guard let current, playback.playing else { return }
        let now = Date()
        recentRestarts = recentRestarts.filter { now.timeIntervalSince($0) < 10 } + [now]
        guard recentRestarts.count <= 3 else { stop(); return }
        _ = engine?.stop()
        engine = nil
        play(current)
    }

    /// Stops at once (leaving the screen, going to the background).
    func stopImmediately() {
        player.stopImmediately()
        finished(renderer)
    }

    private func finished(_ which: SampleRenderer?) {
        guard which === renderer else { return }
        if let final = engine?.stop() { applyFinal(final) }
        engine = nil
        stopTimer()
        renderer = nil
        schedule = nil
        tanpuraPlaying = false
        playback = PlaybackState(repetitions: playback.repetitions)
    }

    private func tick() {
        guard let renderer, let schedule, let frames = player.positionFrames() else { return }
        if !practisedThisPlay, let current, frames >= Int64(renderer.countInFrames) + Int64(renderer.performanceFrames) {
            practisedThisPlay = true
            updateHistory { $0.practised(varisaiId: current.id, nowMillis: Self.nowMillis) }
        }
        let countInBeat = renderer.countInBeat(frame: frames).map { Int(truncating: $0) }
        let seconds = renderer.passSeconds(frame: frames)
        let position = countInBeat == nil ? schedule.cursorAt(seconds: seconds) : nil
        var next = playback
        next.countingIn = countInBeat != nil
        next.repetition = position.map { Int($0.repetition) }
        next.avarta = position.map { Int($0.avarta) }
        next.akshara = position.map { Int($0.akshara) }
        next.slot = position.map { Int($0.slot) }
        next.beatInAvarta = countInBeat ?? schedule.beatAt(seconds: seconds).map { Int($0.beatInAvarta) }
        if next != playback { playback = next }
    }

    // MARK: - Sing along

    /// Turn sing-along on or off; asks for the microphone the first time.
    func toggleSingAlong() {
        if settings.singAlong {
            settings.singAlong = false
        } else if singAlong.micPermitted || syntheticSingerCents != nil {
            settings.singAlong = true
        } else {
            Self.requestMic { [weak self] granted in
                Task { @MainActor in
                    guard let self else { return }
                    self.singAlong.micPermitted = granted
                    self.singAlong.micDenied = !granted
                    if granted { self.settings.singAlong = true }
                }
            }
        }
    }

    private func apply(_ outcome: FrameOutcome, from which: SampleRenderer, saHz: Double) {
        guard which === renderer else { return }
        let live = outcome.live
        singAlong.live = live.reading
        if let reading = live.reading, let pass = live.pass, let t = live.secondsInPass {
            singAlong.trace.append(TraceSample(
                pass: Int(truncating: pass), secondsInPass: Double(truncating: t),
                centsFromSa: 1200 * log2(reading.frequencyHz / saHz), zone: Zone.companion.of(cents: reading.centsOff)))
            if singAlong.trace.count > 1500 { singAlong.trace.removeFirst(singAlong.trace.count - 1500) }
        }
        for mark in outcome.newMarks { record(mark) }
        if let pass = outcome.completedPass { announce(pass) }
    }

    private func applyFinal(_ result: PassResult) {
        singAlong.live = nil
        for mark in result.marks { record(mark) }
        announce(result)
    }

    private func record(_ mark: NoteMark) {
        let p = mark.event.position
        singAlong.marks[MarkKey(avarta: Int(p.avarta), akshara: Int(p.akshara), slot: Int(p.slot))] = mark.result
    }

    private func announce(_ result: PassResult) {
        if let current {
            updateHistory { $0.scored(varisaiId: current.id, result: result, nowMillis: Self.nowMillis) }
        }
        singAlong.lastSummary = (Int(result.pass) + 1, result.summary)
        // Once per pass, never during it (it would talk over the music).
        UIAccessibility.post(notification: .announcement, argument: SingAlongText.summaryHeadline(Int(result.pass) + 1, result.summary))
    }

    private static func micAuthorized() -> Bool {
        if #available(iOS 17.0, *) { return AVAudioApplication.shared.recordPermission == .granted }
        return AVAudioSession.sharedInstance().recordPermission == .granted
    }

    private static func requestMic(_ completion: @escaping @Sendable (Bool) -> Void) {
        if #available(iOS 17.0, *) {
            AVAudioApplication.requestRecordPermission(completionHandler: completion)
        } else {
            AVAudioSession.sharedInstance().requestRecordPermission(completion)
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
}
