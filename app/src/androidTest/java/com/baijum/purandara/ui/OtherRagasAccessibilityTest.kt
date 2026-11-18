package com.baijum.purandara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.varisai.NotationLayout
import com.baijum.purandara.domain.varisai.SwaraNumbers
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.ui.library.HomeScreen
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.library.SetScreen
import com.baijum.purandara.ui.player.NotationView
import com.baijum.purandara.ui.theme.PurandaraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Choosing the raga for the varisais (docs/plan-other-ragas.md) with TalkBack. */
@RunWith(AndroidJUnit4::class)
class OtherRagasAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var raga: Raga by mutableStateOf(VarisaiCatalog.raga)

    private var previewKey: String? by mutableStateOf(null)

    private fun showHome() {
        compose.setContent {
            PurandaraTheme {
                HomeScreen(
                    VarisaiCatalog.inRaga(raga),
                    LibraryState(saMidi = 48, samplesLoaded = true, raga = raga, previewRagaKey = previewKey),
                    LibraryActions(
                        onSelect = {},
                        onRagaChange = { raga = it },
                        onPreviewRaga = { previewKey = if (previewKey == it.key) null else it.key },
                        onStopPreview = { previewKey = null },
                    ),
                )
            }
        }
    }

    private fun openList() {
        val field = compose.onNodeWithContentDescription("Raga for the varisais, ${raga.name}")
        field.performScrollTo()
        assertTouchTarget(field.assertHasClickAction())
        field.performClick()
        compose.waitForIdle()
    }

    @Test
    fun theRagaListReadsEachScaleAndChoosingOneChangesTheVarisais() {
        showHome()
        compose.onNodeWithText("Mayamalavagowla · Adi tala").assertExists()
        openList()
        compose.onNode(hasText("Raga for the varisais") and isHeading()).assertExists()
        compose.onNode(
            hasContentDescription("Mayamalavagowla, The usual raga for the varisais: Sa, Ri 1, Ga 3, Ma 1, Pa, Dha 1, Ni 3, high Sa"),
        ).assertIsSelected()
        val kalyani = compose.onNode(
            hasClickAction() and hasContentDescription("Kalyani, Melakarta 65: Sa, Ri 2, Ga 3, Ma 2, Pa, Dha 2, Ni 3, high Sa"),
        )
        kalyani.assertIsNotSelected()
        kalyani.performScrollTo() // fifteen ragas: Kalyani is last
        assertTouchTarget(kalyani)
        kalyani.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Kalyani · Adi tala").assertExists()
    }

    @Test
    fun eachRagaCanBeHeardFirstAndClosingTheListStopsIt() {
        showHome()
        openList()
        val play = compose.onNodeWithContentDescription("Play Keeravani")
        play.performScrollTo()
        assertTouchTarget(play.assertHasClickAction())
        play.performClick()
        compose.waitForIdle()
        assertEquals("keeravani", previewKey)
        compose.onNodeWithContentDescription("Stop Keeravani").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.waitForIdle()
        assertEquals(null, previewKey)
        assertEquals(VarisaiCatalog.raga, raga)
    }

    @Test
    fun setPagesNameTheRagaOnlyWhereItApplies() {
        raga = Raga.KHARAHARAPRIYA
        var set by mutableStateOf(VarisaiSet.SARALI)
        compose.setContent {
            PurandaraTheme {
                SetScreen(set, VarisaiCatalog.inRaga(raga), LibraryState(saMidi = 48, samplesLoaded = true, raga = raga), LibraryActions(onSelect = {}))
            }
        }
        compose.onNodeWithText("In raga Kharaharapriya").assertExists()
        set = VarisaiSet.GITAM
        compose.waitForIdle()
        compose.onNodeWithText("In raga Kharaharapriya").assertDoesNotExist()
    }

    @Test
    fun aSetPageShowsThePracticeInOtherRagas() {
        val history = PracticeHistory().practised("sarali-1", 1).practised("sarali-2@kalyani", 2)
        compose.setContent {
            PurandaraTheme {
                SetScreen(
                    VarisaiSet.SARALI,
                    VarisaiCatalog.inRaga(Raga.KALYANI),
                    LibraryState(history = history, saMidi = 48, samplesLoaded = true, raga = Raga.KALYANI),
                    LibraryActions(onSelect = {}),
                )
            }
        }
        compose.onNodeWithText("1 of 16 practised").assertExists()
        compose.onNodeWithText("In other ragas: Mayamalavagowla 1 of 16").assertExists()
    }

    @Test
    fun withSwaraNumbersTheNotationReadsThem() {
        val sarali = VarisaiCatalog.byId("sarali-1")!!.inRaga(Raga.KHARAHARAPRIYA)
        compose.setContent {
            PurandaraTheme {
                NotationView(NotationLayout.of(sarali, 1), null, numbersIn = SwaraNumbers.OTHER_RAGAS.ragaFor(sarali))
            }
        }
        compose.onNode(hasContentDescription("S R2 S R2, S R2, G2 M1", substring = true)).assertExists()
    }

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minPx = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("touch target ${bounds.width} x ${bounds.height} px < 48 dp", bounds.width >= minPx && bounds.height >= minPx)
    }
}
