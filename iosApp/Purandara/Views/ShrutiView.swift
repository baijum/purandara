import SwiftUI
import shared

/// Text for Shruti practice and Find my Sa, matching Android's strings.
enum ShrutiText {
    static func target(_ target: HoldTarget) -> String {
        switch target {
        case .pa: return String(localized: "Pa")
        case .upperSa: return String(localized: "Upper Sa")
        default: return String(localized: "Sa")
        }
    }

    static func reached(_ hold: HoldState) -> String {
        String(localized: "Goal reached: \(target(hold.target)) held for \(Int(hold.goal.seconds)) seconds")
    }

    static func suggestion(_ suggestion: SaSuggestion) -> String {
        String(localized: "Suggested Sa: \(PracticeSettingsView.saLabel(Int(suggestion.saMidi)))")
    }

    static func note(_ hz: Double) -> String {
        Shruti.shared.noteName(midi: Pitch.shared.nearestMidi(hz: hz))
    }
}

/// Shruti practice (docs/plan-shruti.md): the tanpura plays, the student's
/// pitch is traced against the swara lines, and hold practice times a held
/// Sa, Pa or upper Sa. Mirrors Android's `ShrutiScreen`.
struct ShrutiView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dynamicTypeSize) private var typeSize

    @State private var choosingHold = false

    private var shruti: ShrutiUI { viewModel.shruti }
    private var micAllowed: Bool { viewModel.singAlong.micPermitted || viewModel.syntheticSingerCents != nil }

    var body: some View {
        List {
            // Few, tall rows: rows that larger text pushes off screen fail the
            // accessibility audit's Dynamic Type check.
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Sa: \(PracticeSettingsView.saLabel(viewModel.settings.saMidi, cents: viewModel.settings.saCents))")
                        .foregroundStyle(Color.subdued)
                    if !micAllowed {
                        Text("Allow the microphone to see your pitch. The tanpura plays either way.")
                            .fixedSize(horizontal: false, vertical: true)
                        Button { viewModel.allowMic() } label: { WrappingText("Allow microphone") }
                            .buttonStyle(.borderedProminent)
                    } else {
                        pitch
                        if viewModel.singAlong.onSpeaker {
                            Text("Headphones give the most accurate results.")
                                .font(.footnote)
                                .foregroundStyle(Color.subdued)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
            if micAllowed {
                holdStatus
            }
        }
        .navigationTitle("Shruti practice")
        .sheet(isPresented: $choosingHold) { HoldChoices() }
        .onAppear { viewModel.openShruti() }
        .onDisappear { viewModel.closeShruti() }
    }

    /// The trace (visual only) and the live reading, read as one element.
    private var pitch: some View {
        let reading = shruti.listeningToRoom
            ? String(localized: "Stay quiet for a moment: listening to the room")
            : SingAlongText.live(shruti.reading)
        return VStack(alignment: .leading, spacing: 6) {
            if !typeSize.isAccessibilitySize {
                // Visual only, and outside the reading's element (the contrast
                // check would otherwise measure the text against the lines).
                FreePitchTrace(trace: shruti.trace, saHz: viewModel.settings.saHz)
                    .frame(height: 180)
                    .accessibilityHidden(true)
            }
            Text(reading).font(.headline).fixedSize(horizontal: false, vertical: true)
                .accessibilityLabel(Text("Your pitch"))
                .accessibilityValue(Text(reading))
        }
    }

    private var holdStatus: some View {
        let current = shruti.hold?.currentSeconds ?? 0
        let goal = shruti.goal.seconds
        let name = ShrutiText.target(shruti.target)
        return Section {
            VStack(alignment: .leading, spacing: 10) {
                // The choices are in a sheet: lists here would push the progress
                // out of sight, and leaving this screen stops the tanpura.
                Button { choosingHold = true } label: {
                    HStack(alignment: .firstTextBaseline) {
                        Text("Hold \(name) for \(Int(goal)) seconds").foregroundStyle(Color.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer()
                        Text("Change").foregroundStyle(Color.accentColor)
                    }
                }
                .buttonStyle(.borderless)
                .accessibilityHint(Text("Choose the note and the goal"))
                // Visual only: the text below says the same.
                ProgressView(value: min(current / goal, 1))
                    .accessibilityHidden(true)
                Text(current > 0
                     ? String(localized: "Holding \(name): \(current.formatted(.number.precision(.fractionLength(1)))) of \(Int(goal)) seconds")
                     : String(localized: "Sing \(name) and hold it"))
                    .fixedSize(horizontal: false, vertical: true)
                if let hold = shruti.hold, hold.bestSeconds > 0 {
                    Text("Best: \(hold.bestSeconds.formatted(.number.precision(.fractionLength(1)))) s")
                        .foregroundStyle(Color.subdued)
                }
                if let hold = shruti.hold, hold.reached {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor).accessibilityHidden(true)
                        Text(ShrutiText.reached(hold)).fixedSize(horizontal: false, vertical: true)
                    }
                    .font(.headline)
                }
            }
            .padding(.vertical, 4)
        } header: {
            Text("Hold a note").foregroundStyle(Color.subdued).accessibilityAddTraits(.isHeader)
        }
    }
}

