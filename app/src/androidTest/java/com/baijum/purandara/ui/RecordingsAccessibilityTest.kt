package com.baijum.purandara.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.recording.Closeness
import com.baijum.purandara.recording.Insights
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.Scale
import com.baijum.purandara.recording.SwaraInsight
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingLibrary
import com.baijum.purandara.recording.SwaraTranscriber
import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.PitchLane
import com.baijum.purandara.ui.recordings.RagaEditorScreen
import com.baijum.purandara.ui.recordings.RecordScreen
import com.baijum.purandara.ui.recordings.RecordingDetailScreen
import com.baijum.purandara.ui.recordings.RecordingsActions
import com.baijum.purandara.ui.recordings.RecordingsListScreen
import com.baijum.purandara.ui.theme.PurandaraTheme
import com.baijum.purandara.viewmodel.RecorderUi
import com.baijum.purandara.viewmodel.RecordingDetailUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Recordings (docs/plan-recordings.md) for TalkBack users: descriptions, roles, headings, targets. */
@RunWith(AndroidJUnit4::class)
class RecordingsAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened: String? = null
    private var started = 0
    private var seekedTo: Double? = null
    private var tanpura: Boolean? = null
    private var usedSa: Pair<Int, Int>? = null

    private val actions = RecordingsActions(
        onBack = {}, onNew = {}, onOpen = { opened = it }, onStart = { started++ }, onStop = {}, onAllowMic = {},
        onTogglePlay = {}, onSeek = { seekedTo = it }, onRename = { _, _ -> }, onChangeSa = { _, midi, cents -> usedSa = midi to cents },
        onChangeRaga = { _, _ -> }, onDelete = {}, onTanpura = { tanpura = it },
    )

    private val recording = Recording(id = "r1", createdMillis = 0, durationSeconds = 134.0, saMidi = 48, ragaKey = Raga.MAYAMALAVAGOWLA.key)

    /** S, R1, G3 for 0.4 s each, at Sa C3. */
    private fun detail(): RecordingDetailUi {
        val hop = 512.0 / 44_100
        val builder = PitchTrack.Builder(hop, hop / 2)
        listOf(1.0, 16 / 15.0, 5 / 4.0).forEach { ratio ->
            repeat((0.4 / hop).toInt()) { builder.add(PitchFrame(0.0, recording.saHz * ratio, 0.1f)) }
        }
        val track = builder.build()
        val t = SwaraTranscriber.transcribe(track, recording.saHz, recording.raga, Tuning.JUST)
        return RecordingDetailUi(recording, track, t, PitchLane.forRecording(t, recording.raga))
    }

    @Test
    fun listRowsReadTitleAndLength() {
        compose.setContent { PurandaraTheme { RecordingsListScreen(RecordingLibrary().add(recording), actions) } }
        val row = compose.onNodeWithContentDescription("Mayamalavagowla · Sa C", substring = true)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assert(SemanticsMatcher("reads the length") { node ->
            node.config[SemanticsProperties.ContentDescription].single().endsWith("2 minutes, 14 seconds")
        })
        row.performClick()
        assertEquals("r1", opened)
        compose.onNodeWithText("New recording").assertHasClickAction()
    }

    @Test
    fun recordButtonIsLabelledAndLarge() {
        compose.setContent { PurandaraTheme { RecordScreen(RecorderUi(), micAllowed = true, actions = actions) } }
        val button = compose.onNodeWithContentDescription("Start recording")
        button.assertHasClickAction()
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val min = with(compose.density) { 48.dp.toPx() }
        assertTrue(bounds.width >= min && bounds.height >= min)
        button.performClick()
        assertEquals(1, started)
    }

    @Test
    fun theTanpuraSwitchIsAToggleWithItsState() {
        compose.setContent { PurandaraTheme { RecordScreen(RecorderUi(tanpura = false), micAllowed = true, actions = actions) } }
        val toggle = compose.onNode(hasText("External tanpura playing", substring = true) and isToggleable())
        toggle.assertIsOff()
        toggle.performClick()
        assertEquals(true, tanpura)
    }

    @Test
    fun whileListeningTheCueIsAnnouncedAndTheCountdownIsNot() {
        compose.setContent {
            PurandaraTheme { RecordScreen(RecorderUi(recording = true, seconds = 1.2, tanpura = true), micAllowed = true, actions = actions) }
        }
        compose.onNodeWithText("Listening to your tanpura…").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive),
        )
        compose.onAllNodesWithText("2").assertCountEquals(0) // the countdown digit is visual only
    }

    @Test
    fun theTanpuraSaOfferSaysWhatItDoes() {
        compose.setContent {
            PurandaraTheme {
                RecordingDetailScreen(detail().copy(recording = recording.copy(externalTanpura = true), tanpuraSa = 49 to 12), actions)
            }
        }
        compose.onNodeWithContentDescription("Use the tanpura's Sa", substring = true).performScrollTo().performClick()
        assertEquals(49 to 12, usedSa)
    }

    @Test
    fun howItWentReadsEachSwaraAndOffersYourSa() {
        val insights = Insights(
            saOffsetCents = -25.0, suggestedSa = 48 to -25,
            swaras = listOf(
                SwaraInsight(Swara.S, 0, Closeness.IN_TUNE, -3.0, 0.6, null),
                SwaraInsight(Swara.M1, 0, Closeness.A_LITTLE_FLAT, -17.0, 0.6, null),
                SwaraInsight(Swara.P, 0, Closeness.MISSED, null, 0.0, null),
            ),
            extras = emptyList(), tanpuraNotHeard = false, summary = "Ma was a little flat (−17¢); the rest were in tune.",
        )
        compose.setContent { PurandaraTheme { RecordingDetailScreen(detail().copy(insights = insights), actions) } }
        compose.onNodeWithContentDescription("Ma, 17 cents flat").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Pa, not heard").assertExists()
        compose.onNodeWithContentDescription("Use your Sa", substring = true).performScrollTo().performClick()
        assertEquals(48 to -25, usedSa)
    }

    @Test
    fun theScaleChoiceAppearsOnceARagaIsChosen() {
        var chosen: Pair<String?, Scale?>? = null
        val withIntent = actions.copy(onIntent = { raga, scale -> chosen = raga to scale })
        compose.setContent {
            PurandaraTheme { RecordScreen(RecorderUi(ragaKey = Raga.MAYAMALAVAGOWLA.key), micAllowed = true, actions = withIntent) }
        }
        compose.onNodeWithText("Arohana").performClick()
        assertEquals(Raga.MAYAMALAVAGOWLA.key to Scale.AROHANA, chosen)
    }

    @Test
    fun theRagaEditorUsesRadioChipsAndSaveWaitsForTheChecks() {
        var saved: Triple<String, List<Swara>, List<Swara>>? = null
        compose.setContent {
            PurandaraTheme {
                RagaEditorScreen(null, CustomRagas(), onSave = { n, up, down -> saved = Triple(n, up, down) }, onDelete = {}, onClose = {})
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Give the raga a name.").assertExists()
        compose.onNodeWithText("Name").performTextInput("Hindolam")
        listOf("Ga 2", "Ma 1", "Dha 1", "Ni 2").forEach { compose.onNodeWithContentDescription(it).performScrollTo().performClick() }
        compose.onNodeWithContentDescription("Ga 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        compose.onNodeWithContentDescription("Ri: None").assertIsSelected()
        compose.onNodeWithContentDescription("Up: Sa, Ga 2, Ma 1, Dha 1, Ni 2, high Sa").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Down: high Sa, Ni 2, Dha 1, Ma 1, Ga 2, Sa").assertExists()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        val hindolam = listOf(Swara.G2, Swara.M1, Swara.D1, Swara.N2)
        assertEquals(Triple("Hindolam", hindolam, hindolam), saved)
    }

    @Test
    fun anOwnRagaIsListedAndCanBeEdited() {
        val own = CustomRagas().save("own-1", "Hindolam", listOf(Swara.G2, Swara.M1, Swara.D1, Swara.N2), listOf(Swara.G2, Swara.M1, Swara.D1, Swara.N2))
        var edited: String? = "none"
        val withEdit = actions.copy(onEditRaga = { edited = it })
        compose.setContent {
            PurandaraTheme { RecordScreen(RecorderUi(ragaKey = "own-1"), micAllowed = true, actions = withEdit, own = own) }
        }
        compose.onNodeWithText("Hindolam").assertExists()
        compose.onNodeWithContentDescription("Edit Hindolam").assertHasClickAction().performClick()
        assertEquals("own-1", edited)
    }

    @Test
    fun recordScreenAsksForTheMicWhenNotAllowed() {
        compose.setContent { PurandaraTheme { RecordScreen(RecorderUi(), micAllowed = false, actions = actions) } }
        compose.onNodeWithText("Allow microphone").assertHasClickAction()
    }

    @Test
    fun eachPhraseIsReadAsOneElementThatPlaysFromItsStart() {
        compose.setContent { PurandaraTheme { RecordingDetailScreen(detail(), actions) } }
        val phrase = compose.onNodeWithContentDescription("Phrase 1: S, R1, G3")
        phrase.performScrollTo().assertHasClickAction()
        phrase.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(0.0, seekedTo!!, 0.05)
        compose.onNodeWithText("Swaras heard").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Play").assertHasClickAction()
        compose.onNodeWithContentDescription("Position").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00:00 of 02:14"),
        )
    }

    @Test
    fun traceSummarisesTheNotes() {
        compose.setContent { PurandaraTheme { RecordingDetailScreen(detail(), actions) } }
        compose.onNodeWithContentDescription("Your pitch").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 notes in 1 phrase, from S to G3"),
        )
    }
}
