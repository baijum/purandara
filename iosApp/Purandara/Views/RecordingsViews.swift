import SwiftUI
import shared

/// Text shared by the recordings screens (docs/plan-recordings.md).
enum RecordingText {
    /// "15 Nov, 6:42 pm" in the device's locale: the date part of the automatic title.
    static func dateLabel(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        return date.formatted(.dateTime.day().month(.abbreviated).hour().minute())
    }

    static func title(_ recording: Recording) -> String { recording.title(dateLabel: dateLabel(recording.createdMillis)) }

    static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded(.down))
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    /// "2 minutes, 14 seconds" for VoiceOver (the screen shows 2:14).
    static func spokenDuration(_ seconds: Double) -> String {
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .full
        formatter.allowedUnits = seconds >= 60 ? [.minute, .second] : [.second]
        return formatter.string(from: seconds.rounded()) ?? clock(seconds)
    }

    /// "R1", "low P", "high S held", "R2, not in the raga".
    static func spoken(_ note: HeardNote) -> String {
        var s = note.swara.symbol
        if note.octave < 0 { s = String(localized: "low \(s)") }
        if note.octave > 0 { s = String(localized: "high \(s)") }
        if note.holds > 0 { s = String(localized: "\(s) held") }
        if !note.inRaga { s = String(localized: "\(s), not in the raga") }
        return s
    }

    static func shown(_ note: HeardNote) -> String { note.label + String(repeating: " ,", count: Int(note.holds)) }

    static func traceSummary(_ t: Transcription) -> String {
        guard let low = t.lowest, let high = t.highest else { return String(localized: "No notes") }
        let plain = { (n: HeardNote) in spoken(n.doCopy(holds: 0, inRaga: true)) }
        // Inflected ("1 note", "3 notes"): only AttributedString applies the grammar agreement.
        let text = AttributedString(localized: "^[\(t.notes.count) note](inflect: true) in ^[\(t.phrases.count) phrase](inflect: true), from \(plain(low)) to \(plain(high))")
        return String(text.characters)
    }
}

private extension HeardNote {
    /// Kotlin `copy` arrives as `doCopy` with every parameter.
    func doCopy(holds: Int32, inRaga: Bool) -> HeardNote {
        HeardNote(swara: swara, octave: octave, startSeconds: startSeconds, durationSeconds: durationSeconds,
                  centsOff: centsOff, inRaga: inRaga, holds: holds)
    }
}

// MARK: - The list

/// The saved recordings, newest first, with a New recording button.
struct RecordingsListView: View {
    @EnvironmentObject private var recordings: RecordingsViewModel
    @Binding var path: [String]

    var body: some View {
        List {
            Section {
                Button { path.append("record") } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "mic.fill").accessibilityHidden(true)
                        Text("New recording").fixedSize(horizontal: false, vertical: true)
                    }
                }
                .buttonStyle(.borderedProminent)
                if recordings.library.isEmpty {
                    Text("Record yourself singing and the app shows the swaras it heard, with your pitch on the swara lines. Recordings stay on this device.")
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            if !recordings.library.isEmpty {
                Section {
                    ForEach(recordings.library.recordings, id: \.id) { recording in
                        row(recording)
                    }
                }
            }
        }
        .navigationTitle("Recordings")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ recording: Recording) -> some View {
        let title = RecordingText.title(recording)
        let date = Date(timeIntervalSince1970: Double(recording.createdMillis) / 1000)
        return Button { path.append("recording:\(recording.id)") } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.primary).fixedSize(horizontal: false, vertical: true)
                    Text("\(RecordingText.clock(recording.durationSeconds)) · \(date.formatted(.relative(presentation: .named)))")
                        .font(.subheadline)
                        .foregroundStyle(Color.subdued)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").foregroundStyle(Color.subdued).accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(title), \(RecordingText.spokenDuration(recording.durationSeconds))"))
        .accessibilityAddTraits(.isButton)
        .swipeActions {
            Button(role: .destructive) { recordings.delete(recording.id) } label: { Text("Delete") }
        }
        .accessibilityAction(named: Text("Delete")) { recordings.delete(recording.id) }
    }
}

