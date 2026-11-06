package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwaraMapperTest {
    private val sa = Pitch.midiToHz(48) // C3
    private val just = SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
    private val equal = SwaraMapper(sa, Raga.MAYAMALAVAGOWLA, Tuning.EQUAL)

    @Test
    fun namesRagaSwarasWithOctaveAndCents() {
        val pa = assertNotNull(just.read(sa * 1.5))
        assertEquals(Swara.P, pa.swara)
        assertEquals(0, pa.octave)
        assertEquals(0.0, pa.centsOff, 1e-9)
        assertTrue(pa.inRaga)
        assertEquals(Zone.IN_TUNE, pa.zone)

        val ga = assertNotNull(just.read(Pitch.midiToHz(52))) // keyboard E3 vs pure major third
        assertEquals(Swara.G3, ga.swara)
        assertEquals(13.69, ga.centsOff, 0.01)
        assertEquals(Zone.CLOSE, ga.zone)
        assertEquals("E3", ga.westernName)
        assertEquals(0.0, assertNotNull(equal.read(Pitch.midiToHz(52))).centsOff, 1e-9)
    }

    @Test
    fun octavesAndNotesOutsideTheRaga() {
        val mandraNi = assertNotNull(just.read(sa * 15 / 16))
        assertEquals(Swara.N3, mandraNi.swara)
        assertEquals(-1, mandraNi.octave)
        val taraSa = assertNotNull(just.read(sa * 2))
        assertEquals(Swara.S, taraSa.swara)
        assertEquals(1, taraSa.octave)
        val ri2 = assertNotNull(just.read(sa * 9 / 8)) // R2: not in Mayamalavagowla
        assertEquals(Swara.R2, ri2.swara)
        assertFalse(ri2.inRaga)
        assertEquals(0.0, ri2.centsOff, 1e-9)
    }

    @Test
    fun zonesFollowThePlan() {
        assertEquals(Zone.IN_TUNE, Zone.of(10.0))
        assertEquals(Zone.CLOSE, Zone.of(-25.0))
        assertEquals(Zone.OFF, Zone.of(25.1))
        assertNull(just.read(0.0))
        assertNull(just.read(Double.NaN))
    }
}

class SingAlongClockTest {
    @Test
    fun accountsForLatencyCountInAndPasses() {
        val clock = SingAlongClock(countInSeconds = 8.0, passSeconds = 16.0, loop = true, outputLatencySeconds = 0.1, inputLatencySeconds = 0.05)
        assertEquals(1.85, clock.heardSeconds(2.0), 1e-9)
        assertTrue(clock.inCountIn(0.2))
        assertNull(clock.locate(8.1)) // heard 7.95: still count-in
        val p = clock.locate(9.0)!!
        assertEquals(0, p.pass)
        assertEquals(0.85, p.secondsInPass, 1e-9)
        assertEquals(1, clock.locate(8.15 + 16.0 + 1.0)!!.pass)
    }

    @Test
    fun withoutLoopOnlyPassZero() {
        val clock = SingAlongClock(0.0, 16.0, loop = false, outputLatencySeconds = 0.0, inputLatencySeconds = 0.0)
        assertEquals(0, clock.locate(15.9)!!.pass)
        assertNull(clock.locate(16.1))
        assertNull(clock.locate(-1.0))
    }
}

class BleedModelTest {
    @Test
    fun leakageMeasuredInTheCountInScalesToTheHarmonium() {
        val bleed = BleedModel()
        repeat(100) { bleed.calibrate(micRms = 0.02f, outputRms = 0.2f) } // drone at the mic: ratio 0.1
        bleed.finishCalibration()
        assertEquals(0.1, bleed.leakageRatio, 1e-6)
        // The louder harmonium leaks proportionally more, and is still not a voice.
        assertFalse(bleed.isVoice(micRms = 0.05f, outputRms = 0.5f))
        // A voice well above the expected leakage is.
        assertTrue(bleed.isVoice(micRms = 0.15f, outputRms = 0.5f))
    }

    @Test
    fun noLeakageMeansAnyAudibleSoundIsVoice() {
        val bleed = BleedModel()
        repeat(50) { bleed.calibrate(0.0f, 0.2f) }
        bleed.finishCalibration()
        assertTrue(bleed.isVoice(0.02f, 0.5f))
        assertFalse(bleed.isVoice(0.005f, 0.5f)) // below the absolute floor
    }
}
