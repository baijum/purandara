package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.NotationLayout
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.singalong.NoteMark
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.singalong.PitchFrame
import com.baijum.purandara.singalong.SingAlongClock
import com.baijum.purandara.singalong.SingAlongSession
import com.baijum.purandara.singalong.Strictness
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Repeat this cycle (docs/plan-repeat-cycle.md): cycles, the renderer's jumps and sing-along through them. */
class CycleRepeatTest {
    private val sr = 44_100
    private val sa = Pitch.midiToHz(48)
    private val clipFrames = (2.5 * sr).toInt()

    /** A constant level per MIDI note: shows exactly which note sounds when. */
    private val levelBank = SampleBank(sr, HarmoniumBank.midiRange.associateWith { m -> FloatArray(clipFrames) { (m - 30) / 100f } })
    private val notesOnly = RenderOptions(harmoniumGain = 1f, drone = false, click = false)

    private fun schedule(id: String, kala: Int = 1, tempo: Int = 120) =
        Schedule.of(VarisaiCatalog.byId(id)!!, PerformanceSettings(saHz = sa, tempo = tempo, kala = kala))

    /** Renders [frames] more frames of [r] in blocks of [block] into [out] from [at]; returns the new end. */
    private fun render(r: SampleRenderer, out: FloatArray, at: Int, frames: Int, block: Int = 4096): Int {
        var pos = at
        while (pos < at + frames) {
            val n = r.render(out, pos, minOf(block, at + frames - pos))
            if (n == 0) break
            pos += n
        }
        return pos
    }

    private fun renderToEnd(r: SampleRenderer, out: FloatArray, at: Int, block: Int = 4096): Int = render(r, out, at, out.size - at, block)

    @Test
    fun everyCycleStartsWhereItsNotationBlockStarts() {
        for (v in VarisaiCatalog.all) {
            for (kala in 1..3) {
                val layout = runCatching { NotationLayout.of(v, kala) }.getOrNull() ?: continue
                val s = Schedule.of(v, PerformanceSettings(saHz = sa, kala = kala))
                assertEquals(layout.blocks.size, s.cycles, "${v.id} at kala $kala: cycles")
                assertEquals(s.durationSeconds, s.cycles * s.cycleSeconds, 1e-6, "${v.id} at kala $kala: whole cycles")
                layout.blocks.forEachIndexed { c, block ->
                    val first = block.cells.first()
                    val at = s.cursorAt(c * s.cycleSeconds + 1e-9)
                    assertNotNull(at, "${v.id} kala $kala cycle $c")
                    assertEquals(Triple(first.repetition, first.avarta, first.akshara), Triple(at.repetition, at.avarta, at.akshara), "${v.id} kala $kala cycle $c")
                    assertEquals(c, s.cycleAt(c * s.cycleSeconds))
                    assertEquals(c, s.cycleAt((c + 0.999) * s.cycleSeconds))
                }
            }
        }
    }

    @Test
    fun repeatingPlaysTheCycleAgainThenCarriesOn() {
        val s = schedule("sarali-3")
        assertTrue(s.cycles >= 2)
        val reference = SampleRenderer(levelBank, s, notesOnly)
        val ref = FloatArray(reference.totalFrames.toInt())
        renderToEnd(reference, ref, 0)

        val cycle = (s.cycleSeconds * sr).toInt()
        val r = SampleRenderer(levelBank, s, notesOnly)
        val out = FloatArray(ref.size + 2 * cycle)
        var pos = render(r, out, 0, cycle / 2)
        val k = assertNotNull(r.cycleToRepeat(pos.toLong()))
        assertEquals(0L, k)
        assertTrue(r.repeatCycle(k))
        assertEquals(0L, r.repeatingCycle)
        // Hear cycle 0 three times in all, then continue during the third.
        pos = render(r, out, pos, 2 * cycle + cycle / 2 - pos)
        r.continueAfterRepeat()
        assertNull(r.repeatingCycle)
        pos = renderToEnd(r, out, pos)
        assertEquals(out.size, pos)
        assertEquals(ref.size + 2L * cycle, r.totalFrames)
        assertEquals(2, r.repeats.jumps)

        val seam = (0.03 * sr).toInt()
        fun assertSame(outFrom: Int, refFrom: Int, length: Int) {
            for (i in seam until length - seam) {
                assertEquals(ref[refFrom + i], out[outFrom + i], 1e-6f, "out ${outFrom + i} vs ref ${refFrom + i}")
            }
        }
        assertSame(0, 0, cycle)
        assertSame(cycle, 0, cycle)
        assertSame(2 * cycle, 0, ref.size) // the third time, then on into cycle 1 to the end

        // The heard position maps back: the second time round is cycle 0 again.
        val inSecond = cycle + cycle / 3L
        assertEquals(cycle / 3L, r.performanceFrame(inSecond))
        assertEquals(0, r.cycleInPassAt(inSecond))
        assertEquals(1, r.cycleInPassAt(3L * cycle + 10))
        assertEquals((cycle / 3.0) / sr, r.passSeconds(inSecond), 1e-9)
        assertEquals(1, r.repeats.jumpsBetween(0, inSecond))
    }

