package com.baijum.purandara.recording

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.recording.RecordingTestTracks.HOP
import com.baijum.purandara.recording.RecordingTestTracks.SA_HZ
import com.baijum.purandara.singalong.PitchFrame
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The student's own tanpura under a recording (docs/plan-recordings-tanpura.md). */
class TanpuraProfileTest {
    private val droneRms = 0.03f

    /**
     * A tanpura plucking pa SA SA sa (or ma SA SA sa), 0.5 s a string, at
     * [droneRms], detuned by [detuneCents]; [voice] gives the sung swara and
     * level at a time (null = the tanpura alone). Where the voice sings, the
     * pitch heard is the voice's and the levels add.
     */
    private fun withTanpura(
        seconds: Double,
        other: Double = 0.75,
        detuneCents: Double = 0.0,
        voice: (Double) -> Pair<Swara, Float>? = { null },
    ): PitchTrack {
        val sa = SA_HZ * 2.0.pow(detuneCents / 1200)
        val strings = listOf(sa * other, sa, sa, sa / 2)
        val builder = PitchTrack.Builder(HOP, HOP / 2)
        for (i in 0 until (seconds / HOP).toInt()) {
            val t = HOP / 2 + i * HOP
            val sung = voice(t)
            if (sung == null) {
                builder.add(PitchFrame(t, strings[((t / 0.5).toInt()) % 4], droneRms))
            } else {
                val level = sqrt(sung.second * sung.second + droneRms * droneRms)
                builder.add(PitchFrame(t, SA_HZ * sung.first.ratio, level))
            }
        }
        return builder.build()
    }

