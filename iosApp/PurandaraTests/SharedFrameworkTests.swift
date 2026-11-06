import XCTest
import shared
@testable import Purandara

final class SharedFrameworkTests: XCTestCase {
    func testSharedFrameworkIsLinked() {
        XCTAssertEqual(AppInfo.shared.NAME, "Purandara")
    }

    func testPlatformNameIsNotEmpty() {
        XCTAssertFalse(Platform_iosKt.platformName().isEmpty)
    }
}
