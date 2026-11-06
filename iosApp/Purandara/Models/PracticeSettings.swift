import Foundation
import shared

/// The student's practice settings. Mirrors Android's `PracticeSettings`.
struct PracticeSettings: Equatable {
    enum TuningChoice: String, CaseIterable {
        case just, equal

        var kotlin: Tuning { self == .just ? .just : .equal }
    }

    /// What counts as in tune and close (docs/plan-shruti.md).
    enum StrictnessChoice: String, CaseIterable {
        case gentle, steady, precise

        var kotlin: Strictness {
            switch self {
            case .gentle: return .gentle
            case .steady: return .steady
            case .precise: return .precise
            }
        }
    }

    /// What a gitam's notation shows: swaras, words or both.
    enum WordsChoice: String, CaseIterable {
        case swaras, words, both

        var kotlin: WordsDisplay {
            switch self {
            case .swaras: return .swaras
            case .words: return .words
            case .both: return .both
            }
        }
    }

    var saMidi: Int = Int(Shruti.shared.DEFAULT_SA_MIDI)
    var tempo: Int = Int(PerformanceSettings.companion.DEFAULT_TEMPO)
    var kala: Int = 1
    var drone = true
    var click = false
    var loop = false
    var countIn = true
    var harmoniumLevel: Float = RenderOptions.companion.DEFAULT_HARMONIUM_LEVEL
    var droneLevel: Float = RenderOptions.companion.DEFAULT_DRONE_LEVEL
    var tuning: TuningChoice = .just
    /// Sing along with pitch feedback (needs the microphone).
    var singAlong = false
    /// Harmonium plays every note (true) or stays silent so only drone and click guide (false).
    var harmoniumGuide = true
    /// Sing-along cancels the app's own sound from the mic when playing through the speaker.
    var echoCancellation = true
    var strictness: StrictnessChoice = .precise
    /// What a gitam's notation shows (shared `WordsDisplay`).
    var words: WordsChoice = .both
    /// Fine-tune of Sa in cents (`Shruti.FINE_TUNE_RANGE`).
    var saCents = 0

    static let saRange = Int(Shruti.shared.SA_RANGE.first)...Int(Shruti.shared.SA_RANGE.last)
    static let tempoRange = Int(PerformanceSettings.companion.TEMPO_RANGE.first)...Int(PerformanceSettings.companion.TEMPO_RANGE.last)
    static let kalaRange = Int(PerformanceSettings.companion.KALA_RANGE.first)...Int(PerformanceSettings.companion.KALA_RANGE.last)

    static let fineTuneRange = Int(Shruti.shared.FINE_TUNE_RANGE.first)...Int(Shruti.shared.FINE_TUNE_RANGE.last)

    var saHz: Double { Shruti.shared.saHz(midi: Int32(saMidi), cents: Int32(saCents)) }

    func performance() -> PerformanceSettings {
        PerformanceSettings.companion.create(
            saHz: saHz,
            tempo: Int32(tempo),
            kala: Int32(kala),
            tuning: tuning.kotlin)
    }

    /// Sing-along forces the count-in (leakage is measured while the student is silent).
    func renderOptions() -> RenderOptions {
        RenderOptions.companion.practice(
            drone: drone, click: click, loop: loop, countIn: countIn || singAlong,
            harmoniumLevel: singAlong && !harmoniumGuide ? 0 : harmoniumLevel, droneLevel: droneLevel)
    }
}

/// Persists `PracticeSettings` in `UserDefaults`. Values out of range fall back to defaults.
struct PracticeSettingsStore {
    let defaults: UserDefaults

    static var standard: PracticeSettingsStore { PracticeSettingsStore(defaults: .standard) }

