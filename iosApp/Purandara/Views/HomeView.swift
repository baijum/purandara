import SwiftUI
import shared

/// Home: continue / next up, the student's Sa with a tanpura to tune to,
/// then the sets by group, each leading to its page (docs/plan-landing.md,
/// docs/plan-navigation.md). Mirrors Android's `HomeScreen`.
struct HomeView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dynamicTypeSize) private var typeSize
    /// An About button in the toolbar (the tabs; the sidebar lists About itself).
    let showAbout: Bool
    let openSet: (VarisaiSet) -> Void

    /// At accessibility text sizes the drawings give their space to text.
    private var showContours: Bool { !typeSize.isAccessibilitySize }

    var body: some View {
        List {
            LoadStatus()
            continueSection
            SaSection()
            ragaSection
            ForEach(NavGroup.entries.filter(\.foldable), id: \.name) { group in
                Section {
                    ForEach(group.destinations.compactMap { ($0 as? NavDestination.SetPage)?.set }, id: \.name) { set in
                        setRow(set)
                    }
                } header: {
                    Text(group.title)
                        .foregroundStyle(Color.subdued)
                        .accessibilityAddTraits(.isHeader)
                }
            }
        }
        .navigationTitle("Purandara")
        .toolbar {
            if showAbout {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(value: "about") {
                        Image(systemName: "info.circle")
                    }
                    .accessibilityLabel(Text("About"))
                }
            }
        }
        .safeAreaInset(edge: .top) {
            Text("\(viewModel.settings.raga.name) · Adi tala")
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
        }
    }

    /// A set: its title and progress, leading to its page.
    private func setRow(_ set: VarisaiSet) -> some View {
        let total = viewModel.catalog.filter { $0.set == set }.count
        let practised = Int(viewModel.history.practisedCount(set: set, catalog: viewModel.catalog))
        let detail = practised > 0 ? String(localized: "\(practised) of \(total) practised") : (total == 1 ? String(localized: "1 exercise") : String(localized: "\(total) exercises"))
        return Button { openSet(set) } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(set.title).foregroundStyle(.primary)
                    Text(detail).font(.subheadline).foregroundStyle(Color.subdued)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .foregroundStyle(Color.subdued)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(set.title), \(detail)"))
        .accessibilityAddTraits(.isButton)
    }

    /// The raga the varisais and alankarams are sung in (docs/plan-other-ragas.md),
    /// below Your Sa: kept out of the Sa section so the first screen stays short at large text.
    private var ragaSection: some View {
        Section {
            NavigationLink {
                VarisaiRagaList(ragaKey: $viewModel.settings.ragaKey)
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(viewModel.settings.raga.name)
                    Text("Gitams, swarajatis and varnams keep their own.")
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .accessibilityHint(Text("Choose the raga the varisais and alankarams are sung in."))
        } header: {
            Text("Raga for the varisais")
                .foregroundStyle(Color.subdued)
                .accessibilityAddTraits(.isHeader)
        }
    }

    // MARK: - Continue / next up

    @ViewBuilder private var continueSection: some View {
        let last = viewModel.history.continueWith(catalog: viewModel.catalog)
        let next = last == nil ? viewModel.catalog.first : viewModel.history.nextUp(catalog: viewModel.catalog)
        Section {
            if let last {
                let record = viewModel.history.get(varisaiId: last.id)
                let detail = Self.detail(record)
                NavigationLink(value: last.id) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(last.title).font(.title3.bold())
                            if !detail.isEmpty { Text(detail).font(.subheadline) }
                        }
                        Spacer()
                        if showContours {
                            ContourShape(varisai: last)
                                .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                                .frame(width: 110, height: 30)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text([String(localized: "Continue \(last.title)"), detail].filter { !$0.isEmpty }.joined(separator: ", ")))
                .accessibilityAddTraits(.isButton)
            }
            if let next {
                NavigationLink(value: next.id) {
                    Text(last == nil ? next.title : String(localized: "Next up: \(next.title)"))
                }
            }
        } header: {
            Text(last == nil ? "Start here" : "Continue")
                .foregroundStyle(Color.subdued)
                .accessibilityAddTraits(.isHeader)
        }
    }

    /// "Yesterday · best 14/16".
    private static func detail(_ record: PracticeRecord?) -> String {
        guard let record else { return "" }
        var parts = [relativeDay(record.lastPracticedMillis)]
        if record.hasBest { parts.append(String(localized: "best \(Int(record.bestInTuneOrClose))/\(Int(record.bestJudged))")) }
        return parts.joined(separator: " · ")
    }

    /// "Today", "Yesterday", "3 days ago": calendar days, in the user's language.
    private static func relativeDay(_ millis: Int64) -> String {
        let calendar = Calendar.current
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: date), to: calendar.startOfDay(for: Date())).day ?? 0
        let formatter = RelativeDateTimeFormatter()
        formatter.dateTimeStyle = .named
        let text = formatter.localizedString(from: DateComponents(day: -max(0, days)))
        return text.prefix(1).capitalized + text.dropFirst()
    }

}

