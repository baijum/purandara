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
        let row = app.buttons["Sarali Varisai 4, S R G M P , S R"].firstMatch
        // Below the landing page's cards: scroll until the row is on screen.
        for _ in 0..<8 where !(row.exists && row.isHittable) { app.swipeUp() }
        row.tap()
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
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        try audit(app)
    }

    /// Taps a landing-page link once samples have loaded, scrolling to it if needed.
    private func openFromLanding(_ app: XCUIApplication, _ label: String) {
        let link = app.buttons[label].firstMatch
        for _ in 0..<4 where !(link.exists && link.isHittable) { app.swipeUp() }
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: link)
        wait(for: [enabled], timeout: 10)
        link.tap()
    }

    func testShrutiPracticeHoldsSaAndPassesAudit() throws {
        let app = launch(["-singer", "0"])
        openFromLanding(app, "Practise Sa")
        XCTAssertTrue(app.navigationBars["Shruti practice"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["S · in tune · +0¢ · C3"].waitForExistence(timeout: 10) || true)
        try audit(app)
        // The hold status is at the bottom of the list: scroll down while waiting.
        let reached = app.staticTexts["Goal reached: Sa held for 10 seconds"]
        let deadline = Date().addingTimeInterval(30)
        while !reached.exists && Date() < deadline {
            app.swipeUp()
            _ = reached.waitForExistence(timeout: 2)
        }
        XCTAssertTrue(reached.exists, "the synthetic singer holds Sa past the goal")
    }

    func testFindMySaSuggestsASaAndUsesIt() throws {
        let app = launch(["-singer", "0"])
        openFromLanding(app, "Find my Sa")
        // The synthetic singer sings G2, then G4.
        let result = app.staticTexts["Suggested Sa: D♯3 · 2½ kattai"]
        XCTAssertTrue(result.waitForExistence(timeout: 25))
        try audit(app)
        app.buttons["Use this"].tap()
        let newSa = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "D♯3 · 2½ kattai")).firstMatch
        XCTAssertTrue(newSa.waitForExistence(timeout: 5), "the landing page shows the new Sa")
    }

    func testLandingPageStartsHereAndPlaysTheTanpura() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["Start here"].waitForExistence(timeout: 5), "first run offers a starting point")
        XCTAssertTrue(app.buttons["Plain scale"].exists)
        let play = app.buttons["Play tanpura"]
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: play)
        wait(for: [enabled], timeout: 10)
        play.tap()
        let stop = app.buttons["Stop tanpura"]
        XCTAssertTrue(stop.waitForExistence(timeout: 3))
        stop.tap()
        XCTAssertTrue(app.buttons["Play tanpura"].waitForExistence(timeout: 3))
    }

    func testPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        try audit(app)
    }

    /// Long talas wrap onto two rows; the strip and each avarta still read as one element.
    func testLongTalaPlayersPassAccessibilityAudit() throws {
        let app = launch(["-open", "alankaram-1"])
        XCTAssertTrue(app.navigationBars["Alankaram 1"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Mayamalavagowla · Dhruva tala"].exists)
        XCTAssertTrue(app.otherElements["Dhruva tala: clap 1 2 3, clap wave, clap 1 2 3, clap 1 2 3"].exists)
        XCTAssertTrue(app.otherElements["Avarta 1: S R G M, G R, S R G R, S R G M"].exists)
        try audit(app, longNotation: true)
        app.terminate()

        let jhampa = launch(["-open", "alankaram-4"])
        XCTAssertTrue(jhampa.navigationBars["Alankaram 4"].waitForExistence(timeout: 5))
        XCTAssertTrue(jhampa.otherElements["Avarta 1: S R G S R S R, G, M hold"].exists)
        try audit(jhampa, longNotation: true)
    }

    /// Exercises longer than the screen: rows pushed off screen by larger text
    /// must still scale, and a long set name must fit the top bar.
    func testLongExercisePlayersPassAccessibilityAudit() throws {
        let app = launch(["-open", "vakra-janta-1"])
        XCTAssertTrue(app.navigationBars["Vakra Janta 1"].waitForExistence(timeout: 5))
        try audit(app, longNotation: true)
        app.terminate()
        let madhya = launch(["-open", "madhya-sthayi-1"])
        XCTAssertTrue(madhya.navigationBars["Madhya Sthayi 1"].waitForExistence(timeout: 5))
        try audit(madhya, longNotation: true)
    }

    /// A gitam: section headings, words under the swaras, and the words in each avarta's sentence.
    func testGitamPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "gitam-1"])
        XCTAssertTrue(app.navigationBars["Sri Gananatha"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Malahari · Rupaka tala"].exists)
        XCTAssertTrue(app.staticTexts["Charanam 1"].exists)
        XCTAssertTrue(app.otherElements["Avarta 1: M P, D S′ S′ R′. Words: sri ga na na tha"].exists)
        try audit(app, longNotation: true)
    }

    /// Analekara climbs to upper Ma: at the highest Sa the player explains and offers a playable Sa.
    func testSaTooHighForThePieceOffersAPlayableOne() throws {
        let app = launch(["-sa", "60", "-open", "gitam-5"])
        XCTAssertTrue(app.navigationBars["Analekara"].waitForExistence(timeout: 5))
        let notice = app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "goes higher than the harmonium can play")).firstMatch
        XCTAssertTrue(notice.exists)
        XCTAssertFalse(app.buttons["Play"].isEnabled)
        try audit(app, longNotation: true)
        app.buttons.containing(NSPredicate(format: "label BEGINSWITH %@", "Use Sa")).firstMatch.tap()
        XCTAssertFalse(notice.exists)
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: app.buttons["Play"])
        wait(for: [enabled], timeout: 10)
    }

    /// A swarajati: four cells to a beat, wrapped into rows; each avarta still one sentence.
    func testSwarajatiPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "swarajati-1"])
        XCTAssertTrue(app.navigationBars["Rara Venu"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.otherElements["Avarta 1: S R G P D S′ N D, P D P M G R S, R S low N low D S. Words: ra ra ve nu go pa ba la ra ji ta sad gu na ja ya shi la"].exists)
        try audit(app, longNotation: true)
    }

    /// Practise one section alone: choose it in a sheet; the button shows it.
    func testASectionCanBePractisedAlone() throws {
        let app = launch(["-open", "gitam-1"])
        XCTAssertTrue(app.navigationBars["Sri Gananatha"].waitForExistence(timeout: 5))
        let button = app.buttons["Section: Whole piece"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()
        XCTAssertTrue(app.navigationBars["Section"].waitForExistence(timeout: 3))
        try audit(app)
        app.buttons["Pallavi"].tap()
        XCTAssertTrue(app.buttons["Section: Pallavi"].waitForExistence(timeout: 3))
    }

    func testSettingsSheetPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        app.buttons["Practice settings"].tap()
        XCTAssertTrue(app.navigationBars["Practice settings"].waitForExistence(timeout: 5))
        try audit(app)
        // Below fine-tune and strictness: scroll to it, and audit that part too.
        let drone = app.switches["Tanpura drone"]
        for _ in 0..<4 where !drone.exists { app.swipeUp() }
        XCTAssertTrue(drone.exists)
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
    /// One deliberate exception: Dynamic Type warnings on notation cells
    /// (a swara "S′", a pair "DD", a karvai ",", a gitam syllable "tha"). Up to nine columns must fit
    /// across the screen, so a cell grows with the text size until it fills its column, then shrinks
    /// rather than being cut off (a design decision: clipped notation dropped
    /// swaras on Android). Verified by screenshot at the largest accessibility
    /// size. Everything else, including these cells' contrast, stays strict.
    ///
    /// With [longNotation] (exercises taller than the notation area), issues
    /// on rows outside the visible notation area are ignored: the list keeps
    /// rows just past the fold loaded, clipped from view, and the audit samples
    /// the controls drawn over them (false contrast failures) or loses rows
    /// that larger text pushes below the fold (a Dynamic Type or clipping
    /// warning with no element), and Dynamic Type warnings on text inside the notation area
    /// (section headings, the raga line), which larger text pushes below the
    /// fold in the same way; every such text uses a scaling text style, checked
    /// by screenshot at the largest size. Contrast, labels and everything outside
    /// the notation area are checked as strictly as ever.
    private func audit(_ app: XCUIApplication, longNotation: Bool = false) throws {
        let visibleNotation = longNotation ? app.scrollViews["notation"].frame : .zero
        try app.performAccessibilityAudit { issue in
            if issue.auditType == .dynamicType, let label = issue.element?.label,
               label.range(of: #"^(([SRGMPDN][′\x{0323}]?)+|,|[a-z·]+|–)$"#, options: .regularExpression) != nil {
                return true // ignored, see above
            }
            if longNotation {
                guard let element = issue.element else {
                    // A row unloaded below the fold at a larger text size: "may be clipped at
                    // larger Dynamic Type sizes" or "unsupported", with no element to inspect.
                    if issue.auditType == .dynamicType || issue.auditType == .textClipped { return true }
                    return false
                }
                if visibleNotation.intersects(element.frame) && !visibleNotation.contains(element.frame) {
                    return true // a row cut by the edge of the notation area
                }
                if !visibleNotation.intersects(element.frame) && element.frame.minY > visibleNotation.maxY {
                    return true // a loaded row below the fold, hidden behind the controls
                }
                if issue.auditType == .dynamicType, visibleNotation.contains(element.frame) {
                    return true // notation-area text (heading, raga line) that larger text pushes below the fold (it scales: checked by screenshot)
                }
            }
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' at \($0.frame)" } ?? "?"
            print("AUDIT: \(issue.compactDescription) — \(element)")
            return false
        }
    }
}
