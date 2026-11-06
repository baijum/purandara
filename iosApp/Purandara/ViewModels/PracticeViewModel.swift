import Foundation
import shared

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
        }
    }
    @Published private(set) var samplesLoaded = false
    @Published private(set) var loadError = false
    @Published private(set) var playback = PlaybackState()

    let catalog: [Varisai] = VarisaiCatalog.shared.all

    private let store: PracticeSettingsStore
    private let player = VarisaiAudioPlayer()
    private var bank: SampleBank?
    private var current: Varisai?
    private var renderer: SampleRenderer?
    private var schedule: Schedule?
    private var timer: Timer?

    init(store: PracticeSettingsStore = .standard, loadSamples: Bool = true) {
        self.store = store
        settings = store.load()
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
            options: RenderOptions.companion.practice(drone: settings.drone, click: settings.click, loop: settings.loop))
        current = varisai
        self.schedule = schedule
        self.renderer = renderer
        do {
            try player.play(renderer) { [weak self] in self?.finished(renderer) }
        } catch {
            stop()
            return
        }
        playback = PlaybackState(playing: true, repetitions: Int(schedule.repetitions))
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    /// Fades out; the state returns to stopped once the fade has played.
    func stop() {
        if player.isPlaying {
            player.stop()
        } else {
            finished(renderer)
        }
    }

    /// Stops at once (leaving the screen, going to the background).
    func stopImmediately() {
        player.stopImmediately()
        finished(renderer)
    }

    private func finished(_ which: SampleRenderer?) {
        guard which === renderer else { return }
        stopTimer()
        renderer = nil
        schedule = nil
        playback = PlaybackState(repetitions: playback.repetitions)
    }

    private func tick() {
        guard let renderer, let schedule, let frames = player.positionFrames() else { return }
        let seconds = renderer.passSeconds(frame: frames)
        let position = schedule.cursorAt(seconds: seconds)
        var next = playback
        next.repetition = position.map { Int($0.repetition) }
        next.avarta = position.map { Int($0.avarta) }
        next.akshara = position.map { Int($0.akshara) }
        next.slot = position.map { Int($0.slot) }
        next.beatInAvarta = schedule.beatAt(seconds: seconds).map { Int($0.beatInAvarta) }
        if next != playback { playback = next }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
}
