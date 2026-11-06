import SwiftUI
import shared

/// Plays one exercise: tala strip and notation on top, controls below,
/// Play/Stop at the bottom. Mirrors Android's `PlayerScreen`.
struct PlayerView: View {
    let varisai: Varisai
    @EnvironmentObject private var viewModel: PracticeViewModel
    @State private var showingSettings = false

    var body: some View {
        VStack(spacing: 0) {
            TalaStripView(beatInAvarta: viewModel.playback.beatInAvarta)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            NotationView(varisai: varisai, playback: viewModel.playback)
                .frame(maxHeight: .infinity)
            if viewModel.playback.countingIn {
                Text("Count-in…")
                    .font(.caption)
                    .foregroundStyle(Color.accentColor)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.vertical, 4)
            } else if viewModel.playback.repetitions > 1 {
                Text("Pass \((viewModel.playback.repetition ?? 0) + 1) of \(viewModel.playback.repetitions)")
                    .font(.caption)
                    .foregroundStyle(Color.subdued)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.vertical, 4)
            }
            Divider()
            QuickControls(settings: $viewModel.settings)
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                viewModel.togglePlay(varisai)
            } label: {
                Label(viewModel.playback.playing ? "Stop" : "Play",
                      systemImage: viewModel.playback.playing ? "stop.fill" : "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.canPlay)
            .padding()
            // Opaque: controls scrolling behind a translucent bar fail contrast checks.
            .background(Color(uiColor: .systemBackground))
        }
        .navigationTitle(varisai.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel(Text("Practice settings"))
            }
        }
        .sheet(isPresented: $showingSettings) {
            PracticeSettingsView(settings: $viewModel.settings)
        }
        .onDisappear { viewModel.stopImmediately() }
    }
}

/// What changes while practising: tempo and speed. Everything else is in `PracticeSettingsView`.
private struct QuickControls: View {
    @Binding var settings: PracticeSettings

    private let kalaNames = ["First kala", "Second kala", "Third kala", "Fourth kala"]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Stepper(value: $settings.tempo, in: PracticeSettings.tempoRange, step: 5) {
                Text("Tempo: \(settings.tempo) bpm")
                    .fixedSize(horizontal: false, vertical: true) // wrap, never clip, at large sizes
            }
            .accessibilityValue(Text("\(settings.tempo) beats per minute"))

            Text("Speed")
            Picker("Speed", selection: $settings.kala) {
                ForEach(PracticeSettings.kalaRange, id: \.self) { kala in
                    Text("\(kala)").tag(kala).accessibilityLabel(Text(kalaNames[kala - 1]))
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
        }
        .padding()
    }
}

/// Sa, drone, levels, click, count-in, repeat and tuning, in a sheet.
struct PracticeSettingsView: View {
    @Binding var settings: PracticeSettings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    // A custom list: the system picker's grey value text fails contrast,
                    // and menus clip at large text sizes.
                    NavigationLink {
                        SaChoiceList(saMidi: $settings.saMidi)
                    } label: {
                        HStack {
                            Text("Sa")
                            Spacer()
                            Text(Self.saLabel(settings.saMidi)).foregroundStyle(Color.subdued)
                        }
                    }
                }
                Section {
                    Picker("Tuning", selection: $settings.tuning) {
                        Text("Just (tanpura)").tag(PracticeSettings.TuningChoice.just)
                        Text("Equal (keyboard)").tag(PracticeSettings.TuningChoice.equal)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } header: {
                    Text("Tuning").foregroundStyle(Color.subdued)
                }
                Section {
                    Toggle("Drone (Sa and Pa)", isOn: $settings.drone)
                    LevelSlider(title: "Drone volume", level: $settings.droneLevel)
                        .disabled(!settings.drone)
                    LevelSlider(title: "Harmonium volume", level: $settings.harmoniumLevel)
                }
                Section {
                    Toggle("Tala click", isOn: $settings.click)
                    Toggle("Count in one avarta", isOn: $settings.countIn)
                    Toggle("Repeat continuously", isOn: $settings.loop)
                }
            }
            .navigationTitle("Practice settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    // An icon: text bar buttons don't fully follow Dynamic Type.
                    Button { dismiss() } label: { Image(systemName: "checkmark") }
                        .accessibilityLabel(Text("Done"))
                }
            }
        }
    }

    static func saLabel(_ midi: Int) -> String {
        "\(Shruti.shared.noteName(midi: Int32(midi))) · \(Shruti.shared.kattai(midi: Int32(midi))) kattai"
    }
}

/// Every Sa choice, current one checked.
private struct SaChoiceList: View {
    @Binding var saMidi: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List(PracticeSettings.saRange, id: \.self) { midi in
            Button {
                saMidi = midi
                dismiss()
            } label: {
                HStack {
                    Text(PracticeSettingsView.saLabel(midi)).foregroundStyle(Color.primary)
                    Spacer()
                    if midi == saMidi { Image(systemName: "checkmark") }
                }
            }
            .accessibilityAddTraits(midi == saMidi ? .isSelected : [])
        }
        .navigationTitle("Sa")
    }
}

/// A 0...1 level, committed when the drag ends so playback restarts only once.
private struct LevelSlider: View {
    let title: LocalizedStringKey
    @Binding var level: Float
    @State private var editing: Float?

    var body: some View {
        let shown = editing ?? level
        VStack(alignment: .leading) {
            HStack {
                Text(title)
                Spacer()
                Text("\(Int((shown * 100).rounded()))%").foregroundStyle(Color.subdued)
            }
            Slider(
                value: Binding(get: { editing ?? level }, set: { editing = $0 }),
                in: 0...1
            ) { isEditing in
                if !isEditing, let value = editing {
                    level = value
                    editing = nil
                }
            }
            .accessibilityLabel(Text(title))
            .accessibilityValue(Text("\(Int((shown * 100).rounded())) percent"))
        }
    }
}
