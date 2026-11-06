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
    var countIn = true
    var harmoniumLevel: Float = RenderOptions.companion.DEFAULT_HARMONIUM_LEVEL
    var droneLevel: Float = RenderOptions.companion.DEFAULT_DRONE_LEVEL
    var tuning: TuningChoice = .just
    /// Sing along with pitch feedback (needs the microphone).
    var singAlong = false
    /// Harmonium plays every note (true) or stays silent so only drone and click guide (false).
    var harmoniumGuide = true

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
        defaults.set(s.countIn, forKey: Key.countIn)
        defaults.set(s.harmoniumLevel, forKey: Key.harmoniumLevel)
        defaults.set(s.droneLevel, forKey: Key.droneLevel)
        defaults.set(s.tuning.rawValue, forKey: Key.tuning)
        defaults.set(s.singAlong, forKey: Key.singAlong)
        defaults.set(s.harmoniumGuide, forKey: Key.harmoniumGuide)
    }
}
