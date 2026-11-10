import SwiftUI
import shared

/// Plays one exercise: tala strip and notation on top, controls below,
/// Play/Stop at the bottom. Mirrors Android's `PlayerScreen`.
struct PlayerView: View {
    let varisai: Varisai
    @EnvironmentObject private var viewModel: PracticeViewModel
    @State private var showingSettings = false
    @State private var showingSections = false
    @State private var showingPhrases = false
    @Environment(\.dynamicTypeSize) private var typeSize

    /// Whether every note of this piece is inside the harmonium samples at the chosen Sa.
    private var saPlayable: Bool { viewModel.saPlayable(varisai) }
    private var nearestSa: Int { Int(varisai.nearestPlayableSa(saMidi: Int32(viewModel.settings.saMidi))) }

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.playback.recovering || viewModel.practicePaused {
                ScrollView {
                    RecoveryControls().padding(.top, 16)
                }.frame(maxHeight: .infinity)
            } else {
            let layout = viewModel.notationLayout(varisai)
            TalaStripView(beatInAvarta: viewModel.playback.beatInAvarta, tala: varisai.tala, kalai: Int(varisai.kalai),
                          block: layout.blocks[NotationView.currentBlock(layout, viewModel.playback) ?? 0])
                .padding(.horizontal, 12)
                // Extra top space: the toolbar's glass shadow otherwise lowers the
                // contrast of the right-most beat (flagged by the accessibility audit).
                .padding(.top, 16)
                .padding(.bottom, 8)
            if !saPlayable {
                SaOutOfRange(
                    sa: PracticeSettingsView.saLabel(viewModel.settings.saMidi, cents: viewModel.settings.saCents),
                    nearest: PracticeSettingsView.saLabel(nearestSa, cents: 0)
                ) {
                    viewModel.settings.saMidi = nearestSa
                    viewModel.settings.saCents = 0
                }
            }
            let showPanel = viewModel.settings.singAlong || (viewModel.singAlong.micDenied && !viewModel.singAlong.micPermitted)
            // At accessibility text sizes the panel scrolls with the notation so nothing is pushed off screen.
            if showPanel && !typeSize.isAccessibilitySize {
                SingAlongPanel(varisai: varisai, saPlayable: saPlayable)
            }
            NotationView(
                layout: layout,
                playback: viewModel.playback,
                marks: viewModel.settings.singAlong ? viewModel.singAlong.marks : [:],
                words: viewModel.settings.words.kotlin,
                header: AnyView(VStack(spacing: 8) {
                    // Raga and tala scroll with the notation, so they never push rows off screen.
                    Text("\(varisai.raga.name) · \(varisai.tala.name) tala")
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if !varisai.sections.isEmpty {
                        Button {
                            showingSections = true
                        } label: {
                            Text("Section: \(viewModel.practicePhrase?.label ?? viewModel.practiceSection?.label ?? String(localized: "Whole piece"))")
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityHint(Text("Practise one section alone"))
                    }
                    let phrases = PracticePhrases.shared.forVarisai(varisai: varisai)
                    if !phrases.isEmpty {
                        Button("Practice unit: \(viewModel.practicePhrase?.label ?? viewModel.practiceSection?.label ?? String(localized: "Whole piece"))") {
                            showingPhrases = true
                        }.buttonStyle(.bordered)
                            .accessibilityHint(Text("Choose a teacher-reviewed practice phrase"))
                    }
                    if showPanel && typeSize.isAccessibilitySize { SingAlongPanel(varisai: varisai, saPlayable: saPlayable) }
                }))
                .frame(maxHeight: .infinity)
            if viewModel.playback.playing {
                PlaybackStatus(playback: viewModel.playback, saMidi: viewModel.settings.saMidi, saCents: viewModel.settings.saCents)
            }
            Divider()
            QuickControls(settings: $viewModel.settings)
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                if viewModel.practicePaused { viewModel.stopImmediately() }
                else { viewModel.togglePlay(varisai) }
            } label: {
                Label(viewModel.playback.playing || viewModel.practicePaused ? "Stop" : "Play",
                      systemImage: viewModel.playback.playing || viewModel.practicePaused ? "stop.fill" : "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            // Stop stays available even when the Sa is out of range or the samples
            // are still loading; only starting playback needs those (issue #13).
            .disabled(!viewModel.playback.playing && (!viewModel.canPlay || !saPlayable))
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
        .onDisappear {
            viewModel.stopImmediately()
            viewModel.practiceSection = nil
            viewModel.practicePhrase = nil
        }
        .sheet(isPresented: $showingSections) {
            SectionChoices(sections: varisai.sections, selected: Binding(
                get: { viewModel.practiceSection },
                set: { viewModel.practicePhrase = nil; viewModel.practiceSection = $0 }))
        }
        .sheet(isPresented: $showingPhrases) {
            PhraseChoices(phrases: PracticePhrases.shared.forVarisai(varisai: varisai),
                          selected: $viewModel.practicePhrase, section: $viewModel.practiceSection)
        }
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
        if playback.practicePhase == .listen {
            Text("Listen · attempt \(playback.attempt)")
        } else if playback.practicePhase == .yourTurn {
            Text("Your turn · attempt \(playback.attempt)")
        } else if playback.practicePhase == .release_ {
            Text("Release")
        } else if playback.recovering {
            Text("Rest")
        } else if playback.countingIn {
            Text("Count-in…").foregroundStyle(Color.accentColor)
        } else if playback.repetitions > 1 {
            Text("Pass \((playback.repetition ?? 0) + 1) of \(playback.repetitions)").foregroundStyle(Color.subdued)
        }
    }
}

private struct RecoveryControls: View {
    @EnvironmentObject private var viewModel: PracticeViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(viewModel.practicePaused ? "Practice paused. Continue when ready." : "Rest between attempts")
                .font(.headline).accessibilityAddTraits(.isHeader)
                .fixedSize(horizontal: false, vertical: true)
            if let summary = viewModel.singAlong.lastSummary {
                Text(SingAlongText.summaryHeadline(summary.pass, summary.summary))
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let seconds = viewModel.playback.recoverySeconds {
                Text("Next attempt in \(seconds) seconds")
                    .monospacedDigit().accessibilityHidden(true)
                // Avoid a changing VoiceOver announcement every second.
            }
            Button("Continue") { viewModel.continuePractice() }.buttonStyle(.borderedProminent)
            if viewModel.playback.recoverySeconds != nil {
                Button("Wait for me") { viewModel.waitForMe() }.buttonStyle(.bordered)
            }
        }
        .padding(.horizontal).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct QuickControls: View {
    @Binding var settings: PracticeSettings
    @State private var showingSpeedHelp = false

    private let kalaNames = ["First kala", "Second kala", "Third kala", "Fourth kala"]
    private var speedMultiplier: Int { Int(settings.performance().aksharasPerBeat) }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Stepper(value: $settings.tempo, in: PracticeSettings.tempoRange, step: 5) {
                Text("Tempo: \(settings.tempo) bpm")
                    .fixedSize(horizontal: false, vertical: true) // wrap, never clip, at large sizes
            }
            .accessibilityValue(Text("\(settings.tempo) beats per minute"))

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("Speed · \(speedMultiplier)×")
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityLabel(Text("Speed, \(speedMultiplier) times the written pace"))
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("speed-multiplier")
                    Spacer()
                    Button {
                        showingSpeedHelp = true
                    } label: {
                        Image(systemName: "info.circle")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel(Text("About speed"))
                }
                Picker("Speed", selection: $settings.kala) {
                    ForEach(PracticeSettings.kalaRange, id: \.self) { kala in
                        Text("\(kala)").tag(kala).accessibilityLabel(Text(kalaNames[kala - 1]))
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }
        }
        .padding()
        .sheet(isPresented: $showingSpeedHelp) {
            SpeedHelpView()
        }
    }
}

/// Written pace is a multiplier, not a universal count of individual swaras.
/// Keep the explanation out of the fixed controls and let it grow and scroll.
private struct SpeedHelpView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                // One text element keeps the explanation together for VoiceOver
                // and the Dynamic Type audit while still scrolling at large sizes.
                Text("""
                    Speed 1–4 uses 1×, 2×, 4× and 8× the written pace. Tempo sets the tala beat, which stays unchanged.

                    Janta 1–6 and both Vakra Janta exercises have two swaras per written cell: 2, 4, 8 or 16 individual swaras per beat at settings 1–4.

                    Janta 7 uses single-swara cells and holds instead. Holds continue a note; rests are silent. Neither starts a new note. Mixed passages need not have one constant note count.
                    """)
                    .font(.body)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, 2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.top, 24) // clear the toolbar's glass shadow
                    .padding(.bottom)
            }
            .accessibilityIdentifier("speed-help")
            .navigationTitle("About speed")
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
                    NavigationLink("Sound") { soundSettings }
                    NavigationLink("Rhythm") { rhythmSettings }
                    NavigationLink("Feedback and notation") { feedbackSettings }
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

    // Short, named pages prevent a growing explanation from displacing unrelated
    // controls during Dynamic Type changes. Explanations follow their own controls.
    private var soundSettings: some View {
        Form {
            NavigationLink("Tuning") {
                Form {
                    Picker("Tuning", selection: $settings.tuning) {
                        Text("Just (tanpura)").tag(PracticeSettings.TuningChoice.just)
                        Text("Equal (keyboard)").tag(PracticeSettings.TuningChoice.equal)
                    }.pickerStyle(.inline).labelsHidden()
                }.navigationTitle("Tuning").navigationBarTitleDisplayMode(.inline)
            }
            NavigationLink("Harmonium guide") {
                Form {
                    Picker("Harmonium guide", selection: $settings.harmoniumGuide) {
                        Text("All notes").tag(true)
                        Text("Off (drone and click)").tag(false)
                    }.pickerStyle(.inline).labelsHidden()
                }.navigationTitle("Harmonium guide").navigationBarTitleDisplayMode(.inline)
            }
            NavigationLink {
                ArticulationChoiceList(articulation: $settings.articulation)
            } label: {
                VStack(alignment: .leading) {
                    Text("Harmonium notes")
                    Text(settings.articulation == .connected ? "Connected" : "Lightly separated")
                        .foregroundStyle(Color.subdued)
                }.fixedSize(horizontal: false, vertical: true)
            }.accessibilityIdentifier("harmonium-notes")
            NavigationLink("Volumes and drone") {
                Form {
                    Toggle("Tanpura drone", isOn: $settings.drone)
                    LevelSlider(title: "Drone volume", level: $settings.droneLevel).disabled(!settings.drone)
                    LevelSlider(title: "Harmonium volume", level: $settings.harmoniumLevel)
                }.navigationTitle("Volumes and drone").navigationBarTitleDisplayMode(.inline)
            }
        }.navigationTitle("Sound").navigationBarTitleDisplayMode(.inline)
    }

    private var rhythmSettings: some View {
        Form {
            NavigationLink("Practice mode") { modeSettings }
            NavigationLink("Click and count-in") {
                Form {
                    Toggle("Tala click", isOn: $settings.click)
                    Toggle("Count in one avarta", isOn: $settings.countIn)
                }.navigationTitle("Click and count-in").navigationBarTitleDisplayMode(.inline)
            }
            NavigationLink("Repeat and recovery") { repeatSettings }
        }.navigationTitle("Rhythm").navigationBarTitleDisplayMode(.inline)
    }

    private var modeSettings: some View {
        Form {
            Picker("Practice mode", selection: $settings.practiceMode) {
                ForEach(PracticeSettings.ModeChoice.allCases, id: \.self) { choice in
                    Text(choice.title).tag(choice)
                }
            }.pickerStyle(.inline).labelsHidden()
            NavigationLink("Response guide") {
                Form {
                    Toggle("Harmonium on your turn", isOn: $settings.responseGuide)
                    Text("The model always plays harmonium. Your turn uses tanpura and your click settings. Sing along controls pitch feedback; without it, no microphone access is requested.")
                        .font(.footnote).foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }.navigationTitle("Response guide").navigationBarTitleDisplayMode(.inline)
            }
        }.navigationTitle("Practice mode").navigationBarTitleDisplayMode(.inline)
    }

    private var repeatSettings: some View {
        Form {
            Toggle("Repeat", isOn: $settings.loop)
            NavigationLink {
                Form {
                    Picker("Between repeats", selection: $settings.recoveryMode) {
                        ForEach(PracticeSettings.RecoveryChoice.allCases, id: \.self) { choice in
                            Text(choice.title).tag(choice)
                        }
                    }.pickerStyle(.inline).labelsHidden()
                    NavigationLink("Timed break duration") {
                        Form {
                            Stepper(value: $settings.recoverySeconds, in: 1...30) {
                                Text("Break: \(settings.recoverySeconds) seconds")
                                    .fixedSize(horizontal: false, vertical: true)
                            }.accessibilityValue(Text("\(settings.recoverySeconds) seconds"))
                        }.navigationTitle("Break duration").navigationBarTitleDisplayMode(.inline)
                    }
                }.navigationTitle("Between repeats").navigationBarTitleDisplayMode(.inline)
            } label: {
                VStack(alignment: .leading) {
                    Text("Between repeats")
                    Text(settings.recoveryMode.title).foregroundStyle(Color.subdued)
                }.fixedSize(horizontal: false, vertical: true)
            }.disabled(!settings.loop && settings.practiceMode != .listenRepeat)
            NavigationLink("About recovery") {
                Form {
                    NavigationLink("During a break") {
                        Form {
                            Text("Repeat off plays once, or one listen–sing pair. In listen, then sing, recovery also separates the model and your turn. Your tanpura continues without exercise clicks or pitch processing. Continue starts the next count-in; Wait for me cancels a timed restart.")
                                .fixedSize(horizontal: false, vertical: true)
                        }.navigationTitle("During a break").navigationBarTitleDisplayMode(.inline)
                    }
                    NavigationLink("Microphone privacy") {
                        Form {
                            Text("With pitch feedback enabled, the microphone connection and privacy indicator may stay on during breaks and listening. No break or model audio is analyzed, stored or sent. Stop disconnects the microphone.")
                                .fixedSize(horizontal: false, vertical: true)
                        }.navigationTitle("Microphone privacy").navigationBarTitleDisplayMode(.inline)
                    }
                }.navigationTitle("About recovery").navigationBarTitleDisplayMode(.inline)
            }
        }.navigationTitle("Repeat and recovery").navigationBarTitleDisplayMode(.inline)
    }

    private var feedbackSettings: some View {
        Form {
            NavigationLink("Echo cancellation") {
                Form {
                    Toggle("Echo cancellation", isOn: $settings.echoCancellation)
                    Text("On the speaker, removes the app's own sound from what the microphone hears; the volume may drop while it works. Turn it off when using a microphone placed away from the speaker.")
                        .font(.footnote).foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }.navigationTitle("Echo cancellation").navigationBarTitleDisplayMode(.inline)
            }
            NavigationLink("Strictness") {
                Form {
                    Picker("Strictness", selection: $settings.strictness) {
                        Text("Gentle").tag(PracticeSettings.StrictnessChoice.gentle)
                        Text("Steady").tag(PracticeSettings.StrictnessChoice.steady)
                        Text("Precise").tag(PracticeSettings.StrictnessChoice.precise)
                    }.pickerStyle(.inline).labelsHidden()
                    Text("In tune within ±\(Int(settings.strictness.kotlin.inTuneCents)) cents, close within ±\(Int(settings.strictness.kotlin.closeCents)).")
                        .font(.footnote).foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }.navigationTitle("Strictness").navigationBarTitleDisplayMode(.inline)
            }
            NavigationLink("Gitam notation") {
                Form {
                    Picker("Gitam notation", selection: $settings.words) {
                        Text("Swaras").tag(PracticeSettings.WordsChoice.swaras)
                        Text("Words").tag(PracticeSettings.WordsChoice.words)
                        Text("Both").tag(PracticeSettings.WordsChoice.both)
                    }.pickerStyle(.inline).labelsHidden()
                    Text("What a gitam shows under each beat: its swaras, its words, or both.")
                        .font(.footnote).foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }.navigationTitle("Gitam notation").navigationBarTitleDisplayMode(.inline)
            }
        }.navigationTitle("Feedback and notation").navigationBarTitleDisplayMode(.inline)
    }

    /// "C3 · 1 kattai", with " +12¢" when fine-tuned.
    /// Pure formatting, also called from nonisolated text helpers (ShrutiText).
    nonisolated static func saLabel(_ midi: Int, cents: Int = 0) -> String {
        "\(Shruti.shared.noteName(midi: Int32(midi))) · \(Shruti.shared.kattai(midi: Int32(midi))) kattai"
            + Shruti.shared.fineTuneSuffix(cents: Int32(cents))
    }
}

