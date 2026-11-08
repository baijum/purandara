import SwiftUI

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
        if defaults.bool(forKey: "autoplay") {
            Task { @MainActor in
                while !viewModel.samplesLoaded { try? await Task.sleep(nanoseconds: 50_000_000) }
                if let varisai = viewModel.catalog.first(where: { $0.id == id }) { viewModel.play(varisai) }
            }
        }
        #endif
    }
}
