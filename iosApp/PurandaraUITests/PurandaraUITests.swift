import XCTest

/// Drives the app like a user and runs Apple's accessibility audit
/// (labels, contrast, hit regions, clipped text, Dynamic Type).
final class PurandaraUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = arguments
        app.launch()
        return app
    }

    func testOpenPlayCountInAndStop() {
        let app = launch()
        app.buttons["Sarali Varisai 4, S R G M P , S R"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))

        let avarta = app.otherElements["Avarta 1: S R G M, P hold, S R"]
        XCTAssertTrue(avarta.exists, "avarta rows are read as one sentence")

        let play = app.buttons["Play"]
        XCTAssertTrue(play.waitForExistence(timeout: 5))
        // Samples load in the background; Play enables when they are ready.
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: play)
        wait(for: [enabled], timeout: 10)
        play.tap()

        XCTAssertTrue(app.staticTexts["Count-in…"].waitForExistence(timeout: 3), "count-in is shown first")
        let stop = app.buttons["Stop"]
        XCTAssertTrue(stop.exists)
        stop.tap()
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 3), "stop returns to Play")
    }

    func testLibraryPassesAccessibilityAudit() throws {
        let app = launch()
        XCTAssertTrue(app.navigationBars["Exercises"].waitForExistence(timeout: 5))
        try audit(app)
    }

    func testPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        try audit(app)
    }

    func testSettingsSheetPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        app.buttons["Practice settings"].tap()
        XCTAssertTrue(app.navigationBars["Practice settings"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.switches["Drone (Sa and Pa)"].exists)
        try audit(app)
    }

    /// Runs the audit and names each offending element in the failure.
    private func audit(_ app: XCUIApplication) throws {
        try app.performAccessibilityAudit { issue in
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)'" } ?? "?"
            print("AUDIT: \(issue.compactDescription) — \(element)")
            return false
        }
    }
}
