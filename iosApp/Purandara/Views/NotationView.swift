import SwiftUI
import shared

/// The exercise as a grid: one block per tala cycle at the chosen speed
/// (`NotationLayout`), so the swaras under a beat are the ones sung on it.
/// While a cycle takes at most two rows the tala strip above is its column
/// header; longer cycles start each row with its beat mark in a gutter. The
/// sounding akshara and swara are highlighted, and the list follows playback.
/// A piece's words go under the swaras (or replace them, per `words`), and a
/// section starts with a heading. Mirrors Android's `NotationView`.
struct NotationView: View {
    let layout: NotationLayout
    let playback: PlaybackState
    var marks: [MarkKey: NoteResult] = [:]
    var words: WordsDisplay = .both
    /// Optional content scrolled above the rows (the sing-along panel at accessibility text sizes).
    var header: AnyView?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var activeCell: CellRef? { NotationView.cell(playback) }

    /// The sounding cell, if any.
    static func cell(_ playback: PlaybackState) -> CellRef? {
        guard let repetition = playback.repetition, let avarta = playback.avarta, let akshara = playback.akshara else { return nil }
        return CellRef(repetition: Int32(repetition), avarta: Int32(avarta), akshara: Int32(akshara))
    }

    /// The block (tala cycle) being sung, if any.
    static func currentBlock(_ layout: NotationLayout, _ playback: PlaybackState) -> Int? {
        cell(playback).flatMap { layout.locate(cell: $0) }.map { Int($0.block) }
    }

    var body: some View {
        let current = NotationView.currentBlock(layout, playback)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let header { header }
                    ForEach(Array(layout.blocks.enumerated()), id: \.offset) { index, block in
                        let cells = Set(block.cells.map { "\($0.avarta):\($0.akshara)" })
                        let blockMarks = marks.filter { cells.contains("\($0.key.avarta):\($0.key.akshara)") }
                        VStack(alignment: .leading, spacing: 4) {
                            if let heading = block.heading {
                                Text(heading)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Color.accentColor)
                                    .padding(.horizontal, 4)
                                    .accessibilityAddTraits(.isHeader)
                            }
                            CycleRows(
                                layout: layout,
                                block: block,
                                activeCell: current == index ? activeCell : nil,
                                activeSlot: current == index ? playback.slot : nil,
                                marks: blockMarks,
                                words: words)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Text("Avarta \(index + 1): \(spoken(index, marks: blockMarks))"))
                        }
                        .id(index)
                    }
                }
                .padding(.horizontal, 12)
            }
            .accessibilityIdentifier("notation") // UI tests find the visible notation area by it
            .onChange(of: current) { block in
                guard let block else { return }
                if reduceMotion {
                    proxy.scrollTo(block)
                } else {
                    withAnimation { proxy.scrollTo(block) }
                }
            }
        }
    }
}

extension NotationView {
    /// Cycle sentence, then the words ("M P, D S′ S′ R′. Words: sri ga na na tha").
    func spoken(_ block: Int, marks: [MarkKey: NoteResult]) -> String {
        let swaras = spokenSwaras(block, marks: marks)
        guard let words = layout.spokenWords(block: Int32(block)) else { return swaras }
        return String(localized: "\(swaras). Words: \(words)")
    }

    /// Cycle sentence, commas at the anga bars; after sing-along, each swara with its mark ("S in tune R high…").
    func spokenSwaras(_ block: Int, marks: [MarkKey: NoteResult]) -> String {
        let hold = String(localized: "hold")
        guard !marks.isEmpty else { return layout.spoken(block: Int32(block), karvaiWord: hold) }
        return layout.spokenWith(block: Int32(block)) { cell, akshara in
            if let swaras = (akshara as? AksharaSwaras)?.swaras {
                return swaras.enumerated().map { slot, swara in
                    let word = SingAlongText.markWords(marks[MarkKey(avarta: Int(cell.avarta), akshara: Int(cell.akshara), slot: slot)])
                    return word.map { "\(swara.spoken()) \($0)" } ?? swara.spoken()
                }.joined(separator: " ")
            }
            if akshara is AksharaRest { return nil }
            // Written several to a beat (a swarajati), holds inside a beat aren't read out.
            return layout.heldInsideBeat(cell: cell) ? nil : hold
        }
    }
}

