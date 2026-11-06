import XCTest

/// Drives the app like a user and runs Apple's accessibility audit
/// (labels, contrast, hit regions, clipped text, Dynamic Type).
final class PurandaraUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(_ arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        // Settings persist between launches; every test starts from the defaults.
        app.launchArguments = ["-resetSettings", "YES"] + arguments
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
        XCTAssertTrue(app.switches["Tanpura drone"].exists)
        try audit(app)
    }

    func testSingAlongWithASharpSingerMarksEveryNoteHigh() {
        // Debug synthetic singer 35 cents sharp on the 16-note plain scale (8 s count-in + 16 s).
        let app = launch(["-open", "plain-scale-1", "-autoplay", "YES", "-singAlong", "YES", "-singer", "35"])
        let summary = app.staticTexts["Pass 1: 0 of 16 notes in tune or close"]
        XCTAssertTrue(summary.waitForExistence(timeout: 40), "pass summary")
        XCTAssertTrue(app.otherElements["Avarta 1: S high R high G high M high, P high D high, N high S′ high"].exists)
        XCTAssertTrue(app.buttons["Sing along"].exists)
        XCTAssertEqual(app.buttons["Sing along"].value as? String, "On")
    }

    func testSingAlongInTuneAndAudit() throws {
        let app = launch(["-open", "plain-scale-1", "-autoplay", "YES", "-singAlong", "YES", "-singer", "0"])
        XCTAssertTrue(app.staticTexts["Pass 1: 16 of 16 notes in tune or close"].waitForExistence(timeout: 40))
        XCTAssertTrue(app.otherElements["Your pitch"].exists)
        // Audit the settled screen, after the release and the final beat highlight have finished.
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 5))
        try audit(app)
    }

    /// Runs the audit and names each offending element in the failure.
    ///
    /// One deliberate exception: Dynamic Type warnings on single notation
    /// swaras ("S", "R", "S′"…). Eight columns must fit across the screen, so a
    /// swara grows with the text size until it fills its column, then shrinks
    /// rather than being cut off (a design decision: clipped notation dropped
    /// swaras on Android). Verified by screenshot at the largest accessibility
    /// size. Everything else, including these cells' contrast, stays strict.
    private func audit(_ app: XCUIApplication) throws {
        try app.performAccessibilityAudit { issue in
            if issue.auditType == .dynamicType, let label = issue.element?.label,
               label.range(of: #"^[SRGMPDN]′?$"#, options: .regularExpression) != nil {
                return true // ignored, see above
            }
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' at \($0.frame)" } ?? "?"
            print("AUDIT: \(issue.compactDescription) — \(element)")
            return false
        }
    }
}
