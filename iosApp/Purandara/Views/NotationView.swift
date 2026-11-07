import SwiftUI
import shared

/// The exercise as a grid: one block per avarta, its akshara cells in the
/// same columns as the tala strip above. Talas longer than eight beats wrap
/// onto two rows at an anga boundary (`Tala.rowStarts`). The sounding akshara
/// and swara are highlighted, and the list follows playback. A gitam's words
/// go under the swaras (or replace them, per `words`), and each section
/// starts with a heading. Mirrors Android's `NotationView`.
struct NotationView: View {
    let varisai: Varisai
    let playback: PlaybackState
    var marks: [MarkKey: NoteResult] = [:]
    var words: WordsDisplay = .both
    /// Optional content scrolled above the rows (the sing-along panel at accessibility text sizes).
    var header: AnyView?
    private var tala: Tala { varisai.tala }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let header { header }
                    ForEach(Array(varisai.avartas.enumerated()), id: \.offset) { index, avarta in
                        let rowMarks = marks.filter { $0.key.avarta == index }
                        VStack(alignment: .leading, spacing: 4) {
                            if let section = avarta.section {
                                Text(section)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.accentColor)
                                    .padding(.horizontal, 4)
                                    .accessibilityAddTraits(.isHeader)
                            }
                            AvartaRow(
                                avarta: avarta,
                                avartaIndex: index,
                                varisai: varisai,
                                activeAkshara: playback.avarta == index ? playback.akshara : nil,
                                activeSlot: playback.avarta == index ? playback.slot : nil,
                                marks: rowMarks,
                                words: words)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Text("Avarta \(index + 1): \(spoken(avarta, index: index, marks: rowMarks))"))
                        }
                        .id(index)
                    }
                }
                .padding(.horizontal, 12)
            }
            .accessibilityIdentifier("notation") // UI tests find the visible notation area by it
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
    /// Row sentence, then the words of a gitam ("M P, D S′ S′ R′. Words: sri ga na na tha").
    func spoken(_ avarta: Avarta, index: Int, marks: [MarkKey: NoteResult]) -> String {
        let swaras = spokenSwaras(avarta, index: index, marks: marks)
        guard let words = avarta.spokenWords() else { return swaras }
        return String(localized: "\(swaras). Words: \(words)")
    }

    /// Row sentence; after sing-along, each swara with its mark ("S in tune R high…").
    func spokenSwaras(_ avarta: Avarta, index: Int, marks: [MarkKey: NoteResult]) -> String {
        let hold = String(localized: "hold")
        guard !marks.isEmpty else { return avarta.spoken(tala: tala, karvaiWord: hold) }
        let pieces: [(Int, String)] = avarta.aksharas.enumerated().compactMap { i, akshara in
            if let swaras = (akshara as? AksharaSwaras)?.swaras {
                return (i, swaras.enumerated().map { slot, swara in
                    let word = SingAlongText.markWords(marks[MarkKey(avarta: index, akshara: i, slot: slot)])
                    return word.map { "\(swara.spoken()) \($0)" } ?? swara.spoken()
                }.joined(separator: " "))
            }
            // Written several to a beat (a swarajati), holds inside a beat aren't read out.
            return varisai.cellsPerBeatIn(avarta: Int32(index)) > 1 ? nil : (i, hold)
        }
        return pieces.enumerated().map { n, piece in
            n == 0 ? piece.1 : (varisai.startsAnga(avarta: Int32(index), cell: Int32(piece.0)) ? ", " : " ") + piece.1
        }.joined()
    }
}

private struct AvartaRow: View {
    let avarta: Avarta
    let avartaIndex: Int
    let varisai: Varisai
    let activeAkshara: Int?
    let activeSlot: Int?
    let marks: [MarkKey: NoteResult]
    let words: WordsDisplay

