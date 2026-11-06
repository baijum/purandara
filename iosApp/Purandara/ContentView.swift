import SwiftUI
import shared

/// Placeholder landing screen. Replaced once the first feature lands.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text(AppInfo.shared.NAME)
                .font(.largeTitle.bold())
                .foregroundStyle(Color.accentColor)
                .accessibilityAddTraits(.isHeader)
            Text("Carnatic music practice companion")
                .font(.body)
            Text(Platform_iosKt.platformName())
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, 16)
        }
        .padding(24)
    }
}

#Preview {
    ContentView()
}
