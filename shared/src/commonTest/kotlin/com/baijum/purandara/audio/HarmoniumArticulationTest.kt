package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiParser
import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HarmoniumArticulationTest {
    private val sr = 8_000
    // Nonzero DC samples reveal real silence, not waveform zero crossings.
    private fun level(midi: Int): Float = 0.2f + (midi - 36) / 100f
    private val bank = SampleBank(sr, HarmoniumBank.midiRange.associateWith { midi -> FloatArray(2 * sr) { level(midi) } })
    private val notesOnly = RenderOptions(harmoniumGain = 1f, drone = false, click = false)

    /** A section with piece-wide positions, allowing a real leading rest. */
    private fun schedule(row: String, tempo: Int = 60, kala: Int = 1, beat: Int = 1): Schedule {
        val first = List(8 * beat) { "S" }.joinToString(" ")
        val v = VarisaiParser().parse("1\nbeat: $beat\n$first\n[Practice]\n$row", VarisaiSet.SARALI).single()
        return Schedule.ofSection(v, PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = tempo, kala = kala), v.sections.single())
    }

    private fun render(schedule: Schedule, options: RenderOptions, frames: Int? = null, blocks: IntArray = intArrayOf(4096)): FloatArray {
        val renderer = SampleRenderer(bank, schedule, options)
        val out = FloatArray(frames ?: renderer.totalFrames.toInt())
        var at = 0
        var block = 0
        while (at < out.size) {
            // Nonzero output offsets and guard cells exercise the mixer's range contract.
            val count = minOf(blocks[block++ % blocks.size], out.size - at)
            val buffer = FloatArray(count + 4) { -9f }
            assertEquals(count, renderer.render(buffer, 2, count))
            assertEquals(-9f, buffer.first())
            assertEquals(-9f, buffer.last())
            buffer.copyInto(out, at, 2, count + 2)
            at += count
        }
        return out
    }

    private fun assertSilence(out: FloatArray, from: Int, to: Int) {
        assertTrue((from until to).all { out[it] == 0f }, "expected silence in $from..<$to")
    }

    @Test
    fun connectedIsTheConstructorAndPracticeFactoryDefault() {
        assertEquals(HarmoniumArticulation.CONNECTED, RenderOptions().articulation)
        val default = RenderOptions.practice(drone = false, click = true, loop = true, countIn = true, harmoniumLevel = 0.5f, droneLevel = 0.25f)
        assertEquals(HarmoniumArticulation.CONNECTED, default.articulation)
        val light = RenderOptions.practice(false, true, true, true, 0.5f, 0.25f, HarmoniumArticulation.LIGHTLY_SEPARATED)
        assertEquals(default.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED), light)
        val s = schedule("S S R R G G M M")
        assertTrue(render(s, notesOnly).contentEquals(render(s, notesOnly.copy(articulation = HarmoniumArticulation.CONNECTED))))
    }

    @Test
    fun lightLeavesTheExactCappedGapForIdenticalAndDifferentNotes() {
        for (tempo in listOf(30, 60, 120)) {
            for (kala in listOf(1, 2, 4)) {
                val s = schedule("SS RR GG MM PP DD NN S′S′", tempo, kala)
                val out = render(s, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
                for (i in 0 until s.notes.lastIndex) {
                    val note = s.notes[i]
                    val boundary = framesOf(s.notes[i + 1].startSeconds, sr)
                    val gap = framesOf(minOf(note.durationSeconds * 0.05, 0.025), sr)
                    assertTrue(gap > 0)
                    assertTrue(out[boundary - gap - 1] > 0f, "release must end exactly at the gap")
                    assertSilence(out, boundary - gap, boundary + 1)
                    assertTrue(out[boundary + 1] > 0f, "on-time attack after $boundary")
                }
            }
        }
    }

    @Test
    fun connectedKeepsTheExistingCentredCrossfadeAtOrdinaryJoins() {
        val s = schedule("SS RR GG MM PP DD NN S′S′")
        val out = render(s, notesOnly)
        val fade = framesOf(notesOnly.legatoSeconds, sr)
        for (i in 0 until s.notes.lastIndex) {
            val a = level(s.notes[i].playback.midi)
            val b = level(s.notes[i + 1].playback.midi)
            val boundary = framesOf(s.notes[i + 1].startSeconds, sr)
            for (delta in -fade / 2 until fade / 2) {
                val weight = (delta + fade / 2).toFloat() / fade
                assertEquals(a * (1f - weight) + b * weight, out[boundary + delta], 1e-6f)
            }
        }
    }

    @Test
    fun karvaisSustainWithoutGapsOrRetriggeringAndAreNotInferredFromDuration() {
        // SS , holds just the second slot, without retriggering at the comma.
        val s = schedule("SS , SSSS S S S S S", tempo = 120)
        val out = render(s, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
        val held = s.notes[1]
        val boundary = framesOf(s.notes[2].startSeconds, sr)
        val sa = level(held.playback.midi)
        for (t in framesOf(held.startSeconds, sr) + sr / 100 until boundary) assertEquals(sa, out[t], 1e-6f)
        assertEquals(sa, out[boundary], 1e-6f, "held release begins after the full written sustain")
        assertTrue(out[boundary + 1] > 0f)
        // A long unheld note still gets a gap; no duration heuristic.
        val long = schedule("S R S R S R S R", tempo = 30)
        val longOut = render(long, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
        assertSilence(longOut, 2 * sr - sr / 40, 2 * sr)
        // Explicit commas, including multiple holds, never make an internal dip.
        val multiple = schedule("S , , R S , , R")
        val multipleOut = render(multiple, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
        for (t in sr / 100 until 3 * sr) assertEquals(sa, multipleOut[t], 1e-6f)
    }

    @Test
    fun equalDurationHeldAndUnheldEventsHaveDifferentArticulation() {
        val s = schedule("SSSS SSSS SSSS SSSS S , S ,", tempo = 120)
        // A held note is 1 s; elsewhere an unheld note is 0.125 s. The same
        // duration can arise unheld at another gati/speed/tempo.
        val unheld = schedule("S R G M P D N S′")
        assertEquals(s.notes[16].durationSeconds, unheld.notes[0].durationSeconds, 1e-9)
        val light = notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED)
        val heldOut = render(s, light)
        val boundary = framesOf(s.notes[17].startSeconds, sr)
        assertTrue(heldOut[boundary - 1] > 0f)
        assertTrue(heldOut[boundary] > 0f)
        val unheldOut = render(unheld, light)
        assertSilence(unheldOut, sr - sr / 40, sr)
    }

    @Test
    fun restsAreSilentIncludingLeadingTrailingAndHeldBeforeRest() {
        for (articulation in HarmoniumArticulation.entries) {
            for (loop in listOf(false, true)) {
                for (row in listOf("_ S , _ R R _ _", "_ S S S S S S S", "S S S S S S S _")) {
                    val s = schedule(row)
                    val options = notesOnly.copy(articulation = articulation, loop = loop, countIn = true)
                    val renderer = SampleRenderer(bank, s, options)
                    val countIn = renderer.countInFrames
                    val pass = renderer.performanceFrames
                    val out = render(s, options, if (loop) countIn + pass * 2 else null)
                    // Nothing but the count-in clicks: no next-note pre-roll.
                    assertSilence(out, countIn - sr / 10, countIn)
                    val rests = row.split(" ").withIndex().filter { it.value == "_" }.map { it.index }
                    for (p in 0 until if (loop) 2 else 1) {
                        for (rest in rests) {
                            val from = countIn + p * pass + rest * sr
                            assertSilence(out, from, from + sr)
                        }
                    }
                    if (!loop && row.endsWith("_")) assertSilence(out, countIn + pass, out.size)
                    val first = countIn + framesOf(s.notes.first().startSeconds, sr)
                    assertEquals(0f, out[first], "attack starts on the scheduled onset after silence")
                    assertTrue(out[first + 1] > 0f)
                }
            }
        }
    }

    @Test
    fun allRestSectionIsSilentInBothModesAndAcrossLoopSeams() {
        val s = schedule("_ _ _ _ _ _ _ _")
        for (articulation in HarmoniumArticulation.entries) {
            for (loop in listOf(false, true)) {
                val options = notesOnly.copy(articulation = articulation, loop = loop)
                val out = render(s, options, if (loop) 16 * sr else null)
                assertSilence(out, 0, out.size)
            }
        }
    }

    @Test
    fun trueFinalNoteKeepsItsFullReleaseInEitherMode() {
        val s = schedule("S R G M P D N S′")
        for (articulation in HarmoniumArticulation.entries) {
            val options = notesOnly.copy(articulation = articulation)
            val renderer = SampleRenderer(bank, s, options)
            val out = render(s, options)
            val end = renderer.performanceFrames
            val release = framesOf(options.releaseSeconds, sr)
            val lastLevel = level(s.notes.last().playback.midi)
            assertEquals(end + release, out.size)
            assertEquals(lastLevel, out[end], 1e-6f)
            assertEquals(lastLevel / 2, out[end + release / 2], 1e-6f)
            assertTrue(out.last() > 0f && out.last() < 0.001f)
        }
    }

    @Test
    fun loopSeamUsesThePresetForIdenticalAndDifferentNotes() {
        for (last in listOf("S", "R")) {
            val s = schedule("S S S S S S S $last")
            for (articulation in HarmoniumArticulation.entries) {
                val options = notesOnly.copy(articulation = articulation, loop = true)
                val out = render(s, options, 16 * sr + sr / 10)
                for (boundary in listOf(8 * sr, 16 * sr)) {
                    if (articulation == HarmoniumArticulation.LIGHTLY_SEPARATED) {
                        assertSilence(out, boundary - sr / 40, boundary + 1)
                        assertTrue(out[boundary - sr / 40 - 1] > 0f)
                        assertTrue(out[boundary + 1] > 0f)
                    } else {
                        assertTrue((boundary - sr / 40 until boundary + sr / 40).all { out[it] > 0f })
                        assertEquals((level(s.notes.last().playback.midi) + level(s.notes.first().playback.midi)) / 2, out[boundary], 1e-6f)
                    }
                }
            }
        }
    }

    @Test
    fun heldLastEventHasNoArtificialGapAtTheLoopSeam() {
        val s = schedule("S S S S S S R ,")
        val options = notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED, loop = true)
        val out = render(s, options, 16 * sr + sr / 10)
        val last = level(s.notes.last().playback.midi)
        for (boundary in listOf(8 * sr, 16 * sr)) {
            for (t in boundary - sr / 20 until boundary) assertEquals(last, out[t], 1e-6f)
            assertEquals(last, out[boundary], 1e-6f)
            assertTrue((boundary until boundary + sr / 100).all { out[it] > 0f })
        }
    }

    @Test
    fun shortestNotesKeepANonzeroBodyAndBoundedEnvelopes() {
        // Fastest tempo/kala, four cells per beat, @4 sections, four swaras
        // per cell: 0.9765625 ms per event. Both fades must fit the event.
        val line = List(32) { "SSSS" }.joinToString(" ")
        val v = VarisaiParser().parse("1\nbeat: 4\n[Fast @4]\n" + List(4) { line }.joinToString("\n"), VarisaiSet.SARALI).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 120, kala = 4))
        val light = render(s, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
        val connected = render(s, notesOnly)
        for (note in s.notes) {
            val from = framesOf(note.startSeconds, sr)
            val to = framesOf(note.startSeconds + note.durationSeconds, sr)
            assertTrue((from until to).any { light[it] >= level(note.playback.midi) * 0.99f }, "note body lost at $from")
        }
        assertTrue(light.all { it.isFinite() && it in 0f..0.5f })
        assertTrue(connected.all { it.isFinite() && it in 0f..0.7f })
    }

    @Test
    fun boundedDiscontinuitiesAcrossAttacksGapsRestsHoldsAndSeams() {
        for (row in listOf("SS RR S , R _ S S", "_ S , _ R S , _")) {
            val s = schedule(row)
            for (articulation in HarmoniumArticulation.entries) {
                for (loop in listOf(false, true)) {
                    val out = render(s, notesOnly.copy(articulation = articulation, loop = loop), if (loop) 16 * sr + sr / 10 else null)
                    // Light's 5 ms fade is the steepest envelope here, with a
                    // maximum bank level < 0.6. This rejects a hard DC edge.
                    val bound = 0.6f / (0.005 * sr).toFloat() + 1e-6f
                    for (t in 1 until out.size) assertTrue(abs(out[t] - out[t - 1]) <= bound, "$articulation loop=$loop jump at $t")
                }
            }
        }
    }

    @Test
    fun arbitraryBlockBoundariesDoNotChangeEitherArticulation() {
        val s = schedule("_ SS S , R _ R ,", tempo = 120, kala = 2)
        for (articulation in HarmoniumArticulation.entries) {
            for (loop in listOf(false, true)) {
                val options = RenderOptions(articulation = articulation, drone = true, click = true, countIn = true, loop = loop)
                val renderer = SampleRenderer(bank, s, options)
                val frames = if (loop) renderer.countInFrames + renderer.performanceFrames * 3 + 123 else renderer.totalFrames.toInt()
                val whole = render(s, options, frames, intArrayOf(frames))
                val chunked = render(s, options, frames, intArrayOf(1, 7, 83, 509, 2, 3, 19))
                assertTrue(whole.contentEquals(chunked), "$articulation loop=$loop block-dependent mix")
            }
        }
    }

    @Test
    fun articulationDoesNotChangeSchedulesCursorsBeatsOrPlaybackClocks() {
        val s = schedule("S , _ SS R , S S", tempo = 120, kala = 3)
        val notes = s.notes.toList()
        val cursor = s.cursor.toList()
        val beats = s.beats.toList()
        val duration = s.durationSeconds
        for (loop in listOf(false, true)) {
            val connected = SampleRenderer(bank, s, notesOnly.copy(countIn = true, loop = loop))
            val light = SampleRenderer(bank, s, notesOnly.copy(countIn = true, loop = loop, articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
            assertSame(s, light.schedule)
            assertEquals(connected.totalFrames, light.totalFrames)
            assertEquals(connected.performanceFrames, light.performanceFrames)
            assertEquals(connected.countInFrames, light.countInFrames)
            for (frame in 0L..(connected.countInFrames + connected.performanceFrames * 2) step 97) {
                assertEquals(connected.passSeconds(frame), light.passSeconds(frame))
                assertEquals(connected.countInBeat(frame), light.countInBeat(frame))
                assertEquals(s.cursorAt(connected.passSeconds(frame)), s.cursorAt(light.passSeconds(frame)))
                assertEquals(s.beatAt(connected.passSeconds(frame)), s.beatAt(light.passSeconds(frame)))
            }
            render(s, notesOnly.copy(articulation = HarmoniumArticulation.LIGHTLY_SEPARATED))
        }
        assertEquals(notes, s.notes)
        assertEquals(cursor, s.cursor)
        assertEquals(beats, s.beats)
        assertEquals(duration, s.durationSeconds)
    }
}
