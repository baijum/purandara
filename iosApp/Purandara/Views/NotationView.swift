import SwiftUI
import shared

/// The exercise as a grid: one row per avarta, eight akshara cells in the
/// same columns as the tala strip above. The sounding akshara and swara are
/// highlighted, and the list follows playback. Mirrors Android's `NotationView`.
struct NotationView: View {
    let varisai: Varisai
    let playback: PlaybackState
    var marks: [MarkKey: NoteResult] = [:]
    /// Optional content scrolled above the rows (the sing-along panel at accessibility text sizes).
    var header: AnyView?
    private let tala = Tala.companion.ADI

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let header { header }
                    ForEach(Array(varisai.avartas.enumerated()), id: \.offset) { index, avarta in
                        let rowMarks = marks.filter { $0.key.avarta == index }
                        AvartaRow(
                            avarta: avarta,
                            tala: tala,
                            activeAkshara: playback.avarta == index ? playback.akshara : nil,
                            activeSlot: playback.avarta == index ? playback.slot : nil,
                            marks: rowMarks)
                        .id(index)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("Avarta \(index + 1): \(spoken(avarta, index: index, marks: rowMarks))"))
                    }
                }
                .padding(.horizontal, 12)
            }
            .onChange(of: playback.avarta) { avarta in
                guard let avarta else { return }
                if reduceMotion {
                    proxy.scrollTo(avarta)
                } else {
                    withAnimation { proxy.scrollTo(avarta) }
                }
            }
        }
    }
}

extension NotationView {
    /// Row sentence; after sing-along, each swara with its mark ("S in tune R high…").
    func spoken(_ avarta: Avarta, index: Int, marks: [MarkKey: NoteResult]) -> String {
        let hold = String(localized: "hold")
        guard !marks.isEmpty else { return avarta.spoken(tala: tala, karvaiWord: hold) }
        return avarta.aksharas.enumerated().map { i, akshara -> String in
            let text: String
            if let swaras = (akshara as? AksharaSwaras)?.swaras {
                text = swaras.enumerated().map { slot, swara in
                    let word = SingAlongText.markWords(marks[MarkKey(avarta: index, akshara: i, slot: slot)])
                    return word.map { "\(swara.description()) \($0)" } ?? swara.description()
                }.joined(separator: " ")
            } else {
                text = hold
            }
            if i == 0 { return text }
            return (tala.beats[i].startsAnga ? ", " : " ") + text
        }.joined()
    }
}

private struct AvartaRow: View {
    let avarta: Avarta
    let tala: Tala
    let activeAkshara: Int?
    let activeSlot: Int?
    let marks: [MarkKey: NoteResult]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(avarta.aksharas.enumerated()), id: \.offset) { i, akshara in
                let beat = tala.beats[i]
                if i > 0 && beat.startsAnga { AngaBar() }
                AksharaCell(
                    akshara: akshara,
                    active: i == activeAkshara,
                    activeSlot: i == activeAkshara ? activeSlot : nil,
                    marks: Dictionary(uniqueKeysWithValues: marks.filter { $0.key.akshara == i }.map { ($0.key.slot, $0.value) }))
            }
            AngaBar(double: true)
        }
        .frame(minHeight: 48)
        // Grow to fit the swaras plus their marks instead of shrinking the text.
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct AksharaCell: View {
    let akshara: any Akshara
    let active: Bool
    let activeSlot: Int?
    let marks: [Int: NoteResult]

    private var swaras: [WrittenSwara] { (akshara as? AksharaSwaras)?.swaras ?? [] }

    /// SF Symbol for each shared mark glyph.
    static func symbol(for glyph: String?) -> String? {
        switch glyph {
        case "✓": return "checkmark"
        case "↑": return "arrow.up"
        case "↓": return "arrow.down"
        case "⇡": return "arrow.up.to.line"
        case "⇣": return "arrow.down.to.line"
        case "✕": return "xmark"
        case "○": return "circle"
        default: return nil
        }
    }

    var body: some View {
        VStack(spacing: 2) {
            text
                // Text styles follow Dynamic Type; rows grow with them.
                .font(swaras.count > 2 ? .callout : swaras.count == 2 ? .title3 : .title2)
                .lineLimit(1)
                .minimumScaleFactor(0.3) // shrink rather than truncate a swara with large text
                .layoutPriority(1) // the swara keeps its size; its mark yields first
            if !marks.isEmpty && !swaras.isEmpty {
                // One glyph per slot (shared with Android): ✓ ↑ ↓ ⇣ ⇡ ✕ ○.
                // SF Symbols sized by the caption text style scale with Dynamic Type
                // (the plain glyphs fall back to a fixed-size font).
                HStack(spacing: 2) {
                    ForEach(swaras.indices, id: \.self) { slot in
                        let mark = marks[slot]
                        if let name = Self.symbol(for: Display.shared.markGlyph(result: mark)) {
                            Image(systemName: name).foregroundStyle(Display.shared.markTone(result: mark).color)
                        } else {
                            Image(systemName: "circle").hidden()
                        }
                    }
                }
                .font(.caption)
                .accessibilityHidden(true) // spoken in the row's sentence instead
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(active ? Color.accentColor.opacity(0.18) : .clear, in: RoundedRectangle(cornerRadius: 6))
    }

    private var text: Text {
        if akshara is AksharaKarvai { return Text(",") }
        return swaras.enumerated().reduce(Text("")) { result, item in
            let piece = Text(item.element.description())
            return result + (item.offset == activeSlot ? piece.bold().foregroundColor(.accentColor) : piece)
        }
    }
}

private struct AngaBar: View {
    var double = false

    var body: some View {
        HStack(spacing: 2) {
            Rectangle().frame(width: 1)
            if double { Rectangle().frame(width: 1) }
        }
        .foregroundStyle(Color.subdued)
        .padding(.vertical, 10)
    }
}

/// The tala being kept, current beat filled. Separate from the notation
/// because at higher kalas several written aksharas share one beat.
struct TalaStripView: View {
    let beatInAvarta: Int?
    private let tala = Tala.companion.ADI

    /// "clap 1 2 3, clap wave, clap wave": the marks as words.
    private var spokenBeats: String {
        tala.beats.enumerated().map { i, beat in
            let word: String
            switch beat.kriya {
            case .clap: word = String(localized: "clap")
            case .wave: word = String(localized: "wave")
            default: word = beat.label
            }
            return (i > 0 && beat.startsAnga ? ", " : i > 0 ? " " : "") + word
        }.joined()
    }

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Array(tala.beats.enumerated()), id: \.offset) { i, beat in
                if i > 0 && beat.startsAnga { AngaBar() }
                Text(beat.label)
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 36)
                    .foregroundStyle(i == beatInAvarta ? Color.white : Color.primary)
                    .background(
                        i == beatInAvarta ? Color.accentColor : Color.secondary.opacity(0.15),
                        in: Capsule())
            }
        }
        .frame(minHeight: 36)
        .fixedSize(horizontal: false, vertical: true) // bars must not stretch the strip
        // One element whose value is read only when focused: no announcements
        // during playback, but the beat is there for anyone who asks.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Adi tala: \(spokenBeats)"))
        .accessibilityValue(beatInAvarta.map { Text("Beat \($0 + 1) of \(tala.beats.count)") } ?? Text(""))
    }
}