    @Test
    fun aJumpIsSeamless() {
        val s = schedule("sarali-3")
        val r = SampleRenderer(levelBank, s, notesOnly)
        val cycle = (s.cycleSeconds * sr).toInt()
        val out = FloatArray(3 * cycle)
        render(r, out, 0, 10)
        r.repeatCycle(0)
        render(r, out, 10, out.size - 10)
        // A constant level per note: across the jump the level changes no faster than a crossfade allows.
        val legato = (0.03 * sr).toInt()
        for (at in listOf(cycle, 2 * cycle)) {
            for (i in at - legato until at + legato) {
                assertTrue(abs(out[i + 1] - out[i]) < 0.05f, "step at $i: ${out[i]} → ${out[i + 1]}")
            }
        }
    }

    @Test
    fun aPressInTheFirstBeatRepeatsTheCycleJustFinished() {
        val s = schedule("sarali-3")
        val r = SampleRenderer(levelBank, s, notesOnly)
        val cycle = (s.cycleSeconds * sr).toLong()
        val beat = (s.settings.talaAksharaSeconds * sr).toLong()
        assertEquals(0L, r.cycleToRepeat(cycle + beat / 2))
        assertEquals(1L, r.cycleToRepeat(cycle + beat + 10))
        assertEquals(0L, r.cycleToRepeat(beat / 2), "no cycle before the first")
        assertNull(r.cycleToRepeat(s.cycles * cycle + 10), "after the end")

        val counted = SampleRenderer(levelBank, s, notesOnly.copy(countIn = true))
        assertNull(counted.cycleToRepeat(10), "in the count-in")
        assertFalse(counted.repeatCycle(0), "in the count-in")

        // Looping: the first beat of a new pass doesn't reach back into the last one.
        val looping = SampleRenderer(levelBank, s, notesOnly.copy(loop = true))
        assertEquals(s.cycles.toLong(), looping.cycleToRepeat(s.cycles * cycle + beat / 2))
    }

    @Test
    fun theCycleJustFinishedComesBackAfterTheOnePlaying() {
        val s = schedule("sarali-3")
        val cycle = (s.cycleSeconds * sr).toInt()
        val reference = SampleRenderer(levelBank, s, notesOnly)
        val ref = FloatArray(reference.totalFrames.toInt())
        renderToEnd(reference, ref, 0)

        val r = SampleRenderer(levelBank, s, notesOnly)
        val out = FloatArray(ref.size + cycle)
        var pos = render(r, out, 0, cycle + 100)
        assertTrue(r.repeatCycle(0)) // heard in the first beat of cycle 1
        pos = render(r, out, pos, 2 * cycle + 100 - pos)
        r.continueAfterRepeat()
        renderToEnd(r, out, pos)
        val seam = (0.03 * sr).toInt()
        for (i in seam until cycle - seam) {
            assertEquals(ref[cycle + i], out[cycle + i], 1e-6f) // cycle 1 plays to its end
            assertEquals(ref[i], out[2 * cycle + i], 1e-6f) // then cycle 0 again
            assertEquals(ref[cycle + i], out[3 * cycle + i], 1e-6f) // then on
        }
    }

    @Test
    fun aCycleNotYetReachedRepeatsTheOnePlaying() {
        val s = schedule("sarali-3")
        val cycle = (s.cycleSeconds * sr).toInt()
        val r = SampleRenderer(levelBank, s, notesOnly)
        val out = FloatArray(3 * cycle)
        render(r, out, 0, 100)
        r.repeatCycle(1)
        assertEquals(0L, r.repeatingCycle)
    }

    @Test
    fun repeatsDoNotDependOnBlockSize() {
        val s = schedule("varnam-1", tempo = 90)
        val options = RenderOptions(harmoniumGain = 0.6f, drone = true, droneStyle = DroneStyle.HARMONIUM, click = true, countIn = true)
        fun run(block: Int): FloatArray {
            val r = SampleRenderer(levelBank, s, options)
            val cycle = (s.cycleSeconds * sr).toInt()
            val out = FloatArray(r.countInFrames + s.cycles * cycle + 4 * cycle + sr)
            var pos = render(r, out, 0, r.countInFrames + 2 * cycle + cycle / 3, block)
            r.repeatCycle(r.cycleToRepeat(pos.toLong())!!)
            pos = render(r, out, pos, 3 * cycle, block)
            r.continueAfterRepeat()
            renderToEnd(r, out, pos, block)
            return out
        }
        val a = run(4096)
        val b = run(333)
        for (i in a.indices) assertEquals(a[i], b[i], "frame $i")
    }

