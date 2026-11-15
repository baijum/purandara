package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.recording.RecordingTestTracks.HOP
import com.baijum.purandara.recording.RecordingTestTracks.SA_HZ
import com.baijum.purandara.recording.RecordingTestTracks.cents
import com.baijum.purandara.recording.RecordingTestTracks.steps
import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.Strictness
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "How it went" (docs/plan-recording-insights.md). */
class RecordingInsightsTest {
    private val arohana = listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3)

    /** Sa C3, as the test tracks. */
    private fun recording(scale: Scale? = Scale.AROHANA, raga: Raga? = Raga.MAYAMALAVAGOWLA, tanpura: Boolean = false) =
        Recording("r", 1, 10.0, saMidi = 48, ragaKey = raga?.key, externalTanpura = tanpura, scale = scale)

    /** The arohana, a note per 0.6 s with a 0.2 s breath, each [offsets] cents off (by swara). */
    private fun sung(offsets: Map<Swara, Double> = emptyMap(), all: Double = 0.0, skip: Set<Swara> = emptySet()): PitchTrack {
        val segments = mutableListOf<Pair<Double?, Double>>(null to 0.3)
        arohana.forEach { s ->
            if (s in skip) return@forEach
            segments += (cents(s) + all + (offsets[s] ?: 0.0)) to 0.6
            segments += null to 0.2
        }
        segments += (cents(Swara.S, 1) + all) to 0.6
        segments += null to 0.3
        return steps(*segments.toTypedArray())
    }

    private fun insights(
        track: PitchTrack,
        recording: Recording = recording(),
        tuning: Tuning = Tuning.JUST,
        strictness: Strictness = Strictness.PRECISE,
    ): Insights {
        val t = SwaraTranscriber.transcribe(track, recording, tuning)
        return RecordingInsights.of(recording, track, t, tuning, strictness)
    }

    @Test
    fun anInTuneArohanaIsAllInTune() {
        val i = insights(sung())
        assertEquals(listOf("S", "R₁", "G₃", "M₁", "P", "D₁", "N₃", "S′"), i.swaras.map { it.label })
        assertTrue(i.swaras.all { it.closeness == Closeness.IN_TUNE })
        assertNull(i.suggestedSa)
        assertEquals("Every swara was in tune (within 10¢).", i.summary)
    }

    @Test
    fun singingToALowerSaIsOfferedAsYourSa() {
        val i = insights(sung(all = -25.0))
        assertEquals(48 to -25, i.suggestedSa, "C3 −25¢")
        assertEquals(-25.0, i.saOffsetCents, 1.0)
        assertTrue(i.swaras.all { it.closeness == Closeness.IN_TUNE }, "against that Sa every note is in tune")
    }

    @Test
    fun aFlatMaBetweenGaAndMaIsReadAsAFlatMa() {
        val i = insights(sung(mapOf(Swara.M1 to -45.0)))
        val ma = i.swaras.single { it.swara == Swara.M1 }
        assertEquals(Closeness.FLAT, ma.closeness)
        assertEquals(-45.0, assertNotNull(ma.cents), 3.0)
        assertTrue(i.extras.isEmpty())
        assertEquals("Ma was flat (−45¢); the rest were in tune.", i.summary)
    }

    @Test
    fun aMissedSwaraIsSaid() {
        val i = insights(sung(skip = setOf(Swara.G3)))
        assertEquals(Closeness.MISSED, i.swaras.single { it.swara == Swara.G3 }.closeness)
        assertEquals(7, i.swaras.count { it.closeness == Closeness.IN_TUNE })
        assertTrue(i.summary.endsWith("Not heard: Ga."), i.summary)
    }

    @Test
    fun aScoopIntoSaIsMovementNotAnExtraNote() {
        val track = steps(
            null to 0.3, cents(Swara.N3, -1) to 0.15, cents(Swara.S) to 0.6, null to 0.2,
            cents(Swara.R1) to 0.6, null to 0.2, cents(Swara.G3) to 0.6, null to 0.3,
        )
        val i = insights(track, recording(scale = Scale.AROHANA))
        assertTrue(i.extras.isEmpty(), "extras: ${i.extras.map { it.label }}")
        assertEquals(Closeness.IN_TUNE, i.swaras.first().closeness)
    }

    @Test
    fun aLongNoteNotMeantIsAnExtra() {
        // The whole arohana, with a Dha sung between Ri and Ga.
        val segments = mutableListOf<Pair<Double?, Double>>(null to 0.3)
        (arohana.take(2) + Swara.D1 + arohana.drop(2)).forEach { segments += cents(it) to 0.6; segments += null to 0.2 }
        segments += cents(Swara.S, 1) to 0.6
        val track = steps(*segments.toTypedArray())
        val i = insights(track)
        assertEquals(listOf("D₁"), i.extras.map { it.label })
        assertTrue(i.summary.contains("Also heard: Dha."), i.summary)
    }

    @Test
    fun singingFreelyListsEachSwaraHeard() {
        val i = insights(sung(mapOf(Swara.D1 to 18.0)), recording(scale = null))
        assertEquals(listOf("S", "R₁", "G₃", "M₁", "P", "D₁", "N₃", "S′"), i.swaras.map { it.label })
        assertEquals(Closeness.A_LITTLE_SHARP, i.swaras.single { it.swara == Swara.D1 }.closeness)
        assertEquals("Dha was a little sharp (+18¢); the rest were in tune.", i.summary)
    }

    @Test
    fun strictnessDecidesWhatIsInTune() {
        val track = sung(mapOf(Swara.P to -12.0))
        val pa = { s: Strictness -> insights(track, strictness = s).swaras.single { it.swara == Swara.P }.closeness }
        assertEquals(Closeness.IN_TUNE, pa(Strictness.GENTLE))
        assertEquals(Closeness.IN_TUNE, pa(Strictness.STEADY))
        assertEquals(Closeness.A_LITTLE_FLAT, pa(Strictness.PRECISE))
    }

    @Test
    fun theTuningDecidesTheTargets() {
        // Sung at just intonation: in tune against Just; Ga is 14¢ flat of a keyboard's.
        val track = sung()
        val just = insights(track, tuning = Tuning.JUST).swaras.single { it.swara == Swara.G3 }
        val equal = insights(track, tuning = Tuning.EQUAL).swaras.single { it.swara == Swara.G3 }
        assertEquals(0.0, assertNotNull(just.cents), 1.0)
        assertEquals(-13.7, assertNotNull(equal.cents), 1.5)
    }

    @Test
    fun bothSharesTheTopSa() {
        assertEquals(15, Scale.BOTH.expected(Raga.MAYAMALAVAGOWLA).size)
        assertEquals(Raga.MAYAMALAVAGOWLA.avarohanam, Scale.AVAROHANA.expected(Raga.MAYAMALAVAGOWLA))
    }

    @Test
    fun aSteadyHoldAndAWaveringOne() {
        val builder = PitchTrack.Builder(HOP, HOP / 2)
        var t = HOP / 2
        fun add(seconds: Double, centsAt: (Double) -> Double?) {
            val end = t + seconds
            while (t < end) {
                val c = centsAt(t)
                builder.add(PitchFrame(t, c?.let { SA_HZ * 2.0.pow(it / 1200) }, if (c != null) 0.1f else 0.002f))
                t += HOP
            }
        }
        add(0.3) { null }
        add(1.5) { 0.0 } // Sa, steady
        add(0.3) { null }
        add(1.5) { tt -> cents(Swara.R1) + 42 * sin(2 * PI * 2 * tt) } // Ri, wavering ±42¢ (inside ±50¢: still one note)
        add(0.3) { null }
        val track = builder.build()
        val i = insights(track, recording(scale = null), strictness = Strictness.STEADY)
        val sa = i.swaras.single { it.swara == Swara.S }
        assertEquals(true, sa.steady(Strictness.STEADY))
        val ri = i.swaras.single { it.swara == Swara.R1 }
        assertEquals(false, ri.steady(Strictness.STEADY), "Ri spread ${ri.spreadCents}")
    }

    @Test
    fun aTanpuraSwitchWithNoTanpuraIsSaidAndReadAsWithout() {
        val track = steps(null to 3.2, *sung().let { s -> (0 until 1).map { cents(Swara.S) to 0.6 }.toTypedArray() }, null to 0.2, cents(Swara.R1) to 0.6, null to 0.3)
        val with = recording(scale = null, tanpura = true)
        val i = insights(track, with)
        assertTrue(i.tanpuraNotHeard)
        assertEquals(listOf("S", "R₁"), SwaraTranscriber.transcribe(track, with, Tuning.JUST).notes.map { it.label })
    }

    @Test
    fun theIntentIsSavedAndNeedsAKnownRaga() {
        val lib = RecordingLibrary(listOf(recording(scale = null)))
        val set = lib.changeIntent("r", Raga.MAYAMALAVAGOWLA.key, Scale.BOTH)
        assertEquals(Scale.BOTH, RecordingLibrary.decode(set.encode())["r"]!!.scale)
        val unknown = lib.changeIntent("r", "nonesuch", Scale.AROHANA)["r"]!!
        assertNull(unknown.ragaKey)
        assertNull(unknown.scale, "no raga, so singing freely")
    }
}
