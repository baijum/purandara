package com.baijum.purandara.shruti

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.VoiceRange
import com.baijum.purandara.singalong.OutputLevels
import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.Strictness
import com.baijum.purandara.singalong.SwaraMapper
import com.baijum.purandara.singalong.Zone
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrictnessTest {
    @Test
    fun zonesWidenFromPreciseToGentle() {
        assertEquals(Zone.OFF, Zone.of(30.0, Strictness.PRECISE))
        assertEquals(Zone.CLOSE, Zone.of(30.0, Strictness.STEADY))
        assertEquals(Zone.CLOSE, Zone.of(30.0, Strictness.GENTLE))
        assertEquals(Zone.CLOSE, Zone.of(18.0, Strictness.STEADY))
        assertEquals(Zone.IN_TUNE, Zone.of(18.0, Strictness.GENTLE))
        assertEquals(Zone.of(12.0), Zone.of(12.0, Strictness.PRECISE), "Precise is the default")
    }

    @Test
    fun readingsCarryTheMappersStrictness() {
        val sa = 130.81
        val sharpPa = sa * 1.5 * 2.0.pow(30 / 1200.0)
        assertEquals(Zone.OFF, SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST).read(sharpPa)!!.zone)
        assertEquals(Zone.CLOSE, SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST, Strictness.GENTLE).read(sharpPa)!!.zone)
    }
}

class HoldTrackerTest {
    private val sa = 130.81
    private val mapper = SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
    private fun at(cents: Double, ratio: Double = 1.0) = mapper.read(sa * ratio * 2.0.pow(cents / 1200.0))

    @Test
    fun holdGrowsWhileOnTargetAndReachesTheGoal() {
        val t = HoldTracker(HoldTarget.SA, HoldGoal.SHORT)
        var state = t.state
        for (i in 0..1050) state = t.onFrame(i * 0.01, at(5.0)) // 10.5 s on Sa
        assertTrue(state.reached)
        assertEquals(10.5, state.currentSeconds, 0.02)
        assertTrue(state.onTarget)
    }

    @Test
    fun shortBreaksAreForgivenLongOnesEndTheHold() {
        val t = HoldTracker(HoldTarget.SA, HoldGoal.SHORT)
        for (i in 0..200) t.onFrame(i * 0.01, at(0.0)) // 2 s
        for (i in 201..220) t.onFrame(i * 0.01, null) // 0.2 s breath
        for (i in 221..400) t.onFrame(i * 0.01, at(0.0))
        assertEquals(4.0, t.state.currentSeconds, 0.02, "one hold through the breath")
        for (i in 401..450) t.onFrame(i * 0.01, null) // 0.5 s silence
        assertEquals(0.0, t.state.currentSeconds)
        assertEquals(4.0, t.state.bestSeconds, 0.02)
        assertFalse(t.state.reached)
    }

    @Test
    fun wrongSwaraWrongOctaveOrOffPitchIsNotOnTarget() {
        val t = HoldTracker(HoldTarget.SA, HoldGoal.SHORT)
        assertFalse(t.onFrame(0.0, at(0.0, ratio = 1.5)).onTarget, "Pa is not Sa")
        assertFalse(t.onFrame(0.01, at(0.0, ratio = 2.0)).onTarget, "upper Sa is not Sa")
        assertFalse(t.onFrame(0.02, at(40.0)).onTarget, "40 cents sharp is off at Precise")
        assertTrue(HoldTracker(HoldTarget.UPPER_SA, HoldGoal.SHORT).onFrame(0.0, at(0.0, ratio = 2.0)).onTarget)
        assertTrue(HoldTracker(HoldTarget.PA, HoldGoal.SHORT).onFrame(0.0, at(-20.0, ratio = 1.5)).onTarget)
    }
}

class ShrutiSessionTest {
    private val sa = 130.81
    private val sr = 44_100

    private fun session(levels: OutputLevels) = ShrutiSession(sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST, sampleRate = sr, outputLevels = levels)

