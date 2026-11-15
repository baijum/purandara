import SwiftUI
import shared

/// Where the app is (docs/plan-navigation.md): tabs on compact width, a
/// sidebar on regular width. Both are kept in step so a launch argument (or a
/// size-class change) lands in the same place. Destination ids are the shared
/// `NavDestination` ids ("home", "set-SARALI", "shruti", "find-sa",
/// "recordings", "about"); pushed screens use the existing path ids (a varisai
/// id, "run", "record", "recording:<id>").
@MainActor
final class AppNavigation: ObservableObject {
    enum Tab: Hashable { case practise, sa, recordings }

    @Published var tab: Tab = .practise
    @Published var practisePath: [String] = []
    @Published var saPath: [String] = []
    @Published var recordingsPath: [String] = []

    /// The sidebar's chosen destination and the detail column's pushed screens.
    @Published var selection: String? = NavDestination.Home.shared.id
    @Published var detailPath: [String] = []

    static func setId(_ set: VarisaiSet) -> String { NavDestination.SetPage(set: set).id }

    /// Goes to a destination or opens a screen (launch arguments).
    func open(_ id: String) {
        detailPath = []
        switch id {
        case "home", "about":
            tab = .practise
            practisePath = id == "about" ? [id] : []
            selection = id
        case "shruti", "find-sa":
            tab = .sa
            saPath = [id]
            selection = id
        case "recordings", "record":
            tab = .recordings
            recordingsPath = id == "record" ? ["record"] : []
            selection = "recordings"
            if id == "record" { detailPath = ["record"] }
        default:
            tab = .practise
            practisePath = [id]
            if NavDestination.companion.fromId(id: id) is NavDestination.SetPage {
                selection = id
            } else {
                selection = "home"
                detailPath = [id]
            }
        }
    }
}

/// Tabs on compact width (iPhone, split screen), a sidebar on regular width (iPad).
struct RootView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        if sizeClass == .regular { SidebarRoot() } else { TabsRoot() }
    }
}

// MARK: - Tabs (compact width)

private struct TabsRoot: View {
    @EnvironmentObject private var nav: AppNavigation

    var body: some View {
        TabView(selection: $nav.tab) {
            NavigationStack(path: $nav.practisePath) {
                HomeView(showAbout: true) { set in nav.practisePath.append(AppNavigation.setId(set)) }
                    .appDestinations(path: $nav.practisePath)
            }
            .tabItem { Label("Practise", systemImage: "music.note.list") }
            .tag(AppNavigation.Tab.practise)

            NavigationStack(path: $nav.saPath) {
                SaHubView(path: $nav.saPath)
                    .appDestinations(path: $nav.saPath)
            }
            .tabItem { Label("Sa", systemImage: "tuningfork") }
            .tag(AppNavigation.Tab.sa)

            NavigationStack(path: $nav.recordingsPath) {
                RecordingsRoot(path: $nav.recordingsPath)
                    .appDestinations(path: $nav.recordingsPath)
            }
            .tabItem { Label("Recordings", systemImage: "mic") }
            .tag(AppNavigation.Tab.recordings)
        }
        .onChange(of: nav.tab) { _ in
            // Shruti practice and Find my Sa stop when their tab is left; coming
            // back shows the Sa page, not a practice that restarted on its own.
            if nav.tab != .sa { nav.saPath = [] }
        }
    }
}

// MARK: - Sidebar (regular width)

private struct SidebarRoot: View {
    @EnvironmentObject private var nav: AppNavigation
    /// Folded set groups, for this session (they start open).
    @State private var folded: Set<String> = []

    private func expanded(_ group: NavGroup) -> Binding<Bool> {
        Binding(get: { !folded.contains(group.name) },
                set: { open in if open { folded.remove(group.name) } else { folded.insert(group.name) } })
    }

    var body: some View {
        NavigationSplitView {
            List(selection: $nav.selection) {
                SidebarRow(id: NavDestination.Home.shared.id)
                // Group headings as rows (section headers cap Dynamic Type); the set
                // groups fold open and shut, as on Android.
                ForEach(NavGroup.entries, id: \.name) { group in
                    if group.foldable {
                        DisclosureGroup(isExpanded: expanded(group)) {
                            ForEach(group.destinations, id: \.id) { SidebarRow(id: $0.id) }
                        } label: {
                            GroupHeading(title: group.title)
                        }
                    } else {
                        GroupHeading(title: group.title)
                        ForEach(group.destinations, id: \.id) { SidebarRow(id: $0.id) }
                    }
                }
                Section {
                    SidebarRow(id: NavDestination.About.shared.id)
                }
            }
            .listStyle(.sidebar)
            .accessibilityIdentifier("sidebar")
            .navigationTitle("Purandara")
        } detail: {
            NavigationStack(path: $nav.detailPath) {
                destinationRoot
                    .id(nav.selection)
                    .appDestinations(path: $nav.detailPath)
            }
        }
        .onChange(of: nav.selection) { _ in nav.detailPath = [] }
    }

    @ViewBuilder private var destinationRoot: some View {
        let id = nav.selection ?? "home"
        switch id {
        case "shruti": ShrutiView().outputChangeTestControl()
        case "find-sa": FindSaView { nav.selection = "home" }.outputChangeTestControl()
        case "recordings": RecordingsRoot(path: $nav.detailPath)
        case "about": AboutView()
        default:
            if let page = NavDestination.companion.fromId(id: id) as? NavDestination.SetPage {
                SetView(set: page.set, path: $nav.detailPath)
            } else {
                HomeView(showAbout: false) { set in nav.selection = AppNavigation.setId(set) }
            }
        }
    }
}

