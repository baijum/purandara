import SwiftUI
import shared

/// All exercises, grouped by set in teaching order.
struct LibraryView: View {
    @EnvironmentObject private var viewModel: PracticeViewModel

    private var sections: [(set: VarisaiSet, exercises: [Varisai])] {
        VarisaiSet.entries.map { set in (set, viewModel.catalog.filter { $0.set == set }) }
            .filter { !$0.exercises.isEmpty }
    }

    var body: some View {
        List {
            if viewModel.loadError {
                Text("The harmonium sounds could not be loaded.")
                    .foregroundStyle(.red)
            } else if !viewModel.samplesLoaded {
                ProgressView("Preparing the harmonium…")
            }
            ForEach(sections, id: \.set.name) { section in
                Section {
                    ForEach(section.exercises, id: \.id) { varisai in
                        NavigationLink(value: varisai.id) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(varisai.title)
                                Text(varisai.avartas.first?.description() ?? "")
                                    .font(.callout.monospaced())
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                } header: {
                    Text(section.set.title)
                        .accessibilityAddTraits(.isHeader)
                }
            }
        }
        .navigationTitle("Exercises")
        .navigationDestination(for: String.self) { id in
            if let varisai = VarisaiCatalog.shared.byId(id: id) {
                PlayerView(varisai: varisai)
            }
        }
        .safeAreaInset(edge: .top) {
            Text("Mayamalavagowla · Adi tala")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
        }
    }
}
