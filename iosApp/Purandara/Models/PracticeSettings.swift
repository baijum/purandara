import Foundation
import shared

/// The student's practice settings. Mirrors Android's `PracticeSettings`.
struct PracticeSettings: Equatable {
    enum TuningChoice: String, CaseIterable {
        case just, equal

        var kotlin: Tuning { self == .just ? .just : .equal }
    }

    var saMidi: Int = Int(Shruti.shared.DEFAULT_SA_MIDI)
    var tempo: Int = Int(PerformanceSettings.companion.DEFAULT_TEMPO)
    var kala: Int = 1
    var drone = true
    var click = false
    var loop = false
    var tuning: TuningChoice = .just

    static let saRange = Int(Shruti.shared.SA_RANGE.first)...Int(Shruti.shared.SA_RANGE.last)
    static let tempoRange = Int(PerformanceSettings.companion.TEMPO_RANGE.first)...Int(PerformanceSettings.companion.TEMPO_RANGE.last)
    static let kalaRange = Int(PerformanceSettings.companion.KALA_RANGE.first)...Int(PerformanceSettings.companion.KALA_RANGE.last)

    func performance() -> PerformanceSettings {
        PerformanceSettings.companion.create(
            saHz: Shruti.shared.saHz(midi: Int32(saMidi)),
            tempo: Int32(tempo),
            kala: Int32(kala),
            tuning: tuning.kotlin)
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
    }

    func load() -> PracticeSettings {
        var s = PracticeSettings()
        if let v = defaults.object(forKey: Key.sa) as? Int, PracticeSettings.saRange.contains(v) { s.saMidi = v }
        if let v = defaults.object(forKey: Key.tempo) as? Int, PracticeSettings.tempoRange.contains(v) { s.tempo = v }
        if let v = defaults.object(forKey: Key.kala) as? Int, PracticeSettings.kalaRange.contains(v) { s.kala = v }
        if let v = defaults.object(forKey: Key.drone) as? Bool { s.drone = v }
        if let v = defaults.object(forKey: Key.click) as? Bool { s.click = v }
        if let v = defaults.object(forKey: Key.loop) as? Bool { s.loop = v }
        if let raw = defaults.string(forKey: Key.tuning), let v = PracticeSettings.TuningChoice(rawValue: raw) { s.tuning = v }
        return s
    }

    func save(_ s: PracticeSettings) {
        defaults.set(s.saMidi, forKey: Key.sa)
        defaults.set(s.tempo, forKey: Key.tempo)
        defaults.set(s.kala, forKey: Key.kala)
        defaults.set(s.drone, forKey: Key.drone)
        defaults.set(s.click, forKey: Key.click)
        defaults.set(s.loop, forKey: Key.loop)
        defaults.set(s.tuning.rawValue, forKey: Key.tuning)
    }
}