// MARK: - Recording

/// Record / Stop, the elapsed time and a level meter; then the analysis progress.
/// "Listening to your tanpura…" with a countdown for the first seconds, then
/// "Now sing", announced to VoiceOver with a haptic tap
/// (docs/plan-recordings-tanpura.md). The countdown digit is visual only.
private struct TanpuraCue: View {
    let listening: Bool
    let secondsLeft: Int

    var body: some View {
        VStack(spacing: 4) {
            Text(listening ? "Listening to your tanpura…" : "Now sing")
                .font(.title2.bold())
                .foregroundStyle(listening ? Color.subdued : Color.accentColor)
                .fixedSize(horizontal: false, vertical: true)
            if listening {
                Text("\(max(1, secondsLeft))").font(.largeTitle.monospacedDigit()).fixedSize().accessibilityHidden(true)
            }
        }
        .onChange(of: listening) { now in
            guard !now else { return }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            UIAccessibility.post(notification: .announcement, argument: String(localized: "Now sing"))
        }
    }
}

struct RecordView: View {
    @EnvironmentObject private var recordings: RecordingsViewModel
    @Binding var path: [String]

    var body: some View {
        let r = recordings.recorder
        ScrollView {
            VStack(spacing: 20) {
                Text("Swaras are read against Sa \(PracticeSettingsView.saLabel(r.saMidi, cents: r.saCents)). You can change it afterwards.")
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                if let analysing = r.analysing {
                    Text("Finding the swaras…").font(.headline)
                    ProgressView(value: analysing)
                } else if !recordings.micAllowed {
                    Text("Allow the microphone to record yourself.").fixedSize(horizontal: false, vertical: true)
                    Button("Allow microphone") { recordings.allowMic() }.buttonStyle(.borderedProminent)
                } else {
                    if !r.recording {
                        IntentSection(raga: r.ragaKey, scale: r.scale, recent: recordings.recentRagas, own: recordings.customRagas,
                                      onEditRaga: { recordings.editRaga($0) }) { raga, scale in
                            recordings.setIntent(raga: raga, scale: scale)
                        }
                        Toggle(isOn: Binding(get: { recordings.recorder.tanpura }, set: { recordings.setTanpura($0) })) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("External tanpura playing")
                                Text("Start your tanpura first, and let it play for 3 seconds before you sing. Keep it a little quieter than your voice.")
                                    .font(.footnote)
                                    .foregroundStyle(Color.subdued)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    if let key = r.ragaKey, let raga = Ragas.shared.byKey(key: key), let scale = r.scale {
                        Text(RecordingInsights.shared.referenceLabels(raga: raga, scale: scale).joined(separator: "  "))
                            .font(.title2.weight(.semibold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityLabel(RecordingInsights.shared.referenceSpoken(raga: raga, scale: scale))
                    }
                    controls(r)
                }
                if r.failed {
                    Text("That recording could not be saved. Try again.")
                        .foregroundStyle(.red)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text("The app plays no tanpura while recording. To sing with your own, turn on External tanpura playing above.")
                    .font(.footnote)
                    .foregroundStyle(Color.subdued)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding()
        }
        .navigationTitle("New recording")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $recordings.ragaEditor) { target in
            RagaEditorView(target: target, onRecording: nil).environmentObject(recordings)
        }
        .onDisappear { if recordings.recorder.recording { recordings.stopRecording() } }
        .onAppear(perform: autoRecord)
        .onChange(of: recordings.justRecorded) { id in
            guard let id else { return }
            recordings.justRecorded = nil
            if path.last == "record" {
                path.removeLast()
                path.append("recording:\(id)")
            }
        }
    }

    /// Debug aid: `-open record -recordSeconds 9` records for that long by itself (with `-singer 0`, a synthetic singer).
    private func autoRecord() {
        #if DEBUG
        let seconds = UserDefaults.standard.integer(forKey: "recordSeconds")
        guard seconds > 0, !recordings.recorder.recording, recordings.recorder.analysing == nil else { return }
        recordings.startRecording()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
            recordings.stopRecording()
        }
        #endif
    }

    @ViewBuilder private func controls(_ r: RecorderUI) -> some View {
        Button {
            if r.recording { recordings.stopRecording() } else { recordings.startRecording() }
        } label: {
            VStack(spacing: 8) {
                Image(systemName: r.recording ? "stop.circle.fill" : "mic.circle.fill")
                    .font(.system(size: 88))
                    .accessibilityHidden(true)
                Text(r.recording ? "Stop recording" : "Start recording").font(.headline)
            }
        }
        .accessibilityLabel(Text(r.recording ? "Stop recording" : "Start recording"))
        if r.recording && r.tanpura {
            TanpuraCue(listening: r.listening, secondsLeft: Int((TanpuraProfile.companion.LISTEN_SECONDS - r.seconds).rounded(.up)))
        }
        if r.recording {
            Text(RecordingText.clock(r.seconds))
                .font(.largeTitle.monospacedDigit())
                .fixedSize()
                .accessibilityLabel(Text("Recording, \(RecordingText.spokenDuration(r.seconds))"))
                .accessibilityAddTraits(.updatesFrequently)
            ProgressView(value: min(1, Double(r.level) * 4))
                .accessibilityHidden(true) // decorative level meter
            if r.seconds >= Double(RecordingLibrary.companion.WARN_SECONDS) {
                Text("Recording stops at 10 minutes.")
            }
        }
    }
}

// MARK: - One recording

/// The swaras heard, the pitch trace, playback, and Sa / raga / rename / delete.
struct RecordingDetailView: View {
    @EnvironmentObject private var recordings: RecordingsViewModel
    let id: String
    @Binding var path: [String]
    @State private var renaming = false
    @State private var newTitle = ""
    @State private var deleting = false

    var body: some View {
        Group {
            if let d = recordings.detail, d.recording.id == id {
                content(d)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Recording")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $recordings.ragaEditor) { target in
            RagaEditorView(target: target, onRecording: id).environmentObject(recordings)
        }
        .onAppear { recordings.open(id) }
        .onDisappear {
            // Leaving for the Sa or raga list keeps it open; leaving the recording closes it.
            if !path.contains("recording:\(id)") { recordings.close(id) }
        }
    }

    @ViewBuilder private func content(_ d: RecordingDetailUI) -> some View {
        let recording = d.recording
        let title = RecordingText.title(recording)
        // A ScrollView, not a List: rows stay loaded when larger text pushes them
        // below the fold (the accessibility audit's Dynamic Type check).
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(title).font(.title3.bold()).accessibilityAddTraits(.isHeader).fixedSize(horizontal: false, vertical: true)
                HStack {
                    Button("Rename") {
                        newTitle = recording.customTitle ?? ""
                        renaming = true
                    }
                    .buttonStyle(.bordered)
                    Button("Delete", role: .destructive) { deleting = true }
                        .buttonStyle(.bordered)
                }
                #if DEBUG
                Button("Re-analyse (debug)") { recordings.reanalyse(recording.id) }
                    .buttonStyle(.bordered)
                    .disabled(d.updating != nil)
                #endif
                if let progress = d.updating {
                    Text("Updating the swaras: the app reads them better now…")
                        .font(.subheadline)
                        .fixedSize(horizontal: false, vertical: true)
                    ProgressView(value: progress)
                        .accessibilityLabel("Updating the swaras")
                }
                playback(d)
                Divider()
                Text("Swaras heard").font(.headline).accessibilityAddTraits(.isHeader)
                Text("Approximate: gamakas and slides are read as their steadiest notes. Check them against your pitch below.")
                    .font(.footnote)
                    .foregroundStyle(Color.subdued)
                    .fixedSize(horizontal: false, vertical: true)
                if d.track == nil {
                    Text("The pitch of this recording could not be read.").foregroundStyle(.red)
                } else if d.transcription.phrases.isEmpty {
                    Text("No steady swaras were heard.")
                }
                phrases(d)
                if let insights = d.insights {
                    Divider()
                    HowItWentView(insights: insights) { midi, cents in recordings.changeSa(id, midi: midi, cents: cents) }
                }
                if d.track != nil {
                    Divider()
                    Text("Your pitch").font(.headline).accessibilityAddTraits(.isHeader)
                    RecordingTraceView(detail: d) { recordings.seek(to: $0) }
                        .frame(height: 220)
                }
                Divider()
                NavigationLink {
                    SaChoiceList(
                        saMidi: Binding(get: { Int(recording.saMidi) }, set: { recordings.changeSa(id, midi: $0, cents: Int(recording.saCents)) }),
                        saCents: Binding(get: { Int(recordings.detail?.recording.saCents ?? recording.saCents) },
                                         set: { recordings.changeSa(id, midi: Int(recordings.detail?.recording.saMidi ?? recording.saMidi), cents: $0) })
                    )
                } label: {
                    LabeledRow(title: "Sa", value: PracticeSettingsView.saLabel(Int(recording.saMidi), cents: Int(recording.saCents)))
                }
                IntentSection(raga: recording.ragaKey, scale: recording.scale, recent: recordings.recentRagas, own: recordings.customRagas,
                              onEditRaga: { recordings.editRaga($0) }) { raga, scale in
                    recordings.changeIntent(id, raga: raga, scale: scale)
                }
                Toggle("External tanpura playing", isOn: Binding(
                    get: { recording.externalTanpura }, set: { recordings.changeTanpura(id, $0) }))
                if let offer = d.tanpuraSa {
                    let sa = PracticeSettingsView.saLabel(offer.midi, cents: offer.cents)
                    Text("Your tanpura is at \(sa).").fixedSize(horizontal: false, vertical: true)
                    Button("Use this Sa") { recordings.changeSa(id, midi: offer.midi, cents: offer.cents) }
                        .buttonStyle(.bordered)
                        .accessibilityLabel(Text("Use the tanpura's Sa, \(sa)"))
                }
            }
            .padding()
        }
        .alert("Rename recording", isPresented: $renaming) {
            TextField("Title", text: $newTitle)
            Button("Save") { recordings.rename(id, newTitle) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Leave empty for the automatic title.")
        }
        .confirmationDialog("Delete this recording?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                recordings.delete(id)
                if path.last == "recording:\(id)" { path.removeLast() }
            }
        } message: {
            Text("\(title) will be deleted from this device. This can't be undone.")
        }
    }

    private func playback(_ d: RecordingDetailUI) -> some View {
        let duration = max(0.1, d.recording.durationSeconds)
        return HStack(spacing: 12) {
            Button { recordings.togglePlay() } label: {
                Image(systemName: d.playing ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 44))
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.accentColor)
            .accessibilityLabel(Text(d.playing ? "Pause" : "Play"))
            Slider(value: Binding(get: { min(d.position, duration) }, set: { recordings.seek(to: $0) }), in: 0...duration)
                .accessibilityLabel(Text("Position"))
                .accessibilityValue(Text("\(RecordingText.clock(d.position)) of \(RecordingText.clock(duration))"))
            Text(RecordingText.clock(d.position)).font(.subheadline.monospacedDigit()).accessibilityHidden(true)
        }
    }

    @ViewBuilder private func phrases(_ d: RecordingDetailUI) -> some View {
        let current = (d.playing || d.position > 0) ? d.transcription.noteIndexAt(seconds: d.position)?.intValue : nil
        let starts = d.transcription.phrases.reduce(into: [0]) { $0.append($0.last! + $1.count) }
        ForEach(Array(d.transcription.phrases.enumerated()), id: \.offset) { p, phrase in
            let spoken = phrase.map(RecordingText.spoken).joined(separator: ", ")
            // One wrapping Text per phrase (Dynamic Type), the sounding swara highlighted.
            Text(Self.phraseText(phrase, highlighted: current.map { $0 - starts[p] }))
                .font(.title3.bold())
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture { recordings.seek(to: phrase.first?.startSeconds ?? 0) }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Phrase \(p + 1): \(spoken)"))
                .accessibilityHint(Text("Plays from here"))
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { recordings.seek(to: phrase.first?.startSeconds ?? 0) }
        }
    }

    /// "S  R₁  G₃ ,", out-of-raga swaras in red, the [highlighted] one on the accent colour.
    private static func phraseText(_ phrase: [HeardNote], highlighted: Int?) -> AttributedString {
        var text = AttributedString()
        for (i, note) in phrase.enumerated() {
            if i > 0 { text.append(AttributedString("   ")) }
            var part = AttributedString(RecordingText.shown(note))
            if i == highlighted {
                part.foregroundColor = .white
                part.backgroundColor = .accentColor
            } else if !note.inRaga {
                part.foregroundColor = .red
            }
            text.append(part)
        }
        return text
    }
}

/// A title and its current value in one row (Label clips in list rows).
private struct LabeledRow: View {
    let title: LocalizedStringKey
    let value: String

