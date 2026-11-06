import SwiftUI
import shared

/// Text for sing-along, matching Android's strings.
enum SingAlongText {
    static func label(_ swara: Swara, octave: Int = 0) -> String {
        Display.shared.swaraLabel(swara: swara, octave: Int32(octave))
    }

    static func zoneWord(_ zone: Zone, cents: Double) -> String {
        switch zone {
        case .inTune: return String(localized: "in tune")
        case .close: return cents > 0 ? String(localized: "slightly high") : String(localized: "slightly low")
        default: return cents > 0 ? String(localized: "high") : String(localized: "low")
        }
    }

    /// "P · in tune · +3¢ · G3", or "Not hearing you yet".
    static func live(_ reading: SwaraReading?) -> String {
        guard let reading else { return String(localized: "Not hearing you yet") }
        let cents = Int(reading.centsOff.rounded())
        return "\(label(reading.swara, octave: Int(reading.octave))) · \(zoneWord(Zone.companion.of(cents: reading.centsOff), cents: Double(cents))) · \(cents >= 0 ? "+" : "")\(cents)¢ · \(reading.westernName)"
    }

    /// Words for a mark ("in tune", "high", "not heard"…), or nil.
    static func markWords(_ result: NoteResult?) -> String? {
        guard let result else { return nil }
        if result is NoteResultNoVoice { return String(localized: "not heard") }
        guard let judged = result as? NoteResultJudged else { return nil }
        if let sung = judged.sung { return String(localized: "sang \(label(sung.swara, octave: Int(sung.octave)))") }
        if judged.octaveShift < 0 { return String(localized: "an octave low") }
        if judged.octaveShift > 0 { return String(localized: "an octave high") }
        return zoneWord(judged.zone(), cents: judged.cents)
    }

    static func summaryHeadline(_ pass: Int, _ summary: PassSummary) -> String {
        String(localized: "Pass \(pass): \(Int(summary.inTuneOrClose)) of \(Int(summary.judged)) notes in tune or close")
    }

    static func attention(_ summary: PassSummary) -> String? {
        let items = summary.attention.map { a -> String in
            let name = label(a.note.swara, octave: Int(a.note.octave.offset))
            let n = "(\(a.times) of \(a.of))"
            switch a.tendency {
            case .high: return "\(name) " + String(localized: "high") + " \(n)"
            case .low: return "\(name) " + String(localized: "low") + " \(n)"
            case .octaveLow: return "\(name) " + String(localized: "an octave low") + " \(n)"
            case .octaveHigh: return "\(name) " + String(localized: "an octave high") + " \(n)"
            default: return "\(name): " + String(localized: "a different swara") + " \(n)"
            }
        }
        return items.isEmpty ? nil : String(localized: "Needs attention: \(items.joined(separator: "; "))")
    }
}

extension Display.MarkTone {
    var color: Color {
        switch self {
        case .good: return .accentColor
        case .near: return .orange
        case .off: return .red
        default: return Color.subdued
        }
    }
}

/// Pitch lane, live reading, headphone hint, permission message and last summary.
struct SingAlongPanel: View {
    let varisai: Varisai
    @EnvironmentObject private var viewModel: PracticeViewModel

    var body: some View {
        let sing = viewModel.singAlong
        VStack(alignment: .leading, spacing: 6) {
            if sing.micDenied && !sing.micPermitted {
                Text("Microphone access is off, so sing-along can't hear you. You can allow it in Settings.")
                    .foregroundStyle(.red)
                Button("Open settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
            } else {
                PitchLane(
                    schedule: Schedule.companion.of(varisai: varisai, settings: viewModel.settings.performance()),
                    avarta: viewModel.playback.avarta,
                    pass: viewModel.playback.repetition,
                    trace: sing.trace,
                    live: sing.live)
                if sing.onSpeaker {
                    Text("Headphones give the most accurate results.")
                        .font(.caption)
                        .foregroundStyle(Color.subdued)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let last = sing.lastSummary {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(SingAlongText.summaryHeadline(last.pass, last.summary)).font(.subheadline.bold())
                            .fixedSize(horizontal: false, vertical: true)
                        if let attention = SingAlongText.attention(last.summary) {
                            Text(attention).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.accentColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .padding(.horizontal, 12)
    }
}

/// The current avarta's expected swaras as a staircase, the sung pitch over
/// them, and swara lines. One accessibility element whose value is the live reading.
struct PitchLane: View {
    let schedule: Schedule
    let avarta: Int?
    let pass: Int?
    let trace: [TraceSample]
    let live: SwaraReading?

    private static let lowCents = -150.0
    private static let highCents = 1350.0

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // The drawing is visual only (VoiceOver reads the value below); at
            // accessibility text sizes it gives its space back to the notation.
            if !typeSize.isAccessibilitySize {
                Canvas { context, size in draw(context, size) }
                    .frame(height: 120)
            }
            Text(SingAlongText.live(live)).font(.callout.weight(.medium))
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Your pitch"))
        .accessibilityValue(Text(SingAlongText.live(live)))
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let sa = schedule.settings.saHz
        let raga = schedule.settings.raga
        let labelWidth: CGFloat = 26
        func y(_ cents: Double) -> CGFloat { size.height * (1 - (cents - Self.lowCents) / (Self.highCents - Self.lowCents)) }
        func cents(_ note: Note) -> Double { 1200 * log2(note.frequency(saHz: sa) / sa) }
        var lines = SwaraLetter.entries.map { Note(swara: raga.swara(letter: $0), octave: .madhya) }
        lines.append(Note(swara: .s, octave: .tara))
        for note in lines {
            let yy = y(cents(note))
            var path = Path()
            path.move(to: CGPoint(x: labelWidth, y: yy))
            path.addLine(to: CGPoint(x: size.width, y: yy))
            context.stroke(path, with: .color(.secondary.opacity(0.4)), lineWidth: 1)
            let name = String(note.swara.symbol.prefix(1)) + (note.octave == .tara ? "′" : "")
            // Fixed small labels: the lines are fixed-spaced, so scaled labels would collide.
            context.draw(Text(name).font(.system(size: 10)), at: CGPoint(x: 8, y: yy), anchor: .leading)
        }
        guard let avarta else { return }
        let span = Double(schedule.settings.tala.aksharas) * schedule.settings.writtenAksharaSeconds
        let start = Double(avarta) * span
        func x(_ t: Double) -> CGFloat { labelWidth + (size.width - labelWidth) * (t - start) / span }
        for e in schedule.notes where Int(e.position.avarta) == avarta {
            var path = Path()
            let yy = y(cents(e.note))
            path.move(to: CGPoint(x: x(e.startSeconds), y: yy))
            path.addLine(to: CGPoint(x: x(e.startSeconds + e.durationSeconds), y: yy))
            context.stroke(path, with: .color(.accentColor.opacity(0.35)), lineWidth: 8)
        }
        let p = pass ?? 0
        for t in trace where t.pass == p && t.secondsInPass >= start && t.secondsInPass < start + span {
            let color: Color = t.zone == .inTune ? .accentColor : (t.zone == .close ? .orange : .red)
            let point = CGPoint(x: x(t.secondsInPass), y: y(t.centsFromSa))
            context.fill(Path(ellipseIn: CGRect(x: point.x - 2, y: point.y - 2, width: 4, height: 4)), with: .color(color))
        }
    }
}
