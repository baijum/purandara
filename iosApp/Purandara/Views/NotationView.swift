import SwiftUI
import shared

/// The exercise as a grid: one row per avarta, eight akshara cells in the
/// same columns as the tala strip above. The sounding akshara and swara are
/// highlighted, and the list follows playback. Mirrors Android's `NotationView`.
struct NotationView: View {
    let varisai: Varisai
    let playback: PlaybackState
    private let tala = Tala.companion.ADI

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(Array(varisai.avartas.enumerated()), id: \.offset) { index, avarta in
                        AvartaRow(
                            avarta: avarta,
                            tala: tala,
                            activeAkshara: playback.avarta == index ? playback.akshara : nil,
                            activeSlot: playback.avarta == index ? playback.slot : nil)
                        .id(index)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("Avarta \(index + 1): \(avarta.spoken(tala: tala, karvaiWord: String(localized: "hold")))"))
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

private struct AvartaRow: View {
    let avarta: Avarta
    let tala: Tala
    let activeAkshara: Int?
    let activeSlot: Int?

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(avarta.aksharas.enumerated()), id: \.offset) { i, akshara in
                let beat = tala.beats[i]
                if i > 0 && beat.startsAnga { AngaBar() }
                AksharaCell(
                    akshara: akshara,
                    active: i == activeAkshara,
                    activeSlot: i == activeAkshara ? activeSlot : nil)
            }
            AngaBar(double: true)
        }
        .frame(minHeight: 48)
    }
}

private struct AksharaCell: View {
    let akshara: any Akshara
    let active: Bool
    let activeSlot: Int?

    private var swaras: [WrittenSwara] { (akshara as? AksharaSwaras)?.swaras ?? [] }

    var body: some View {
        VStack(spacing: 2) {
            text
                // Text styles follow Dynamic Type; rows grow with them.
                .font(swaras.count > 2 ? .callout : swaras.count == 2 ? .title3 : .title2)
                .lineLimit(1)
                .minimumScaleFactor(0.3) // shrink rather than truncate a swara with large text
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
