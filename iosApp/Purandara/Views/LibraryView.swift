import SwiftUI
import shared

/// The landing page: continue / next up, the student's Sa with a tanpura to
/// tune to, then every exercise by set with its shape and progress
/// (docs/plan-landing.md). Mirrors Android's `LibraryScreen`.
struct LibraryView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dynamicTypeSize) private var typeSize

    private var sections: [(set: VarisaiSet, exercises: [Varisai])] {
        VarisaiSet.entries.map { set in (set, viewModel.catalog.filter { $0.set == set }) }
            .filter { !$0.exercises.isEmpty }
    }

    /// At accessibility text sizes the drawings give their space to text.
    private var showContours: Bool { !typeSize.isAccessibilitySize }

    var body: some View {
        List {
            if viewModel.loadError {
                Text("The harmonium sounds could not be loaded.")
                    .foregroundStyle(.red)
            } else if !viewModel.samplesLoaded {
                ProgressView("Preparing the harmonium…")
            }
            continueSection
            saSection
            ForEach(sections, id: \.set.name) { section in
                Section {
                    Text(section.set.description_)
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                    ForEach(section.exercises, id: \.id) { varisai in
                        NavigationLink(value: varisai.id) {
                            ExerciseRow(varisai: varisai, record: viewModel.history.get(varisaiId: varisai.id), showContour: showContours)
                        }
                    }
                } header: {
                    SetHeader(set: section.set,
                              practised: Int(viewModel.history.practisedCount(set: section.set, catalog: viewModel.catalog)),
                              total: section.exercises.count)
                }
            }
        }
        .navigationTitle("Purandara")
        .navigationDestination(for: String.self) { id in
            if let varisai = VarisaiCatalog.shared.byId(id: id) {
                PlayerView(varisai: varisai)
                    .onAppear { viewModel.stopTanpura() }
            }
        }
        .safeAreaInset(edge: .top) {
            Text("Mayamalavagowla · Adi tala")
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
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

    // MARK: - Your Sa

    private var saSection: some View {
        Section {
            NavigationLink {
                SaChoiceList(saMidi: $viewModel.settings.saMidi)
            } label: {
                HStack {
                    Text("Sa")
                    Spacer()
                    Text(PracticeSettingsView.saLabel(viewModel.settings.saMidi)).foregroundStyle(Color.subdued)
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
            Text("Your Sa")
                .foregroundStyle(Color.subdued)
                .accessibilityAddTraits(.isHeader)
        }
    }
}

/// Set title, with "n of m practised" once any has been.
private struct SetHeader: View {
    let set: VarisaiSet
    let practised: Int
    let total: Int

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack { title; Spacer(); count }
            VStack(alignment: .leading) { title; count }
        }
        .foregroundStyle(Color.subdued)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var title: some View { Text(set.title) }

    @ViewBuilder private var count: some View {
        if practised > 0 { Text("\(practised) of \(total) practised") }
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
