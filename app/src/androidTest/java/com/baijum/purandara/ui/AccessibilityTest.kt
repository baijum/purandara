package com.baijum.purandara.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.audio.HarmoniumArticulation
import com.baijum.purandara.audio.PracticePhase
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.RecoveryMode
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.domain.varisai.WordsDisplay
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.HomeScreen
import com.baijum.purandara.ui.library.SetScreen
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.practice.RunStep
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.run.RunActions
import com.baijum.purandara.ui.run.RunScreen
import com.baijum.purandara.ui.theme.PurandaraTheme
import com.baijum.purandara.viewmodel.PlaybackState
import com.baijum.purandara.viewmodel.PracticeUiState
import com.baijum.purandara.viewmodel.RunPhase
import com.baijum.purandara.viewmodel.RunUiState
import com.baijum.purandara.viewmodel.SingAlongState
import com.baijum.purandara.viewmodel.MarkKey
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.singalong.Attention
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.singalong.PassSummary
import com.baijum.purandara.singalong.SwaraMapper
import com.baijum.purandara.singalong.Tendency
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldState
import com.baijum.purandara.shruti.HoldTarget
import com.baijum.purandara.shruti.SaSuggestion
import com.baijum.purandara.singalong.Strictness
import com.baijum.purandara.ui.shruti.FindSaActions
import com.baijum.purandara.ui.shruti.FindSaScreen
import com.baijum.purandara.ui.shruti.ShrutiActions
import com.baijum.purandara.ui.shruti.ShrutiScreen
import com.baijum.purandara.shruti.FindSaProgress
import com.baijum.purandara.shruti.FindSaStep
import com.baijum.purandara.viewmodel.ShrutiUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What TalkBack users get: descriptions, roles, states, headings and target sizes. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(PracticeSettings())
    private var singAlongToggled = 0
    private var sectionPicked: String? = null
    private var runSpeedApplied: Int? = null
    private var runRepeatsApplied: Int? = null

    private fun showPlayer(
        playback: PlaybackState = PlaybackState(),
        singAlong: SingAlongState = SingAlongState(micPermitted = true),
        id: String = "sarali-4",
        micDenied: Boolean = false,
        fontScale: Float? = null,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                PurandaraTheme {
                    PlayerScreen(
                        varisai = VarisaiCatalog.byId(id)!!,
                        state = PracticeUiState(settings = settings, samplesLoaded = true, playback = playback, singAlong = singAlong),
                        micDenied = micDenied,
                        actions = PlayerActions(
                            onBack = {},
                            onTogglePlay = {},
                            onSettingsChange = { settings = it(settings) },
                            onToggleSingAlong = { singAlongToggled++ },
                            onSelectSection = { sectionPicked = it?.label ?: "whole" },
                        ),
                    )
                }
            }
        }
    }

    private fun runSteps(count: Int = 3): List<RunStep> =
        VarisaiCatalog.all.filter { it.set == VarisaiSet.SARALI }.take(count)
            .mapIndexed { i, varisai -> RunStep(varisai, speed = 1 + i % 4, repeats = 2) }

    private fun showRun(
        run: RunUiState,
        fontScale: Float? = null,
        singAlong: SingAlongState = SingAlongState(),
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                PurandaraTheme {
                    RunScreen(
                        state = PracticeUiState(settings = settings, samplesLoaded = true, run = run, singAlong = singAlong),
                        actions = RunActions(
                            onBack = {},
                            onApplySpeed = { runSpeedApplied = it },
                            onApplyRepeats = { runRepeatsApplied = it },
                        ),
                    )
                }
            }
        }
    }

    private fun showRunPlayer(
        run: RunUiState,
        singAlong: SingAlongState = SingAlongState(),
        onSkipRun: () -> Unit = {},
    ) {
        compose.setContent {
            PurandaraTheme {
                PlayerScreen(
                    varisai = requireNotNull(run.current).varisai,
                    state = PracticeUiState(
                        settings = settings, samplesLoaded = true, run = run,
                        playback = PlaybackState(playing = true), singAlong = singAlong,
                    ),
                    actions = PlayerActions(
                        onBack = {},
                        onTogglePlay = {},
                        onSettingsChange = { settings = it(settings) },
                        onSkipRun = onSkipRun,
                    ),
                )
            }
        }
    }

    @Test
    fun runSetupKeepsEachVarisaiSpeedAndRepeatsSeparate() {
        val steps = runSteps()
        showRun(RunUiState(VarisaiSet.SARALI, RunPhase.SETUP, steps, 0, 0, totalSeconds = 120.0))
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Set every varisai to speed") and heading).assertExists()
        assertTouchTarget(compose.onNodeWithContentDescription("Set every varisai to speed 3").assertHasClickAction().performClick())
        assertEquals(3, runSpeedApplied)
        // One speed group per varisai, plus the "set every varisai" buttons.
        compose.onAllNodesWithContentDescription("First kala").assertCountEquals(steps.size)
        compose.onAllNodesWithContentDescription("One more time").assertCountEquals(steps.size + 1)
        // The bulk control comes first and applies to every varisai.
        compose.onAllNodesWithContentDescription("One more time").onFirst().assertHasClickAction().performClick()
        assertEquals(4, runRepeatsApplied)
        assertTouchTarget(compose.onNodeWithText("Start practice").assertHasClickAction())
        compose.onNodeWithText("about 2 minutes").assertExists()
    }

    @Test
    fun runSetupShowsTheSavedContinuePoint() {
        val steps = runSteps()
        showRun(RunUiState(VarisaiSet.SARALI, RunPhase.SETUP, steps, 1, 1, totalSeconds = 120.0))
        assertTouchTarget(compose.onNodeWithText("Start from Sarali Varisai 2").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithText("Start from the beginning").assertHasClickAction())
    }

    @Test
    fun runSetupRemainsUsableAtLargeTextSizes() {
        showRun(RunUiState(VarisaiSet.SARALI, RunPhase.SETUP, runSteps(), 0, 0, totalSeconds = 120.0), fontScale = 2f)
        assertTouchTarget(compose.onNodeWithText("Start practice").assertIsDisplayed())
        compose.onNodeWithText("Set every varisai to speed").performScrollTo().assertIsDisplayed()
        captureScreenshot("run-setup-large-text")
    }

    @Test
    fun runPlayingShowsTheBannerInTheNormalPlayer() {
        var skipped = 0
        showRunPlayer(
            RunUiState(VarisaiSet.SARALI, RunPhase.PLAYING, runSteps(), 0, 1, pass = 1, kala = 2, totalSeconds = 120.0),
            onSkipRun = { skipped++ },
        )
        compose.onNodeWithText("Sarali Varisai · 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Second kala · Pass 2 of 2").assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithText("Stop").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithContentDescription("Skip to the next varisai").assertHasClickAction().performClick())
        assertEquals(1, skipped)
    }

    @Test
    fun runPlayingShowsTheLivePitchLaneWhenSingAlongIsOn() {
        settings = settings.copy(singAlong = true)
        val sa = settings.saHz
        showRunPlayer(
            RunUiState(VarisaiSet.SARALI, RunPhase.PLAYING, runSteps(), 0, 0, totalSeconds = 120.0),
            singAlong = SingAlongState(
                micPermitted = true,
                live = SwaraMapper(sa, VarisaiCatalog.raga, settings.tuning).read(sa),
                pass = 0,
                trace = emptyList(),
                onSpeaker = false,
            ),
        )
        compose.onNodeWithContentDescription("Your pitch").assertExists()
    }

    @Test
    fun runCompleteOffersAgainAndDone() {
        showRun(RunUiState(VarisaiSet.SARALI, RunPhase.COMPLETE, runSteps(), 0, 0, totalSeconds = 120.0))
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Sarali Varisai complete") and heading).assertIsDisplayed()
        compose.onNodeWithText("3 varisais, one after another.").assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithText("Practise again").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithText("Done").assertHasClickAction())
    }

    @Test
    fun setPageOffersOneRunThroughTheSet() {
        showSet(VarisaiSet.SARALI)
        val row = hasContentDescription("Practise Sarali Varisai, All 16 varisais, one after another")
        compose.onNode(hasScrollAction()).performScrollToNode(row)
        assertTouchTarget(compose.onNode(row).assertHasClickAction())
    }

    @Test
    fun eachAvartaIsReadAsOneSentence() {
        showPlayer()
        compose.onNodeWithContentDescription("Avarta 1: S R G M, P hold, S R").assertIsDisplayed()
        compose.onNodeWithContentDescription("Avarta 4: S′ N D P, M G, R S").assertExists()
    }

    @Test
    fun talaMarksAreNotReadOneByOne() {
        showPlayer()
        // Notation cells and the tala strip are summarised or hidden, so no
        // lone "x" or "v" nodes reach the screen reader.
        compose.onAllNodesWithText("x").assertCountEquals(0)
        compose.onAllNodesWithText("v").assertCountEquals(0)
    }

    @Test
    fun controlsAreLabelledAndLargeEnough() {
        showPlayer()
        assertTouchTarget(compose.onNodeWithContentDescription("Back").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithContentDescription("Practice settings").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithContentDescription("About speed").assertHasClickAction())
        for (label in listOf("Slower", "Faster", "First kala", "Second kala", "Third kala", "Fourth kala")) {
            assertTouchTarget(compose.onNodeWithContentDescription(label).assertHasClickAction())
        }
        assertTouchTarget(compose.onNodeWithText("Play").assertHasClickAction())
    }

    /** The touchable area (which may extend beyond what is drawn) is at least 48 x 48 dp. */
    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val semantics = node.fetchSemanticsNode()
        val bounds = semantics.touchBoundsInRoot
        val minPx = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("touch target ${bounds.width} x ${bounds.height} px < 48 dp", bounds.width >= minPx && bounds.height >= minPx)
    }

    @Test
    fun kalaButtonsChangeTheSetting() {
        showPlayer()
        assertSpeedHeading(1)
        compose.onNodeWithContentDescription("Third kala").performClick().assertIsSelected()
        assertEquals(3, settings.kala)
        assertSpeedHeading(4)
        for ((label, kala, multiplier) in listOf(
            Triple("First kala", 1, 1),
            Triple("Second kala", 2, 2),
            Triple("Fourth kala", 4, 8),
        )) {
            compose.onNodeWithContentDescription(label).performClick().assertIsSelected()
            assertEquals(kala, settings.kala)
            assertSpeedHeading(multiplier)
        }
    }

    private fun assertSpeedHeading(multiplier: Int) {
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasContentDescription("Speed, $multiplier times the written pace") and heading).assertIsDisplayed()
    }

    @Test
    fun speedHelpHasAnAccessibleTitleScrollableTextAndDismissalWithoutSettingChanges() {
        settings = settings.copy(kala = 3)
        showPlayer()
        val before = settings
        assertSpeedHeading(4)
        captureScreenshot("speed-player-normal")
        assertSpeedHelp("normal")
        assertEquals(before, settings)
        assertSpeedHeading(4)
        // The platform Back action dismisses the same dialog without touching settings.
        compose.onNodeWithContentDescription("About speed").performClick()
        compose.onNodeWithText("About speed").assertIsDisplayed()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("About speed").assertDoesNotExist()
        assertEquals(before, settings)
        assertSpeedHeading(4)
    }

    @Test
    fun speedHeadingAndHelpRemainUsableAtLargeTextSizes() {
        settings = settings.copy(kala = 4)
        showPlayer(fontScale = 2f)
        val before = settings
        assertSpeedHeading(8)
        assertTouchTarget(compose.onNodeWithContentDescription("Fourth kala").assertIsDisplayed().assertIsSelected())
        captureScreenshot("speed-player-large-text")
        assertSpeedHelp("large-text")
        assertEquals(before, settings)
        assertSpeedHeading(8)
    }

    private fun assertSpeedHelp(screenshotSuffix: String) {
        val about = compose.onNodeWithContentDescription("About speed").assertIsDisplayed().assertHasClickAction()
        assertTouchTarget(about)
        about.performClick()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val dialog = SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)
        compose.onNode(hasText("About speed") and heading and hasAnyAncestor(dialog)).assertIsDisplayed()
        compose.onNode(hasScrollAction() and hasAnyAncestor(dialog)).assertExists()
        captureScreenshot("speed-help-initial-$screenshotSuffix")
        for (text in listOf(
            "Speed 1–4 uses 1×, 2×, 4× and 8× the written pace. Tempo sets the tala beat, which stays unchanged.",
            "Janta 1–6 and both Vakra Janta exercises have two swaras per written cell: 2, 4, 8 or 16 individual swaras per beat at settings 1–4.",
            "Janta 7 uses single-swara cells and holds instead. Holds continue a note; rests are silent. Neither starts a new note. Mixed passages need not have one constant note count.",
        )) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        captureScreenshot("speed-help-final-$screenshotSuffix")
        val done = compose.onNodeWithText("Done").assertIsDisplayed().assertHasClickAction()
        assertTouchTarget(done)
        done.performClick()
        compose.onNodeWithText("About speed").assertDoesNotExist()
    }

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "$name.png").outputStream().use {
                assertTrue("Could not save screenshot $name", screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Practice settings").performClick()
        compose.onNodeWithText("Practice settings").assertIsDisplayed()
    }

    @Test
    fun togglesAreSwitchesWithState() {
        showPlayer()
        openSettings()
        val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onNode(hasText("Tanpura drone") and isSwitch).performScrollTo().assertIsOn()
        compose.onNode(hasText("Count in one avarta") and isSwitch).performScrollTo().assertIsOn()
        compose.onNode(hasText("Tala click") and isSwitch).performScrollTo().performClick()
        assertEquals(true, settings.click)
    }

    @Test
    fun harmoniumNotesHaveAHeadingAndSelectableChoices() {
        showPlayer()
        openSettings()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNode(hasText("Harmonium notes") and heading).performScrollTo().assertIsDisplayed()
        val connected = compose.onNode(hasText("Connected") and radio)
        val separated = compose.onNode(hasText("Lightly separated") and radio)
        assertTouchTarget(connected.performScrollTo().assertHasClickAction().assertIsSelected())
        assertTouchTarget(separated.performScrollTo().assertHasClickAction().assertIsNotSelected())
        compose.onNodeWithText("Changes the harmonium guide, not tala timing or where you breathe.")
            .performScrollTo().assertIsDisplayed()
        separated.performScrollTo().performClick().assertIsSelected()
        connected.assertIsNotSelected()
        assertEquals(HarmoniumArticulation.LIGHTLY_SEPARATED, settings.articulation)
        connected.performScrollTo().performClick().assertIsSelected()
        separated.assertIsNotSelected()
        assertEquals(HarmoniumArticulation.CONNECTED, settings.articulation)
    }

    @Test
    fun practiceModesAndRecoveryAreSelectedLabelledChoices() {
        showPlayer()
        openSettings()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Practice mode") and heading).performScrollTo().assertIsDisplayed()
        assertTouchTarget(compose.onNode(hasText("Normal playback") and radio).performScrollTo().assertIsSelected())
        compose.onNode(hasText("Listen, then sing") and radio).performScrollTo().performClick().assertIsSelected()
        assertEquals(PracticeMode.LISTEN_REPEAT, settings.practiceMode)
        compose.onNodeWithText("Harmonium during your turn").performScrollTo().assertHasClickAction()
        compose.onNode(hasText("Between repeats") and heading).performScrollTo().assertIsDisplayed()
        assertTouchTarget(compose.onNode(hasText("When ready") and radio).performScrollTo().performClick().assertIsSelected())
        assertEquals(RecoveryMode.WHEN_READY, settings.recoveryMode)
        compose.onNode(hasText("Timed break") and radio).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Break duration").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5 seconds"))
        assertEquals(false, settings.loop)
    }

    @Test
    fun recoveryActionsHaveLargeTargetsAndCountdownIsNotALiveRegion() {
        showPlayer(PlaybackState(playing = true, phase = PracticePhase.RECOVERY, recoverySeconds = 5))
        for (text in listOf("Continue", "Wait for me", "Stop")) {
            assertTouchTarget(compose.onNodeWithText(text).assertHasClickAction())
        }
        compose.onNodeWithText("Rest: 5 seconds remaining").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        compose.onNode(hasText("Rest") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        compose.onAllNodesWithText("Reviewed practice phrase").assertCountEquals(0)
    }

    @Test
    fun tempoAndLevelsAnnounceTheirValues() {
        showPlayer()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "60 beats per minute"))
            .assertExists()
        openSettings()
        compose.onNodeWithContentDescription("Harmonium volume").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "75 percent"))
    }

    @Test
    fun countInIsShown() {
        showPlayer(PlaybackState(playing = true, countingIn = true, beatInAvarta = 2))
        compose.onNodeWithText("Count-in…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Adi tala: clap 1 2 3, clap wave, clap wave")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Beat 3 of 8"))
        compose.onNodeWithText("Stop").assertHasClickAction()
    }

    @Test
    fun secondSpeedReadsOneTalaCycleWithItsAngas() {
        settings = settings.copy(kala = 2)
        showPlayer(PlaybackState(playing = true, beatInAvarta = 4))
        compose.onNodeWithContentDescription("Adi tala: 2 swaras to a beat, clap 1 2 3, clap wave, clap wave")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Beat 5 of 8"))
        // Two written avartas make one cycle; the commas fall at the beats' angas.
        compose.onNodeWithContentDescription("Avarta 1: S R G M P hold S R, S R G M, P D N S′").assertIsDisplayed()
        compose.onNodeWithContentDescription("Avarta 2: S′ N D P M hold S′ N, S′ N D P, M G R S").assertExists()
    }

    @Test
    fun longTalasWrapButReadAsOneTalaAndOneAvarta() {
        showPlayer(PlaybackState(playing = true, beatInAvarta = 11), id = "alankaram-1")
        compose.onNodeWithText("Mayamalavagowla · Dhruva tala").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dhruva tala: clap 1 2 3, clap wave, clap 1 2 3, clap 1 2 3")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Beat 12 of 14"))
        // The avarta wraps onto two rows but is still one sentence.
        compose.onNodeWithContentDescription("Avarta 1: S R G M, G R, S R G R, S R G M").assertIsDisplayed()
        compose.onAllNodesWithText("x").assertCountEquals(0)
    }

    @Test
    fun gitamSectionsAreHeadingsAndAvartasReadTheirWords() {
        showPlayer(id = "gitam-1")
        compose.onNodeWithText("Malahari · Rupaka tala").assertIsDisplayed()
        compose.onNode(hasText("Charanam 1") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Avarta 1: M P, D S′ S′ R′. Words: sri ga na na tha").assertIsDisplayed()
        // The syllables are summarised in the avarta's sentence, not read one by one.
        compose.onAllNodesWithText("tha").assertCountEquals(0)
    }

    @Test
    fun aSaTooHighForThePieceIsExplainedWithAPlayableOne() {
        settings = settings.copy(saMidi = 60)
        showPlayer(id = "gitam-5")
        compose.onNodeWithText("this piece goes higher than the harmonium can play", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsNotEnabled()
        compose.onNodeWithText("Use Sa", substring = true).assertHasClickAction().performClick()
        assertTrue(settings.saMidi in VarisaiCatalog.byId("gitam-5")!!.playableSaMidis)
    }

    @Test
    fun singAlongWithAnOutOfRangeSaOffersAPlayableOneInsteadOfCrashing() {
        // The sing-along panel used to build a schedule the samples can't play (issue #9).
        settings = settings.copy(saMidi = 60, singAlong = true)
        showPlayer(id = "gitam-5")
        compose.onNodeWithText("this piece goes higher than the harmonium can play", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sing along needs a Sa this piece can play", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Use Sa", substring = true).assertHasClickAction().performClick()
        assertTrue(settings.saMidi in VarisaiCatalog.byId("gitam-5")!!.playableSaMidis)
        // The reactive fixture now uses the playable Sa: warnings disappear and the pitch lane can build its schedule.
        compose.onNodeWithText("Sing along needs a Sa this piece can play", substring = true).assertDoesNotExist()
        compose.onNodeWithText("this piece goes higher than the harmonium can play", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Your pitch").assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsEnabled()
    }

    @Test
    fun singAlongWithAnOutOfRangeSaAndNoMicrophoneStaysUsable() {
        // The permission message and the range recovery must both survive together.
        settings = settings.copy(saMidi = 60, singAlong = true)
        showPlayer(id = "gitam-5", singAlong = SingAlongState(micPermitted = false), micDenied = true)
        compose.onNodeWithText("this piece goes higher than the harmonium can play", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Microphone access is off", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Use Sa", substring = true).assertHasClickAction().performClick()
        assertTrue(settings.saMidi in VarisaiCatalog.byId("gitam-5")!!.playableSaMidis)
        // The microphone is still off: the panel keeps explaining that instead of building a schedule.
        compose.onNodeWithText("Microphone access is off", substring = true).assertIsDisplayed()
    }

    @Test
    fun swarajatiAvartasWrapButReadAsOneSentenceWithoutInnerHolds() {
        showPlayer(id = "swarajati-1")
        compose.onNodeWithText("Bilahari · Adi tala").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Avarta 1: S R G P D S′ N D, P D P M G R S, R S low N low D S. Words: ra ra ve nu go pa ba la ra ji ta sad gu na ja ya shi la",
        ).assertIsDisplayed()
    }

    @Test
    fun aSectionCanBePractisedAlone() {
        showPlayer(id = "gitam-1")
        compose.onNodeWithText("Section: Whole piece").assertHasClickAction().performClick()
        // The menu item, not the "Pallavi" heading in the notation.
        compose.onNode(hasText("Pallavi") and hasClickAction()).performClick()
        assertEquals("Pallavi", sectionPicked)
    }

    @Test
    fun exercisesWithoutSectionsDontOfferThem() {
        showPlayer(id = "sarali-4")
        compose.onAllNodesWithText("Section: Whole piece").assertCountEquals(0)
    }

    @Test
    fun gitamNotationChoiceIsInTheSettings() {
        showPlayer(id = "gitam-1")
        openSettings()
        compose.onNodeWithText("Gitam notation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Words").performScrollTo().performClick()
        assertEquals(WordsDisplay.WORDS, settings.words)
    }

    @Test
    fun singAlongToggleIsLabelledWithItsState() {
        showPlayer()
        compose.onNodeWithContentDescription("Sing along")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
            .performClick()
        assertEquals(1, singAlongToggled)
        assertTouchTarget(compose.onNodeWithContentDescription("Sing along"))
    }

    @Test
    fun singAlongPanelDescribesTheLiveReadingAndAnnouncesThePassSummary() {
        settings = settings.copy(singAlong = true)
        val schedule = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, settings.performance())
        val ri = schedule.notes[1]
        val summary = PassSummary(judged = 16, inTune = 12, close = 2, noVoice = 0,
            attention = listOf(Attention(Note(Swara.R1), Tendency.HIGH, 2, 2)))
        showPlayer(
            id = "plain-scale-1",
            singAlong = SingAlongState(
                micPermitted = true,
                live = SwaraMapper(schedule.settings.saHz, schedule.raga, schedule.settings.tuning).read(schedule.settings.saHz * 1.5),
                marks = mapOf(MarkKey(0, 1, 0) to NoteResult.Judged(cents = 30.0, octaveShift = 0, sung = null)),
                lastSummary = 1 to summary,
            ),
        )
        compose.onNodeWithContentDescription("Your pitch")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "P · in tune · +0¢ · G3"))
        compose.onNodeWithText("Pass 1: 14 of 16 notes in tune or close")
            .assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and
            androidx.compose.ui.test.hasAnyDescendant(hasText("Pass 1: 14 of 16 notes in tune or close"))).assertExists()
        compose.onNodeWithText("Needs attention: R₁ high (2 of 2)").assertExists()
        compose.onNodeWithContentDescription("Avarta 1: S R high G M, P D, N S′").assertExists()
        assertEquals(ri.note.swara, Swara.R1)
    }

    @Test
    fun deniedMicExplainsAndOffersSettings() {
        // Sing-along stays off after a refusal; the explanation must still show.
        compose.setContent {
            PurandaraTheme {
                PlayerScreen(
                    varisai = VarisaiCatalog.byId("sarali-4")!!,
                    state = PracticeUiState(settings = settings, samplesLoaded = true, singAlong = SingAlongState(micPermitted = false)),
                    actions = PlayerActions({}, {}, {}),
                    micDenied = true,
                )
            }
        }
        compose.onNodeWithText("Open settings").assertHasClickAction()
    }

    private var tanpuraToggled = 0

    private var setOpened: VarisaiSet? = null

    private fun showLibrary(history: PracticeHistory = PracticeHistory(), tanpuraPlaying: Boolean = false) {
        compose.setContent {
            PurandaraTheme {
                HomeScreen(
                    catalog = VarisaiCatalog.all,
                    state = LibraryState(history = history, saMidi = 48, tanpuraPlaying = tanpuraPlaying, samplesLoaded = true),
                    actions = LibraryActions(onSelect = {}, onToggleTanpura = { tanpuraToggled++ }, onOpenSet = { setOpened = it }),
                    nowMillis = NOW,
                )
            }
        }
    }

    private fun showSet(set: VarisaiSet, history: PracticeHistory = PracticeHistory()) {
        compose.setContent {
            PurandaraTheme {
                SetScreen(
                    set = set,
                    catalog = VarisaiCatalog.all,
                    state = LibraryState(history = history, saMidi = 48, samplesLoaded = true),
                    actions = LibraryActions(onSelect = {}),
                )
            }
        }
    }

    @Test
    fun homeGroupsAreHeadingsAndSetsOpenTheirPages() {
        showLibrary(PracticeHistory().practised("sarali-5", NOW - DAY))
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Your Sa (Shruthi)") and heading).assertExists()
        compose.onNode(hasText("Continue") and heading).assertExists()
        val group = hasText("Varisais") and heading
        compose.onNode(hasScrollAction()).performScrollToNode(group)
        compose.onNode(group).assertExists()
        val row = hasContentDescription("Sarali Varisai, 1 of 16 practised")
        compose.onNode(hasScrollAction()).performScrollToNode(row)
        assertTouchTarget(compose.onNode(row).assertHasClickAction())
        compose.onNode(row).performClick()
        assertEquals(VarisaiSet.SARALI, setOpened)
        val songs = hasContentDescription("Varnam", substring = true) and hasClickAction()
        compose.onNode(hasScrollAction()).performScrollToNode(songs)
        compose.onNode(songs).assertExists()
    }

    @Test
    fun aNewStudentStartsHere() {
        showLibrary()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Start here") and heading).assertExists()
    }

    @Test
    fun setPageTitleIsAHeadingWithTheFullTitleBelow() {
        showSet(VarisaiSet.MADHYA_STHAYI)
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Madhya Sthayi") and heading).assertExists()
        compose.onNodeWithText("Madhya Sthayi Varisai").assertExists() // the full title below the short one
    }

    @Test
    fun setPageShowsItsProgress() {
        showSet(VarisaiSet.SARALI, PracticeHistory().practised("sarali-5", NOW - DAY))
        compose.onNodeWithText("1 of 16 practised").assertExists()
    }

    @Test
    fun continueCardOffersTheLastAndNextExercises() {
        val history = PracticeHistory().practised("sarali-5", NOW - DAY)
        showLibrary(history)
        assertTouchTarget(
            compose.onNodeWithContentDescription("Continue Sarali Varisai 5", substring = true).assertHasClickAction(),
        )
        assertTouchTarget(compose.onNodeWithContentDescription("Next up: Sarali Varisai 6").assertHasClickAction())
    }

    @Test
    fun exerciseRowsReadTheirNotationAndProgress() {
        showSet(VarisaiSet.SARALI, PracticeHistory().practised("sarali-4", NOW))
        val row = hasContentDescription("Sarali Varisai 4, S R G M P , S R, Practised")
        // Off screen in the lazy list: scroll the list to it.
        compose.onNode(hasScrollAction()).performScrollToNode(row)
        compose.onNode(row).assertHasClickAction()
    }

    @Test
    fun tanpuraButtonIsLabelledAndToggles() {
        showLibrary()
        val button = compose.onNodeWithText("Play tanpura").assertHasClickAction()
        assertTouchTarget(button)
        button.performClick()
        assertEquals(1, tanpuraToggled)
    }

    @Test
    fun strictnessAndFineTuneAreDescribed() {
        showPlayer()
        openSettings()
        compose.onNodeWithContentDescription("Fine tune").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 cents"))
        compose.onNodeWithText("Precise").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Gentle").performScrollTo().performClick()
        assertEquals(Strictness.GENTLE, settings.strictness)
    }

    private fun showShruti(shruti: ShrutiUiState, micPermitted: Boolean = true) {
        compose.setContent {
            PurandaraTheme {
                ShrutiScreen(
                    PracticeUiState(settings = settings, samplesLoaded = true, singAlong = SingAlongState(micPermitted = micPermitted), shruti = shruti),
                    ShrutiActions(onBack = {}, onHoldChange = { _, _ -> }, onAllowMic = {}),
                )
            }
        }
    }

    @Test
    fun shrutiPracticeReadsThePitchAndAnnouncesAReachedHold() {
        val sa = settings.saHz
        val reading = SwaraMapper(sa, VarisaiCatalog.raga, settings.tuning).read(sa)
        val hold = HoldState(HoldTarget.SA, HoldGoal.TEN, currentSeconds = 10.4, bestSeconds = 10.4, onTarget = true, reached = true)
        showShruti(ShrutiUiState(open = true, reading = reading, hold = hold))
        compose.onNodeWithContentDescription("Your pitch").assertExists()
        assertTouchTarget(compose.onNodeWithContentDescription("Back").assertHasClickAction())
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Hold a note") and heading).performScrollTo()
        compose.onNodeWithText("Upper Sa").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("30 s").performScrollTo().assertHasClickAction()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and hasText("Goal reached: Sa held for 10 seconds"))
            .performScrollTo().assertExists()
    }

    @Test
    fun shrutiPracticeWithoutTheMicOffersToAllowIt() {
        showShruti(ShrutiUiState(open = true), micPermitted = false)
        assertTouchTarget(compose.onNodeWithText("Allow microphone").assertHasClickAction())
    }

    @Test
    fun findMySaShowsTheSuggestionWithActions() {
        compose.setContent {
            PurandaraTheme {
                FindSaScreen(
                    FindSaProgress(
                        step = FindSaStep.DONE,
                        lowestHz = Pitch.midiToHz(41),
                        highestHz = Pitch.midiToHz(67),
                        suggestion = SaSuggestion(50, narrowRange = false),
                    ),
                    micPermitted = true,
                    actions = FindSaActions(onBack = {}, onUse = {}, onRetry = {}, onAllowMic = {}),
                )
            }
        }
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Suggested Sa: D3 · 2 kattai") and heading).assertExists()
        compose.onNodeWithText("Lowest F2 · Highest G4").assertExists()
        assertTouchTarget(compose.onNodeWithText("Use this").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithText("Try again").assertHasClickAction())
    }

    @Test
    fun stoppedShrutiAnnouncesTheChangeAndRestartsOnlyFromItsButton() {
        val stopped = mutableStateOf(true)
        var starts = 0
        compose.setContent {
            PurandaraTheme {
                ShrutiScreen(
                    PracticeUiState(
                        settings = settings, outputChanged = stopped.value,
                        singAlong = SingAlongState(micPermitted = true),
                        shruti = ShrutiUiState(open = true, target = HoldTarget.PA, goal = HoldGoal.THIRTY),
                    ),
                    ShrutiActions({}, { _, _ -> }, {}, onStartAgain = { starts++; stopped.value = false }),
                )
            }
        }
        compose.onNodeWithText("Audio output changed. Start again when ready.")
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onAllNodesWithText("Not hearing you yet").assertCountEquals(0)
        val button = compose.onNodeWithText("Start again").assertHasClickAction()
        assertTouchTarget(button)
        assertEquals(0, starts)
        button.performClick()
        assertEquals(1, starts)
        compose.onAllNodesWithText("Audio output changed. Start again when ready.").assertCountEquals(0)
        compose.onNodeWithContentDescription("Your pitch").assertExists()
    }

    @Test
    fun stoppedFindSaOffersAnAccessibleExplicitRestartInsteadOfLivePrompts() {
        val stopped = mutableStateOf(true)
        var starts = 0
        compose.setContent {
            PurandaraTheme {
                FindSaScreen(
                    FindSaProgress(), micPermitted = true,
                    actions = FindSaActions({}, {}, { starts++; stopped.value = false }, {}),
                    outputChanged = stopped.value,
                )
            }
        }
        compose.onNodeWithText("Audio output changed. Start again when ready.")
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onAllNodesWithText("Sing your lowest comfortable note and hold it").assertCountEquals(0)
        val button = compose.onNodeWithText("Start again").assertHasClickAction()
        assertTouchTarget(button)
        assertEquals(0, starts)
        button.performClick()
        assertEquals(1, starts)
        compose.onNodeWithText("Sing your lowest comfortable note and hold it").assertIsDisplayed()
    }

    @Test
    fun completedFindSaKeepsItsSuggestionAfterOutputChanges() {
        compose.setContent {
            PurandaraTheme {
                FindSaScreen(
                    FindSaProgress(step = FindSaStep.DONE, suggestion = SaSuggestion(50, narrowRange = false)),
                    micPermitted = false, actions = FindSaActions({}, {}, {}, {}), outputChanged = true,
                )
            }
        }
        compose.onAllNodesWithText("Start again").assertCountEquals(0)
        compose.onNodeWithText("Suggested Sa: D3 · 2 kattai").assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithText("Use this").assertHasClickAction())
        assertTouchTarget(compose.onNodeWithText("Try again").assertHasClickAction())
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
