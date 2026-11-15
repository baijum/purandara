import AVFoundation
import SwiftUI
import shared

/// The record screen: recording, then analysing. Mirrors Android's `RecorderUi`.
struct RecorderUI {
    var recording = false
    var seconds = 0.0
    /// Level of the last buffer, 0...1 (RMS).
    var level: Float = 0
    /// Analysis progress 0...1 after Stop, or nil when not analysing.
    var analysing: Double?
    /// The last recording could not be saved.
    var failed = false
    var saMidi = Int(Shruti.shared.DEFAULT_SA_MIDI)
    var saCents = 0
    /// The student's own tanpura is playing (docs/plan-recordings-tanpura.md).
    var tanpura = false

    /// Recording, in the first seconds with a tanpura: the app is learning it, the student waits.
    var listening: Bool { recording && tanpura && seconds < TanpuraProfile.companion.LISTEN_SECONDS }
}

/// One recording opened: its swaras, trace and playback. Mirrors Android's `RecordingDetailUi`.
struct RecordingDetailUI {
    var recording: Recording
    var track: PitchTrack?
    var transcription: Transcription
    var lane: shared.PitchLane
    var playing = false
    var position = 0.0
    /// Progress while the swaras are re-read with an improved analysis, else nil.
    var updating: Double?
    /// The student's tanpura's Sa (MIDI, cents) when it differs from the recording's, to offer.
    var tanpuraSa: (midi: Int, cents: Int)?
}

