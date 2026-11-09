import AVFoundation
import SwiftUI

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
    @StateObject private var viewModel = PracticeViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var path: [String] = []

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $path) {
                LibraryView(path: $path)
            }
            .environmentObject(viewModel)
            .onAppear(perform: openFromLaunchArguments)
        }
        .onChange(of: scenePhase) { phase in
            // Practice audio stops when the app leaves the screen, and resumes
            // (a fresh session) on return (issue #11).
            if phase == .background { viewModel.onBackgrounded() }
            if phase == .active { viewModel.onForegrounded() }
        }
    }

    /// Debug aid for screenshots, UI tests and manual checks:
    /// `-open sarali-4` opens an exercise (`-open shruti` / `-open find-sa` the Sa screens), `-autoplay YES` also starts it once samples load,
    /// `-singAlong YES` turns sing-along on, `-singer <cents>` uses a synthetic singer,
    /// `-resetSettings YES` starts from default settings (UI tests), `-sa <midi>` then picks a Sa.
    private func openFromLaunchArguments() {
        #if DEBUG
        let defaults = UserDefaults.standard
        if defaults.bool(forKey: "resetSettings") {
            viewModel.settings = PracticeSettings()
            viewModel.resetHistory()
        }
        let sa = defaults.integer(forKey: "sa") // launch arguments arrive as strings
        if PracticeSettings.saRange.contains(sa) { viewModel.settings.saMidi = sa }
        guard let id = defaults.string(forKey: "open") else { return }
        path = [id]
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