/// Columns of one row: views marked `.columns(n)` share the width in
/// proportion to n, the rest (bars, gaps, the gutter) keep their own width.
/// The tala strip and the notation both use it, so their columns line up.
struct ColumnsLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        let widths = columnWidths(width, subviews)
        let height = zip(subviews, widths).map { view, w in
            view.sizeThatFits(ProposedViewSize(width: w, height: proposal.height)).height
        }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        for (view, w) in zip(subviews, columnWidths(bounds.width, subviews)) {
            view.place(at: CGPoint(x: x, y: bounds.minY), proposal: ProposedViewSize(width: w, height: bounds.height))
            x += w
        }
    }

    private func columnWidths(_ width: CGFloat, _ subviews: Subviews) -> [CGFloat] {
        let weights = subviews.map { $0[ColumnWeight.self] ?? 0 }
        let fixed = zip(subviews, weights).map { view, weight in weight > 0 ? 0 : view.sizeThatFits(.unspecified).width }
        let unit = max(0, width - fixed.reduce(0, +)) / max(1, weights.reduce(0, +))
        return zip(fixed, weights).map { fixedWidth, weight in weight > 0 ? weight * unit : fixedWidth }
    }
}

private struct ColumnWeight: LayoutValueKey {
    static let defaultValue: CGFloat? = nil
}

extension View {
    /// Takes [n] columns of a `ColumnsLayout` row.
    func columns(_ n: Int) -> some View { layoutValue(key: ColumnWeight.self, value: CGFloat(n)) }
}

/// One row of a cycle, shared by the notation and the tala strip: each count
/// takes its width in columns, an anga bar goes before a new anga, a small gap
/// between beats of several cells, and a double bar ends the cycle. Mirrors
/// Android's `BeatRow`.
struct BeatRow<Group: View>: View {
    let row: LayoutRow
    let columns: Int
    let lastRow: Bool
    var gutter = false
    var sectionStarts: Set<CellRef> = []
    @ViewBuilder let group: (BeatGroup) -> Group

    var body: some View {
        let groups = row.groups
        ColumnsLayout {
            if gutter {
                Text(row.gutterLabel)
                    .font(.caption)
                    .foregroundStyle(Color.subdued)
                    .frame(width: 20)
                    .frame(maxHeight: .infinity)
                    .accessibilityHidden(true)
            }
            ForEach(groups.indices, id: \.self) { g in
                let beatGroup = groups[g]
                if g > 0 {
                    if let first = beatGroup.cells.first, sectionStarts.contains(first) {
                        AngaBar(double: true)
                    } else if beatGroup.startsAnga {
                        AngaBar()
                    } else if beatGroup.cells.count > 1 || groups[g - 1].cells.count > 1 {
                        Color.clear.frame(width: 4, height: 1)
                    }
                }
                group(beatGroup).columns(Int(beatGroup.width))
            }
            if lastRow { AngaBar(double: true) }
            let missing = columns - Int(row.width)
            if missing > 0 { Color.clear.frame(height: 1).columns(missing) }
            if !lastRow { Color.clear.frame(width: 4, height: 1) }
        }
    }
}

private struct CycleRows: View {
    let layout: NotationLayout
    let block: CycleBlock
    let activeCell: CellRef?
    let activeSlot: Int?
    let marks: [MarkKey: NoteResult]
    let words: WordsDisplay

