import SwiftUI
import shared

/// "Your own raga" (docs/plan-recording-insights.md, step 3): a name and, for
/// the arohana and the avarohana, one swara per letter or none (each letter a
/// row that opens its choices). The checks show as one sentence; Save waits
/// for them. A ScrollView, not a List, so rows pushed below the fold by larger
/// text stay loaded (the accessibility audit's Dynamic Type check).
struct RagaEditorView: View {
    @EnvironmentObject private var recordings: RecordingsViewModel
    @Environment(\.dismiss) private var dismiss
    let target: RagaEditorTarget
    /// The recording it was opened from, or nil for the Record screen.
    let onRecording: String?

    @State private var name = ""
    @State private var up: [Swara] = []
    @State private var down: [Swara] = []
    @State private var same = true
    @State private var loaded = false
    @State private var deleting = false

    private var editing: CustomRaga? { target.key.flatMap { recordings.customRagas.get(key: $0) } }
    private var arohana: [Swara] { up.sorted { $0.semitone < $1.semitone } }
    private var avarohana: [Swara] { same ? arohana : down.sorted { $0.semitone < $1.semitone } }
    private var problem: String? {
        recordings.customRagas.problem(key: target.key, name: name, arohana: arohana, avarohana: avarohana)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    TextField("Name", text: $name)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: name) { value in
                            let limit = Int(CustomRaga.companion.MAX_NAME)
                            if value.count > limit { name = String(value.prefix(limit)) }
                        }
                    Text("Choose one swara for each letter, or none. Sa is always there.")
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Arohana (going up)").font(.headline).accessibilityAddTraits(.isHeader)
                    letters($up)
                    Toggle("Same swaras coming down", isOn: Binding(get: { same }, set: { on in
                        if !on { down = up }
                        same = on
                    }))
                    if !same {
                        Text("Avarohana (coming down)").font(.headline).accessibilityAddTraits(.isHeader)
                        letters($down)
                    }
                    readBack("Up", CustomRaga.companion.line(swaras: arohana, descending: false),
                             CustomRaga.companion.spokenLine(swaras: arohana, descending: false))
                    readBack("Down", CustomRaga.companion.line(swaras: avarohana, descending: true),
                             CustomRaga.companion.spokenLine(swaras: avarohana, descending: true))
                    if let problem {
                        Text(problem).foregroundStyle(.red).fixedSize(horizontal: false, vertical: true)
                    }
                    if let editing {
                        Button("Delete raga", role: .destructive) { deleting = true }
                            .buttonStyle(.bordered)
                            .alert("Delete this raga?", isPresented: $deleting) {
                                Button("Delete raga", role: .destructive) { recordings.deleteRaga() }
                                Button("Cancel", role: .cancel) {}
                            } message: {
                                Text("\(editing.name) will be removed. Recordings that used it go back to default names and free singing.")
                            }
                    }
                }
                .padding()
            }
            .navigationTitle(target.key == nil ? "Your own raga" : "Edit raga")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // Symbols, not text: text bar buttons cap Dynamic Type (the audit).
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel(Text("Cancel"))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        recordings.saveRaga(name: name, arohana: arohana, avarohana: avarohana, onRecording: onRecording)
                    } label: { Image(systemName: "checkmark") }
                        .accessibilityLabel(Text("Save"))
                        .disabled(problem != nil)
                }
            }
        }
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        if let editing {
            name = editing.name
            up = editing.arohana
            down = editing.avarohana
            same = editing.arohana == editing.avarohana
        }
    }

    /// A row per letter (Ri … Ni): "Ri   R₂ ›", opening None and its swaras.
    private func letters(_ chosen: Binding<[Swara]>) -> some View {
        VStack(spacing: 0) {
            ForEach(CustomRaga.companion.LETTERS, id: \.name) { letter in
                let current = chosen.wrappedValue.first { CustomRaga.companion.letterOf(swara: $0) == letter }
                let letterName = CustomRaga.companion.spoken(letter: letter)
                NavigationLink {
                    SwaraLetterChoice(letter: letter, selected: current) { swara in
                        chosen.wrappedValue = chosen.wrappedValue.filter { CustomRaga.companion.letterOf(swara: $0) != letter }
                            + (swara.map { [$0] } ?? [])
                    }
                } label: {
                    HStack {
                        Text(letterName).foregroundStyle(Color.primary)
                        Spacer(minLength: 8)
                        Text(current.map { CustomRaga.companion.label(swara: $0) } ?? String(localized: "None"))
                            .foregroundStyle(Color.subdued)
                        Image(systemName: "chevron.right").foregroundStyle(Color.subdued).accessibilityHidden(true)
                    }
                    .padding(.vertical, 8)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(letterName))
                .accessibilityValue(Text(current.map { CustomRaga.companion.spokenSwara(swara: $0) } ?? String(localized: "None")))
                .accessibilityAddTraits(.isButton)
                Divider()
            }
        }
    }

    /// "Up: S R₂ G₃ P D₂ S′", read as "Up: Sa, Ri 2, …".
    private func readBack(_ title: LocalizedStringKey, _ line: String, _ spoken: String) -> some View {
        (Text(title) + Text(": ") + Text(line))
            .font(.title3.weight(.semibold))
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(title) + Text(": ") + Text(spoken))
    }
}

/// None, or one of a letter's swaras (R₁ R₂ R₃), the current one checked.
private struct SwaraLetterChoice: View {
    let letter: SwaraLetter
    let selected: Swara?
    let onSelect: (Swara?) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            row(nil, String(localized: "None"), String(localized: "None"))
            ForEach(CustomRaga.companion.choices(letter: letter), id: \.name) { swara in
                row(swara, CustomRaga.companion.label(swara: swara), CustomRaga.companion.spokenSwara(swara: swara))
            }
        }
        .navigationTitle(CustomRaga.companion.spoken(letter: letter))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ swara: Swara?, _ text: String, _ spoken: String) -> some View {
        Button {
            onSelect(swara)
            dismiss()
        } label: {
            HStack {
                Text(text).foregroundStyle(Color.primary)
                Spacer()
                if swara == selected { Image(systemName: "checkmark").accessibilityHidden(true) }
            }
        }
        .accessibilityLabel(Text(spoken))
        .accessibilityAddTraits(swara == selected ? .isSelected : [])
    }
}