/// How the harmonium joins notes; the tala and notation keep their timing.
private struct ArticulationChoiceList: View {
    @Binding var articulation: PracticeSettings.ArticulationChoice
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                row(.connected, "Connected")
                row(.lightlySeparated, "Lightly separated")
                // Keep the explanation in the section: footers cap Dynamic Type.
                Text("Changes the harmonium guide, not tala timing or where you breathe.")
                    .font(.footnote)
                    .foregroundStyle(Color.subdued)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .navigationTitle("Harmonium notes")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ choice: PracticeSettings.ArticulationChoice, _ label: LocalizedStringKey) -> some View {
        Button {
            articulation = choice
            dismiss()
        } label: {
            HStack {
                Text(label).foregroundStyle(Color.primary).fixedSize(horizontal: false, vertical: true)
                Spacer()
                if articulation == choice {
                    Image(systemName: "checkmark").accessibilityHidden(true)
                }
            }
        }
        .accessibilityAddTraits(articulation == choice ? .isSelected : [])
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

/// This piece climbs past the harmonium samples at the chosen Sa: say so and offer one that fits.
private struct SaOutOfRange: View {
    let sa: String
    let nearest: String
    let useNearest: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                // Red symbol, normal text: red callout text fails contrast.
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.red).accessibilityHidden(true)
                Text("At Sa \(sa) this piece goes higher than the harmonium can play.")
                    .font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button(action: useNearest) {
                Text("Use Sa \(nearest)").fixedSize(horizontal: false, vertical: true)
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true) // never squeezed by the notation below
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}