    /** Sarali up, 0.4 s a note with a 0.3 s pause after every second note, after 3 s of tanpura. */
    private fun sarali(level: Float): (Double) -> Pair<Swara, Float>? {
        val swaras = listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3)
        return { t ->
            val s = t - TanpuraProfile.LISTEN_SECONDS
            val pair = (s / 1.1).toInt()
            val inPair = s - pair * 1.1
            val index = pair * 2 + if (inPair < 0.4) 0 else 1
            if (s < 0 || inPair >= 0.8 || index >= swaras.size) null else swaras[index] to level
        }
    }

    private fun names(t: Transcription) = t.notes.map { it.label }

    private fun cents(a: Double, b: Double) = Pitch.cents(a, b)

    // ── The profile ──────────────────────────────────────────────────

    @Test
    fun learnsTheTanpurasLevelSaAndPa() {
        val p = assertNotNull(TanpuraProfile.from(withTanpura(4.0)))
        assertEquals(droneRms, p.rms, 1e-4f)
        val sa = assertNotNull(p.saHz)
        assertTrue(abs(cents(SA_HZ, sa).let { it - 1200 * kotlin.math.round(it / 1200) }) < 5, "Sa read as $sa")
        val pa = assertNotNull(p.otherHz)
        assertTrue(abs(cents(SA_HZ * 1.5, pa).let { it - 1200 * kotlin.math.round(it / 1200) }) < 5, "Pa read as $pa")
    }

    @Test
    fun aMaTunedTanpuraStillGivesItsSa() {
        val p = assertNotNull(TanpuraProfile.from(withTanpura(4.0, other = 2.0 / 3)))
        val sa = assertNotNull(p.saHz)
        assertTrue(abs(cents(SA_HZ, sa).let { it - 1200 * kotlin.math.round(it / 1200) }) < 5, "Sa read as $sa")
    }

    @Test
    fun aDetunedTanpuraIsOfferedAsTheSa() {
        val p = assertNotNull(TanpuraProfile.from(withTanpura(4.0, detuneCents = 12.0)))
        assertEquals(48 to 12, p.suggestedSa(SA_HZ), "C3 +12¢")
        val inTune = assertNotNull(TanpuraProfile.from(withTanpura(4.0)))
        assertNull(inTune.suggestedSa(SA_HZ), "no offer when it already matches")
        assertEquals(48 to 0, inTune.suggestedSa(Pitch.midiToHz(50)), "a C tanpura under a D3 Sa offers C3, the nearest octave")
    }

    @Test
    fun noiseGivesNoSa() {
        val random = Random(3)
        val builder = PitchTrack.Builder(HOP, HOP / 2)
        for (i in 0 until (4.0 / HOP).toInt()) builder.add(PitchFrame(HOP / 2 + i * HOP, 80.0 + random.nextDouble() * 600, droneRms))
        val p = assertNotNull(TanpuraProfile.from(builder.build()))
        assertNull(p.saHz)
        assertNull(p.suggestedSa(SA_HZ))
    }

    // ── The swaras with a tanpura ────────────────────────────────────

    @Test
    fun theTanpuraIsNotReadAsNotes() {
        val track = withTanpura(3.0 + 4 * 1.1, voice = sarali(0.1f))
        val expected = listOf("S", "R₁", "G₃", "M₁", "P", "D₁", "N₃")
        val plain = SwaraTranscriber.transcribe(track, SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertTrue(names(plain) != expected, "without the profile the tanpura adds notes: ${names(plain)}")
        val withProfile = SwaraTranscriber.transcribe(track, SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST, TanpuraProfile.from(track))
        assertEquals(expected, names(withProfile))
    }

    @Test
    fun aSungSaOverTheSaDroneIsANote() {
        val track = withTanpura(5.0) { t -> if (t in 3.5..4.5) Swara.S to 0.1f else null }
        val t = SwaraTranscriber.transcribe(track, SA_HZ, null, Tuning.JUST, TanpuraProfile.from(track))
        assertEquals(listOf("S"), names(t))
        assertEquals(1.0, t.notes.single().durationSeconds, 0.05)
    }

    @Test
    fun aVoiceSofterThanTheTanpuraGivesNoNotesRatherThanWrongOnes() {
        val track = withTanpura(3.0 + 4 * 1.1, voice = sarali(0.015f))
        val t = SwaraTranscriber.transcribe(track, SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST, TanpuraProfile.from(track))
        assertEquals(emptyList(), names(t))
    }

    @Test
    fun theSettingOnARecordingChangesTheSwaras() {
        val track = withTanpura(3.0 + 4 * 1.1, voice = sarali(0.1f))
        val off = Recording("a", 1, 7.4, saMidi = 48)
        val on = RecordingLibrary(listOf(off)).changeTanpura("a", true)["a"]!!
        assertTrue(on.externalTanpura)
        assertEquals(listOf("S", "R₁", "G₃", "M₁", "P", "D₁", "N₃"), names(SwaraTranscriber.transcribe(track, on, Tuning.JUST)))
        assertTrue(names(SwaraTranscriber.transcribe(track, off, Tuning.JUST)) != names(SwaraTranscriber.transcribe(track, on, Tuning.JUST)))
    }

    @Test
    fun theSettingSurvivesTheIndex() {
        val lib = RecordingLibrary(listOf(Recording("a", 1, 7.4, saMidi = 48, externalTanpura = true)))
        assertTrue(RecordingLibrary.decode(lib.encode())["a"]!!.externalTanpura)
        assertTrue(!RecordingLibrary.decode("""[{"id":"b","createdMillis":1,"durationSeconds":2.0,"saMidi":48}]""")["b"]!!.externalTanpura)
    }

    @Test
    fun changingTheSettingMarksTheRecordingForAnalysis() {
        val lib = RecordingLibrary(listOf(Recording("a", 1, 7.4, saMidi = 48, analysisVersion = RecordingLibrary.ANALYSIS_VERSION)))
        val changed = lib.changeTanpura("a", true)
        assertTrue(changed["a"]!!.needsAnalysis)
        assertTrue(!changed.analysed("a")["a"]!!.needsAnalysis)
        assertTrue(!lib.changeTanpura("a", false)["a"]!!.needsAnalysis, "no change, no analysis")
    }
}