/// One set's page: what it trains, a run through it (numbered varisai sets),
/// then its exercises with their shape and progress. Mirrors Android's `SetScreen`.
struct SetView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dynamicTypeSize) private var typeSize
    let set: VarisaiSet
    /// The stack's path, for the run (links in a shared row would all open the first).
    @Binding var path: [String]

    private var exercises: [Varisai] { viewModel.catalog.filter { $0.set == set } }

    var body: some View {
        let practised = Int(viewModel.history.practisedCount(set: set, catalog: viewModel.catalog))
        List {
            LoadStatus()
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    if set.shortTitle != set.title { Text(set.title).font(.headline) }
                    Text(set.description_)
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                    if set.followsChosenRaga {
                        Text("In raga \(viewModel.settings.raga.name)")
                            .font(.subheadline)
                    }
                    if practised > 0 {
                        Text("\(practised) of \(exercises.count) practised")
                            .font(.subheadline)
                            .foregroundStyle(Color.subdued)
                    }
                }
                if set.practisesAsRun {
                    runRow(set: set, total: exercises.count)
                }
                ForEach(exercises, id: \.id) { varisai in
                    NavigationLink(value: varisai.id) {
                        ExerciseRow(varisai: varisai, record: viewModel.history.get(varisaiId: varisai.id),
                                    showContour: !typeSize.isAccessibilitySize)
                    }
                }
            }
        }
        .navigationTitle(set.shortTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// "Practise Sarali Varisai 1–16": one continuous run through a set, with its saved continue point.
    @ViewBuilder private func runRow(set: VarisaiSet, total: Int) -> some View {
        let plan = viewModel.runPlans.planFor(set: set, catalog: viewModel.catalog)
        let next = Int(plan.nextIndex)
        let title = next > 0 ? "Continue \(set.title)" : "Practise \(set.title)"
        let detail = next > 0 ? "Continue from \(next + 1) of \(total)" : "All \(total) varisais, one after another"
        Button {
            viewModel.openRun(set)
            path.append("run")
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "play.fill")
                    .foregroundStyle(Color.accentColor)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.primary)
                    Text(detail).font(.subheadline).foregroundStyle(Color.subdued)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .foregroundStyle(Color.subdued)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(title), \(detail)"))
        .accessibilityAddTraits(.isButton)
    }

}

/// The harmonium samples are loading, or could not be loaded.
private struct LoadStatus: View {
    @EnvironmentObject private var viewModel: PracticeViewModel

    var body: some View {
        if viewModel.loadError {
            Text("The harmonium sounds could not be loaded.")
                .foregroundStyle(.red)
        } else if !viewModel.samplesLoaded {
            ProgressView("Preparing the harmonium…")
        }
    }
}

/// Your Sa: the Sa choice and a tanpura to settle the voice on (Home and the Sa tab).
struct SaSection: View {
    @EnvironmentObject private var viewModel: PracticeViewModel

