package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.recording.RecordingTestTracks.SA_HZ
import com.baijum.purandara.recording.RecordingTestTracks.cents
import com.baijum.purandara.recording.RecordingTestTracks.steps
import com.baijum.purandara.recording.RecordingTestTracks.track
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwaraTranscriberTest {
    private val mayamalavagowla = Raga.MAYAMALAVAGOWLA

    private fun transcribe(track: PitchTrack, raga: Raga? = mayamalavagowla, saHz: Double = SA_HZ) =
        SwaraTranscriber.transcribe(track, saHz, raga, Tuning.JUST)

    private fun names(t: Transcription) = t.phrases.map { phrase -> phrase.map { it.label } }

    @Test
    fun steadyNotesAreReadInOrder() {
        val t = transcribe(
            steps(
                cents(Swara.S) to 0.3, cents(Swara.R1) to 0.3, cents(Swara.G3) to 0.3,
                cents(Swara.M1) to 0.3, cents(Swara.P) to 0.3,
            ),
        )
        assertEquals(listOf(listOf("S", "R₁", "G₃", "M₁", "P")), names(t))
        assertTrue(t.notes.all { abs(it.centsOff) < 1.0 && it.inRaga && it.holds == 0 })
        assertEquals(0.3, t.notes[1].durationSeconds, 0.03)
        assertEquals(0.3, t.notes[1].startSeconds, 0.03)
    }

    @Test
    fun aLongNoteGetsHolds() {
        val t = transcribe(
            steps(
                cents(Swara.S) to 0.3, cents(Swara.R1) to 0.3, cents(Swara.G3) to 0.9, cents(Swara.M1) to 0.3,
                cents(Swara.P) to 3.0,
            ),
        )
        assertEquals(listOf(0, 0, 2, 0, 3), t.notes.map { it.holds })
    }

    @Test
    fun aSlideBetweenTwoNotesHasNoNotesInBetween() {
        // Sa held, then a 0.4 s glide up to Pa, then Pa held.
        val t = transcribe(
            track(1.4) { s ->
                when {
                    s < 0.5 -> 0.0
                    s < 0.9 -> (s - 0.5) / 0.4 * cents(Swara.P)
                    else -> cents(Swara.P)
                }
            },
        )
        assertEquals(listOf(listOf("S", "P")), names(t))
    }

    @Test
    fun aKampitaAroundItsAnchorIsOneNote() {
        // Dwell 120 ms on G3, a 90 ms excursion up to M1 and back, four times.
        val g = cents(Swara.G3)
        val m = cents(Swara.M1)
        val cycle = 0.21
        val t = transcribe(
            track(4 * cycle) { s ->
                val phase = s % cycle
                if (phase < 0.12) g else g + (m - g) * kotlin.math.sin((phase - 0.12) / 0.09 * kotlin.math.PI)
            },
        )
        assertEquals(listOf(listOf("G₃")), names(t))
        assertEquals(4 * cycle, t.notes.single().durationSeconds, 0.12)
    }

    @Test
    fun anOctaveJumpIsReadWithItsOctave() {
        val t = transcribe(steps(cents(Swara.P, -1) to 0.4, 0.0 to 0.4, cents(Swara.S, 1) to 0.4))
        assertEquals(listOf(listOf("P̣", "S", "S′")), names(t))
        assertEquals(listOf(-1, 0, 1), t.notes.map { it.octave })
        assertEquals("P̣", t.lowest?.label)
        assertEquals("S′", t.highest?.label)
    }

    @Test
    fun aLongSilenceStartsANewPhraseAndAShortOneMergesTheNote() {
        val t = transcribe(
            steps(
                0.0 to 0.4, null to 0.05, 0.0 to 0.4, // a consonant: one Sa
                cents(Swara.R1) to 0.4, null to 0.8,
                cents(Swara.G3) to 0.4,
            ),
        )
        assertEquals(listOf(listOf("S", "R₁"), listOf("G₃")), names(t))
        assertEquals(0.85, t.notes[0].durationSeconds, 0.03)
    }

    @Test
    fun aShortSilenceBetweenRepeatedNotesUnderThePhraseGapKeepsThemApart() {
        val t = transcribe(steps(0.0 to 0.4, null to 0.2, 0.0 to 0.4))
        assertEquals(listOf(listOf("S", "S")), names(t))
    }

    @Test
    fun quietRoomNoiseIsNotANote() {
        // Pitched room noise far below the singer's level.
        val loud = steps(null to 1.0, 0.0 to 0.5, null to 1.0)
        val builder = PitchTrack.Builder(loud.hopSeconds, loud.offsetSeconds)
        for (i in 0 until loud.size) {
            val voiced = loud.frequencyAt(i) != null
            builder.add(
                com.baijum.purandara.singalong.PitchFrame(
                    loud.timeAt(i),
                    if (voiced) SA_HZ else SA_HZ * 1.5, // a hum on Pa in the room
                    if (voiced) 0.1f else 0.002f,
                ),
            )
        }
        assertEquals(listOf(listOf("S")), names(transcribe(builder.build())))
    }

    @Test
    fun loudPitchlessNoteChangesDoNotSilenceTheVoice() {
        // Continuous singing with no silence: a few loud frames without a pitch at each note change.
        val notes = listOf(0.0, cents(Swara.R1), cents(Swara.G3), cents(Swara.M1), cents(Swara.P))
        val builder = PitchTrack.Builder(RecordingTestTracks.HOP, RecordingTestTracks.HOP / 2)
        val perNote = (0.4 / RecordingTestTracks.HOP).toInt()
        notes.forEach { c ->
            repeat(perNote) { i ->
                val hz = if (i < 4) null else SA_HZ * 2.0.pow(c / 1200.0)
                builder.add(com.baijum.purandara.singalong.PitchFrame(0.0, hz, 0.1f))
            }
        }
        assertEquals(listOf(listOf("S", "R₁", "G₃", "M₁", "P")), names(transcribe(builder.build())))
    }

    @Test
    fun tooShortAFlickIsNotANote() {
        val t = transcribe(steps(0.0 to 0.4, cents(Swara.G3) to 0.06, 0.0 to 0.4))
        assertEquals(listOf(listOf("S")), names(t))
    }

    @Test
    fun changingSaAfterwardsRenamesTheNotes() {
        // Sung Sa-Ri-Ga at C3, read against Sa a whole tone lower (A#2): Ri, Ga, Ma-ish.
        val track = steps(0.0 to 0.4, cents(Swara.R2) to 0.4, cents(Swara.G3) to 0.4)
        val lower = SA_HZ / (9.0 / 8.0)
        val t = transcribe(track, raga = null, saHz = lower)
        assertEquals(listOf(Swara.R2, Swara.G3), t.notes.take(2).map { it.swara })
    }

    @Test
    fun ragaDecidesTheNameOfASharedPosition() {
        // Position 3 is G2 by default, R3 in a raga that uses R3.
        val withR3 = Raga(
            "Test",
            mapOf(
                SwaraLetter.S to Swara.S, SwaraLetter.R to Swara.R3, SwaraLetter.G to Swara.G3,
                SwaraLetter.M to Swara.M1, SwaraLetter.P to Swara.P,
            ),
        )
        val track = steps(cents(Swara.R3) to 0.5)
        assertEquals(Swara.G2, transcribe(track, raga = null).notes.single().swara)
        assertEquals(Swara.R3, transcribe(track, raga = withR3).notes.single().swara)
    }

    @Test
    fun notesOutsideTheRagaAreFlaggedAndNoRagaFlagsNothing() {
        val track = steps(cents(Swara.R2) to 0.5) // R2 is not in Mayamalavagowla
        val inRaga = transcribe(track).notes.single()
        assertEquals(Swara.R2, inRaga.swara)
        assertFalse(inRaga.inRaga)
        assertTrue(transcribe(track, raga = null).notes.single().inRaga)
    }

    @Test
    fun silenceGivesNoNotes() {
        val t = transcribe(steps(null to 2.0))
        assertTrue(t.notes.isEmpty())
        assertNull(t.lowest)
        assertNull(t.noteIndexAt(1.0))
    }

    @Test
    fun noteIndexAtFindsTheSoundingNote() {
        val t = transcribe(steps(0.0 to 0.4, null to 0.2, cents(Swara.P) to 0.4))
        assertEquals(0, t.noteIndexAt(0.2))
        assertNull(t.noteIndexAt(0.5))
        assertEquals(1, t.noteIndexAt(0.8))
        assertNull(t.noteIndexAt(5.0))
    }

    @Test
    fun notesNeverOverlapAndStayInOrderWithinTheTrack() = runTest {
        val positions = listOf(null, 0.0, 112.0, 386.0, 498.0, 702.0, 814.0, 1088.0, 1200.0, 250.0, 600.0)
        checkAll(50, Arb.list(Arb.pair(Arb.element(positions), Arb.double(0.02, 0.6)), 1..25)) { segments ->
            val track = steps(*segments.toTypedArray())
            val notes = transcribe(track).notes
            notes.zipWithNext().forEach { (a, b) -> assertTrue(a.endSeconds <= b.startSeconds + 1e-9, "$a then $b") }
            notes.forEach {
                assertTrue(it.startSeconds >= 0.0 && it.endSeconds <= track.durationSeconds + 1e-9)
                assertTrue(it.durationSeconds >= SwaraTranscriber.MIN_NOTE_SECONDS - track.hopSeconds)
                assertTrue(abs(it.centsOff) <= SwaraTranscriber.STEADY_CENTS)
            }
        }
    }
}