/// The note to hold and the goal.
private struct HoldChoices: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let shruti = viewModel.shruti
        NavigationStack {
        List {
            Section {
                Picker("Note to hold", selection: Binding(get: { shruti.target }, set: { viewModel.setHold(target: $0, goal: shruti.goal) })) {
                    ForEach(HoldTarget.entries, id: \.name) { Text(ShrutiText.target($0)).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            } header: {
                Text("Note to hold").foregroundStyle(Color.subdued).accessibilityAddTraits(.isHeader)
            }
            Section {
                Picker("Goal", selection: Binding(get: { shruti.goal }, set: { viewModel.setHold(target: shruti.target, goal: $0) })) {
                    ForEach(HoldGoal.entries, id: \.name) { Text("\(Int($0.seconds)) seconds").tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            } header: {
                Text("Goal").foregroundStyle(Color.subdued).accessibilityAddTraits(.isHeader)
            }
        }
        .navigationTitle("Hold a note")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button { dismiss() } label: { Image(systemName: "checkmark") }
                    .accessibilityLabel(Text("Done"))
            }
        }
        }
    }
}

/// The last ten seconds of pitch against the raga's swara lines (decorative).
private struct FreePitchTrace: View {
    let trace: [FreeTracePoint]
    let saHz: Double

    // Pure geometry constants, read from the nonisolated Canvas closure.
    private nonisolated static let low = -800.0
    private nonisolated static let high = 1350.0

    var body: some View {
        Canvas { context, size in
            let raga = VarisaiCatalog.shared.raga
            let labelWidth: CGFloat = 26
            func y(_ cents: Double) -> CGFloat { size.height * (1 - (cents - Self.low) / (Self.high - Self.low)) }
            var lines: [Note] = [SwaraLetter.p, .d, .n].map { Note(swara: raga.swara(letter: $0), octave: .mandra) }
            lines += SwaraLetter.entries.map { Note(swara: raga.swara(letter: $0), octave: .madhya) }
            lines.append(Note(swara: .s, octave: .tara))
            for note in lines {
                let yy = y(1200 * log2(note.frequency(saHz: saHz) / saHz))
                var path = Path()
                path.move(to: CGPoint(x: labelWidth, y: yy))
                path.addLine(to: CGPoint(x: size.width, y: yy))
                let isSa = note.swara == .s
                context.stroke(path, with: .color(isSa ? Color.accentColor : .secondary.opacity(0.4)), lineWidth: isSa ? 2 : 1)
                let mark = note.octave == .tara ? "′" : (note.octave == .mandra ? "." : "")
                context.draw(Text(String(note.swara.symbol.prefix(1)) + mark).font(.system(size: 12)).foregroundColor(.primary),
                             at: CGPoint(x: 4, y: yy), anchor: .leading)
            }
            guard let now = trace.last?.seconds else { return }
            for p in trace {
                let x = labelWidth + (size.width - labelWidth) * (1 - (now - p.seconds) / 10)
                let color: Color = p.zone == .inTune ? .accentColor : (p.zone == .close ? .orange : .red)
                context.fill(Path(ellipseIn: CGRect(x: x - 2, y: y(p.centsFromSa) - 2, width: 4, height: 4)), with: .color(color))
            }
        }
    }
}

/// A list-row button title that wraps at large text sizes (a plain title is clipped).
private struct WrappingText: View {
    let key: LocalizedStringKey
    init(_ key: LocalizedStringKey) { self.key = key }
    var body: some View { Text(key).fixedSize(horizontal: false, vertical: true) }
}

/// Find my Sa: catch the lowest and highest comfortable notes, then suggest a
/// Sa (docs/plan-shruti.md). Mirrors Android's `FindSaScreen`.
struct FindSaView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dismiss) private var dismiss

    private var micAllowed: Bool { viewModel.singAlong.micPermitted || viewModel.syntheticSingerCents != nil }

    var body: some View {
        List {
            Section {
                Text("Sing two comfortable notes, not strained. Hold each for a couple of seconds.")
                    .fixedSize(horizontal: false, vertical: true)
                if !micAllowed {
                    Button { viewModel.allowMic() } label: { WrappingText("Allow microphone") }
                }
            }
            if micAllowed {
                if let progress = viewModel.findSa, progress.step == .done, let suggestion = progress.suggestion {
                    result(progress, suggestion)
                } else {
                    listening(viewModel.findSa)
                }
            }
        }
        .navigationTitle("Find my Sa")
        .onAppear { viewModel.openFindSa() }
        .onDisappear { viewModel.closeFindSa() }
    }

    private func listening(_ progress: FindSaProgress?) -> some View {
        Section {
            Text(progress?.step == .highest
                 ? "Now sing your highest comfortable note and hold it"
                 : "Sing your lowest comfortable note and hold it")
                .font(.headline)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            ProgressView(value: progress?.holding ?? 0)
                .accessibilityLabel(Text("Holding the note"))
            if let hz = progress?.hearingHz {
                Text("Hearing \(ShrutiText.note(Double(truncating: hz)))")
            }
            if progress?.notHigher == true {
                Text("Go clearly higher than your lowest note").foregroundStyle(.red)
            }
        }
    }

    private func result(_ progress: FindSaProgress, _ suggestion: SaSuggestion) -> some View {
        // One row: separate rows push each other out of the audit's view at large text sizes.
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Text(ShrutiText.suggestion(suggestion))
                    .font(.title3.bold())
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let low = progress.lowestHz, let high = progress.highestHz {
                    Text("Lowest \(ShrutiText.note(Double(truncating: low))) · Highest \(ShrutiText.note(Double(truncating: high)))")
                        .fixedSize(horizontal: false, vertical: true)
                }
                if suggestion.narrowRange {
                    Text("Your range is narrow for both lower Pa and upper Ri at any Sa; this is the closest fit.")
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Button {
                    viewModel.useSuggestedSa()
                    dismiss()
                } label: { WrappingText("Use this") }
                .buttonStyle(.borderedProminent)
                Button { viewModel.openFindSa() } label: { WrappingText("Try again") }
                .buttonStyle(.bordered)
            }
            .padding(.vertical, 4)
        }
    }
}
