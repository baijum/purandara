import SwiftUI
import shared

/// Plays one exercise: tala strip and notation on top, controls below,
/// Play/Stop at the bottom. Mirrors Android's `PlayerScreen`.
struct PlayerView: View {
    let varisai: Varisai
    @EnvironmentObject private var viewModel: PracticeViewModel
    @State private var showingSettings = false
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(spacing: 0) {
            TalaStripView(beatInAvarta: viewModel.playback.beatInAvarta, tala: varisai.tala)
                .padding(.horizontal, 12)
                // Extra top space: the toolbar's glass shadow otherwise lowers the
                // contrast of the right-most beat (flagged by the accessibility audit).
                .padding(.top, 16)
                .padding(.bottom, 8)
            let showPanel = viewModel.settings.singAlong || (viewModel.singAlong.micDenied && !viewModel.singAlong.micPermitted)
            // At accessibility text sizes the panel scrolls with the notation so nothing is pushed off screen.
            if showPanel && !typeSize.isAccessibilitySize {
                SingAlongPanel(varisai: varisai)
            }
            NotationView(
                varisai: varisai,
                playback: viewModel.playback,
                marks: viewModel.settings.singAlong ? viewModel.singAlong.marks : [:],
                header: AnyView(VStack(spacing: 8) {
                    // Raga and tala scroll with the notation, so they never push rows off screen.
                    Text("\(varisai.raga.name) · \(varisai.tala.name) tala")
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if showPanel && typeSize.isAccessibilitySize { SingAlongPanel(varisai: varisai) }
                }))
                .frame(maxHeight: .infinity)
            if viewModel.playback.playing {
                PlaybackStatus(playback: viewModel.playback, saMidi: viewModel.settings.saMidi, saCents: viewModel.settings.saCents)
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
        // The short title fits between the back and toolbar buttons; the tala
        // and raga are shown above the notation.
        .navigationTitle(varisai.shortTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    viewModel.toggleSingAlong()
                } label: {
                    Image(systemName: viewModel.settings.singAlong ? "mic.fill" : "mic")
                }
                .accessibilityLabel(Text("Sing along"))
                .accessibilityValue(Text(viewModel.settings.singAlong ? "On" : "Off"))
            }
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
/// While playing: the Sa in use, then "Count-in…" or "Pass n of m". Side by
/// side when they fit, stacked at large text sizes rather than clipped.
private struct PlaybackStatus: View {
    let playback: PlaybackState
    let saMidi: Int
    var saCents = 0

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack { sa; Spacer(); status }
            VStack(alignment: .leading, spacing: 2) { sa; status }
        }
        .font(.callout)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
        .padding(.vertical, 4)
    }

    private var sa: some View {
        Text("Sa: \(PracticeSettingsView.saLabel(saMidi, cents: saCents))").fontWeight(.medium).foregroundStyle(Color.subdued)
    }

    @ViewBuilder private var status: some View {
        if playback.countingIn {
            Text("Count-in…").foregroundStyle(Color.accentColor)
        } else if playback.repetitions > 1 {
            Text("Pass \((playback.repetition ?? 0) + 1) of \(playback.repetitions)").foregroundStyle(Color.subdued)
        }
    }
}

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
                        SaChoiceList(saMidi: $settings.saMidi, saCents: $settings.saCents)
                    } label: {
                        HStack {
                            Text("Sa")
                            Spacer()
                            Text(Self.saLabel(settings.saMidi, cents: settings.saCents)).foregroundStyle(Color.subdued)
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
                    Picker("Harmonium guide", selection: $settings.harmoniumGuide) {
                        Text("All notes").tag(true)
                        Text("Off (drone and click)").tag(false)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } header: {
                    Text("Harmonium guide").foregroundStyle(Color.subdued)
                }
                Section {
                    Toggle("Echo cancellation", isOn: $settings.echoCancellation)
                    // In the section rather than its footer: footers cap Dynamic Type.
                    Text("On the speaker, removes the app's own sound from what the microphone hears; the volume may drop while it works. Turn it off when using a microphone placed away from the speaker.")
                        .font(.footnote)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Section {
                    Toggle("Tanpura drone", isOn: $settings.drone)

                    LevelSlider(title: "Drone volume", level: $settings.droneLevel)
                        .disabled(!settings.drone)
                    LevelSlider(title: "Harmonium volume", level: $settings.harmoniumLevel)
                }
                Section {
                    Toggle("Tala click", isOn: $settings.click)
                    Toggle("Count in one avarta", isOn: $settings.countIn)
                    Toggle("Repeat continuously", isOn: $settings.loop)
                }
                Section {
                    Picker("Strictness", selection: $settings.strictness) {
                        Text("Gentle").tag(PracticeSettings.StrictnessChoice.gentle)
                        Text("Steady").tag(PracticeSettings.StrictnessChoice.steady)
                        Text("Precise").tag(PracticeSettings.StrictnessChoice.precise)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    Text("In tune within ±\(Int(settings.strictness.kotlin.inTuneCents)) cents, close within ±\(Int(settings.strictness.kotlin.closeCents)).")
                        .font(.footnote)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                } header: {
                    Text("Strictness").foregroundStyle(Color.subdued)
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

    /// "C3 · 1 kattai", with " +12¢" when fine-tuned.
    static func saLabel(_ midi: Int, cents: Int = 0) -> String {
        "\(Shruti.shared.noteName(midi: Int32(midi))) · \(Shruti.shared.kattai(midi: Int32(midi))) kattai"
            + Shruti.shared.fineTuneSuffix(cents: Int32(cents))
    }
}

/// Every Sa choice, current one checked.
struct SaChoiceList: View {
    @Binding var saMidi: Int
    @Binding var saCents: Int
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        // Grouped by the voices that usually use each Sa (docs/plan-shruti.md).
        List {
            Section {
                FineTuneControl(cents: $saCents)
            }
            ForEach(VoiceRange.entries, id: \.name) { range in
                Section {
                    ForEach(PracticeSettings.saRange.filter { Int32($0) >= range.midis.first && Int32($0) <= range.midis.last }, id: \.self) { midi in
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
                } header: {
                    Text(range.title).foregroundStyle(Color.subdued).accessibilityAddTraits(.isHeader)
                }
            }
        }
        .navigationTitle("Sa")
    }
}

/// Sa fine-tune in cents, in one-cent steps (hold the button to step quickly).
private struct FineTuneControl: View {
    @Binding var cents: Int

    private var valueText: String {
        cents == 0 ? String(localized: "exact") : "\(cents > 0 ? "+" : "−")\(abs(cents))¢"
    }

    var body: some View {
        Stepper(value: $cents, in: PracticeSettings.fineTuneRange) {
            Text("Fine tune: \(valueText)").fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityValue(Text("\(cents) cents"))
        Text("Nudge Sa up or down to match a teacher's shruti box or a recording.")
            .font(.footnote)
            .foregroundStyle(Color.subdued)
            .fixedSize(horizontal: false, vertical: true)
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
