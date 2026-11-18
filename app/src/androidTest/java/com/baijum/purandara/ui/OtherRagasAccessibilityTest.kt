package com.baijum.purandara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.ui.library.HomeScreen
import com.baijum.purandara.ui.library.LibraryActions
import com.baijum.purandara.ui.library.LibraryState
import com.baijum.purandara.ui.library.SetScreen
import com.baijum.purandara.ui.theme.PurandaraTheme
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

    private fun showHome() {
        compose.setContent {
            PurandaraTheme {
                HomeScreen(
                    VarisaiCatalog.inRaga(raga),
                    LibraryState(saMidi = 48, samplesLoaded = true, raga = raga),
                    LibraryActions(onSelect = {}, onRagaChange = { raga = it }),
                )
            }
        }
    }

    @Test
    fun theRagaListReadsEachScaleAndChoosingOneChangesTheVarisais() {
        showHome()
        compose.onNodeWithText("Mayamalavagowla · Adi tala").assertExists()
        val field = compose.onNode(hasText("Raga for the varisais"))
        field.performScrollTo().performClick()
        compose.waitForIdle()
        val usual = compose.onNode(
            hasContentDescription("Mayamalavagowla, The usual raga for the varisais: Sa, Ri 1, Ga 3, Ma 1, Pa, Dha 1, Ni 3, high Sa"),
            useUnmergedTree = true,
        )
        usual.assertExists()
        val kalyani = compose.onNode(
            hasClickAction() and hasContentDescription("Kalyani, Melakarta 65: Sa, Ri 2, Ga 3, Ma 2, Pa, Dha 2, Ni 3, high Sa"),
        )
        assertTouchTarget(kalyani.assertHasClickAction())
        kalyani.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Kalyani · Adi tala").assertExists()
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

    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minPx = with(compose.density) { 48.dp.toPx() } - 1f
        assertTrue("touch target ${bounds.width} x ${bounds.height} px < 48 dp", bounds.width >= minPx && bounds.height >= minPx)
    }
}
