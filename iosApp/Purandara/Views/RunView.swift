import SwiftUI
import shared

/// One set practised as a run (docs/plan-practice-run.md): plan it, sing it,
/// then see it complete. Mirrors Android's `RunScreen`.
struct RunView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let run = viewModel.run {
                switch run.phase {
                case .setup:
                    RunSetupView(run: run)
                case .playing:
                    if let step = run.current {
                        // The full player: tempo, room check, note marks and pass summaries.
                        PlayerView(varisai: step.varisai, runMode: true)
                    }
                case .complete:
                    RunCompleteView(run: run)
                }
            } else {
                Color.clear
            }
        }
        .onChange(of: viewModel.run == nil) { empty in
            if empty { dismiss() }
        }
        .onDisappear { viewModel.leaveRun() }
    }
}

// MARK: - Setup

private struct RunSetupView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    let run: RunUI
    @State private var bulkRepeats = 3

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.outputChanged {
                    Text("Audio output changed. Start again when ready.")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                }
                Text("Every varisai in the set, one after another, opened in the normal player. Speed 3 sings first, second and third kala; each speed starts with its count-in and room check. Sing is how many passes at each speed.")
                    .font(.subheadline)
                    .foregroundStyle(Color.subdued)
                    .fixedSize(horizontal: false, vertical: true)
                bulk
                Divider()
                ForEach(Array(run.steps.enumerated()), id: \.offset) { index, step in
                    RunStepRow(index: index, step: step)
                    Divider()
                }
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) { startBar }
        .navigationTitle("\(run.set.shortTitle) run")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Speed for every varisai at once, then repeats for every varisai at once.
    private var bulk: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Set every varisai to speed")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                ForEach(1...4, id: \.self) { kala in
                    Button { viewModel.applyRunSpeed(kala) } label: {
                        Text("\(kala)").frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(Text("Set every varisai to speed \(kala)"))
                }
            }
            Text("Sing every varisai")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                Button {
                    bulkRepeats = max(1, bulkRepeats - 1)
                    viewModel.applyRunRepeats(bulkRepeats)
                } label: {
                    Image(systemName: "minus").frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel(Text("One fewer time"))
                .disabled(bulkRepeats <= 1)
                Text(repeatLabel(bulkRepeats)).frame(minWidth: 80)
                Button {
                    bulkRepeats = min(7, bulkRepeats + 1)
                    viewModel.applyRunRepeats(bulkRepeats)
                } label: {
                    Image(systemName: "plus").frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel(Text("One more time"))
                .disabled(bulkRepeats >= 7)
            }
        }
    }

    private var startBar: some View {
        VStack(spacing: 8) {
            if run.totalSeconds > 0 {
                Text(estimateText).font(.footnote).foregroundStyle(Color.subdued)
            }
            if run.startStep > 0, run.steps.indices.contains(run.startStep) {
                Button {
                    viewModel.startRun(from: run.startStep)
                } label: {
                    Text("Start from \(run.steps[run.startStep].varisai.title)").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.samplesLoaded)
                Button {
                    viewModel.startRun(from: 0)
                } label: {
                    Text("Start from the beginning").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.samplesLoaded)
            } else {
                Button {
                    viewModel.startRun(from: 0)
                } label: {
                    Text("Start practice").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.samplesLoaded)
            }
        }
        .padding()
        .background(Color(.systemBackground))
    }

    private var estimateText: String {
        let minutes = max(1, Int((run.totalSeconds / 60).rounded(.up)))
        return "about \(minutes) minutes"
    }
}

private struct RunStepRow: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    let index: Int
    let step: RunStep

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(step.varisai.title).font(.headline)
            Text("Speed").font(.subheadline).foregroundStyle(Color.subdued)
            HStack(spacing: 8) {
                ForEach(1...4, id: \.self) { kala in
                    let selected = Int(step.speed) == kala
                    Button { viewModel.setRunSpeed(index: index, speed: kala) } label: {
                        Text("\(kala)").frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                    .tint(selected ? Color.accentColor : Color.secondary)
                    .accessibilityLabel(Text(kalaName(kala)))
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            Text(speedHint(Int(step.speed), repeats: Int(step.repeats)))
                .font(.footnote)
                .foregroundStyle(Color.subdued)
            HStack(spacing: 8) {
                Text("Sing").frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    viewModel.setRunRepeats(index: index, repeats: Int(step.repeats) - 1)
                } label: {
                    Image(systemName: "minus").frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel(Text("One fewer time"))
                .disabled(step.repeats <= 1)
                Text(repeatLabel(Int(step.repeats))).frame(minWidth: 80)
                Button {
                    viewModel.setRunRepeats(index: index, repeats: Int(step.repeats) + 1)
                } label: {
                    Image(systemName: "plus").frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel(Text("One more time"))
                .disabled(step.repeats >= 7)
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Playing

private struct RunCompleteView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel
    let run: RunUI

    var body: some View {
        VStack(spacing: 16) {
            Text("\(run.set.title) complete")
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("\(run.steps.count) varisais, one after another.")
                .multilineTextAlignment(.center)
            Button { viewModel.practiseAgain() } label: {
                Text("Practise again").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            Button { viewModel.closeRun() } label: {
                Text("Done").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxHeight: .infinity, alignment: .center)
        .navigationTitle("\(run.set.shortTitle) run")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Shared labels

private func kalaName(_ kala: Int) -> String {
    switch kala {
    case 1: return String(localized: "First kala")
    case 2: return String(localized: "Second kala")
    case 3: return String(localized: "Third kala")
    default: return String(localized: "Fourth kala")
    }
}

private func repeatLabel(_ repeats: Int) -> String {
    repeats == 1 ? String(localized: "Once") : String(localized: "\(repeats) times")
}

private func speedHint(_ speed: Int, repeats: Int) -> String {
    let range = speed == 1 ? String(localized: "Kala 1 only") : String(localized: "Kalas 1–\(speed)")
    let times = repeatLabel(repeats)
    return speed == 1 ? "\(range) · \(times)" : "\(range) · \(times) each"
}
