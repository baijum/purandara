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
    /// Which whole-performance loop the live readings and trace are from (0-based); nil during the count-in.
    var pass: Int?
    var trace: [TraceSample] = []
    var marks: [MarkKey: NoteResult] = [:]
    /// Last completed pass (1-based) and its summary.
    var lastSummary: (pass: Int, summary: PassSummary)?
}

/// One point of Shruti practice's free pitch trace.
struct FreeTracePoint {
    let seconds: Double
    let centsFromSa: Double
    let zone: Zone
}

/// Hands a value made on a listener queue to the main actor. Safe because the
/// value is created there once and only read afterwards (Kotlin objects aren't `Sendable`).
struct Handoff<T>: @unchecked Sendable {
    let value: T
    init(_ value: T) { self.value = value }
}

/// What the Shruti practice screen shows (docs/plan-shruti.md). Mirrors Android's `ShrutiUiState`.
struct ShrutiUI {
    var open = false
    /// The opening room check: the student should stay quiet.
    var listeningToRoom = false
    var reading: SwaraReading?
    /// About the last ten seconds.
    var trace: [FreeTracePoint] = []
    var target: HoldTarget = .sa
    var goal: HoldGoal = .ten
    var hold: HoldState?
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
            if shruti.open { startShruti() }
        }
    }
    @Published private(set) var samplesLoaded = false
    @Published private(set) var loadError = false
    @Published private(set) var playback = PlaybackState()
    /// One section of the open piece practised alone, or nil for the whole piece (`PlayerView` resets it).
    @Published var practiceSection: SectionSpan? {
        didSet { if playback.playing { stop() } }
    }
    @Published private(set) var singAlong = SingAlongUI()
    /// What has been practised (landing page).
    @Published private(set) var history: PracticeHistory
    /// The landing page's tanpura-only drone is sounding.
    @Published private(set) var tanpuraPlaying = false
    @Published private(set) var shruti = ShrutiUI()
    /// Find my Sa: open, and its progress (nil until the first frame).
    @Published private(set) var findSaOpen = false
    @Published private(set) var findSa: FindSaProgress?

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
    /// Shruti practice or Find my Sa listening.
    private var listener: PitchListener?
    private var shrutiSession: ShrutiSession?

    /// What sounded before the app was backgrounded, to resume on return (issue #11).
    private var resumeAfterBackground: (() -> Void)?

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
                    // Opened before the samples were ready (e.g. a launch argument).
                    if self.shruti.open { self.startShruti() }
                    if self.findSaOpen { self.startFindSa() }
                }
            } catch {
                await MainActor.run { self.loadError = true }
            }
        }
    }

    var canPlay: Bool { samplesLoaded }

    /// Whether every note of [varisai] is inside the harmonium samples at the chosen Sa (`Varisai.playableSaMidis`).
    func saPlayable(_ varisai: Varisai) -> Bool {
        let range = varisai.playableSaMidis
        return settings.saMidi >= Int(range.first) && settings.saMidi <= Int(range.last)
    }

    func togglePlay(_ varisai: Varisai) {
        if playback.playing { stop() } else { play(varisai) }
    }

    func play(_ varisai: Varisai) {
        guard let bank else { return }
        // The player offers a playable Sa instead; never build a schedule the samples can't play.
        // A rejected restart must still silence the previous session instead of
        // leaving it playing with Stop disabled (issue #13).
        guard saPlayable(varisai) else { stop(); return }
        stopTimer()
        let schedule = practiceSection.map { Schedule.companion.ofSection(varisai: varisai, settings: settings.performance(), section: $0) }
            ?? Schedule.companion.of(varisai: varisai, settings: settings.performance())
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
        singAlong.pass = nil
        singAlong.trace = []
        singAlong.marks = [:]
        singAlong.lastSummary = nil
        let listening = settings.singAlong && (singAlong.micPermitted || syntheticSingerCents != nil)
        let sa = schedule.settings.saHz
        let newEngine = listening ? SingAlongEngine(
            player: player, renderer: renderer, schedule: schedule, strictness: settings.strictness.kotlin,
            syntheticCents: syntheticSingerCents,
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

    // MARK: - Shruti practice and Find my Sa (docs/plan-shruti.md)

    private var listeningAllowed: Bool { singAlong.micPermitted || syntheticSingerCents != nil }

    func openShruti() {
        shruti = ShrutiUI(open: true, target: shruti.target, goal: shruti.goal)
        startShruti()
    }

    func closeShruti() {
        guard shruti.open else { return }
        shruti.open = false
        stopImmediately()
        shruti.reading = nil
        shruti.trace = []
        shruti.hold = nil
    }

    func setHold(target: HoldTarget, goal: HoldGoal) {
        shrutiSession?.startHold(target: target, goal: goal)
        shruti.target = target
        shruti.goal = goal
        shruti.hold = nil
    }

    /// Asks for the microphone, then restarts the open Shruti practice or Find my Sa.
    func allowMic() {
        Self.requestMic { [weak self] granted in
            Task { @MainActor in
                guard let self else { return }
                self.singAlong.micPermitted = granted
                self.singAlong.micDenied = !granted
                if granted, self.shruti.open { self.startShruti() }
                if granted, self.findSaOpen { self.startFindSa() }
            }
        }
    }

    /// The tanpura plus, with the mic allowed, a `ShrutiSession` listening to the student.
    private func startShruti() {
        guard let bank, let first = catalog.first else { return }
        stopEverything()
        let renderer = SampleRenderer(
            bank: bank,
            schedule: Schedule.companion.of(varisai: first, settings: settings.performance()),
            options: RenderOptions.companion.droneOnly(droneLevel: settings.droneLevel))
        self.renderer = renderer
        let rate = Double(renderer.sampleRate)
        let room = ShrutiSession.companion.DEFAULT_ROOM_CHECK_SECONDS
        let session = ShrutiSession(
            saHz: settings.saHz, raga: VarisaiCatalog.shared.raga, tuning: settings.tuning.kotlin,
            strictness: settings.strictness.kotlin, sampleRate: renderer.sampleRate,
            outputLevels: OutputLevels(capacity: 512), roomCheckSeconds: room)
        session.startHold(target: shruti.target, goal: shruti.goal)
        var mic: VarisaiAudioPlayer.MicRequest?
        if listeningAllowed {
            let listener = Self.shrutiListener(
                player: player, session: session, rate: rate, room: room, saHz: settings.saHz,
                syntheticCents: syntheticSingerCents
            ) { [weak self, id = ObjectIdentifier(renderer)] outcome, seconds in
                let box = Handoff(outcome)
                Task { @MainActor in self?.apply(box.value, at: seconds, from: id) }
            }
            self.listener = listener
            shrutiSession = session
            player.onRendered = { start, rms in session.outputLevels.record(startFrame: start, rms: rms) }
            mic = listener.micRequest(echoCancellation: settings.echoCancellation)
        }
        do {
            try player.play(renderer, mic: mic) { [weak self] in self?.finished(renderer) }
            singAlong.onSpeaker = mic != nil ? player.micOnSpeaker : VarisaiAudioPlayer.outputIsBuiltInSpeaker()
        } catch {
            finished(renderer)
            return
        }
        listener?.start()
        shruti.listeningToRoom = listeningAllowed
        shruti.reading = nil
        shruti.trace = []
        shruti.hold = nil
    }

    /// Frames from a listener whose playback has since been replaced are ignored.
    private func isCurrent(_ id: ObjectIdentifier) -> Bool { renderer.map(ObjectIdentifier.init) == id }

    private func apply(_ outcome: ShrutiOutcome, at seconds: Double, from id: ObjectIdentifier) {
        guard isCurrent(id), shruti.open else { return }
        let wasReached = shruti.hold?.reached ?? false
        shruti.listeningToRoom = outcome.listeningToRoom
        shruti.reading = outcome.reading
        shruti.hold = outcome.hold
        if let reading = outcome.reading {
            shruti.trace.append(FreeTracePoint(
                seconds: seconds, centsFromSa: 1200 * log2(reading.frequencyHz / settings.saHz), zone: reading.zone()))
        }
        if let first = shruti.trace.firstIndex(where: { $0.seconds >= seconds - 10 }), first > 0 {
            shruti.trace.removeFirst(first)
        }
        if !wasReached, let hold = outcome.hold, hold.reached {
            UIAccessibility.post(notification: .announcement, argument: ShrutiText.reached(hold))
        }
    }

    func openFindSa() {
        findSaOpen = true
        findSa = nil
        startFindSa()
    }

    func closeFindSa() {
        guard findSaOpen else { return }
        findSaOpen = false
        stopImmediately()
    }

    func useSuggestedSa() {
        guard let suggestion = findSa?.suggestion else { return }
        settings.saMidi = Int(suggestion.saMidi)
        settings.saCents = 0
    }

    /// Listens without sound (a silent drone keeps the engine, and its mic, running).
    private func startFindSa() {
        guard let bank, let first = catalog.first else { return }
        stopEverything()
        findSa = nil
        guard listeningAllowed else { return }
        let renderer = SampleRenderer(
            bank: bank,
            schedule: Schedule.companion.of(varisai: first, settings: settings.performance()),
            options: RenderOptions.companion.droneOnly(droneLevel: 0))
        self.renderer = renderer
        let listener = Self.findSaListener(syntheticCents: syntheticSingerCents) { [weak self, id = ObjectIdentifier(renderer)] progress in
            let box = Handoff(progress)
            Task { @MainActor in self?.apply(box.value, from: id) }
        }
        self.listener = listener
        do {
            try player.play(renderer, mic: listener.micRequest(echoCancellation: false)) { [weak self] in self?.finished(renderer) }
        } catch {
            finished(renderer)
            return
        }
        listener.start()
    }

    private func apply(_ progress: FindSaProgress, from id: ObjectIdentifier) {
        guard isCurrent(id), findSaOpen else { return }
        let wasDone = findSa?.step == .done
        findSa = progress
        if !wasDone, progress.step == .done, let suggestion = progress.suggestion {
            UIAccessibility.post(notification: .announcement, argument: ShrutiText.suggestion(suggestion))
        }
    }

    // Built outside the main actor: the listener calls these closures on its own queue.

    nonisolated private static func shrutiListener(
        player: VarisaiAudioPlayer, session: ShrutiSession, rate: Double, room: Double, saHz: Double,
        syntheticCents: Double?, deliver: @escaping @Sendable (ShrutiOutcome, Double) -> Void
    ) -> PitchListener {
        // The debug synthetic singer sings Sa after the room check, like a student would.
        let synthetic = syntheticCents.map { cents in
            PitchListener.SyntheticSinger(cents: cents) { [weak player] host in
                guard let frame = player?.outputFrame(atHostTime: host), Double(frame) / rate >= room else { return nil }
                return saHz
            }
        }
        return PitchListener(
            timeOf: { [weak player] host in
                guard let frame = player?.outputFrame(atHostTime: host), frame >= 0 else { return nil }
                return Double(frame) / rate
            },
            synthetic: synthetic,
            supervisorFactory: { NeuralPitchSupervisor(captureSampleRate: $0) }
        ) { frame in deliver(session.onFrame(frame: frame), frame.timeSeconds) }
    }

    nonisolated private static func findSaListener(
        syntheticCents: Double?, deliver: @escaping @Sendable (FindSaProgress) -> Void
    ) -> PitchListener {
        let guide = FindSaGuide()
        let start = AVAudioTime.seconds(forHostTime: mach_absolute_time())
        let minVoice = BleedModel.companion.MIN_VOICE_RMS
        // The debug synthetic singer sings G2, then G4.
        let synthetic = syntheticCents.map { cents in
            PitchListener.SyntheticSinger(cents: cents) { host in
                AVAudioTime.seconds(forHostTime: host) - start < 4 ? 98 : 392
            }
        }
        return PitchListener(
            timeOf: { host in AVAudioTime.seconds(forHostTime: host) - start },
            synthetic: synthetic,
            supervisorFactory: { NeuralPitchSupervisor(captureSampleRate: $0) }
        ) { frame in
            let hz = frame.rms >= minVoice ? frame.frequencyHz : nil
            deliver(guide.onFrame(timeSeconds: frame.timeSeconds, frequencyHz: hz))
        }
    }

    /// Stops any exercise, drone or listening at once.
    private func stopEverything() {
        stopTimer()
        _ = engine?.stop()
        engine = nil
        stopListening()
        current = nil
        schedule = nil
        tanpuraPlaying = false
        player.stopImmediately()
    }

    private func stopListening() {
        listener?.stop {}
        listener = nil
        if shrutiSession != nil { player.onRendered = nil }
        shrutiSession = nil
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
        if shruti.open { startShruti(); return }
        if findSaOpen { startFindSa(); return }
        // iOS rebuilds the engine after leaving record mode (Practise Sa, Find my Sa):
        // without a restart the tanpura would go silent.
        if tanpuraPlaying { playTanpura(); return }
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

    /// The app left the screen (scene phase .background): remember what was sounding,
    /// then silence everything. The practice destinations stay open — Shruti practice,
    /// an unfinished Find my Sa and playback resume on return, as a fresh session
    /// (hold progress and a half-caught guide start over), unlike the plain
    /// `stopImmediately` the screens call when leaving (issue #11).
    func onBackgrounded() {
        let shrutiWasOpen = shruti.open
        let findSaWasOpen = findSaOpen
        let tanpuraWasPlaying = tanpuraPlaying
        let exercise = current
        let wasPlaying = playback.playing
        resumeAfterBackground = { [weak self] in
            guard let self else { return }
            if shrutiWasOpen { self.startShruti() }
            else if findSaWasOpen { self.startFindSa() }
            else if tanpuraWasPlaying { self.playTanpura() }
            else if wasPlaying, let exercise { self.play(exercise) }
        }
        stopImmediately()
    }

    /// The app is back (scene phase .active): start again what was sounding.
    func onForegrounded() {
        resumeAfterBackground?()
        resumeAfterBackground = nil
    }

    private func finished(_ which: SampleRenderer?) {
        guard which === renderer else { return }
        if let final = engine?.stop() { applyFinal(final) }
        engine = nil
        stopListening()
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
            // A section alone doesn't count as practising the whole piece.
            if schedule.section == nil { updateHistory { $0.practised(varisaiId: current.id, nowMillis: Self.nowMillis) } }
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
        // Same counter the trace is tagged with: the whole-performance
        // loop, reported even while no voice is heard (issue #12).
        singAlong.pass = live.pass.map { Int(truncating: $0) }
        if let reading = live.reading, let pass = live.pass, let t = live.secondsInPass {
            singAlong.trace.append(TraceSample(
                pass: Int(truncating: pass), secondsInPass: Double(truncating: t),
                centsFromSa: 1200 * log2(reading.frequencyHz / saHz), zone: reading.zone()))
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
        if let current, schedule?.section == nil { // only whole-piece passes are a best result
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