private struct PhraseChoices: View {
    let phrases: [PracticePhrase]
    @Binding var selected: PracticePhrase?
    @Binding var section: SectionSpan?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Button("Whole piece") { selected = nil; section = nil; dismiss() }
                ForEach(phrases, id: \.id) { phrase in
                    Button {
                        section = nil
                        selected = phrase
                        dismiss()
                    } label: {
                        VStack(alignment: .leading) {
                            Text(phrase.label).foregroundStyle(Color.primary)
                            Text("Avartas \(phrase.firstAvarta + 1)–\(phrase.lastAvarta + 1)")
                                .foregroundStyle(Color.subdued)
                        }.fixedSize(horizontal: false, vertical: true)
                    }.accessibilityAddTraits(selected?.id == phrase.id ? .isSelected : [])
                }
            }.navigationTitle("Practice unit").navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// Practise one section alone, or the whole piece: a sheet, so the choice
/// follows Dynamic Type (menu pickers cap it).
private struct SectionChoices: View {
    let sections: [SectionSpan]
    @Binding var selected: SectionSpan?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                row(nil, String(localized: "Whole piece"))
                ForEach(sections, id: \.self) { section in row(section, section.label) }
            }
            .navigationTitle("Section")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func row(_ section: SectionSpan?, _ label: String) -> some View {
        Button {
            selected = section
            dismiss()
        } label: {
            HStack {
                Text(label).foregroundStyle(Color.primary)
                Spacer()
                if selected == section {
                    Image(systemName: "checkmark").foregroundStyle(Color.accentColor).accessibilityHidden(true)
                }
            }
        }
        .accessibilityAddTraits(selected == section ? .isSelected : [])
    }
}