    @Test
    fun roomCheckFirstThenVoiceAboveTheLeakageIsRead() {
        val levels = OutputLevels()
        levels.record(0, 0.1f) // the tanpura, steady
        val s = session(levels)
        // Quiet room with a little tanpura leakage (0.005 at the mic).
        for (i in 0 until 190) assertTrue(s.onFrame(PitchFrame(i * 0.01, sa, 0.005f)).listeningToRoom)
        // After the check: the tanpura alone is not a voice ...
        assertNull(s.onFrame(PitchFrame(2.5, sa, 0.005f)).reading)
        // ... the student singing Pa is.
        val outcome = s.onFrame(PitchFrame(2.6, sa * 1.5, 0.1f))
        assertFalse(outcome.listeningToRoom)
        assertEquals("P", outcome.reading?.swara?.symbol)
    }

    @Test
    fun holdPracticeFollowsTheReadings() {
        val levels = OutputLevels()
        levels.record(0, 0.1f)
        val s = session(levels)
        s.startHold(HoldTarget.SA, HoldGoal.SHORT)
        for (i in 0 until 200) s.onFrame(PitchFrame(i * 0.01, null, 0.004f))
        var last = s.hold
        for (i in 200..1300) last = s.onFrame(PitchFrame(i * 0.01, sa, 0.1f)).hold
        assertNotNull(last)
        assertTrue(last.reached)
        s.stopHold()
        assertNull(s.hold)
    }
}

class SaFinderTest {
    private fun hz(midi: Int) = Pitch.midiToHz(midi)

    @Test
    fun aTypicalMaleRangeGetsAboutOneKattai() {
        // Lowest F2 (41), highest G4 (67): Sa between 46 and 53 → 50 (D3).
        val s = SaFinder.suggest(hz(41), hz(67))
        assertEquals(50, s.saMidi)
        assertFalse(s.narrowRange)
    }

    @Test
    fun aNarrowRangeGetsACompromiseAndSaysSo() {
        val s = SaFinder.suggest(hz(50), hz(62))
        assertTrue(s.narrowRange)
        assertTrue(s.saMidi in Shruti.SA_RANGE)
    }

    @Test
    fun suggestionsStayInTheSaRange() {
        assertEquals(Shruti.SA_RANGE.first, SaFinder.suggest(hz(20), hz(40)).saMidi)
        assertEquals(Shruti.SA_RANGE.last, SaFinder.suggest(hz(80), hz(100)).saMidi)
    }

    @Test
    fun noteCatcherNeedsASteadyHold() {
        val c = NoteCatcher()
        var caught: Double? = null
        // Wavering ±200 cents never settles.
        for (i in 0 until 300) caught = c.onFrame(i * 0.01, hz(48) * if (i % 20 < 10) 1.12 else 0.89) ?: caught
        assertNull(caught)
        c.clear()
        for (i in 0..160) caught = c.onFrame(i * 0.01, hz(55) * (1 + 0.003 * (i % 3))) ?: caught
        assertNotNull(caught)
        assertEquals(55, Pitch.nearestMidi(caught))
    }
}

class FineTuneAndVoiceRangeTest {
    @Test
    fun fineTuneShiftsSaByCents() {
        assertEquals(12.0, Pitch.cents(Shruti.saHz(48), Shruti.saHz(48, 12)), 1e-9)
        assertEquals(50.0, Pitch.cents(Shruti.saHz(48), Shruti.saHz(48, 80)), 1e-9, "clamped to ±50")
        assertEquals(" +12¢", Shruti.fineTuneSuffix(12))
        assertEquals(" −8¢", Shruti.fineTuneSuffix(-8))
        assertEquals("", Shruti.fineTuneSuffix(0))
    }

    @Test
    fun everySaChoiceHasAVoiceRange() {
        for (midi in Shruti.SA_RANGE) Shruti.voiceRange(midi)
        assertEquals(VoiceRange.MEN, Shruti.voiceRange(48))
        assertEquals(VoiceRange.WOMEN, Shruti.voiceRange(55))
    }
}
