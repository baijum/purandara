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
    var practicePhase: PracticePhase?
    var attempt = 1
    var recoverySeconds: Int?

    var recovering: Bool { practicePhase == .recovery || practicePhase == .waiting }
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
            guard !outputChanged else { return }
            if practicePaused, settings.saMidi != oldValue.saMidi || settings.saCents != oldValue.saCents {
                stopImmediately()
            }
            if playback.playing, let current {
                if let transport, playback.recovering {
                    if settings.saMidi != oldValue.saMidi || settings.saCents != oldValue.saCents
                        || (settings.practiceMode == .normal && oldValue.loop && !settings.loop) { stopImmediately() }
                    else if settings.tempo != oldValue.tempo || settings.kala != oldValue.kala
                        || settings.tuning != oldValue.tuning || settings.practiceMode != oldValue.practiceMode
                        || settings.singAlong != oldValue.singAlong || settings.echoCancellation != oldValue.echoCancellation
                        || settings.strictness != oldValue.strictness || settings.drone != oldValue.drone
                        || settings.droneLevel != oldValue.droneLevel || !settings.usesTransport {
                        // These change the schedule/capture graph or drone. Do not
                        // unexpectedly sound a new attempt while the learner rests.
                        stopImmediately()
                        pausedPractice = current
                        practicePaused = true
                    } else {
                        let values = Handoff((settings.policy(feedback: settings.singAlong && listeningAllowed), settings.transportOptions()))
                        player.command(transport) {
                            _ = $0.updatePolicy(runId: $0.runId, policy: values.value.0)
                            _ = $0.updateOptions(runId: $0.runId, options: values.value.1)
                        }
                    }
                } else { playExercise(current, newLogicalPlay: false) }
            }
            if tanpuraPlaying { playTanpura() }
            if shruti.open { startShruti() }
        }
    }
    @Published private(set) var samplesLoaded = false
    @Published private(set) var loadError = false
    @Published private(set) var playback = PlaybackState()
    /// One section of the open piece practised alone, or nil for the whole piece (`PlayerView` resets it).
    @Published var practiceSection: SectionSpan? {
        didSet { if playback.playing { stop() }; pausedPractice = nil; practicePaused = false }
    }
    @Published var practicePhrase: PracticePhrase? {
        didSet { if playback.playing { stop() }; pausedPractice = nil; practicePaused = false }
    }
    @Published private(set) var practicePaused = false
    private var pausedPractice: Varisai?
    @Published private(set) var singAlong = SingAlongUI()
    /// What has been practised (landing page).
    @Published private(set) var history: PracticeHistory
    /// The landing page's tanpura-only drone is sounding.
    @Published private(set) var tanpuraPlaying = false
    @Published private(set) var shruti = ShrutiUI()
    /// Find my Sa: open, and its progress (nil until the first frame).
    @Published private(set) var findSaOpen = false
    @Published private(set) var findSa: FindSaProgress?
    @Published private(set) var outputChanged = false

    let catalog: [Varisai] = VarisaiCatalog.shared.all

    private let store: PracticeSettingsStore
    private let player: VarisaiAudioPlayer
    private var bank: SampleBank?
    private var current: Varisai?
    private var recentRestarts: [Date] = []
    private var renderer: SampleRenderer?
    private var transport: PracticeTransport?
    private var transportEngine: TransportSingAlongEngine?
    private var runId: Int64 = 0
    private var finalizedSegments: Set<Int64> = []
    private var displayedSegment: Int64?
    private var acceptingTransportOutcomes = false
    private var transportStopRequested = false
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
    private var backgroundOutput: VarisaiAudioPlayer.OutputSnapshot?

    /// Debug builds: launch argument `-singer <cents>` replaces the mic with a synthetic singer.
    let syntheticSingerCents: Double?

    init(store: PracticeSettingsStore = .standard, loadSamples: Bool = true, player: VarisaiAudioPlayer = VarisaiAudioPlayer(), bank: SampleBank? = nil) {
        self.player = player
        self.bank = bank
        samplesLoaded = bank != nil
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
        player.onOutputChanged = { [weak self] in self?.onOutputChanged() }
        guard loadSamples, bank == nil else { return }
        Task.detached(priority: .userInitiated) {
            do {
                let bank = try HarmoniumSamples.load()
                await MainActor.run {
                    self.bank = bank
                    self.samplesLoaded = true
                    // Opened before the samples were ready (e.g. a launch argument).
                    if !self.outputChanged {
                        if self.shruti.open { self.startShruti() }
                        if self.findSaOpen { self.startFindSa() }
                    }
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
        player.acknowledgeOutputChange()
        playExercise(varisai)
    }

    private func playExercise(_ varisai: Varisai, newLogicalPlay: Bool = true) {
        outputChanged = false
        resumeAfterBackground = nil
        guard let bank else { return }
        // The player offers a playable Sa instead; never build a schedule the samples can't play.
        // A rejected restart must still silence the previous session instead of
        // leaving it playing with Stop disabled (issue #13).
        guard saPlayable(varisai) else { stop(); return }
        stopTimer()
        player.stopImmediately()
        _ = engine?.stop()
        engine = nil
        finishTransportCapture()
        transportEngine = nil
        transport = nil
        player.onWillRenderTransport = nil
        practicePaused = false
        pausedPractice = nil
        let schedule = practicePhrase.map { Schedule.companion.ofPhrase(varisai: varisai, settings: settings.performance(), phrase: $0) }
            ?? practiceSection.map { Schedule.companion.ofSection(varisai: varisai, settings: settings.performance(), section: $0) }
            ?? Schedule.companion.of(varisai: varisai, settings: settings.performance())
        if newLogicalPlay { practisedThisPlay = false }
        if settings.usesTransport {
            playTransport(varisai, bank: bank, schedule: schedule)
            return
        }
        let renderer = SampleRenderer(
            bank: bank,
            schedule: schedule,
            options: settings.renderOptions())
        current = varisai
        tanpuraPlaying = false
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
            print("[practice] could not start audio: \(error)")
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

    private func playTransport(_ varisai: Varisai, bank: SampleBank, schedule: Schedule) {
        runId += 1
        let id = runId
        let transport = PracticeTransport.companion.create(
            bank: bank, schedule: schedule, options: settings.transportOptions(),
            policy: settings.policy(feedback: settings.singAlong && listeningAllowed), runId: id)
        self.transport = transport
        self.renderer = nil
        self.schedule = schedule
        current = varisai
        tanpuraPlaying = false
        finalizedSegments.removeAll()
        displayedSegment = nil
        acceptingTransportOutcomes = true
        transportStopRequested = false
        singAlong.live = nil
        singAlong.pass = nil
        singAlong.trace = []
        singAlong.marks = [:]
        singAlong.lastSummary = nil
        let capture = settings.singAlong && listeningAllowed
        // Built outside @MainActor: AVFoundation/render/pitch queues invoke its closures.
        let scoring = capture ? Self.transportScoring(
            player: player, schedule: schedule, strictness: settings.strictness.kotlin,
            syntheticCents: syntheticSingerCents, sampleRate: transport.sampleRate,
            deliver: { [weak self] segment, outcome in
                let box = Handoff((segment, outcome))
                Task { @MainActor in self?.applyTransport(box.value.1, segment: box.value.0, run: id) }
            }) : nil
        transportEngine = scoring
        Self.configureTransport(player: player, transport: transport, scoring: scoring)
        do {
            let mic = scoring?.micRequest(echoCancellation: settings.echoCancellation)
            try player.play(transport, mic: mic) { [weak self] in self?.finishedTransport(id) }
            singAlong.onSpeaker = mic != nil ? player.micOnSpeaker : VarisaiAudioPlayer.outputIsBuiltInSpeaker()
        } catch {
            print("[practice] could not start transport: \(error)")
            finishedTransport(id)
            return
        }
        playback = PlaybackState(playing: true, repetitions: Int(schedule.repetitions))
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    nonisolated private static func transportScoring(
        player: VarisaiAudioPlayer, schedule: Schedule, strictness: Strictness,
        syntheticCents: Double?, sampleRate: Int32,
        deliver: @escaping @Sendable (PracticeSegment, FrameOutcome) -> Void
    ) -> TransportSingAlongEngine {
        TransportSingAlongEngine(player: player, schedule: schedule, strictness: strictness,
                                 syntheticCents: syntheticCents, sampleRate: sampleRate,
                                 onOutcome: deliver)
    }

    nonisolated private static func configureTransport(
        player: VarisaiAudioPlayer, transport: PracticeTransport, scoring: TransportSingAlongEngine?
    ) {
        transport.onSegmentStarted = { segment in scoring?.begin(segment) }
        player.onRendered = scoring.map { scoring in
            { start, rms in scoring.outputLevels.record(startFrame: start, rms: rms) }
        }
        player.onWillRenderTransport = { current in
            guard let pending = current.pendingPreparation, pending.scored else { return }
            // Capture graph is stable for the entire run. Per-attempt listeners
            // are created at commit; admission still follows the heard clock.
            _ = current.setReady(runId: pending.runId, segmentId: pending.segmentId, ready: scoring?.ready == true)
        }
    }

    func continuePractice() {
        guard !outputChanged, !(transportStopRequested && transport != nil) else { return }
        if let paused = pausedPractice {
            pausedPractice = nil
            practicePaused = false
            playExercise(paused, newLogicalPlay: false)
        } else if let transport {
            player.command(transport) { _ = $0.continuePractice(runId: $0.runId) }
        }
    }

    func waitForMe() {
        guard let transport else { return }
        player.command(transport) { _ = $0.waitForMe(runId: $0.runId) }
    }

    private func applyTransport(_ outcome: FrameOutcome, segment: PracticeSegment, run: Int64) {
        guard acceptingTransportOutcomes, run == runId, transport != nil, !finalizedSegments.contains(segment.segmentId) else { return }
        // A queued old outcome must never overwrite a newer heard attempt.
        guard displayedSegment == nil || displayedSegment == segment.segmentId else { return }
        applyOutcome(outcome, saHz: settings.saHz, attempt: Int(segment.attempt))
    }

    private func finishSegment(_ segment: PracticeSegment, result: PassResult, run: Int64) {
        guard run == runId, finalizedSegments.insert(segment.segmentId).inserted else { return }
        singAlong.live = nil
        for mark in result.marks { record(mark) }
        announce(result, attempt: Int(segment.attempt))
    }

    private func finishTransportCapture() {
        for (segment, result) in transportEngine?.stop() ?? [] {
            finishSegment(segment, result: result, run: runId)
        }
    }

    private func finishedTransport(_ id: Int64) {
        guard id == runId, transport != nil else { return }
        finishTransportCapture()
        transportEngine = nil
        transport = nil
        player.onWillRenderTransport = nil
        player.onRendered = nil
        stopTimer()
        schedule = nil
        playback = PlaybackState(repetitions: playback.repetitions)
    }

    private func tickTransport(_ transport: PracticeTransport, frames: Int64, schedule: Schedule) {
        guard let snapshot = player.practiceSnapshot() else { return }
        for (segment, result) in transportEngine?.finalize(heardFrame: frames) ?? [] {
            finishSegment(segment, result: result, run: runId)
        }
        let phase = snapshot.phaseAt(heardFrame: frames)
        let segment = snapshot.segmentAt(heardFrame: frames)
        if let segment, displayedSegment != segment.segmentId {
            displayedSegment = segment.segmentId
            if segment.scored || segment.response {
                singAlong.trace = []
                singAlong.marks = [:]
                singAlong.lastSummary = nil
            }
        }
        if !practisedThisPlay, let current,
           snapshot.segments.contains(where: { !$0.listening && frames >= $0.performanceEndFrame }) {
            practisedThisPlay = true
            if schedule.isWholePiece { updateHistory { $0.practised(varisaiId: current.id, nowMillis: Self.nowMillis) } }
        }
        var next = PlaybackState(playing: true, repetitions: Int(schedule.repetitions))
        next.practicePhase = phase
        next.attempt = segment.map { Int($0.attempt) } ?? playback.attempt
        next.countingIn = phase == .countIn
        next.recoverySeconds = snapshot.remainingFramesAt(heardFrame: frames).map {
            Int(ceil(Double(truncating: $0) / Double(transport.sampleRate)))
        }
        if let segment {
            let seconds = Double(frames - segment.performanceStartFrame) / Double(transport.sampleRate)
            if phase == .countIn {
                next.beatInAvarta = Int(Double(frames - segment.startFrame) / Double(transport.sampleRate)
                    / (schedule.settings.talaAksharaSeconds / Double(schedule.varisai.kalai)))
                    % (Int(schedule.tala.aksharas) * Int(schedule.varisai.kalai))
            } else if frames < segment.performanceEndFrame {
                let position = schedule.cursorAt(seconds: seconds)
                next.repetition = position.map { Int($0.repetition) }
                next.avarta = position.map { Int($0.avarta) }
                next.akshara = position.map { Int($0.akshara) }
                next.slot = position.map { Int($0.slot) }
                next.beatInAvarta = schedule.beatAt(seconds: seconds).map { Int($0.beatInAvarta) }
            }
        }
        if next.recovering { singAlong.live = nil }
        if next != playback { playback = next }
    }

    // MARK: - Tanpura and history (landing page)

    /// The drone alone, at the current Sa, until stopped.
    func toggleTanpura() {
        if tanpuraPlaying { stop() } else {
            player.acknowledgeOutputChange()
            playTanpura()
        }
    }

    func stopTanpura() {
        if tanpuraPlaying { stop() }
    }

    private func playTanpura() {
        outputChanged = false
        resumeAfterBackground = nil
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
            print("[practice] could not start audio: \(error)")
            finished(renderer)
            return
        }
        tanpuraPlaying = true
    }

    // MARK: - Shruti practice and Find my Sa (docs/plan-shruti.md)

    private var listeningAllowed: Bool { singAlong.micPermitted || syntheticSingerCents != nil }

    func openShruti() {
        guard !shruti.open else { return }
        player.acknowledgeOutputChange()
        outputChanged = false
        resumeAfterBackground = nil
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
                guard !self.outputChanged else { return }
                if granted, self.shruti.open { self.startShruti() }
                if granted, self.findSaOpen { self.startFindSa() }
            }
        }
    }

    /// The tanpura plus, with the mic allowed, a `ShrutiSession` listening to the student.
    private func startShruti() {
        guard !outputChanged else { return }
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
            print("[practice] could not start audio: \(error)")
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
        guard !findSaOpen else { return }
        retryFindSa()
    }

    func retryFindSa() {
        player.acknowledgeOutputChange()
        outputChanged = false
        resumeAfterBackground = nil
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
        guard !outputChanged else { return }
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
            print("[practice] could not start audio: \(error)")
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
        if transport != nil {
            player.stopImmediately()
            finishedTransport(runId)
        }
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
        transportStopRequested = true
        acceptingTransportOutcomes = false
        pausedPractice = nil
        practicePaused = false
        resumeAfterBackground = nil
        if player.isPlaying {
            player.stop()
        } else {
            finished(renderer)
        }
    }

    /// Only internal engine rebuilds restart. An external output change always
    /// wins over a queued recovery and waits for an explicit user start.
    private func restartPlayback() {
        guard !outputChanged else { return }
        if shruti.open { startShruti(); return }
        if findSaOpen { startFindSa(); return }
        // iOS rebuilds the engine after leaving record mode (Practise Sa, Find my Sa):
        // without a restart the tanpura would go silent.
        if tanpuraPlaying { playTanpura(); return }
        guard let current, playback.playing else { return }
        if playback.recovering {
            stopImmediately()
            pausedPractice = current
            practicePaused = true
            return
        }
        let now = Date()
        recentRestarts = recentRestarts.filter { now.timeIntervalSince($0) < 10 } + [now]
        guard recentRestarts.count <= 3 else { stop(); return }
        _ = engine?.stop()
        engine = nil
        playExercise(current, newLogicalPlay: false)
    }

    private func onOutputChanged() {
        guard !outputChanged, renderer != nil || transport != nil || pausedPractice != nil || resumeAfterBackground != nil || shruti.open || findSaOpen else { return }
        outputChanged = true
        resumeAfterBackground = nil
        stopImmediately()
        shruti.listeningToRoom = false
        shruti.reading = nil
        shruti.trace = []
        shruti.hold = nil
        singAlong.live = nil
        if findSa?.step != .done { findSa = nil }
        UIAccessibility.post(notification: .announcement, argument: "Audio output changed. Start again when ready.")
    }

    func restartAfterOutputChange() {
        player.acknowledgeOutputChange()
        outputChanged = false
        resumeAfterBackground = nil
        if shruti.open { startShruti() }
        else if findSaOpen { startFindSa() }
    }

    /// Stops at once (leaving the screen, going to the background).
    func stopImmediately() {
        acceptingTransportOutcomes = false
        pausedPractice = nil
        practicePaused = false
        resumeAfterBackground = nil
        player.stopImmediately()
        if transport != nil { finishedTransport(runId) }
        else { finished(renderer) }
    }

    /// The app left the screen (scene phase .background): remember what was sounding,
    /// then silence everything. The practice destinations stay open — Shruti practice,
    /// an unfinished Find my Sa and playback resume on return, as a fresh session
    /// (hold progress and a half-caught guide start over), unlike the plain
    /// `stopImmediately` the screens call when leaving (issue #11).
    func onBackgrounded() {
        if transportStopRequested, transport != nil { stopImmediately(); return }
        guard !outputChanged else { resumeAfterBackground = nil; stopImmediately(); return }
        backgroundOutput = player.outputSnapshot()
        if pausedPractice != nil { return }
        if playback.recovering, let exercise = current {
            stopImmediately()
            pausedPractice = exercise
            practicePaused = true
            return
        }
        let shrutiWasOpen = shruti.open
        let findSaWasOpen = findSaOpen
        let tanpuraWasPlaying = tanpuraPlaying
        let exercise = current
        let wasPlaying = playback.playing
        // Nothing to resume: a route change while away must not count as an
        // interrupted session (Android's `backgroundAudio()` is null here too).
        guard shrutiWasOpen || findSaWasOpen || tanpuraWasPlaying || (wasPlaying && exercise != nil) else {
            resumeAfterBackground = nil
            stopImmediately()
            return
        }
        stopImmediately()
        resumeAfterBackground = { [weak self] in
            guard let self else { return }
            if shrutiWasOpen { self.startShruti() }
            else if findSaWasOpen { self.startFindSa() }
            else if tanpuraWasPlaying { self.playTanpura() }
            else if wasPlaying, let exercise { self.playExercise(exercise, newLogicalPlay: false) }
        }
    }

    /// The app is back (scene phase .active): start again what was sounding.
    func onForegrounded() {
        if let backgroundOutput, backgroundOutput != player.outputSnapshot() {
            onOutputChanged()
        }
        backgroundOutput = nil
        let resume = resumeAfterBackground
        resumeAfterBackground = nil
        if !outputChanged { resume?() }
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
        if let transport, let schedule, let frames = player.positionFrames() {
            tickTransport(transport, frames: frames, schedule: schedule)
            return
        }
        guard let renderer, let schedule, let frames = player.positionFrames() else { return }
        if !practisedThisPlay, let current, frames >= Int64(renderer.countInFrames) + Int64(renderer.performanceFrames) {
            practisedThisPlay = true
            // A section alone doesn't count as practising the whole piece.
            if schedule.isWholePiece { updateHistory { $0.practised(varisaiId: current.id, nowMillis: Self.nowMillis) } }
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
        applyOutcome(outcome, saHz: saHz)
    }

    private func applyOutcome(_ outcome: FrameOutcome, saHz: Double, attempt: Int? = nil) {
        let live = outcome.live
        singAlong.live = live.reading
        // Same counter the trace is tagged with: the whole-performance
        // loop, reported even while no voice is heard (issue #12).
        singAlong.pass = live.pass.map { attempt.map { $0 - 1 } ?? Int(truncating: $0) }
        if let reading = live.reading, let pass = live.pass, let t = live.secondsInPass {
            singAlong.trace.append(TraceSample(
                pass: attempt.map { $0 - 1 } ?? Int(truncating: pass), secondsInPass: Double(truncating: t),
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

    private func announce(_ result: PassResult, attempt: Int? = nil) {
        if let current, schedule?.isWholePiece == true { // only whole-piece passes are a best result
            updateHistory { $0.scored(varisaiId: current.id, result: result, nowMillis: Self.nowMillis) }
        }
        let number = attempt ?? (Int(result.pass) + 1)
        singAlong.lastSummary = (number, result.summary)
        // Once per pass, never during it (it would talk over the music).
        UIAccessibility.post(notification: .announcement, argument: SingAlongText.summaryHeadline(number, result.summary))
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
