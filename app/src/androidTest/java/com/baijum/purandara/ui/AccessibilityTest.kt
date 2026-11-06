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
                live = SwaraMapper(schedule.settings.saHz, schedule.settings.raga, schedule.settings.tuning).read(schedule.settings.saHz * 1.5),
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
        compose.onNode(hasText("Your Sa") and heading).assertExists()
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

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
