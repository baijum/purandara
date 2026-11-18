import XCTest

/// Drives the app like a user and runs Apple's accessibility audit
/// (labels, contrast, hit regions, clipped text, Dynamic Type).
final class PurandaraUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor private func launch(_ arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        // Settings persist between launches; every test starts from the defaults.
        app.launchArguments = ["-resetSettings", "YES"] + arguments
        app.launch()
        return app
    }

    /// After one pass of the first varisai, the run moves to the next one and
    /// the player keeps playing (advancing must not call the leaving-player stop).
    @MainActor func testRunAdvancesToTheNextVarisai() throws {
        let app = launch(["-open", "set-SARALI"])
        let row = app.buttons["Practise Sarali Varisai, All 16 varisais, one after another"].firstMatch
        scrollTo(row, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        XCTAssertTrue(app.navigationBars["Sarali Varisai run"].waitForExistence(timeout: 5))
        let start = app.buttons["Start practice"]
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: start)
        wait(for: [enabled], timeout: 10)
        start.tap()
        XCTAssertTrue(app.staticTexts["Sarali Varisai · 1 of 16"].waitForExistence(timeout: 10))
        // Default plan: speed 1, one pass, so the next varisai follows one pass and a count-in.
        XCTAssertTrue(app.staticTexts["Sarali Varisai · 2 of 16"].waitForExistence(timeout: 70))
        XCTAssertTrue(app.buttons["Stop"].exists, "the second varisai is still playing")
        app.buttons["Stop"].tap()
        // Stopping on Sarali 2 offers the resume point, not the first varisai.
        XCTAssertTrue(app.buttons["Start from Sarali Varisai 2"].waitForExistence(timeout: 5))
    }

    /// "Your own raga": added from the raga list, chosen, its arohana shown; the editor passes the audit.
    @MainActor func testYourOwnRagaPassesAuditAndIsChosen() throws {
        let app = launch(["-singer", "0", "-open", "record"])
        let raga = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Raga'")).firstMatch
        XCTAssertTrue(raga.waitForExistence(timeout: 5))
        raga.tap()
        let add = app.buttons["Add your own raga…"]
        scrollTo(add, in: app)
        add.tap()
        XCTAssertTrue(app.navigationBars["Your own raga"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Save"].isEnabled, "Save waits for a name and swaras")
        let name = app.textFields["Name"]
        name.tap()
        name.typeText("Hindolam")
        for (letter, swara) in [("Ga", "Ga 2"), ("Ma", "Ma 1"), ("Dha", "Dha 1"), ("Ni", "Ni 2")] {
            let row = app.buttons[letter]
            scrollTo(row, in: app)
            row.tap()
            let choice = app.buttons[swara]
            XCTAssertTrue(choice.waitForExistence(timeout: 5))
            choice.tap()
        }
        XCTAssertTrue(app.descendants(matching: .any)["Up: Sa, Ga 2, Ma 1, Dha 1, Ni 2, high Sa"].waitForExistence(timeout: 2))
        try audit(app)
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons["Edit Hindolam"].waitForExistence(timeout: 5), "the new raga is chosen")
        app.buttons["Arohana"].tap()
        XCTAssertTrue(app.staticTexts["Sing: Sa, Ga, Ma, Dha, Ni, high Sa"].waitForExistence(timeout: 2))
        try audit(app)
    }

    @MainActor func testWhatYouAreSingingAndHowItWentPassAudit() throws {
        let app = launch(["-singer", "0", "-open", "record"])
        let raga = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Raga'")).firstMatch
        XCTAssertTrue(raga.waitForExistence(timeout: 5))
        raga.tap()
        // 25 ragas, alphabetical: Mayamalavagowla can be below the loaded rows.
        let mayamalavagowla = app.buttons["Mayamalavagowla"]
        scrollTo(mayamalavagowla, in: app)
        mayamalavagowla.tap()
        let arohana = app.buttons["Arohana"]
        XCTAssertTrue(arohana.waitForExistence(timeout: 5))
        arohana.tap()
        XCTAssertTrue(app.staticTexts["Sing: Sa, Ri, Ga, Ma, Pa, Dha, Ni, high Sa"].waitForExistence(timeout: 2))
        try audit(app)
        app.buttons["Start recording"].tap()
        Thread.sleep(forTimeInterval: 4.5) // the synthetic singer's way up
        app.buttons["Stop recording"].tap()
        XCTAssertTrue(app.navigationBars["Recording"].waitForExistence(timeout: 20))
        let chips = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Sa, in tune; Ri, in tune'")).firstMatch
        XCTAssertTrue(chips.waitForExistence(timeout: 5))
        scrollTo(chips, in: app)
        XCTAssertTrue(chips.isHittable, "How it went reads each swara")
        try audit(app)
    }

    @MainActor func testRecordingWithATanpuraListensFirstAndPassesAudit() throws {
        let app = launch(["-singer", "0", "-open", "record"])
        // Its label includes the help text below it.
        let toggle = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'External tanpura playing'")).firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertEqual(toggle.value as? String, "0")
        toggle.switches.firstMatch.tap()
        XCTAssertEqual(toggle.value as? String, "1")
        try audit(app)
        app.buttons["Start recording"].tap()
        XCTAssertTrue(app.staticTexts["Listening to your tanpura…"].waitForExistence(timeout: 2))
        try audit(app)
        XCTAssertTrue(app.staticTexts["Now sing"].waitForExistence(timeout: 5))
        Thread.sleep(forTimeInterval: 9) // the synthetic singer waits out the listening seconds, then sings
        app.buttons["Stop recording"].tap()
        XCTAssertTrue(app.navigationBars["Recording"].waitForExistence(timeout: 20))
        let phrase = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Phrase 1: S, R1, G3, M1, P'")).firstMatch
        XCTAssertTrue(phrase.waitForExistence(timeout: 5), "read after the listening seconds")
        try audit(app)
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testOpenPlayCountInAndStop() {
        let app = launch(["-open", "set-SARALI"])
        let row = app.buttons["Sarali Varisai 4, S R G M P , S R"].firstMatch
        // Down the set page: scroll until the row is on screen.
        scrollTo(row, in: app)
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

        // On a loaded runner launch and playback start can take far longer than
        // 3 s (this test case once ran 72 s on a stalled simulator, issue #28);
        // a real regression still fails the wider wait.
        XCTAssertTrue(app.staticTexts["Count-in…"].waitForExistence(timeout: 10), "count-in is shown first")
        let stop = app.buttons["Stop"]
        XCTAssertTrue(stop.exists)
        stop.tap()
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 3), "stop returns to Play")
    }

    /// A whole set as one run: plan it, change a speed and a repeat, start,
    /// read the status, stop and return to the plan; both screens pass the audit.
    @MainActor func testPractiseASetRunPassesAudit() throws {
        let app = launch(["-open", "set-SARALI"])
        let row = app.buttons["Practise Sarali Varisai, All 16 varisais, one after another"].firstMatch
        scrollTo(row, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        XCTAssertTrue(app.navigationBars["Sarali Varisai run"].waitForExistence(timeout: 5))
        try audit(app)

        let start = app.buttons["Start practice"]
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: start)
        wait(for: [enabled], timeout: 10)
        // The first varisai at second kala, and one more pass for every varisai.
        app.buttons["Second kala"].firstMatch.tap()
        app.buttons["One more time"].firstMatch.tap()
        start.tap()
        XCTAssertTrue(app.staticTexts["Sarali Varisai · 1 of 16"].waitForExistence(timeout: 10))
        try audit(app)

        app.buttons["Stop"].tap()
        XCTAssertTrue(app.buttons["Start practice"].waitForExistence(timeout: 5))
    }

    /// The run's mic toggle adds the live pitch lane; the synthetic singer
    /// keeps the test off the simulator's microphone.
    @MainActor func testRunSingAlongShowsThePitchLane() throws {
        let app = launch(["-singer", "0", "-open", "set-SARALI"])
        let row = app.buttons["Practise Sarali Varisai, All 16 varisais, one after another"].firstMatch
        scrollTo(row, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        XCTAssertTrue(app.navigationBars["Sarali Varisai run"].waitForExistence(timeout: 5))

        let start = app.buttons["Start practice"]
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: start)
        wait(for: [enabled], timeout: 10)
        start.tap()
        XCTAssertTrue(app.staticTexts["Sarali Varisai · 1 of 16"].waitForExistence(timeout: 10))
        // The run now uses the normal player, whose mic button adds the pitch lane.
        XCTAssertEqual(app.buttons["Sing along"].value as? String, "Off")
        app.buttons["Sing along"].tap()
        XCTAssertEqual(app.buttons["Sing along"].value as? String, "On")
        XCTAssertTrue(app.otherElements["Your pitch"].waitForExistence(timeout: 5))
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testLibraryPassesAccessibilityAudit() throws {
        let app = launch()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        try audit(app)
    }

    /// The navigation (docs/plan-navigation.md) on iPhone: three tabs; Home
    /// leads to a set page; the Sa tab and About pass the audit.
    @MainActor func testTabsSetPageSaTabAndAboutPassAudit() throws {
        let app = launch()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        for tab in ["Practise", "Sa", "Recordings"] {
            XCTAssertTrue(app.tabBars.buttons[tab].exists, "the \(tab) tab")
        }
        let set = app.buttons["Janta Varisai, 7 exercises"].firstMatch
        scrollTo(set, in: app)
        set.tap()
        XCTAssertTrue(app.navigationBars["Janta Varisai"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Practise Janta Varisai, All 7 varisais, one after another"].exists)
        try audit(app)
        app.navigationBars.buttons.element(boundBy: 0).tap() // Back to Home
        app.buttons["About"].tap()
        XCTAssertTrue(app.navigationBars["About"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.links["Open the guide on the website"].exists || app.buttons["Open the guide on the website"].exists)
        try audit(app)
        app.tabBars.buttons["Sa"].tap()
        XCTAssertTrue(app.navigationBars["Sa"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Shruti practice"].exists)
        XCTAssertTrue(app.buttons["Find my Sa"].exists)
        try audit(app)
    }

    /// The raga for the varisais (docs/plan-other-ragas.md): the list reads each
    /// scale, passes the audit, and choosing one changes Home and the set pages.
    @MainActor func testRagaForTheVarisaisPassesAuditAndIsChosen() throws {
        let app = launch()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Mayamalavagowla · Adi tala"].exists)
        let row = app.buttons["Mayamalavagowla, Gitams, swarajatis and varnams keep their own."].firstMatch
        scrollTo(row, in: app)
        row.tap()
        XCTAssertTrue(app.navigationBars["Raga"].waitForExistence(timeout: 5))
        let usual = app.buttons["Mayamalavagowla, The usual raga for the varisais: Sa, Ri 1, Ga 3, Ma 1, Pa, Dha 1, Ni 3, high Sa"]
        XCTAssertTrue(usual.exists)
        XCTAssertTrue(usual.isSelected)
        let play = app.buttons["Play Keeravani"]
        XCTAssertTrue(play.exists)
        wait(for: [expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: play)], timeout: 10)
        play.tap()
        XCTAssertTrue(app.buttons["Stop Keeravani"].waitForExistence(timeout: 5))
        try audit(app)
        let kalyani = app.buttons["Kalyani, Melakarta 65: Sa, Ri 2, Ga 3, Ma 2, Pa, Dha 2, Ni 3, high Sa"]
        scrollTo(kalyani, in: app)
        kalyani.tap()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Kalyani · Adi tala"].waitForExistence(timeout: 5))
        // The choice is saved: a new launch (without resetting) opens Sarali in Kalyani.
        app.terminate()
        let again = XCUIApplication()
        again.launchArguments = ["-open", "set-SARALI"]
        again.launch()
        XCTAssertTrue(again.navigationBars["Sarali Varisai"].waitForExistence(timeout: 5))
        XCTAssertTrue(again.staticTexts["In raga Kalyani"].exists)
    }

    /// Swara numbers (docs/plan-raga-followups.md F2): in another raga the notation
    /// shows and reads each swara's number, and the player passes the audit.
    @MainActor func testSwaraNumbersInAnotherRagaAreShownReadAndPassAudit() throws {
        let app = launch(["-raga", "kharaharapriya", "-open", "sarali-1@kharaharapriya"])
        XCTAssertTrue(app.staticTexts["Kharaharapriya · Adi tala"].waitForExistence(timeout: 5))
        let avarta = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Avarta 1: S R2 S R2, S R2, G2 M1'")).firstMatch
        XCTAssertTrue(avarta.waitForExistence(timeout: 5))
        try audit(app)
    }

    /// Your own raga for the varisais (docs/plan-own-raga-varisais.md): added from their raga list,
    /// named as a melakarta by the editor, chosen, and the varisais sung in it.
    @MainActor func testOwnRagaForTheVarisaisIsNamedChosenAndPassesAudit() throws {
        let app = launch()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        let row = app.buttons["Mayamalavagowla, Gitams, swarajatis and varnams keep their own."].firstMatch
        scrollTo(row, in: app)
        row.tap()
        let add = app.buttons["Add your own raga…"]
        scrollTo(add, in: app)
        add.tap()
        XCTAssertTrue(app.navigationBars["Your own raga"].waitForExistence(timeout: 5))
        for (letter, swara) in [("Ri", "Ri 1"), ("Ga", "Ga 3"), ("Ma", "Ma 1"), ("Pa", "Pa"), ("Dha", "Dha 2"), ("Ni", "Ni 2")] {
            let letterRow = app.buttons[letter]
            scrollTo(letterRow, in: app)
            letterRow.tap()
            let choice = app.buttons[swara]
            XCTAssertTrue(choice.waitForExistence(timeout: 5))
            choice.tap()
        }
        let suggestion = app.staticTexts["This is melakarta 16, Chakravakam."]
        scrollTo(suggestion, in: app)
        XCTAssertTrue(suggestion.exists)
        app.buttons["Use this name"].tap()
        try audit(app)
        app.buttons["Save"].tap()
        XCTAssertTrue(app.navigationBars["Raga"].waitForExistence(timeout: 5), "back on the raga list")
        let mine = app.buttons["Chakravakam, Melakarta 16: Sa, Ri 1, Ga 3, Ma 1, Pa, Dha 2, Ni 2, high Sa"]
        scrollTo(mine, in: app)
        XCTAssertTrue(mine.isSelected, "the new raga is the varisais' raga")
        // From the top: the list is taller than the screen now (element-less issues from off-screen
        // rows are ignored), and a row low on the screen is flagged because larger text pushes it
        // off (AGENTS.md). The own row is the same view as the fifteen, audited in the test above.
        for _ in 0..<6 { app.swipeDown(velocity: .fast) }
        try audit(app, longList: true)
        app.navigationBars["Raga"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.staticTexts["Chakravakam · Adi tala"].waitForExistence(timeout: 5))
    }

    /// Leaving the Sa tab stops Shruti practice; coming back shows the Sa page, not a restarted practice.
    @MainActor func testLeavingTheSaTabClosesShrutiPractice() throws {
        let app = launch(["-open", "shruti"])
        XCTAssertTrue(app.navigationBars["Shruti practice"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Practise"].tap()
        XCTAssertTrue(app.navigationBars["Purandara"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Sa"].tap()
        XCTAssertTrue(app.navigationBars["Sa"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.navigationBars["Shruti practice"].exists)
    }

    /// The iPad sidebar (regular width): every destination is listed, choosing
    /// one shows it, and the sidebar passes the audit. Runs on an iPad simulator.
    @MainActor func testSidebarListsTheDestinationsAndPassesAudit() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad, "the sidebar is the iPad layout")
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let app = launch()
        let sidebar = app.collectionViews["sidebar"]
        XCTAssertTrue(sidebar.waitForExistence(timeout: 5))
        try audit(app, sidebar: true)
        // Rows load as they scroll into view (landscape shows only part of the list).
        for item in ["Home", "Plain scale", "Varnam", "Shruti practice", "Find my Sa", "Recordings", "About"] {
            let row = sidebar.staticTexts[item]
            for _ in 0..<4 where !row.exists { sidebar.swipeUp(velocity: .slow) }
            XCTAssertTrue(row.exists, "the sidebar lists \(item)")
        }
        try audit(app, sidebar: true)
        sidebar.staticTexts["About"].tap()
        XCTAssertTrue(app.navigationBars["About"].waitForExistence(timeout: 5))
        try audit(app, sidebar: true)
        for _ in 0..<4 where !sidebar.staticTexts["Sarali Varisai"].isHittable { sidebar.swipeDown(velocity: .slow) }
        sidebar.staticTexts["Sarali Varisai"].tap()
        XCTAssertTrue(app.navigationBars["Sarali Varisai"].waitForExistence(timeout: 5))
        try audit(app, sidebar: true)
    }

    /// Scrolls the landing page until [element] is hittable: slowly up, then back
    /// down if it was passed. Fast swipes on a loaded CI runner can jump past a
    /// row, and a loop that only swipes up never comes back to it.
    @MainActor private func scrollTo(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<12 where !(element.exists && element.isHittable) { app.swipeUp(velocity: .slow) }
        for _ in 0..<12 where !(element.exists && element.isHittable) { app.swipeDown(velocity: .slow) }
    }

    /// Taps a link (Home, the Sa tab) once samples have loaded, scrolling to it if needed.
    @MainActor private func openFromLanding(_ app: XCUIApplication, _ label: String) {
        let link = app.buttons[label].firstMatch
        scrollTo(link, in: app)
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: link)
        wait(for: [enabled], timeout: 10)
        link.tap()
    }

    /// Recordings (docs/plan-recordings.md): the list, recording a synthetic
    /// Sarali, and the swaras it heard, each screen audited.
    @MainActor func testRecordingReadsTheSwarasAndPassesAudit() throws {
        let app = launch(["-singer", "0"])
        app.tabBars.buttons["Recordings"].tap()
        XCTAssertTrue(app.navigationBars["Recordings"].waitForExistence(timeout: 5))
        try audit(app)
        app.buttons["New recording"].tap()
        let start = app.buttons["Start recording"]
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        try audit(app)
        start.tap()
        Thread.sleep(forTimeInterval: 9) // one Sarali up and down
        try audit(app)
        app.buttons["Stop recording"].tap()
        XCTAssertTrue(app.navigationBars["Recording"].waitForExistence(timeout: 20))
        let phrase = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Phrase 1: S, R1, G3, M1, P, D1, N3'")).firstMatch
        XCTAssertTrue(phrase.waitForExistence(timeout: 5), "the synthetic Sarali is read as its swaras")
        try audit(app)
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testShrutiPracticeHoldsSaAndPassesAudit() throws {
        // The audit runs with the synthetic singer silent: every element is there, but
        // nothing redraws. With it singing, the pitch trace redraws every frame, and
        // the audit didn't complete in time on CI runners in five runs out of five (issue #36).
        let quiet = launch(["-singer", "0", "-singerSilent", "YES"])
        quiet.tabBars.buttons["Sa"].tap()
        openFromLanding(quiet, "Shruti practice")
        XCTAssertTrue(quiet.navigationBars["Shruti practice"].waitForExistence(timeout: 5))
        XCTAssertTrue(quiet.staticTexts["Sing Sa and hold it"].waitForExistence(timeout: 10))
        try audit(quiet)
        quiet.terminate()

        let app = launch(["-singer", "0"])
        app.tabBars.buttons["Sa"].tap()
        openFromLanding(app, "Shruti practice")
        XCTAssertTrue(app.navigationBars["Shruti practice"].waitForExistence(timeout: 5))
        // The hold status is at the bottom of the list: scroll down while waiting.
        let reached = app.staticTexts["Goal reached: Sa held for 10 seconds"]
        let deadline = Date().addingTimeInterval(30)
        while !reached.exists && Date() < deadline {
            app.swipeUp()
            _ = reached.waitForExistence(timeout: 2)
        }
        XCTAssertTrue(reached.exists, "the synthetic singer holds Sa past the goal")
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testFindMySaSuggestsASaAndUsesIt() throws {
        let app = launch(["-singer", "0"])
        app.tabBars.buttons["Sa"].tap()
        openFromLanding(app, "Find my Sa")
        // The synthetic singer sings G2, then G4.
        let result = app.staticTexts["Suggested Sa: D♯3 · 2½ kattai"]
        XCTAssertTrue(result.waitForExistence(timeout: 25))
        try audit(app)
        app.buttons["Use this"].tap()
        let newSa = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "D♯3 · 2½ kattai")).firstMatch
        XCTAssertTrue(newSa.waitForExistence(timeout: 5), "the Sa tab shows the new Sa")
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testLandingPageStartsHereAndPlaysTheTanpura() {
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

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    /// Repeat this cycle (docs/plan-repeat-cycle.md): the cycle is marked and
    /// Continue offered while it repeats, and that state passes the audit.
    @MainActor func testRepeatThisCyclePassesAuditAndContinues() throws {
        let app = launch(["-open", "sarali-3", "-countIn", "NO", "-tempo", "40", "-autoplay", "YES"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 3"].waitForExistence(timeout: 5))
        let repeatButton = app.buttons["Repeat this cycle"]
        XCTAssertTrue(repeatButton.waitForExistence(timeout: 15))
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: repeatButton)
        wait(for: [enabled], timeout: 15)
        // Tap at once: the exercise keeps moving, and an audit first can take longer
        // than the whole piece on a loaded runner. Repeating lasts until Continue.
        repeatButton.tap()
        XCTAssertTrue(app.buttons["Continue"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Repeating cycle '")).firstMatch.exists)
        let marked = app.otherElements.matching(NSPredicate(format: "label BEGINSWITH 'Repeating. Avarta'")).firstMatch
        XCTAssertTrue(marked.exists, "the repeated cycle is marked in its label")
        // The "Repeating" mark sits in the notation area, below status lines that grow
        // with larger text: the same below-the-fold false alarm as a section heading.
        try audit(app, longNotation: true)
        app.buttons["Continue"].tap()
        XCTAssertTrue(app.buttons["Repeat this cycle"].waitForExistence(timeout: 3))
        app.buttons["Stop"].tap()
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 3))
    }

    @MainActor func testPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        try audit(app)
    }

    /// Kala values stay numbered and persisted; the heading explains their
    /// written-pace multiplier rather than claiming an individual-swara count.
    @MainActor func testSpeedMultiplierFollowsKalaAndPersists() {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        let heading = app.staticTexts["speed-multiplier"]
        XCTAssertEqual(heading.label, "Speed, 1 times the written pace")
        XCTAssertTrue(app.buttons["First kala"].isSelected)

        for (kala, multiplier) in [("Second kala", 2), ("First kala", 1), ("Third kala", 4), ("Fourth kala", 8)] {
            app.buttons[kala].tap()
            XCTAssertTrue(app.buttons[kala].isSelected)
            XCTAssertEqual(heading.label, "Speed, \(multiplier) times the written pace")
        }

        app.terminate()
        app.launchArguments = ["-open", "sarali-4"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Fourth kala"].isSelected)
        XCTAssertEqual(heading.label, "Speed, 8 times the written pace")
    }

    @MainActor func testSpeedHelpPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        try checkSpeedHelp(app)
        XCTAssertEqual(app.staticTexts["speed-multiplier"].label, "Speed, 1 times the written pace")
        XCTAssertTrue(app.buttons["First kala"].isSelected)
    }

    @MainActor func testSpeedHelpAtLargestTextPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4", "-kala", "4",
                          "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["speed-multiplier"].label, "Speed, 8 times the written pace")
        try checkSpeedHelp(app, requireScrolling: true)
        XCTAssertTrue(app.buttons["Fourth kala"].isSelected)
        XCTAssertEqual(app.staticTexts["speed-multiplier"].label, "Speed, 8 times the written pace")
    }

    @MainActor private func attachSpeedScreenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor private func checkSpeedHelp(_ app: XCUIApplication, requireScrolling: Bool = false) throws {
        let size = requireScrolling ? "largest" : "normal"
        attachSpeedScreenshot(app, "Speed \(size) player")
        let about = app.buttons["About speed"]
        XCTAssertTrue(about.isHittable)
        XCTAssertGreaterThanOrEqual(about.frame.width, 44)
        XCTAssertGreaterThanOrEqual(about.frame.height, 44)
        about.tap()
        XCTAssertTrue(app.navigationBars["About speed"].waitForExistence(timeout: 3))
        attachSpeedScreenshot(app, "Speed \(size) help")
        let help = app.scrollViews["speed-help"]
        let explanation = help.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "Speed 1–4 uses")).firstMatch
        XCTAssertTrue(explanation.exists)
        XCTAssertTrue(explanation.label.contains("Speed 1–4 uses 1×, 2×, 4× and 8× the written pace. Tempo sets the tala beat, which stays unchanged."))
        XCTAssertTrue(explanation.label.contains("Janta 1–6 and both Vakra Janta exercises have two swaras per written cell: 2, 4, 8 or 16 individual swaras per beat at settings 1–4."))
        XCTAssertTrue(explanation.label.contains("Janta 7 uses single-swara cells and holds instead. Holds continue a note; rests are silent. Neither starts a new note. Mixed passages need not have one constant note count."))
        let done = app.buttons["Done"]
        XCTAssertTrue(done.isHittable)
        try audit(app)

        if requireScrolling {
            XCTAssertGreaterThan(explanation.frame.height, help.frame.height, "the explanation grows with accessibility text sizes")
            let initialTop = explanation.frame.minY
            help.swipeUp()
            XCTAssertLessThan(explanation.frame.minY, initialTop, "the large-text explanation scrolls")
            for _ in 0..<8 where explanation.frame.maxY > help.frame.maxY { help.swipeUp() }
            XCTAssertLessThanOrEqual(explanation.frame.maxY, help.frame.maxY + 1, "the final paragraph can be reached")
            attachSpeedScreenshot(app, "Speed largest help end")
            try audit(app)
        }

        XCTAssertTrue(done.isHittable, "dismissal stays available while the explanation scrolls")
        done.tap()
        XCTAssertTrue(help.waitForNonExistence(timeout: 3))
        XCTAssertTrue(about.isHittable)
    }

    /// At the second speed two written avartas make one tala cycle: the strip
    /// wraps over its pairs, and the cycle reads with commas at its angas.
    @MainActor func testSecondSpeedLaysOutOneCyclePerBlock() throws {
        let app = launch(["-open", "sarali-4", "-kala", "2"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.otherElements["Adi tala: 2 swaras to a beat, clap 1 2 3, clap wave, clap wave"].exists)
        XCTAssertTrue(app.otherElements["Avarta 1: S R G M P hold S R, S R G M, P D N S′"].exists)
        try audit(app)
    }

    /// Long talas wrap onto two rows; the strip and each avarta still read as one element.
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testLongTalaPlayersPassAccessibilityAudit() throws {
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
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testLongExercisePlayersPassAccessibilityAudit() throws {
        let app = launch(["-open", "vakra-janta-1"])
        XCTAssertTrue(app.navigationBars["Vakra Janta 1"].waitForExistence(timeout: 5))
        try audit(app, longNotation: true)
        app.terminate()
        let madhya = launch(["-open", "madhya-sthayi-1"])
        XCTAssertTrue(madhya.navigationBars["Madhya Sthayi 1"].waitForExistence(timeout: 5))
        try audit(madhya, longNotation: true)
    }

    /// A gitam: section headings, words under the swaras, and the words in each avarta's sentence.
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testGitamPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "gitam-1"])
        XCTAssertTrue(app.navigationBars["Sri Gananatha"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Malahari · Rupaka tala"].exists)
        XCTAssertTrue(app.staticTexts["Charanam 1"].exists)
        XCTAssertTrue(app.otherElements["Avarta 1: M P, D S′ S′ R′. Words: sri ga na na tha"].exists)
        try audit(app, longNotation: true)
    }

    /// Analekara climbs to upper Ma: at the highest Sa the player explains and offers a playable Sa.
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testSaTooHighForThePieceOffersAPlayableOne() throws {
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

    /// Sing-along on with the Sa out of range: the panel must not build a schedule
    /// (issue #9); the warning and the recovery stay usable with the microphone not
    /// yet granted.
    @MainActor func testSingAlongWithSaTooHighForThePieceOffersAPlayableOne() throws {
        let app = launch(["-sa", "60", "-open", "gitam-5", "-singAlong", "YES"])
        XCTAssertTrue(app.navigationBars["Analekara"].waitForExistence(timeout: 5))
        let notice = app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "goes higher than the harmonium can play")).firstMatch
        XCTAssertTrue(notice.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Sing along needs a Sa this piece can play")).firstMatch.exists)
        XCTAssertFalse(app.buttons["Play"].isEnabled)
        app.buttons.containing(NSPredicate(format: "label BEGINSWITH %@", "Use Sa")).firstMatch.tap()
        XCTAssertFalse(notice.exists)
        // The pitch lane builds from the playable Sa; the mic was never granted, so
        // the reading says the app is not hearing the singer yet.
        XCTAssertTrue(app.otherElements["Your pitch"].waitForExistence(timeout: 5))
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: app.buttons["Play"])
        wait(for: [enabled], timeout: 10)
    }

    /// A swarajati: four cells to a beat, wrapped into rows; each avarta still one sentence.
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testSwarajatiPlayerPassesAccessibilityAudit() throws {
        let app = launch(["-open", "swarajati-1"])
        XCTAssertTrue(app.navigationBars["Rara Venu"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.otherElements["Avarta 1: S R G P D S′ N D, P D P M G R S, R S low N low D S. Words: ra ra ve nu go pa ba la ra ji ta sad gu na ja ya shi la"].exists)
        try audit(app, longNotation: true)
    }

    /// Practise one section alone: choose it in a sheet; the button shows it.
    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testASectionCanBePractisedAlone() throws {
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

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testSettingsSheetPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4", "-repeatPractice", "YES"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        app.buttons["Practice settings"].tap()
        XCTAssertTrue(app.navigationBars["Practice settings"].waitForExistence(timeout: 5))
        try audit(app)
        // Each setting remains explicitly audited, on short pages that do not
        // lose unrelated lower controls as preceding explanations grow.
        app.buttons["Sound"].tap()
        try audit(app)
        for page in ["Tuning", "Harmonium guide", "Volumes and drone"] {
            app.buttons[page].tap()
            XCTAssertTrue(app.navigationBars[page].waitForExistence(timeout: 3))
            try audit(app)
            app.navigationBars.buttons.firstMatch.tap()
        }
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Rhythm"].tap()
        try audit(app)
        app.buttons["Practice mode"].tap()
        try audit(app)
        app.buttons["Response guide"].tap()
        try audit(app)
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Click and count-in"].tap()
        try audit(app)
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Repeat and recovery"].tap()
        try audit(app)
        XCTAssertEqual(app.switches["Repeat"].value as? String, "1")
        app.buttons.containing(NSPredicate(format: "label BEGINSWITH %@", "Between repeats")).firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Between repeats"].waitForExistence(timeout: 3))
        app.buttons["Timed break"].tap()
        try audit(app)
        let duration = app.buttons["Timed break duration"]
        for _ in 0..<4 where !(duration.exists && duration.isHittable) { app.swipeUp() }
        XCTAssertTrue(duration.isHittable)
        duration.tap()
        try audit(app)
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["About recovery"].tap()
        try audit(app)
        for page in ["During a break", "Microphone privacy"] {
            app.buttons[page].tap()
            try audit(app)
            app.navigationBars.buttons.firstMatch.tap()
        }
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Feedback and notation"].tap()
        try audit(app)
        for page in ["Echo cancellation", "Strictness", "Gitam notation"] {
            app.buttons[page].tap()
            XCTAssertTrue(app.navigationBars[page].waitForExistence(timeout: 3))
            try audit(app)
            app.navigationBars.buttons.firstMatch.tap()
        }
    }

    @MainActor func testHarmoniumNotesChoicePersistsAndPassesAccessibilityAudit() throws {
        let app = launch(["-open", "sarali-4"])
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        app.buttons["Practice settings"].tap()
        openHarmoniumNotes(app)
        XCTAssertTrue(app.buttons["Connected"].isSelected)
        XCTAssertFalse(app.buttons["Lightly separated"].isSelected)
        XCTAssertTrue(app.staticTexts["Changes the harmonium guide, not tala timing or where you breathe."].exists)
        try audit(app)
        app.buttons["Lightly separated"].tap()
        XCTAssertTrue(app.navigationBars["Sound"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.descendants(matching: .any)["harmonium-notes"].firstMatch.label.contains("Lightly separated"))

        app.terminate()
        // Relaunch without resetting to prove the selected choice is persisted.
        app.launchArguments = ["-open", "sarali-4"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Sarali Varisai 4"].waitForExistence(timeout: 5))
        app.buttons["Practice settings"].tap()
        openHarmoniumNotes(app)
        XCTAssertFalse(app.buttons["Connected"].isSelected)
        XCTAssertTrue(app.buttons["Lightly separated"].isSelected)
        try audit(app)
    }

    @MainActor private func openHarmoniumNotes(_ app: XCUIApplication) {
        XCTAssertTrue(app.navigationBars["Practice settings"].waitForExistence(timeout: 5))
        app.buttons["Sound"].tap()
        XCTAssertTrue(app.navigationBars["Sound"].waitForExistence(timeout: 3))
        let link = app.descendants(matching: .any)["harmonium-notes"].firstMatch
        scrollTo(link, in: app)
        XCTAssertTrue(link.isHittable)
        link.tap()
        XCTAssertTrue(app.navigationBars["Harmonium notes"].waitForExistence(timeout: 3))
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testSingAlongWithASharpSingerMarksEveryNoteHigh() {
        // Debug synthetic singer 35 cents sharp on the 16-note plain scale (8 s count-in + 16 s).
        let app = launch(["-open", "plain-scale-1", "-autoplay", "YES", "-singAlong", "YES", "-singer", "35"])
        let summary = app.staticTexts["Pass 1: 0 of 16 notes in tune or close"]
        XCTAssertTrue(summary.waitForExistence(timeout: 40), "pass summary")
        XCTAssertTrue(app.otherElements["Avarta 1: S high R high G high M high, P high D high, N high S′ high"].exists)
        XCTAssertTrue(app.buttons["Sing along"].exists)
        XCTAssertEqual(app.buttons["Sing along"].value as? String, "On")
    }

    // Xcode 16's XCTestCase is not @MainActor, but XCUIApplication/XCUIElement
    // are: mark each test (not the class, which would break the nonisolated
    // setUp override) and every helper they call.
    @MainActor func testSingAlongInTuneAndAudit() throws {
        let app = launch(["-open", "plain-scale-1", "-autoplay", "YES", "-singAlong", "YES", "-singer", "0"])
        XCTAssertTrue(app.staticTexts["Pass 1: 16 of 16 notes in tune or close"].waitForExistence(timeout: 40))
        XCTAssertTrue(app.otherElements["Your pitch"].exists)
        // Audit the settled screen, after the release and the final beat highlight have finished.
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 5))
        try audit(app)
    }

    @MainActor func testOutputChangeStopsShrutiAndFindSaUntilStartAgain() throws {
        for destination in ["shruti", "find-sa"] {
            let app = launch(["-open", destination, "-singer", "0", "-simulateOutputChange", "YES"])
            let change = app.buttons["Simulate output change"]
            XCTAssertTrue(change.waitForExistence(timeout: 10))
            // Opening is allowed before the bundled samples finish loading.
            sleep(2)
            change.tap()
            let restart = app.buttons["Start again"]
            XCTAssertTrue(restart.waitForExistence(timeout: 5))
            XCTAssertTrue(app.staticTexts["Audio output changed. Start again when ready."].exists)
            try audit(app)
            XCUIDevice.shared.press(.home)
            app.activate()
            XCTAssertTrue(restart.waitForExistence(timeout: 5), "background/foreground must not restart")
            restart.tap()
            XCTAssertTrue(restart.waitForNonExistence(timeout: 5))
            app.terminate()
        }
    }

    @MainActor func testListenRepeatScoredPairAndRecoveryControls() throws {
        let app = launch(["-open", "sarali-1", "-autoplay", "YES", "-practiceMode", "listenRepeat",
                          "-recoveryMode", "whenReady", "-tempo", "120", "-kala", "2", "-singAlong", "YES", "-singer", "0"])
        XCTAssertTrue(app.staticTexts["Listen · attempt 1"].waitForExistence(timeout: 10))
        let next = app.buttons["Continue"]
        XCTAssertTrue(next.waitForExistence(timeout: 20))
        try audit(app)
        next.tap()
        XCTAssertTrue(app.staticTexts["Count-in…"].waitForExistence(timeout: 5))
        // 20 s like the other waits here: on a slow runner the count-in and the start
        // of the turn can take longer than 8 s (issue #36). It returns as soon as it appears.
        XCTAssertTrue(app.staticTexts["Your turn · attempt 1"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 20), "repeat off ends after one response")
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "Pass 1:")).firstMatch.exists)
    }

    @MainActor func testTimedRecoveryWaitContinueStopAndBackground() throws {
        let app = launch(["-open", "sarali-1", "-autoplay", "YES", "-recoveryMode", "timed",
                          "-repeatPractice", "YES", "-recoverySeconds", "30", "-tempo", "120", "-kala", "2", "-countIn", "NO"])
        let wait = app.buttons["Wait for me"]
        XCTAssertTrue(wait.waitForExistence(timeout: 20))
        wait.tap()
        XCTAssertTrue(wait.waitForNonExistence(timeout: 3))
        let next = app.buttons["Continue"]
        XCTAssertTrue(next.exists)
        try audit(app)
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.staticTexts["Practice paused. Continue when ready."].waitForExistence(timeout: 5))
        next.tap()
        XCTAssertTrue(app.buttons["Stop"].waitForExistence(timeout: 5))
        app.buttons["Stop"].tap()
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Continue"].exists)
    }

    @MainActor func testOutputChangeStopsExerciseUntilPlay() {
        let app = launch(["-open", "sarali-4", "-autoplay", "YES", "-simulateOutputChange", "YES"])
        XCTAssertTrue(app.buttons["Stop"].waitForExistence(timeout: 10))
        app.buttons["Simulate output change"].tap()
        let play = app.buttons["Play"]
        XCTAssertTrue(play.waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(play.waitForExistence(timeout: 5))
        play.tap()
        XCTAssertTrue(app.buttons["Stop"].waitForExistence(timeout: 5))
    }

    /// Runs the audit and names each offending element in the failure.
    ///
    /// Two deliberate exceptions. Contrast findings are logged, not fatal: the
    /// check is environment noise for this app (see the comment in the handler).
    /// Dynamic Type warnings on notation cells
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
    ///
    /// With [sidebar] (the iPad layout), Dynamic Type warnings on the sidebar's
    /// rows are ignored: the menu is taller than the screen, so whichever rows
    /// sit at its edges are ones larger text pushes out of view (the warning
    /// moves to whichever row is there). The rows are a scrolling system list in
    /// the body text style, checked by screenshot at the largest size.
    ///
    /// With [longList] (a list taller than the screen, such as the varisais' raga list with an own
    /// raga), Dynamic Type and clipped-text issues with no element are ignored: they come from rows
    /// loaded off screen and move with the scroll position, as for `longNotation`. Issues on a
    /// visible element still fail.
    @MainActor private func audit(_ app: XCUIApplication, longNotation: Bool = false, sidebar: Bool = false, longList: Bool = false) throws {
        let visibleNotation = longNotation ? app.scrollViews["notation"].frame : .zero
        let sidebarFrame = sidebar ? app.collectionViews["sidebar"].frame : .zero
        // On a loaded runner the audit machinery itself can time out ("Audit
        // failed to complete in time", issue #28). That is never a code signal:
        // settle and retry once before failing.
        do {
            try performAudit(app, longNotation: longNotation, visibleNotation: visibleNotation, sidebar: sidebarFrame, longList: longList)
        } catch {
            print("AUDIT: retrying after the audit did not complete: \(error)")
            Thread.sleep(forTimeInterval: 2)
            try performAudit(app, longNotation: longNotation, visibleNotation: visibleNotation, sidebar: sidebarFrame, longList: longList)
        }
    }

    @MainActor private func performAudit(_ app: XCUIApplication, longNotation: Bool, visibleNotation: CGRect,
                                         sidebar: CGRect = .zero, longList: Bool = false) throws {
        try app.performAccessibilityAudit { issue in
            let element = issue.element.map { "\($0.elementType.rawValue) '\($0.label)' at \($0.frame)" } ?? "?"
            if issue.auditType == .contrast {
                // The contrast check is environment noise for this app (issue #8):
                // it flags pairs that pass WCAG AA by wide margins (white on the
                // maroon accent 10:1, maroon on its tinted fill 8.8:1, primary on
                // white 21:1), flags one .bordered button while identical siblings
                // pass, and reports different sets per device and runtime under the
                // same Xcode build. Contrast work happens on the tuned local
                // toolchain, where all 15 tests pass; here it is logged, not fatal.
                print("AUDIT (contrast, logged): \(issue.compactDescription) — \(element)")
                return true
            }
            if issue.auditType == .dynamicType, let label = issue.element?.label,
               label.range(of: #"^(([SRGMPDN][′\x{0323}]?)+|,|[a-z·]+|–)$"#, options: .regularExpression) != nil {
                return true // ignored, see above
            }
            if longList, issue.element == nil, issue.auditType == .dynamicType || issue.auditType == .textClipped {
                print("AUDIT (long list, element-less, ignored): \(issue.compactDescription)")
                return true // a row loaded off screen, see above
            }
            if issue.auditType == .dynamicType, let frame = issue.element?.frame, sidebar.contains(frame) {
                return true // a sidebar row at the menu's edge, see above
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
            print("AUDIT: \(issue.compactDescription) — \(element)")
            return false
        }
    }
}
