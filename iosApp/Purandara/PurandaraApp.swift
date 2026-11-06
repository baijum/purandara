import SwiftUI

@main
struct PurandaraApp: App {
    @StateObject private var viewModel = PracticeViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var path: [String] = []

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $path) {
                LibraryView()
            }
            .environmentObject(viewModel)
            .onAppear(perform: openFromLaunchArguments)
        }
        .onChange(of: scenePhase) { phase in
            // Practice audio stops when the app leaves the screen.
            if phase == .background { viewModel.stopImmediately() }
        }
    }

    /// Debug aid for screenshots and manual checks:
    /// `-open sarali-4` opens an exercise, `-autoplay YES` also starts it once samples load.
    private func openFromLaunchArguments() {
        #if DEBUG
        let defaults = UserDefaults.standard
        guard let id = defaults.string(forKey: "open") else { return }
        path = [id]
        if defaults.bool(forKey: "autoplay") {
            Task { @MainActor in
                while !viewModel.samplesLoaded { try? await Task.sleep(nanoseconds: 50_000_000) }
                if let varisai = viewModel.catalog.first(where: { $0.id == id }) { viewModel.play(varisai) }
            }
        }
        #endif
    }
}
