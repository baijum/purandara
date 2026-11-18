import AVFoundation
import SwiftUI
import shared

extension View {
    @ViewBuilder func outputChangeTestControl() -> some View {
        #if DEBUG
        toolbar {
            if UserDefaults.standard.bool(forKey: "simulateOutputChange") {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        NotificationCenter.default.post(
                            name: AVAudioSession.routeChangeNotification, object: nil,
                            userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.newDeviceAvailable.rawValue,
                                       "purandara.testOutputChange": true])
                    } label: { Image(systemName: "headphones") }
                    .accessibilityLabel("Simulate output change")
                }
            }
        }
        #else
        self
        #endif
    }
}

@main
struct PurandaraApp: App {
    // Recordings first: it loads the student's own ragas (Ragas.useOwn), and the raga chosen
    // for the varisais may be one of them (docs/plan-own-raga-varisais.md).
    @StateObject private var recordings = RecordingsViewModel()
    @StateObject private var viewModel = PracticeViewModel()
    @StateObject private var navigation = AppNavigation()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
            .environmentObject(navigation)
            .environmentObject(recordings) // first: it loads the own ragas the varisais' raga may be one of
            .environmentObject(viewModel)
            .onAppear {
                // A new own raga (or one of the app's) chosen from the varisais' raga list.
                recordings.onVarisaiRaga = { [weak viewModel] key in viewModel?.settings.ragaKey = key }
                openFromLaunchArguments()
            }
        }
        .onChange(of: scenePhase) { phase in
            // Practice audio stops when the app leaves the screen, and resumes
            // (a fresh session) on return (issue #11).
            if phase == .background {
                viewModel.onBackgrounded()
                recordings.onBackgrounded()
            }
            if phase == .active { viewModel.onForegrounded() }
        }
    }

    /// Debug aid for screenshots, UI tests and manual checks:
    /// `-open sarali-4` opens an exercise (`-open shruti` / `-open find-sa` the Sa screens, `-open recordings` / `-open record` the recordings,
    /// `-open set-SARALI` a set page, `-open about` About), `-autoplay YES` also starts it once samples load,
    /// `-singAlong YES` turns sing-along on, `-singer <cents>` uses a synthetic singer,
    /// `-resetSettings YES` starts from default settings and no recordings (UI tests), `-sa <midi>` then picks a Sa,
    /// `-raga <key>` the raga for the varisais (`kalyani`), `-swaraNumbers off|otherRagas|always`.
    private func openFromLaunchArguments() {
        #if DEBUG
        let defaults = UserDefaults.standard
        if defaults.bool(forKey: "resetSettings") {
            viewModel.settings = PracticeSettings()
            viewModel.resetHistory()
            viewModel.resetRunPlans()
            recordings.resetAll()
        }
        let sa = defaults.integer(forKey: "sa") // launch arguments arrive as strings
        if PracticeSettings.saRange.contains(sa) { viewModel.settings.saMidi = sa }
        if let raga = defaults.string(forKey: "raga"), Ragas.shared.melakarta(key: raga) != nil { viewModel.settings.ragaKey = raga }
        if let raw = defaults.string(forKey: "swaraNumbers"), let v = PracticeSettings.SwaraNumbersChoice(rawValue: raw) {
            viewModel.settings.swaraNumbers = v
        }
        guard let id = defaults.string(forKey: "open") else { return }
        if id == "recordings" || id == "record" {
            recordings.prepare(tuning: viewModel.settings.tuning.kotlin, saMidi: viewModel.settings.saMidi, saCents: viewModel.settings.saCents,
                               strictness: viewModel.settings.strictness.kotlin)
            navigation.open(id)
            return
        }
        navigation.open(id)
        if defaults.bool(forKey: "singAlong") { viewModel.settings.singAlong = true }
        if let raw = defaults.string(forKey: "practiceMode"), let mode = PracticeSettings.ModeChoice(rawValue: raw) {
            viewModel.settings.practiceMode = mode
        }
        if let raw = defaults.string(forKey: "recoveryMode"), let mode = PracticeSettings.RecoveryChoice(rawValue: raw) {
            viewModel.settings.recoveryMode = mode
        }
        if defaults.object(forKey: "repeatPractice") != nil { viewModel.settings.loop = defaults.bool(forKey: "repeatPractice") }
        let recovery = defaults.integer(forKey: "recoverySeconds")
        if (1...30).contains(recovery) { viewModel.settings.recoverySeconds = recovery }
        let tempo = defaults.integer(forKey: "tempo")
        if PracticeSettings.tempoRange.contains(tempo) { viewModel.settings.tempo = tempo }
        let kala = defaults.integer(forKey: "kala")
        if PracticeSettings.kalaRange.contains(kala) { viewModel.settings.kala = kala }
        if defaults.object(forKey: "countIn") != nil { viewModel.settings.countIn = defaults.bool(forKey: "countIn") }
        if defaults.bool(forKey: "autoplay") {
            Task { @MainActor in
                while !viewModel.samplesLoaded { try? await Task.sleep(nanoseconds: 50_000_000) }
                if let varisai = viewModel.catalog.first(where: { $0.id == id }) { viewModel.play(varisai) }
            }
        }
        #endif
    }
}