    var body: some View {
        Section {
            NavigationLink {
                SaChoiceList(saMidi: $viewModel.settings.saMidi, saCents: $viewModel.settings.saCents)
            } label: {
                HStack {
                    Text("Sa")
                    Spacer()
                    Text(PracticeSettingsView.saLabel(viewModel.settings.saMidi, cents: viewModel.settings.saCents)).foregroundStyle(Color.subdued)
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Button {
                    viewModel.toggleTanpura()
                } label: {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Image(systemName: viewModel.tanpuraPlaying ? "stop.fill" : "play.fill")
                            .accessibilityHidden(true)
                        Text(viewModel.tanpuraPlaying ? "Stop tanpura" : "Play tanpura")
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.samplesLoaded)
                Text("Settle your voice on Sa before you practise.")
                    .font(.footnote)
                    .foregroundStyle(Color.subdued)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 2)
        } header: {
            Text("Your Sa (Shruthi)")
                .foregroundStyle(Color.subdued)
                .accessibilityAddTraits(.isHeader)
        }
    }
}

/// The ragas the varisais and alankarams can be sung in, the current one
/// checked; each row shows its arohanam and reads it out in full
/// (docs/plan-other-ragas.md). Mirrors Android's `RagaPicker`.
struct VarisaiRagaList: View {
    @Binding var ragaKey: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                Text("The varisais and alankarams are sung in this raga. Gitams, swarajatis and varnams keep their own.")
                    .font(.subheadline)
                    .foregroundStyle(Color.subdued)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(VarisaiCatalog.shared.ragas, id: \.key) { raga in
                    row(raga)
                }
            }
        }
        .navigationTitle("Raga")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ raga: Raga) -> some View {
        let swaras = Array(raga.swaras.dropFirst())
        let detail = raga.key == VarisaiCatalog.shared.raga.key
            ? String(localized: "The usual raga for the varisais")
            : String(localized: "Melakarta \(Int(truncating: raga.mela ?? 0))")
        let line = CustomRaga.companion.line(swaras: swaras, descending: false)
        let spoken = CustomRaga.companion.spokenLine(swaras: swaras, descending: false)
        let selected = raga.key == ragaKey
        return Button {
            ragaKey = raga.key
            dismiss()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(raga.name).foregroundStyle(Color.primary)
                    Text("\(detail) · \(line)")
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                if selected { Image(systemName: "checkmark").accessibilityHidden(true) }
            }
            .contentShape(Rectangle())
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(raga.name), \(detail): \(spoken)"))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// Title, progress and the exercise's melodic shape. Screen readers hear the
/// first avarta's notation (which the drawing stands for) and the progress.
private struct ExerciseRow: View {
    let varisai: Varisai
    let record: PracticeRecord?
    let showContour: Bool

    private var practised: Bool { (record?.timesPracticed ?? 0) > 0 }

    private var progress: String {
        var parts: [String] = []
        if practised { parts.append(String(localized: "Practised")) }
        if let record, record.hasBest {
            parts.append(String(localized: "best \(Int(record.bestInTuneOrClose))/\(Int(record.bestJudged))"))
        }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: practised ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(practised ? Color.accentColor : Color.subdued)
            VStack(alignment: .leading, spacing: 2) {
                Text(varisai.title)
                if !progress.isEmpty {
                    Text(progress).font(.subheadline).foregroundStyle(Color.subdued)
                }
            }
            Spacer(minLength: 8)
            if showContour {
                ContourShape(varisai: varisai)
                    .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .frame(width: 110, height: 30)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text([varisai.title, varisai.avartas.first?.description() ?? "", progress]
            .filter { !$0.isEmpty }.joined(separator: ", ")))
    }
}

/// An exercise's melodic shape as a stepped line.
struct ContourShape: Shape {
    /// Plain values copied from the shared `Contour` (a `Shape` must be `Sendable`).
    struct Step: Sendable {
        let start: Double
        let end: Double
        let degree: Int
    }

    let steps: [Step]

    init(varisai: Varisai) {
        steps = Contour.shared.of(varisai: varisai).map { Step(start: $0.start, end: $0.end, degree: Int($0.degree)) }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard let low = steps.map(\.degree).min(), let highest = steps.map(\.degree).max() else { return path }
        let high = max(highest, low + 1)
        let inset: CGFloat = 2
        func y(_ degree: Int) -> CGFloat {
            rect.minY + inset + (rect.height - 2 * inset) * (1 - CGFloat(degree - low) / CGFloat(high - low))
        }
        for (i, step) in steps.enumerated() {
            let x0 = rect.minX + rect.width * step.start
            let x1 = rect.minX + rect.width * step.end
            if i == 0 { path.move(to: CGPoint(x: x0, y: y(step.degree))) } else { path.addLine(to: CGPoint(x: x0, y: y(step.degree))) }
            path.addLine(to: CGPoint(x: x1, y: y(step.degree)))
        }
        return path
    }
}