    var body: some View {
        let hasWords = words.showsWords && layout.hasWords(block: block.index)
        let showSwaras = !hasWords || words.showsSwaras
        let sectionStarts = Set(block.sections.filter { $0.countInCycle > 0 }.map { $0.cell })
        VStack(spacing: 4) {
            ForEach(block.rows.indices, id: \.self) { r in
                BeatRow(row: block.rows[r], columns: Int(block.columns), lastRow: r == block.rows.count - 1,
                        gutter: !block.mirrorsStrip, sectionStarts: sectionStarts) { group in
                    HStack(spacing: 0) {
                        if group.cells.isEmpty { Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                        ForEach(group.cells.indices, id: \.self) { c in
                            let cell = group.cells[c]
                            AksharaCell(
                                akshara: layout.akshara(cell: cell),
                                active: cell == activeCell,
                                activeSlot: cell == activeCell ? activeSlot : nil,
                                marks: Dictionary(uniqueKeysWithValues: marks
                                    .filter { $0.key.avarta == Int(cell.avarta) && $0.key.akshara == Int(cell.akshara) }
                                    .map { ($0.key.slot, $0.value) }),
                                word: hasWords ? layout.word(cell: cell) : nil,
                                showSwaras: showSwaras)
                        }
                    }
                    // Several swaras to a beat: a light band shows which ones share it.
                    .background(group.cells.count > 1 ? Color.secondary.opacity(0.08) : .clear,
                                in: RoundedRectangle(cornerRadius: 8))
                    .padding(.horizontal, 2)
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

/// The rows a tala is laid out on (shared rule: `Tala.rowStartsFor`), for
/// the compact strip.
struct TalaRows {
    let ranges: [Range<Int>]
    let columns: Int

    /// Rows from given start cells, for the tala strip's counts (a two-kalai
    /// piece counts its beats twice; `Tala.rowStartsFor(kalai)`).
    init(starts: [Int], columns: Int, total: Int) {
        let ends = Array(starts.dropFirst()) + [total]
        ranges = zip(starts, ends).map { $0..<$1 }
        self.columns = columns
    }

    /// Empty columns after row [row], so its cells line up with the longest row.
    func missing(_ row: Int) -> Int { columns - ranges[row].count }
}

/// The tala being kept, current count filled. While `block` (the cycle being
/// sung) takes at most two rows the strip has its rows and columns, so each
/// mark stands over the swaras sung on it; otherwise one compact row. A
/// two-kalai piece splits each beat into its kriya and a "·" second count.
/// Mirrors Android's `TalaStrip`.
struct TalaStripView: View {
    let beatInAvarta: Int?
    let tala: Tala
    var kalai: Int = 1
    var block: CycleBlock?

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

    /// Swaras sung to each beat of the block, or nil when the beats differ.
    private var swarasPerBeat: Int? {
        guard let block else { return nil }
        var perBeat: [Int: Int] = [:]
        for row in block.rows {
            for group in row.groups { perBeat[Int(group.countInCycle) / kalai, default: 0] += group.cells.count }
        }
        let distinct = Set(perBeat.values)
        return distinct.count == 1 ? distinct.first : nil
    }

    private var label: String {
        var parts: [String] = []
        if kalai > 1 { parts.append(String(localized: "\(kalai) kalai,")) }
        if let n = swarasPerBeat, n > 1 { parts.append(String(localized: "\(n) swaras to a beat,")) }
        parts.append(spokenBeats)
        return "\(tala.name) tala: " + parts.joined(separator: " ")
    }

    private var counts: [TalaCount] { Array(tala.countsAt(kalai: Int32(kalai))) }

    private var stripRows: TalaRows {
        TalaRows(starts: tala.rowStartsFor(cellsPerBeat: Int32(kalai)).map { $0.intValue },
                 columns: Int(tala.rowCellsFor(cellsPerBeat: Int32(kalai))),
                 total: counts.count)
    }

    private func chip(_ text: String, current: Bool) -> some View {
        Text(text)
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 36)
            .foregroundStyle(current ? Color.white : Color.primary)
            .background(current ? Color.accentColor : Color.secondary.opacity(0.15), in: Capsule())
    }

    var body: some View {
        VStack(spacing: 6) {
            if let block, block.mirrorsStrip {
                ForEach(block.rows.indices, id: \.self) { r in
                    BeatRow(row: block.rows[r], columns: Int(block.columns), lastRow: r == block.rows.count - 1) { group in
                        chip(group.label, current: Int(group.countInCycle) == beatInAvarta).padding(.horizontal, 3)
                    }
                    .frame(minHeight: 36)
                    .fixedSize(horizontal: false, vertical: true) // bars must not stretch the strip
                }
            } else {
                let rows = stripRows
                ForEach(rows.ranges.indices, id: \.self) { row in
                    let range = rows.ranges[row]
                    HStack(spacing: 6) {
                        ForEach(Array(range), id: \.self) { i in
                            let count = counts[i]
                            if i > range.lowerBound && count.startsAnga { AngaBar() }
                            chip(count.label, current: i == beatInAvarta)
                        }
                        ForEach(0..<rows.missing(row), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                        }
                    }
                    .frame(minHeight: 36)
                    .fixedSize(horizontal: false, vertical: true) // bars must not stretch the strip
                }
            }
        }
        // One element whose value is read only when focused: no announcements
        // during playback, but the beat is there for anyone who asks.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(beatInAvarta.map { Text(kalai > 1 ? "Count \($0 + 1) of \(counts.count)" : "Beat \($0 + 1) of \(tala.beats.count)") } ?? Text(""))
    }
}