/// Recordings on the device (docs/plan-recordings.md): `recordings/` in
/// Application Support holds each `<id>.m4a` and `<id>.pitch`, and the index.
struct RecordingStore {
    let folder: URL

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        folder = support.appendingPathComponent("recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    private var index: URL { folder.appendingPathComponent(RecordingLibrary.companion.INDEX_FILE_NAME) }

    /// The saved library without entries whose files are gone; stray files are removed.
    func load() -> RecordingLibrary {
        let text = try? String(contentsOf: index, encoding: .utf8)
        let files = (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
        let library = RecordingLibrary.companion.decode(text: text).withFiles(fileNames: files)
        for name in library.strayFiles(fileNames: files) {
            try? FileManager.default.removeItem(at: folder.appendingPathComponent(name))
        }
        return library
    }

    func save(_ library: RecordingLibrary) {
        try? library.encode().write(to: index, atomically: true, encoding: .utf8)
    }

    func audioURL(_ id: String) -> URL { folder.appendingPathComponent("\(id).\(Recording.companion.AUDIO_EXTENSION)") }
    func pitchURL(_ id: String) -> URL { folder.appendingPathComponent("\(id).\(Recording.companion.PITCH_EXTENSION)") }

    func writePitch(_ id: String, _ track: PitchTrack) throws {
        let bytes = track.encode()
        var data = Data(count: Int(bytes.size))
        data.withUnsafeMutableBytes { ByteBridge.shared.write(source: bytes, destination: $0.baseAddress!) }
        try data.write(to: pitchURL(id), options: .atomic)
    }

    func readPitch(_ id: String) -> PitchTrack? {
        guard let data = try? Data(contentsOf: pitchURL(id)), !data.isEmpty else { return nil }
        let bytes = data.withUnsafeBytes {
            ByteBridge.shared.toByteArray(source: UnsafeMutableRawPointer(mutating: $0.baseAddress!), count: Int32(data.count))
        }
        return PitchTrack.companion.decode(bytes: bytes)
    }

    func deleteFiles(_ id: String) {
        try? FileManager.default.removeItem(at: audioURL(id))
        try? FileManager.default.removeItem(at: pitchURL(id))
    }
}

/// Recordings with approximate swaras (docs/plan-recordings.md): record,
/// analyse after Stop, then list, open, play, rename, re-read against
/// another Sa or raga, and delete. Mirrors Android's `RecordingsViewModel`.
@MainActor
final class RecordingsViewModel: ObservableObject {
    @Published private(set) var library: RecordingLibrary
    @Published var recorder = RecorderUI()
    @Published private(set) var detail: RecordingDetailUI?
    /// Set when a finished recording should be shown (the record screen replaces itself with it).
    @Published var justRecorded: String?
    @Published private(set) var micAllowed = false

    private let store = RecordingStore()
    private var tuning: Tuning = .just
    private var active: Recorder?
    private var recordingId: String?
    private var recordingStarted: Int64 = 0
    private var player: AVAudioPlayer?
    private var playerFor: String?
    private var reanalysing = false
    private var timer: Timer?

    /// Debug builds: launch argument `-singer <cents>` records a synthetic singer instead of the mic.
    private let syntheticSingerCents: Double? = {
        #if DEBUG
        UserDefaults.standard.object(forKey: "singer") != nil ? UserDefaults.standard.double(forKey: "singer") : nil
        #else
        nil
        #endif
    }()

    init() {
        library = store.load()
        micAllowed = Self.micAuthorized() || syntheticSingerCents != nil
    }

    #if DEBUG
    /// UI tests (`-resetSettings YES`): start with no recordings.
    func resetAll() {
        for recording in library.recordings { store.deleteFiles(recording.id) }
        updateLibrary { _ in RecordingLibrary(recordings: []) }
        UserDefaults.standard.removeObject(forKey: Self.tanpuraKey)
    }
    #endif

    /// New recordings use this Sa; swaras are read in [tuning].
    func prepare(tuning: Tuning, saMidi: Int, saCents: Int) {
        self.tuning = tuning
        if !recorder.recording && recorder.analysing == nil {
            recorder = RecorderUI(saMidi: saMidi, saCents: saCents, tanpura: UserDefaults.standard.bool(forKey: Self.tanpuraKey))
        }
    }

    /// The Record screen's "External tanpura playing" switch, remembered for next time.
    private static let tanpuraKey = "recordingExternalTanpura"

    func setTanpura(_ playing: Bool) {
        guard !recorder.recording else { return }
        UserDefaults.standard.set(playing, forKey: Self.tanpuraKey)
        recorder.tanpura = playing
    }

    /// The app left the screen: recording stops and keeps what it has; playback pauses.
    func onBackgrounded() {
        if recorder.recording { stopRecording() }
        pause()
    }

    // MARK: - Recording

    func allowMic() {
        Self.requestMic { granted in
            Task { @MainActor [weak self] in self?.micAllowed = granted }
        }
    }

    func startRecording() {
        guard active == nil, recorder.analysing == nil else { return }
        let id = UUID().uuidString.lowercased()
        let r = Self.makeRecorder(deliver: Self.levelHandler(self), limit: Self.limitHandler(self))
        do {
            #if DEBUG
            if let cents = syntheticSingerCents {
                try r.startSynthetic(
                    url: store.audioURL(id), saHz: Shruti.shared.saHz(midi: Int32(recorder.saMidi), cents: Int32(recorder.saCents)),
                    cents: cents, silentSeconds: recorder.tanpura ? TanpuraProfile.companion.LISTEN_SECONDS : 0)
            } else {
                try r.start(url: store.audioURL(id))
            }
            #else
            try r.start(url: store.audioURL(id))
            #endif
        } catch {
            _ = r.stop()
            store.deleteFiles(id)
            recorder.failed = true
            return
        }
        active = r
        recordingId = id
        recordingStarted = Int64(Date().timeIntervalSince1970 * 1000)
        recorder.recording = true
        recorder.seconds = 0
        recorder.level = 0
        recorder.failed = false
        UIApplication.shared.isIdleTimerDisabled = true
    }

    func stopRecording() {
        guard let r = active, let id = recordingId else { return }
        active = nil
        recordingId = nil
        UIApplication.shared.isIdleTimerDisabled = false
        let seconds = r.stop()
        recorder.recording = false
        recorder.level = 0
        guard seconds >= 0.5 else {
            store.deleteFiles(id)
            recorder.failed = true
            return
        }
        recorder.analysing = 0
        let created = recordingStarted
        let sa = (recorder.saMidi, recorder.saCents)
        let saHz = Shruti.shared.saHz(midi: Int32(sa.0), cents: Int32(sa.1))
        let tanpura = recorder.tanpura
        let url = store.audioURL(id)
        let store = self.store
        let progress = Self.progressHandler(self)
        Task.detached(priority: .userInitiated) {
            let track = try? RecordingDecoder.analyse(url: url, saHz: saHz, tanpura: tanpura, onProgress: progress)
            let saved: Bool
            if let track { saved = (try? store.writePitch(id, track)) != nil } else { saved = false }
            await MainActor.run { [weak self] in
                self?.onAnalysed(id: id, saved: saved, created: created, seconds: seconds, sa: sa, tanpura: tanpura)
            }
        }
    }

    private func onAnalysed(id: String, saved: Bool, created: Int64, seconds: Double, sa: (Int, Int), tanpura: Bool) {
        recorder.analysing = nil
        guard saved else {
            store.deleteFiles(id)
            recorder.failed = true
            return
        }
        let recording = Recording(
            id: id, createdMillis: created, durationSeconds: seconds, saMidi: Int32(sa.0), saCents: Int32(sa.1),
            ragaKey: nil, customTitle: nil, analysisVersion: RecordingLibrary.companion.ANALYSIS_VERSION, externalTanpura: tanpura, scale: nil
        )
        updateLibrary { $0.add(recording: recording) }
        justRecorded = id
    }

    // Built outside the main actor: the recorder calls these on the audio thread.

    nonisolated private static func makeRecorder(
        deliver: @escaping @Sendable (Float, Double) -> Void, limit: @escaping @Sendable () -> Void
    ) -> Recorder {
        Recorder(maxSeconds: Double(RecordingLibrary.companion.MAX_SECONDS), onLevel: deliver, onLimit: limit)
    }

    nonisolated private static func levelHandler(_ model: RecordingsViewModel) -> @Sendable (Float, Double) -> Void {
        { [weak model] rms, seconds in
            Task { @MainActor in
                guard let model, model.recorder.recording else { return }
                model.recorder.level = rms
                model.recorder.seconds = seconds
            }
        }
    }

    nonisolated private static func limitHandler(_ model: RecordingsViewModel) -> @Sendable () -> Void {
        { [weak model] in Task { @MainActor in model?.stopRecording() } }
    }

    nonisolated private static func progressHandler(_ model: RecordingsViewModel) -> @Sendable (Double) -> Void {
        { [weak model] p in Task { @MainActor in if model?.recorder.analysing != nil { model?.recorder.analysing = p } } }
    }

    // MARK: - One recording

    func open(_ id: String) {
        guard let recording = library.get(id: id) else { detail = nil; return }
        if detail?.recording.id == id { return }
        releasePlayer()
        detail = read(recording, track: store.readPitch(id))
        if recording.needsAnalysis { reanalyse(id) }
    }

    /// Reads the swaras again from the saved audio: automatically when the
    /// recording predates the current analysis (docs/plan-swiftf0-upgrade.md),
    /// and from a debug-only button for tuning. The old pitch stays until the
    /// new one is ready, and stays if analysis fails.
    func reanalyse(_ id: String) {
        guard let recording = library.get(id: id), !reanalysing else { return }
        reanalysing = true
        if detail?.recording.id == id { detail?.updating = 0 }
        let url = store.audioURL(id)
        let store = self.store
        let saHz = recording.saHz
        let tanpura = recording.externalTanpura
        let progress = Self.updatingHandler(self, id: id)
        Task.detached(priority: .userInitiated) {
            let track = try? RecordingDecoder.analyse(url: url, saHz: saHz, tanpura: tanpura, onProgress: progress)
            let saved = track.map { (try? store.writePitch(id, $0)) != nil } ?? false
            await MainActor.run { [weak self] in self?.onReanalysed(id: id, track: saved ? track : nil) }
        }
    }

    private func onReanalysed(id: String, track: PitchTrack?) {
        reanalysing = false
        if track != nil { updateLibrary { $0.analysed(id: id) } }
        guard var d = detail, d.recording.id == id, let current = library.get(id: id) else { return }
        let playing = d.playing
        let position = d.position
        if let track { d = read(current, track: track) }
        d.playing = playing
        d.position = position
        d.updating = nil
        detail = d
    }

    nonisolated private static func updatingHandler(_ model: RecordingsViewModel, id: String) -> @Sendable (Double) -> Void {
        { [weak model] p in
            Task { @MainActor in
                guard let model, model.detail?.recording.id == id, model.detail?.updating != nil else { return }
                model.detail?.updating = p
            }
        }
    }

    func close(_ id: String) {
        guard detail?.recording.id == id else { return }
        releasePlayer()
        detail = nil
    }

    private func read(_ recording: Recording, track: PitchTrack?) -> RecordingDetailUI {
        let transcription = track.map {
            SwaraTranscriber.shared.transcribe(track: $0, recording: recording, tuning: tuning)
        } ?? Transcription(phrases: [])
        var ui = RecordingDetailUI(recording: recording, track: track, transcription: transcription,
                                   lane: shared.PitchLane.companion.forRecording(transcription: transcription, raga: recording.raga))
        if recording.externalTanpura, let track, let offer = TanpuraProfile.companion.from(track: track, listenSeconds: TanpuraProfile.companion.LISTEN_SECONDS)?.suggestedSa(currentSaHz: recording.saHz) {
            ui.tanpuraSa = (Int(truncating: offer.first ?? 0), Int(truncating: offer.second ?? 0))
        }
        return ui
    }

    func rename(_ id: String, _ title: String) { updateLibrary { $0.rename(id: id, title: title) } }

    func changeSa(_ id: String, midi: Int, cents: Int) {
        updateLibrary { $0.changeSa(id: id, midi: Int32(midi), cents: Int32(cents)) }
    }

    func changeRaga(_ id: String, _ key: String?) { updateLibrary { $0.changeRaga(id: id, ragaKey: key) } }

    /// The recording's tanpura switch: the pitch is read differently, so the audio is analysed again.
    func changeTanpura(_ id: String, _ playing: Bool) {
        updateLibrary { $0.changeTanpura(id: id, playing: playing) }
        reanalyse(id)
    }

    func delete(_ id: String) {
        if playerFor == id { releasePlayer() }
        store.deleteFiles(id)
        updateLibrary { $0.delete(id: id) }
        if detail?.recording.id == id { detail = nil }
    }

    private func updateLibrary(_ transform: (RecordingLibrary) -> RecordingLibrary) {
        let next = transform(library)
        store.save(next)
        library = next
        guard var d = detail, let updated = next.get(id: d.recording.id) else { return }
        if updated.saHz != d.recording.saHz || updated.ragaKey != d.recording.ragaKey || updated.externalTanpura != d.recording.externalTanpura {
            let playing = d.playing
            let position = d.position
            let updating = d.updating
            d = read(updated, track: d.track)
            d.playing = playing
            d.position = position
            d.updating = updating
        } else {
            d.recording = updated
        }
        detail = d
    }

    // MARK: - Playback

    func togglePlay() {
        if detail?.playing == true { pause() } else { play() }
    }

    private func play() {
        guard let d = detail else { return }
        let id = d.recording.id
        if playerFor != id {
            releasePlayer()
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                player = try AVAudioPlayer(contentsOf: store.audioURL(id))
                player?.prepareToPlay()
                playerFor = id
            } catch {
                return
            }
        }
        guard let player else { return }
        if d.position >= d.recording.durationSeconds - 0.1 { player.currentTime = 0 } else { player.currentTime = d.position }
        player.play()
        detail?.playing = true
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            Task { @MainActor [weak self] in self?.tick() }
        }
    }

    private func tick() {
        guard let player, detail != nil else { return }
        if player.isPlaying {
            detail?.position = player.currentTime
        } else {
            timer?.invalidate()
            detail?.playing = false
            detail?.position = detail?.recording.durationSeconds ?? 0
        }
    }

    private func pause() {
        player?.pause()
        timer?.invalidate()
        detail?.playing = false
    }

    /// Moves playback to [seconds] (tapping a swara or the trace, or the position slider).
    func seek(to seconds: Double) {
        guard let d = detail else { return }
        let clamped = min(max(0, seconds), d.recording.durationSeconds)
        if playerFor == d.recording.id { player?.currentTime = clamped }
        detail?.position = clamped
    }

    private func releasePlayer() {
        timer?.invalidate()
        timer = nil
        player?.stop()
        player = nil
        playerFor = nil
        detail?.playing = false
    }

    // MARK: - Permission

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
}