/// A group's name in the sidebar.
private struct GroupHeading: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Color.subdued)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A sidebar row: an image and a title that wraps (`Label` clips in list rows).
private struct SidebarRow: View {
    let id: String
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        HStack(spacing: 12) {
            // Decorative: at accessibility text sizes the title gets the whole width.
            // The system tints it (the accent colour would vanish on the selected row).
            if !typeSize.isAccessibilitySize {
                Image(systemName: NavText.icon(id))
                    .frame(width: 28)
                    .accessibilityHidden(true)
            }
            Text(NavText.title(id)).fixedSize(horizontal: false, vertical: true)
        }
        .tag(id)
    }
}

/// Titles and symbols for the menu's destinations (set pages by their set's title).
enum NavText {
    static func title(_ id: String) -> String {
        switch id {
        case "home": return String(localized: "Home")
        case "shruti": return String(localized: "Shruti practice")
        case "find-sa": return String(localized: "Find my Sa")
        case "recordings": return String(localized: "Recordings")
        case "about": return String(localized: "About")
        default: return (NavDestination.companion.fromId(id: id) as? NavDestination.SetPage)?.set.title ?? id
        }
    }

    static func icon(_ id: String) -> String {
        switch id {
        case "home": return "house"
        case "shruti": return "waveform"
        case "find-sa": return "person.wave.2"
        case "recordings": return "mic"
        case "about": return "info.circle"
        default: return "stairs"
        }
    }
}

// MARK: - Pushed screens

extension View {
    /// The screens any stack can push: an exercise, a run, Shruti practice,
    /// Find my Sa, a set page, About, the recorder and a recording.
    func appDestinations(path: Binding<[String]>) -> some View {
        navigationDestination(for: String.self) { id in
            AppDestination(id: id, path: path)
        }
    }
}

private struct AppDestination: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    let id: String
    @Binding var path: [String]

    var body: some View {
        if id == "shruti" {
            ShrutiView().outputChangeTestControl()
        } else if id == "find-sa" {
            FindSaView().outputChangeTestControl()
        } else if id == "run" {
            RunView().outputChangeTestControl()
        } else if id == "about" {
            AboutView()
        } else if id == "recordings" {
            RecordingsRoot(path: $path)
        } else if id == "record" {
            RecordView(path: $path)
        } else if id.hasPrefix("recording:") {
            RecordingDetailView(id: String(id.dropFirst("recording:".count)), path: $path)
        } else if let page = NavDestination.companion.fromId(id: id) as? NavDestination.SetPage {
            SetView(set: page.set, path: $path)
        } else if let varisai = VarisaiCatalog.shared.byId(id: id) {
            PlayerView(varisai: varisai)
                .outputChangeTestControl()
                .onAppear { viewModel.stopTanpura() }
        }
    }
}

/// The recordings list, with new recordings at the current Sa and tuning (docs/plan-recordings.md).
private struct RecordingsRoot: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @EnvironmentObject private var recordings: RecordingsViewModel
    @Binding var path: [String]

    var body: some View {
        RecordingsListView(path: $path)
            .onAppear {
                viewModel.stopTanpura()
                recordings.prepare(tuning: viewModel.settings.tuning.kotlin, saMidi: viewModel.settings.saMidi,
                                   saCents: viewModel.settings.saCents, strictness: viewModel.settings.strictness.kotlin)
            }
    }
}

// MARK: - The Sa tab

/// The Sa tab (compact width): Your Sa with the tanpura, then Shruti practice and Find my Sa.
struct SaHubView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Binding var path: [String]

    var body: some View {
        List {
            SaSection()
            Section {
                // Buttons through the path: navigation links sharing a list all open the first.
                hubButton("Shruti practice", systemImage: "waveform",
                          hint: "Hold Sa against the tanpura and see your pitch") { path.append("shruti") }
                hubButton("Find my Sa", systemImage: "person.wave.2",
                          hint: "Sing two comfortable notes to find a Sa for your voice") { path.append("find-sa") }
            } header: {
                Text("Sa & voice")
                    .foregroundStyle(Color.subdued)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .navigationTitle("Sa")
    }

    private func hubButton(_ title: LocalizedStringKey, systemImage: String, hint: LocalizedStringKey,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage).foregroundStyle(Color.accentColor).accessibilityHidden(true)
                Text(title).foregroundStyle(.primary).fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").foregroundStyle(Color.subdued).accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.samplesLoaded)
        .accessibilityHint(Text(hint))
    }
}

// MARK: - About

/// The app's website: the guide and the Learn pages. Opened only when the student taps it.
private let websiteURL = URL(string: "https://baijum.github.io/purandara/")!

/// About: version, what the app is, its licence, and a link to the website.
struct AboutView: View {
    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Purandara").font(.title2.bold())
                    Text("Version \(version)").foregroundStyle(Color.subdued)
                    Text("Purandara is a free app for practising Carnatic music, named after Purandara Dasa, the Pitamaha of Carnatic music. It works fully offline: no accounts, no ads, no tracking.")
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            }
            // The heading in the section, not its header: list section headers cap Dynamic Type.
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Licence").font(.headline).accessibilityAddTraits(.isHeader)
                    Text("Free software under the MIT licence. The tanpura and harmonium recordings, fonts and pitch model are credited in ATTRIBUTION.md, with their own licences.")
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            }
            Section {
                Link(destination: websiteURL) {
                    Text("Open the guide on the website").fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityHint(Text("Opens your browser"))
            }
        }
        .navigationTitle("About")
    }
}
