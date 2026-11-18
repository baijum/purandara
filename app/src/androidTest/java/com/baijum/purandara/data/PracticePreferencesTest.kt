package com.baijum.purandara.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.audio.HarmoniumArticulation
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.RunEntry
import com.baijum.purandara.practice.RunPlan
import com.baijum.purandara.practice.RunPlans
import com.baijum.purandara.domain.varisai.SwaraNumbers
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.practice.RecoveryMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticePreferencesTest {
    // Keep the tests separate from the student's saved practice settings.
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("test_articulation_$name", mode)
    }
    private val stored = context.getSharedPreferences("practice", Context.MODE_PRIVATE)
    private val preferences = PracticePreferences(context)

    @Before
    fun clearSettings() {
        stored.edit().clear().commit()
    }

    @After
    fun removeSettings() {
        stored.edit().clear().commit()
    }

    @Test
    fun theRagaForTheVarisaisRoundTripsAndAnUnknownOneIsMayamalavagowla() {
        assertEquals(Raga.MAYAMALAVAGOWLA, preferences.load().raga)
        Ragas.melakartas.forEach { raga ->
            preferences.save(PracticeSettings(ragaKey = raga.key))
            assertEquals(raga, PracticePreferences(context).load().raga)
        }
        for (value in listOf("malahari", "nowhere", "")) {
            stored.edit().putString("varisai_raga", value).commit()
            assertEquals(Raga.MAYAMALAVAGOWLA, preferences.load().raga)
        }
    }

    @Test
    fun swaraNumbersRoundTripAndDefaultToOtherRagas() {
        assertEquals(SwaraNumbers.OTHER_RAGAS, preferences.load().swaraNumbers)
        SwaraNumbers.entries.forEach { numbers ->
            preferences.save(PracticeSettings(swaraNumbers = numbers))
            assertEquals(numbers, PracticePreferences(context).load().swaraNumbers)
        }
        stored.edit().putString("swara_numbers", "future").commit()
        assertEquals(SwaraNumbers.OTHER_RAGAS, preferences.load().swaraNumbers)
    }

    @Test
    fun missingArticulationDefaultsToConnected() {
        assertEquals(HarmoniumArticulation.CONNECTED, PracticeSettings().articulation)
        assertEquals(HarmoniumArticulation.CONNECTED, preferences.load().articulation)
    }

    @Test
    fun articulationRoundTripsByEnumName() {
        HarmoniumArticulation.entries.forEach { articulation ->
            val settings = PracticeSettings(articulation = articulation)
            preferences.save(settings)
            assertEquals(articulation.name, stored.getString("articulation", null))
            assertEquals(settings, PracticePreferences(context).load())
        }
    }

    @Test
    fun unknownArticulationFallsBackToConnected() {
        for (value in listOf("unknown", "lightly_separated", "")) {
            stored.edit().putString("articulation", value).commit()
            assertEquals(HarmoniumArticulation.CONNECTED, preferences.load().articulation)
        }
    }

    @Test
    fun practiceAndRecoveryChoicesRoundTripIndependentlyOfRepeat() {
        for (mode in PracticeMode.entries) for (recovery in RecoveryMode.entries) for (seconds in listOf(1, 5, 30)) {
            val settings = PracticeSettings(practiceMode = mode, recoveryMode = recovery, recoverySeconds = seconds, responseGuide = true, loop = false)
            preferences.save(settings)
            assertEquals(settings, PracticePreferences(context).load())
        }
    }

    @Test
    fun unknownPracticeChoicesAndInvalidSecondsUseCurrentDefaults() {
        val defaults = PracticeSettings()
        for (seconds in listOf(-1, 0, 31, Int.MAX_VALUE)) {
            stored.edit().putString("practice_mode", "future").putString("recovery_mode", "future")
                .putInt("recovery_seconds", seconds).commit()
            assertEquals(defaults, preferences.load())
        }
        assertEquals(PracticeMode.NORMAL, defaults.practiceMode)
        assertEquals(RecoveryMode.CONTINUOUS, defaults.recoveryMode)
        assertEquals(5, defaults.recoverySeconds)
        assertEquals(false, defaults.responseGuide)
    }

    @Test
    fun runPlansRoundTripAndStartEmpty() {
        assertEquals(RunPlans(), preferences.loadRunPlans())
        val plans = RunPlans().with(
            RunPlan(VarisaiSet.SARALI.name, 3, listOf(RunEntry("sarali-1", 2, 3))),
        )
        preferences.saveRunPlans(plans)
        assertEquals(plans, PracticePreferences(context).loadRunPlans())
    }

    @Test
    fun renderOptionsReceiveTheChosenArticulation() {
        HarmoniumArticulation.entries.forEach { articulation ->
            assertEquals(articulation, PracticeSettings(articulation = articulation).renderOptions().articulation)
        }
    }

    @Test
    fun articulationDoesNotEnableASilentGuide() {
        HarmoniumArticulation.entries.forEach { articulation ->
            val options = PracticeSettings(singAlong = true, harmoniumGuide = false, articulation = articulation).renderOptions()
            assertEquals(0f, options.harmoniumGain, 0f)
            assertEquals(articulation, options.articulation)
        }
    }
}
