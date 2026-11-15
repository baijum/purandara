package com.baijum.purandara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.navigation.NavDestination
import com.baijum.purandara.ui.about.AboutScreen
import com.baijum.purandara.ui.library.HomeScreen
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.navigation.MenuScaffold
import com.baijum.purandara.ui.theme.PurandaraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The navigation menu (docs/plan-navigation.md) for TalkBack users: the menu button, headings, items, folding. */
@RunWith(AndroidJUnit4::class)
class NavigationAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var current: NavDestination by mutableStateOf(NavDestination.Home)

    private fun showMenu() {
        compose.setContent {
            PurandaraTheme {
                MenuScaffold(current, onNavigate = { current = it }) {
                    HomeScreen(VarisaiCatalog.all, LibraryState(saMidi = 48, samplesLoaded = true), LibraryActions(onSelect = {}))
                }
            }
        }
    }

    private fun openMenu() {
        val button = compose.onNodeWithContentDescription("Open menu").assertHasClickAction()
        assertTouchTarget(button)
        button.performClick()
        compose.waitForIdle()
    }

    private val inMenu = hasAnyAncestor(hasContentDescription("Navigation menu"))
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun homeShowsAMenuButtonInsteadOfBack() {
        showMenu()
        compose.onNodeWithContentDescription("Open menu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun menuHasHeadingsAndTheCurrentDestinationIsSelected() {
        showMenu()
        openMenu()
        compose.onNode(hasText("Purandara") and heading and inMenu).assertExists()
        compose.onNode(hasText("Varisais") and heading and inMenu).assertExists()
        compose.onNode(hasText("Alankaram & songs") and heading and inMenu).performScrollTo().assertExists()
        compose.onNode(hasText("Sa & voice") and heading and inMenu).performScrollTo().assertExists()
        compose.onNode(hasText("Home") and inMenu).assertIsSelected()
    }

    @Test
    fun everyDestinationIsALargeEnoughItem() {
        showMenu()
        openMenu()
        for (label in listOf("Home", "Plain scale", "Sarali Varisai", "Varnam", "Shruti practice", "Find my Sa", "Recordings", "About")) {
            val item = compose.onNode(hasText(label) and inMenu, useUnmergedTree = false).performScrollTo()
            item.assertHasClickAction()
            assertTouchTarget(item)
        }
    }

    @Test
    fun choosingAnItemNavigatesThere() {
        showMenu()
        openMenu()
        compose.onNode(hasText("Janta Varisai") and inMenu).performScrollTo().performClick()
        assertEquals(NavDestination.SetPage(VarisaiSet.JANTA), current)
        openMenu()
        compose.onNode(hasText("Find my Sa") and inMenu).performScrollTo().performClick()
        assertEquals(NavDestination.FindSa, current)
    }

    @Test
    fun setGroupsFoldWithTheirStateAnnounced() {
        showMenu()
        openMenu()
        val group = compose.onNode(hasText("Varisais") and heading and inMenu)
        group.assertHasClickAction()
        assertTouchTarget(group)
        group.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        group.performClick()
        group.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNode(hasText("Sarali Varisai") and inMenu).assertDoesNotExist()
        group.performClick()
        compose.onNode(hasText("Sarali Varisai") and inMenu).assertExists()
    }

    @Test
    fun aboutHasAHeadingVersionAndTheWebsite() {
        compose.setContent { PurandaraTheme { AboutScreen("1.2.3", onBack = {}) } }
        compose.onNode(hasText("About") and heading).assertExists()
        compose.onNodeWithText("Version 1.2.3").assertExists()
        compose.onNode(hasText("Licence") and heading).assertExists()
        val link = compose.onNodeWithText("Open the guide on the website").performScrollTo().assertHasClickAction()
        assertTouchTarget(link)
        link.assert(SemanticsMatcher("click label") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Opens your browser" })
    }

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minPx = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("touch target ${bounds.width} x ${bounds.height} px < 48 dp", bounds.width >= minPx && bounds.height >= minPx)
    }
}
