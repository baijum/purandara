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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.ui.library.LibraryScreen
import com.baijum.purandara.ui.player.PlayerActions
import com.baijum.purandara.ui.player.PlayerScreen
import com.baijum.purandara.ui.theme.PurandaraTheme
import com.baijum.purandara.viewmodel.PlaybackState
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

    private fun showPlayer(playback: PlaybackState = PlaybackState()) {
        compose.setContent {
            PurandaraTheme {
                PlayerScreen(
                    varisai = VarisaiCatalog.byId("sarali-4")!!,
                    settings = settings,
                    playback = playback,
                    canPlay = true,
                    actions = PlayerActions(
                        onBack = {},
                        onTogglePlay = {},
                        onSettingsChange = { settings = it(settings) },
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
        compose.onNode(hasText("Drone (Sa and Pa)") and isSwitch).performScrollTo().assertIsOn()
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
    fun librarySetsAreHeadings() {
        compose.setContent {
            PurandaraTheme {
                LibraryScreen(VarisaiCatalog.all, samplesLoaded = true, loadError = false, onSelect = {})
            }
        }
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(hasText("Plain scale") and heading).assertExists()
        compose.onNode(hasText("Sarali Varisai") and heading).assertExists()
    }
}