    private enum Key {
        static let sa = "practice.saMidi"
        static let tempo = "practice.tempo"
        static let kala = "practice.kala"
        static let drone = "practice.drone"
        static let click = "practice.click"
        static let loop = "practice.loop"
        static let tuning = "practice.tuning"
        static let countIn = "practice.countIn"
        static let harmoniumLevel = "practice.harmoniumLevel"
        static let droneLevel = "practice.droneLevel"
        static let singAlong = "practice.singAlong"
        static let harmoniumGuide = "practice.harmoniumGuide"
        static let echoCancellation = "practice.echoCancellation"
        static let history = "practice.history"
        static let strictness = "practice.strictness"
        static let saCents = "practice.saCents"
        static let words = "practice.words"
    }

    func load() -> PracticeSettings {
        var s = PracticeSettings()
        if let v = defaults.object(forKey: Key.sa) as? Int, PracticeSettings.saRange.contains(v) { s.saMidi = v }
        if let v = defaults.object(forKey: Key.tempo) as? Int, PracticeSettings.tempoRange.contains(v) { s.tempo = v }
        if let v = defaults.object(forKey: Key.kala) as? Int, PracticeSettings.kalaRange.contains(v) { s.kala = v }
        if let v = defaults.object(forKey: Key.drone) as? Bool { s.drone = v }
        if let v = defaults.object(forKey: Key.click) as? Bool { s.click = v }
        if let v = defaults.object(forKey: Key.loop) as? Bool { s.loop = v }
        if let v = defaults.object(forKey: Key.countIn) as? Bool { s.countIn = v }
        if let v = defaults.object(forKey: Key.harmoniumLevel) as? Float, (0...1).contains(v) { s.harmoniumLevel = v }
        if let v = defaults.object(forKey: Key.droneLevel) as? Float, (0...1).contains(v) { s.droneLevel = v }
        if let v = defaults.object(forKey: Key.singAlong) as? Bool { s.singAlong = v }
        if let v = defaults.object(forKey: Key.harmoniumGuide) as? Bool { s.harmoniumGuide = v }
        if let v = defaults.object(forKey: Key.echoCancellation) as? Bool { s.echoCancellation = v }
        if let raw = defaults.string(forKey: Key.strictness), let v = PracticeSettings.StrictnessChoice(rawValue: raw) { s.strictness = v }
        if let v = defaults.object(forKey: Key.saCents) as? Int, PracticeSettings.fineTuneRange.contains(v) { s.saCents = v }
        if let raw = defaults.string(forKey: Key.tuning), let v = PracticeSettings.TuningChoice(rawValue: raw) { s.tuning = v }
        if let raw = defaults.string(forKey: Key.words), let v = PracticeSettings.WordsChoice(rawValue: raw) { s.words = v }
        return s
    }

    func loadHistory() -> PracticeHistory {
        PracticeHistory.companion.decode(text: defaults.string(forKey: Key.history))
    }

    func saveHistory(_ history: PracticeHistory) {
        defaults.set(history.encode(), forKey: Key.history)
    }

    func save(_ s: PracticeSettings) {
        defaults.set(s.saMidi, forKey: Key.sa)
        defaults.set(s.tempo, forKey: Key.tempo)
        defaults.set(s.kala, forKey: Key.kala)
        defaults.set(s.drone, forKey: Key.drone)
        defaults.set(s.click, forKey: Key.click)
        defaults.set(s.loop, forKey: Key.loop)
        defaults.set(s.countIn, forKey: Key.countIn)
        defaults.set(s.harmoniumLevel, forKey: Key.harmoniumLevel)
        defaults.set(s.droneLevel, forKey: Key.droneLevel)
        defaults.set(s.tuning.rawValue, forKey: Key.tuning)
        defaults.set(s.singAlong, forKey: Key.singAlong)
        defaults.set(s.harmoniumGuide, forKey: Key.harmoniumGuide)
        defaults.set(s.echoCancellation, forKey: Key.echoCancellation)
        defaults.set(s.strictness.rawValue, forKey: Key.strictness)
        defaults.set(s.saCents, forKey: Key.saCents)
        defaults.set(s.words.rawValue, forKey: Key.words)
    }
}
