import SwiftUI

extension Color {
    /// De-emphasised text that still meets WCAG AA contrast (4.5:1) on the
    /// light and dark backgrounds; the system `.secondary` falls just short
    /// for small text (flagged by the accessibility audit).
    static let subdued = Color.primary.opacity(0.72)
}