    var body: some View {
        let rows = TalaRows(varisai: varisai, avarta: avarta, index: avartaIndex)
        let rowWords = words.showsWords ? avarta.words : nil
        let showSwaras = rowWords == nil || words.showsSwaras
        VStack(spacing: 4) {
            ForEach(rows.ranges.indices, id: \.self) { row in
                let range = rows.ranges[row]
                HStack(spacing: 0) {
                    ForEach(Array(range), id: \.self) { i in
                        if i > range.lowerBound && varisai.startsAnga(avarta: Int32(avartaIndex), cell: Int32(i)) { AngaBar() }
                        AksharaCell(
                            akshara: avarta.aksharas[i],
                            active: i == activeAkshara,
                            activeSlot: i == activeAkshara ? activeSlot : nil,
                            marks: Dictionary(uniqueKeysWithValues: marks.filter { $0.key.akshara == i }.map { ($0.key.slot, $0.value) }),
                            word: rowWords?[i],
                            showSwaras: showSwaras)
                    }
                    if row == rows.ranges.count - 1 { AngaBar(double: true) }
                    // Keep a shorter row's columns in line with the longest.
                    ForEach(0..<rows.missing(row), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                    }
                }
                .frame(minHeight: 48)
                // Grow to fit the swaras plus their marks instead of shrinking the text.
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

private struct AksharaCell: View {
    let akshara: any Akshara
    let active: Bool
    let activeSlot: Int?
    let marks: [Int: NoteResult]
    var word: String?
    var showSwaras = true

    private var swaras: [WrittenSwara] { (akshara as? AksharaSwaras)?.swaras ?? [] }

    /// A lone lower-octave swara: its dot is drawn as a shape under the letter
    /// (a combining dot below falls outside the text's frame and is clipped).
    private var singleMandra: Bool { swaras.count == 1 && swaras[0].octave == .mandra }

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
            if showSwaras {
                text
                    // Text styles follow Dynamic Type; rows grow with them.
                    .font(swaras.count > 2 ? .callout : swaras.count == 2 ? .title3 : .title2)
                    .lineLimit(1)
                    .minimumScaleFactor(0.3) // shrink rather than truncate a swara with large text
                    .overlay(alignment: .bottom) {
                        if singleMandra {
                            Circle().frame(width: 4, height: 4).offset(y: 5).accessibilityHidden(true)
                        }
                    }
                    .padding(.bottom, singleMandra ? 6 : 0)
                    .layoutPriority(1) // the swara keeps its size; its mark yields first
            }
            if let word {
                // Words alone are the main text; under swaras they are smaller and italic.
                // A held syllable ("-") is drawn as a bold en dash: a thin hyphen fails contrast.
                let held = word == VarisaiKt.HELD
                Text(held ? "–" : word)
                    .font(showSwaras ? (held ? .callout.bold() : .callout.italic()) : (held ? .title3.bold() : .title3))
                    .foregroundStyle(Color.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.3)
            }
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
        if akshara is AksharaRest { return Text("_") }
        return swaras.enumerated().reduce(Text("")) { result, item in
            let swara = item.element
            var piece = Text(singleMandra ? swara.letter.name : swara.baseText)
            if item.offset == activeSlot { piece = piece.bold().foregroundColor(.accentColor) }
            // A variant digit (the 1 of D1) is written as a subscript.
            if let digit = swara.variantDigit, !singleMandra {
                piece = piece + Text(digit).font(.caption)
            }
            return result + piece
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

/// The rows a tala is laid out on (shared rule: `Tala.rowStarts`).
struct TalaRows {
    let ranges: [Range<Int>]
    let columns: Int

    init(tala: Tala) {
        let starts = tala.rowStarts.map { $0.intValue }
        let ends = Array(starts.dropFirst()) + [Int(tala.aksharas)]
        ranges = zip(starts, ends).map { $0..<$1 }
        columns = Int(tala.rowAksharas)
    }

    /// A piece's notation rows for one avarta: cells, several to a beat in a
    /// swarajati (`Varisai.rowStartsIn`) — a khanda-gati avarta has its own.
    init(varisai: Varisai, avarta: Avarta, index: Int) {
        let starts = varisai.rowStartsIn(avarta: Int32(index)).map { $0.intValue }
        let ends = Array(starts.dropFirst()) + [avarta.aksharas.count]
        ranges = zip(starts, ends).map { $0..<$1 }
        columns = Int(varisai.rowCellsIn(avarta: Int32(index)))
    }

    /// Empty columns after row [row], so its cells line up with the longest row.
    func missing(_ row: Int) -> Int { columns - ranges[row].count }
}

/// The tala being kept, current beat filled. Separate from the notation
/// because at higher kalas several written aksharas share one beat.
struct TalaStripView: View {
    let beatInAvarta: Int?
    let tala: Tala

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
        let rows = TalaRows(tala: tala)
        VStack(spacing: 6) {
            ForEach(rows.ranges.indices, id: \.self) { row in
                let range = rows.ranges[row]
                HStack(spacing: 6) {
                    ForEach(Array(range), id: \.self) { i in
                        let beat = tala.beats[i]
                        if i > range.lowerBound && beat.startsAnga { AngaBar() }
                        Text(beat.label)
                            .font(.headline)
                            .frame(maxWidth: .infinity, minHeight: 36)
                            .foregroundStyle(i == beatInAvarta ? Color.white : Color.primary)
                            .background(
                                i == beatInAvarta ? Color.accentColor : Color.secondary.opacity(0.15),
                                in: Capsule())
                    }
                    ForEach(0..<rows.missing(row), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                    }
                }
                .frame(minHeight: 36)
                .fixedSize(horizontal: false, vertical: true) // bars must not stretch the strip
            }
        }
        // One element whose value is read only when focused: no announcements
        // during playback, but the beat is there for anyone who asks.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(tala.name) tala: \(spokenBeats)"))
        .accessibilityValue(beatInAvarta.map { Text("Beat \($0 + 1) of \(tala.beats.count)") } ?? Text(""))
    }
}
