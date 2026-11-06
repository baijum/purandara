package com.baijum.purandara.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.WordsDisplay
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.theme.PurandaraTheme
import com.baijum.purandara.viewmodel.PlaybackState
import com.baijum.purandara.viewmodel.PracticeUiState
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

    private var settings = PracticeSettings()
    private var singAlongToggled = 0

    private fun showPlayer(
        playback: PlaybackState = PlaybackState(),
        singAlong: SingAlongState = SingAlongState(micPermitted = true),
        id: String = "sarali-4",
    ) {
        compose.setContent {
            PurandaraTheme {
                PlayerScreen(
                    varisai = VarisaiCatalog.byId(id)!!,
                    state = PracticeUiState(settings = settings, samplesLoaded = true, playback = playback, singAlong = singAlong),
                    actions = PlayerActions(
                        onBack = {},
                        onTogglePlay = {},
                        onSettingsChange = { settings = it(settings) },
                        onToggleSingAlong = { singAlongToggled++ },
                    ),
                )
            }
        }
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
        compose.onNodeWithContentDescription("Third kala").performClick()
        assertEquals(3, settings.kala)
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

    private fun showLibrary(history: PracticeHistory = PracticeHistory(), tanpuraPlaying: Boolean = false) {
        compose.setContent {
            PurandaraTheme {
                LibraryScreen(
                    catalog = VarisaiCatalog.all,
                    state = LibraryState(history = history, saMidi = 48, tanpuraPlaying = tanpuraPlaying, samplesLoaded = true),
                    actions = LibraryActions(onSelect = {}, onToggleTanpura = { tanpuraToggled++ }),
                    nowMillis = NOW,
                )
            }
        }
    }

    @Test
    fun librarySetsAreHeadings() {
        showLibrary()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Plain scale", substring = true) and heading).assertExists()
        compose.onNode(hasText("Your Sa (Shruthi)") and heading).assertExists()
        compose.onNode(hasText("Start here") and heading).assertExists()
    }

    @Test
    fun continueCardOffersTheLastAndNextExercises() {
        val history = PracticeHistory().practised("sarali-5", NOW - DAY)
        showLibrary(history)
        assertTouchTarget(
            compose.onNodeWithContentDescription("Continue Sarali Varisai 5", substring = true).assertHasClickAction(),
        )
        assertTouchTarget(compose.onNodeWithContentDescription("Next up: Sarali Varisai 6").assertHasClickAction())
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("1 of 16 practised") and heading).assertExists()
    }

    @Test
    fun exerciseRowsReadTheirNotationAndProgress() {
        showLibrary(PracticeHistory().practised("sarali-4", NOW))
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

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
