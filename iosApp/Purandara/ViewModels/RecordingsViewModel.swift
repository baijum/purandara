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
}

/// One recording opened: its swaras, trace and playback. Mirrors Android's `RecordingDetailUi`.
struct RecordingDetailUI {
    var recording: Recording
    var track: PitchTrack?
    var transcription: Transcription
    var lane: shared.PitchLane
    var playing = false
    var position = 0.0
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
    }
    #endif

    /// New recordings use this Sa; swaras are read in [tuning].
    func prepare(tuning: Tuning, saMidi: Int, saCents: Int) {
        self.tuning = tuning
        if !recorder.recording && recorder.analysing == nil {
            recorder = RecorderUI(saMidi: saMidi, saCents: saCents)
        }
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
                try r.startSynthetic(url: store.audioURL(id), saHz: Shruti.shared.saHz(midi: Int32(recorder.saMidi), cents: Int32(recorder.saCents)), cents: cents)
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
        let url = store.audioURL(id)
        let store = self.store
        let progress = Self.progressHandler(self)
        Task.detached(priority: .userInitiated) {
            let track = try? RecordingDecoder.analyse(url: url, onProgress: progress)
            let saved: Bool
            if let track { saved = (try? store.writePitch(id, track)) != nil } else { saved = false }
            await MainActor.run { [weak self] in
                self?.onAnalysed(id: id, saved: saved, created: created, seconds: seconds, sa: sa)
            }
        }
    }

    private func onAnalysed(id: String, saved: Bool, created: Int64, seconds: Double, sa: (Int, Int)) {
        recorder.analysing = nil
        guard saved else {
            store.deleteFiles(id)
            recorder.failed = true
            return
        }
        let recording = Recording(
            id: id, createdMillis: created, durationSeconds: seconds, saMidi: Int32(sa.0), saCents: Int32(sa.1),
            ragaKey: nil, customTitle: nil, analysisVersion: RecordingLibrary.companion.ANALYSIS_VERSION
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
    }

    func close(_ id: String) {
        guard detail?.recording.id == id else { return }
        releasePlayer()
        detail = nil
    }

    private func read(_ recording: Recording, track: PitchTrack?) -> RecordingDetailUI {
        let transcription = track.map {
            SwaraTranscriber.shared.transcribe(track: $0, saHz: recording.saHz, raga: recording.raga, tuning: tuning)
        } ?? Transcription(phrases: [])
        return RecordingDetailUI(recording: recording, track: track, transcription: transcription,
                                 lane: shared.PitchLane.companion.forRecording(transcription: transcription, raga: recording.raga))
    }

    func rename(_ id: String, _ title: String) { updateLibrary { $0.rename(id: id, title: title) } }

    func changeSa(_ id: String, midi: Int, cents: Int) {
        updateLibrary { $0.changeSa(id: id, midi: Int32(midi), cents: Int32(cents)) }
    }

    func changeRaga(_ id: String, _ key: String?) { updateLibrary { $0.changeRaga(id: id, ragaKey: key) } }

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
        if updated.saHz != d.recording.saHz || updated.ragaKey != d.recording.ragaKey {
            let playing = d.playing
            let position = d.position
            d = read(updated, track: d.track)
            d.playing = playing
            d.position = position
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
