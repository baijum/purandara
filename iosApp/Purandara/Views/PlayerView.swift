import SwiftUI
import shared

/// Plays one exercise: tala strip and notation on top, controls below,
/// Play/Stop at the bottom. Mirrors Android's `PlayerScreen`.
struct PlayerView: View {
    let varisai: Varisai
    @EnvironmentObject private var viewModel: PracticeViewModel

    var body: some View {
        VStack(spacing: 0) {
            TalaStripView(beatInAvarta: viewModel.playback.beatInAvarta)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            NotationView(varisai: varisai, playback: viewModel.playback)
                .frame(maxHeight: .infinity)
            if viewModel.playback.repetitions > 1 {
                Text("Pass \((viewModel.playback.repetition ?? 0) + 1) of \(viewModel.playback.repetitions)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .padding(.vertical, 4)
            }
            Divider()
            PracticeControls(settings: $viewModel.settings)
                .frame(maxHeight: .infinity)
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
            .background(.bar)
        }
        .navigationTitle(varisai.title)
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { viewModel.stopImmediately() }
    }
}

private struct PracticeControls: View {
    @Binding var settings: PracticeSettings

    private let kalaNames = ["First kala", "Second kala", "Third kala", "Fourth kala"]

    var body: some View {
        Form {
            Picker("Sa", selection: $settings.saMidi) {
                ForEach(PracticeSettings.saRange, id: \.self) { midi in
                    Text(saLabel(midi)).tag(midi)
                }
            }

            Stepper(value: $settings.tempo, in: PracticeSettings.tempoRange, step: 5) {
                HStack {
                    Text("Tempo")
                    Spacer()
                    Text("\(settings.tempo) bpm").foregroundStyle(.secondary)
                }
            }
            .accessibilityValue(Text("\(settings.tempo) beats per minute"))

            VStack(alignment: .leading, spacing: 8) {
                Text("Speed")
                Picker("Speed", selection: $settings.kala) {
                    ForEach(PracticeSettings.kalaRange, id: \.self) { kala in
                        Text("\(kala)").tag(kala).accessibilityLabel(Text(kalaNames[kala - 1]))
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Toggle("Drone (Sa and Pa)", isOn: $settings.drone)
            Toggle("Tala click", isOn: $settings.click)
            Toggle("Repeat continuously", isOn: $settings.loop)

            Picker("Tuning", selection: $settings.tuning) {
                Text("Just (tanpura)").tag(PracticeSettings.TuningChoice.just)
                Text("Equal (keyboard)").tag(PracticeSettings.TuningChoice.equal)
            }
        }
    }

    private func saLabel(_ midi: Int) -> String {
        "\(Shruti.shared.noteName(midi: Int32(midi))) · \(Shruti.shared.kattai(midi: Int32(midi))) kattai"
    }
}
