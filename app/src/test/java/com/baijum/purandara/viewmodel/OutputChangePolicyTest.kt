package com.baijum.purandara.viewmodel

import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.shruti.FindSaProgress
import com.baijum.purandara.shruti.FindSaStep
import com.baijum.purandara.shruti.HoldGoal
import com.baijum.purandara.shruti.HoldState
import com.baijum.purandara.shruti.HoldTarget
import com.baijum.purandara.shruti.SaSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputChangePolicyTest {
    @Test
    fun availabilityDoesNotInterruptAnUnchangedActiveTrack() {
        assertFalse(shouldStopForOutputAvailability(true, true, true, null))
        assertFalse(shouldStopForOutputAvailability(true, true, false, null))
        assertFalse(shouldStopForOutputAvailability(false, false, true, null))
        assertFalse(shouldStopForOutputAvailability(true, false, false, null))
        assertTrue(shouldStopForOutputAvailability(true, false, true, null))
        BackgroundAudio.entries.forEach {
            assertTrue(shouldStopForOutputAvailability(true, false, false, it))
        }
    }

    @Test
    fun changedOutputGatesSettingsPermissionAndBackgroundStartsUntilExplicitStart() {
        val stopped = PracticeUiState().afterOutputChange()
        assertTrue(stopped.outputChanged)
        assertFalse(stopped.canAutoStartAudio(backgrounded = false))
        assertFalse(stopped.canAutoStartAudio(backgrounded = true))
        assertTrue(stopped.copy(outputChanged = false).canAutoStartAudio(backgrounded = false))
        assertFalse(stopped.copy(outputChanged = false).canAutoStartAudio(backgrounded = true))
    }

    @Test
    fun staleFindSaCallbacksCannotRepopulateAStoppedOrReplacementGuide() {
        val open = PracticeUiState(findSa = FindSaUiState(open = true))
        assertTrue(open.acceptsFindSaOutcome(1, 1))
        assertFalse(open.acceptsFindSaOutcome(1, 2))
        assertFalse(open.afterOutputChange().acceptsFindSaOutcome(1, 1))
        assertFalse(PracticeUiState().acceptsFindSaOutcome(1, 1))
    }

    @Test
    fun normalBackgroundResumeStillWorksForEverySoundingDestination() {
        assertEquals(BackgroundAudio.SHRUTI, PracticeUiState(shruti = ShrutiUiState(open = true)).backgroundAudio())
        assertEquals(BackgroundAudio.FIND_SA, PracticeUiState(findSa = FindSaUiState(open = true)).backgroundAudio())
        assertEquals(BackgroundAudio.PLAYBACK, PracticeUiState(playback = PlaybackState(playing = true)).backgroundAudio())
        assertEquals(BackgroundAudio.TANPURA, PracticeUiState(tanpuraPlaying = true).backgroundAudio())
        assertNull(PracticeUiState().backgroundAudio())
        assertNull(PracticeUiState(shruti = ShrutiUiState(open = true)).afterOutputChange().backgroundAudio())
    }

    @Test
    fun shrutiKeepsDestinationSettingsAndGoalButDropsCalibrationAndHold() {
        val before = PracticeUiState(
            settings = PracticeSettings(saMidi = 50),
            selected = VarisaiCatalog.byId("sarali-4"),
            shruti = ShrutiUiState(
                open = true, listeningToRoom = true, target = HoldTarget.PA, goal = HoldGoal.THIRTY,
                hold = HoldState(HoldTarget.PA, HoldGoal.THIRTY, 4.0, 5.0, true, false),
            ),
        )
        val stopped = before.afterOutputChange()
        assertEquals(before.settings, stopped.settings)
        assertEquals(before.selected, stopped.selected)
        assertTrue(stopped.shruti.open)
        assertEquals(HoldTarget.PA, stopped.shruti.target)
        assertEquals(HoldGoal.THIRTY, stopped.shruti.goal)
        assertFalse(stopped.shruti.listeningToRoom)
        assertNull(stopped.shruti.hold)
        assertNull(stopped.shruti.reading)
        assertTrue(stopped.shruti.trace.isEmpty())
    }

    @Test
    fun unfinishedFindSaResetsButACompletedSuggestionSurvivesAndDoesNotBackgroundResume() {
        val unfinished = PracticeUiState(findSa = FindSaUiState(open = true, progress = FindSaProgress(step = FindSaStep.HIGHEST, lowestHz = 98.0)))
        val stopped = unfinished.afterOutputChange()
        assertTrue(stopped.findSa.open)
        assertEquals(FindSaProgress(), stopped.findSa.progress)
        val result = FindSaProgress(step = FindSaStep.DONE, lowestHz = 98.0, highestHz = 392.0, suggestion = SaSuggestion(50, false))
        val complete = unfinished.copy(findSa = FindSaUiState(open = true, progress = result))
        assertEquals(result, complete.afterOutputChange().findSa.progress)
        assertNull(complete.backgroundAudio())
    }
}