    @Test
    fun theDroneLastsThroughRepeatsWithoutLooping() {
        val s = schedule("sarali-3")
        val options = RenderOptions(harmoniumGain = 0f, drone = true, droneStyle = DroneStyle.HARMONIUM)
        val r = SampleRenderer(levelBank, s, options)
        val cycle = (s.cycleSeconds * sr).toInt()
        val out = FloatArray(r.totalFrames.toInt() + cycle)
        var pos = render(r, out, 0, s.cycles * cycle - cycle / 2) // in the last cycle
        r.repeatCycle(r.cycleToRepeat(pos.toLong())!!)
        pos = render(r, out, pos, cycle)
        r.continueAfterRepeat()
        pos = renderToEnd(r, out, pos)
        assertEquals(out.size, pos)
        // Still at full level a cycle after the performance would have ended.
        val at = s.cycles * cycle + cycle / 2
        assertTrue(abs(out[at]) > 0.01f, "drone at $at: ${out[at]}")
    }

    @Test
    fun loopingRepeatsTheLastCycleOfAPassWithoutStartingTheNext() {
        val s = schedule("sarali-3")
        val cycle = (s.cycleSeconds * sr).toInt()
        val r = SampleRenderer(levelBank, s, notesOnly.copy(loop = true))
        val out = FloatArray((s.cycles + 3) * cycle)
        val last = s.cycles - 1
        var pos = render(r, out, 0, last * cycle + cycle / 2)
        r.repeatCycle(last.toLong())
        pos = render(r, out, pos, 2 * cycle)
        val heard = (s.cycles + 1) * cycle.toLong() + cycle / 4
        assertEquals(0, r.passAt(heard))
        assertEquals(last, r.cycleInPassAt(heard))
        r.continueAfterRepeat()
        render(r, out, pos, out.size - pos)
        assertEquals(1, r.passAt((s.cycles + 2) * cycle.toLong() + cycle / 4))
    }

    @Test
    fun singAlongJudgesARepeatedCycleAfresh() {
        val s = schedule("sarali-3")
        val cycle = (s.cycleSeconds * sr).toInt()
        val r = SampleRenderer(levelBank, s, notesOnly)
        val sink = FloatArray(r.totalFrames.toInt() + cycle)
        var pos = render(r, sink, 0, cycle / 2)
        r.repeatCycle(0)
        pos = render(r, sink, pos, cycle)
        r.continueAfterRepeat()
        renderToEnd(r, sink, pos)

        val session = SingAlongSession(s, SingAlongClock.forRenderer(r), sr, strictness = Strictness.PRECISE)
        val outcomes = mutableListOf<com.baijum.purandara.singalong.FrameOutcome>()
        val step = 0.01
        var t = 0.0
        val end = (s.cycles + 1) * s.cycleSeconds
        while (t < end) {
            val u = r.passSeconds((t * sr).toLong())
            val note = s.notes.lastOrNull { it.startSeconds <= u && u < it.startSeconds + it.durationSeconds }
            // A semitone sharp the first time through cycle 0, in tune after.
            val hz = note?.frequencyHz?.let { if (t < s.cycleSeconds) it * 2.0.pow(1.0 / 12) else it }
            outcomes += session.onFrame(PitchFrame(t, hz, 0.2f))
            t += step
        }
        val result = assertNotNull(session.finish())
        assertEquals(1, outcomes.count { it.rewoundToSeconds != null })
        assertEquals(0.0, outcomes.first { it.rewoundToSeconds != null }.rewoundToSeconds!!, 1e-9)
        assertEquals(s.notes.size, result.marks.size, "each note judged once in the result")
        fun inTune(m: NoteMark) = (m.result as? NoteResult.Judged)?.let { it.sung == null && abs(it.cents) < 30 } == true
        val judged = result.marks.filter { it.result is NoteResult.Judged }
        assertTrue(judged.size >= s.notes.size - 1 && judged.all(::inTune), "the cycle sung again in tune replaces the first time: ${result.marks}")
        val firstTime = outcomes.takeWhile { it.rewoundToSeconds == null }.flatMap { it.newMarks }
        assertTrue(firstTime.isNotEmpty() && firstTime.none(::inTune), "the first time was sharp")
    }
}