    var body: some View {
        HStack {
            Text(title).foregroundStyle(Color.primary)
            Spacer(minLength: 8)
            Text(value).foregroundStyle(Color.subdued).multilineTextAlignment(.trailing)
            Image(systemName: "chevron.right").foregroundStyle(Color.subdued).accessibilityHidden(true)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// None, or one of the app's ragas; the current one checked.
/// "What are you singing?" (docs/plan-recording-insights.md): the raga (a
/// pushed list, recent first) and, once one is chosen, Free singing /
/// Arohana / Avarohana / Arohana and avarohana as buttons with a checkmark
/// (menu and segmented pickers cap Dynamic Type).
private struct IntentSection: View {
    let raga: String?
    let scale: Scale?
    let recent: [String]
    var own = CustomRagas(ragas: [])
    var onEditRaga: (String?) -> Void = { _ in }
    let onChange: (String?, Scale?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("What are you singing?").font(.headline).accessibilityAddTraits(.isHeader)
            NavigationLink {
                RagaChoiceList(selected: raga, recent: recent, own: own, onAdd: { onEditRaga(nil) }) { onChange($0, $0 == nil ? nil : scale) }
            } label: {
                LabeledRow(title: "Raga", value: raga.flatMap { Ragas.shared.byKey(key: $0)?.name } ?? String(localized: "None (default names)"))
            }
            if let key = raga, let mine = own.get(key: key) {
                Button("Edit raga") { onEditRaga(key) }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(Text("Edit \(mine.name)"))
            }
            if raga != nil {
                choice(nil, "Free singing")
                choice(.arohana, "Arohana")
                choice(.avarohana, "Avarohana")
                choice(.both, "Arohana and avarohana")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func choice(_ value: Scale?, _ title: LocalizedStringKey) -> some View {
        Button { onChange(raga, value) } label: {
            HStack {
                Text(title).foregroundStyle(Color.primary)
                Spacer()
                if value == scale { Image(systemName: "checkmark").accessibilityHidden(true) }
            }
            .contentShape(Rectangle())
            .frame(minHeight: 44)
        }
        .accessibilityAddTraits(value == scale ? .isSelected : [])
    }
}

/// "How it went": the Sa the notes fit (with a button), the swaras with their
/// cents as one wrapping line (one element, read as "Sa, in tune; Ma, 17
/// cents flat; …"), a sentence, steadiness.
private struct HowItWentView: View {
    let insights: Insights
    let onUseSa: (Int, Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("How it went").font(.headline).accessibilityAddTraits(.isHeader)
            if insights.tanpuraNotHeard {
                Text("No tanpura was heard in the first 3 seconds, so the recording was read without it.")
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let offer = insights.suggestedSa, let midi = offer.first?.intValue, let cents = offer.second?.intValue {
                let sa = PracticeSettingsView.saLabel(midi, cents: cents)
                Text("Your notes fit a Sa of \(sa).").fixedSize(horizontal: false, vertical: true)
                Button("Use this Sa") { onUseSa(midi, cents) }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(Text("Use your Sa, \(sa)"))
            }
            if !insights.swaras.isEmpty {
                chips
                    .font(.title3)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(insights.swaras.map { $0.spoken() }.joined(separator: "; "))
            }
            Text(insights.summary).fixedSize(horizontal: false, vertical: true)
            ForEach(insights.steadiness, id: \.self) { line in
                Text(line).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// "S −4¢  R₁ −3¢  G₃ +24¢ …" as one Text, coloured by closeness.
    private var chips: Text {
        insights.swaras.enumerated().reduce(Text("")) { line, item in
            let (index, s) = item
            let gap = index == 0 ? Text("") : Text("   ")
            let mark: Text
            // Cents for every swara (an inline image here measures as clipped text in the audit).
            let cents = Text(RecordingInsights.shared.centsText(cents: s.cents?.doubleValue ?? 0))
            switch s.closeness {
            case .inTune: mark = cents.foregroundColor(.accentColor)
            case .missed: mark = Text("not heard").foregroundColor(.red)
            case .aLittleFlat, .aLittleSharp: mark = cents.foregroundColor(.orange)
            default: mark = cents.foregroundColor(.red)
            }
            return line + gap + Text(s.label).bold() + Text(" ") + mark
        }
    }
}

private struct RagaChoiceList: View {
    let selected: String?
    var recent: [String] = []
    var own = CustomRagas(ragas: [])
    /// "Add your own raga…": closes the list and opens the editor; nil hides it.
    var onAdd: (() -> Void)?
    let onSelect: (String?) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let recentRagas = recent.compactMap { Ragas.shared.byKey(key: $0) }
        let mine = own.toRagas().filter { r in !recentRagas.contains { $0.key == r.key } }
        List {
            row(nil, String(localized: "None (default names)"))
            if !recentRagas.isEmpty {
                Section("Recent") {
                    ForEach(recentRagas, id: \.key) { raga in row(raga.key, raga.name) }
                }
            }
            Section(recentRagas.isEmpty ? "" : "All ragas") {
                ForEach(Ragas.shared.all.filter { r in !recentRagas.contains { $0.key == r.key } }.sorted { $0.name < $1.name }, id: \.key) { raga in
                    row(raga.key, raga.name)
                }
            }
            Section("Your ragas") {
                ForEach(mine, id: \.key) { raga in row(raga.key, raga.name) }
                if let onAdd {
                    Button {
                        dismiss()
                        onAdd()
                    } label: {
                        HStack {
                            Image(systemName: "plus").accessibilityHidden(true)
                            Text("Add your own raga…")
                        }
                    }
                }
            }
        }
        .navigationTitle("Raga")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ key: String?, _ name: String) -> some View {
        Button {
            onSelect(key)
            dismiss()
        } label: {
            HStack {
                Text(name).foregroundStyle(Color.primary)
                Spacer()
                if key == selected { Image(systemName: "checkmark").accessibilityHidden(true) }
            }
        }
        .accessibilityAddTraits(key == selected ? .isSelected : [])
    }
}

// MARK: - Trace

/// The pitch on the swara lines: an 8-second window that follows playback
/// (paging, with Reduce Motion); tap to move there.
struct RecordingTraceView: View {
    let detail: RecordingDetailUI
    let onSeek: (Double) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private nonisolated static let window = 8.0
    private nonisolated static let labelWidth: CGFloat = 22

    private var windowStart: Double {
        let duration = detail.recording.durationSeconds
        guard duration > Self.window else { return 0 }
        let start = reduceMotion ? (detail.position / Self.window).rounded(.down) * Self.window : detail.position - Self.window / 3
        return min(max(0, start), duration - Self.window)
    }

    var body: some View {
        let start = windowStart
        let lane = detail.lane
        let track = detail.track
        let notes = detail.transcription.notes
        let saHz = detail.recording.saHz
        let position = detail.position
        let tanpura = detail.recording.externalTanpura
        GeometryReader { geo in
            Canvas { context, size in
                let left = Self.labelWidth
                func x(_ s: Double) -> CGFloat { left + CGFloat((s - start) / Self.window) * (size.width - left) }
                func y(_ cents: Double) -> CGFloat { size.height * CGFloat(1 - lane.fraction(cents: cents)) }
                if tanpura {
                    // The listening seconds (the tanpura alone), which the swaras ignore.
                    let end = x(TanpuraProfile.companion.LISTEN_SECONDS)
                    if end > left {
                        context.fill(Path(CGRect(x: left, y: 0, width: end - left, height: size.height)), with: .color(.secondary.opacity(0.15)))
                    }
                }
                for line in lane.lines {
                    let yy = y(shared.PitchLane.companion.centsOf(note: line))
                    var path = Path()
                    path.move(to: CGPoint(x: left, y: yy))
                    path.addLine(to: CGPoint(x: size.width, y: yy))
                    let isSa = line.swara.semitone == 0
                    context.stroke(path, with: .color(isSa ? .accentColor : .secondary.opacity(0.4)), lineWidth: isSa ? 1.5 : 0.7)
                    context.draw(Text(shared.PitchLane.companion.label(note: line)).font(.caption2).foregroundColor(.secondary),
                                 at: CGPoint(x: 2, y: yy), anchor: .leading)
                }
                if let track {
                    let from = Int(track.indexAt(seconds: start))
                    let to = Int(track.indexAt(seconds: start + Self.window))
                    if from <= to {
                        for i in from...to {
                            guard let hz = track.frequencyAt(index: Int32(i))?.doubleValue else { continue }
                            let cents = Pitch.shared.cents(fromHz: saHz, toHz: hz)
                            let f = lane.fraction(cents: cents)
                            guard f >= 0 && f <= 1 else { continue }
                            let p = CGPoint(x: x(track.timeAt(index: Int32(i))), y: y(cents))
                            context.fill(Path(ellipseIn: CGRect(x: p.x - 1.5, y: p.y - 1.5, width: 3, height: 3)), with: .color(.orange))
                        }
                    }
                }
                for note in notes where note.endSeconds >= start && note.startSeconds <= start + Self.window {
                    let cents = shared.PitchLane.companion.centsOf(note: Note(swara: note.swara, octave: .madhya)) + Double(note.octave) * 1200
                    var path = Path()
                    path.move(to: CGPoint(x: max(left, x(note.startSeconds)), y: y(cents)))
                    path.addLine(to: CGPoint(x: x(note.endSeconds), y: y(cents)))
                    context.stroke(path, with: .color(.accentColor), lineWidth: 3)
                }
                let cx = x(position)
                if cx >= left {
                    var cursor = Path()
                    cursor.move(to: CGPoint(x: cx, y: 0))
                    cursor.addLine(to: CGPoint(x: cx, y: size.height))
                    context.stroke(cursor, with: .color(.primary), lineWidth: 1.5)
                }
            }
            .clipped()
            .contentShape(Rectangle())
            .onTapGesture { location in
                let fraction = (location.x - Self.labelWidth) / max(1, geo.size.width - Self.labelWidth)
                onSeek(start + Double(fraction) * Self.window)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Pitch trace"))
        .accessibilityValue(Text(RecordingText.traceSummary(detail.transcription)))
    }
}
