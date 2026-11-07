package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiParser
import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SampleRendererTest {
    private val sr = 44_100
    private val saC3 = Pitch.midiToHz(48)
    private val clipFrames = (2.5 * sr).toInt()

    /** Pure sine at each MIDI pitch: lets pitch be measured to a fraction of a cent. */
    private val sineBank = SampleBank(
        sr,
        HarmoniumBank.midiRange.associateWith { midi ->
            val hz = Pitch.midiToHz(midi)
            FloatArray(clipFrames) { (0.5 * sin(2 * PI * hz * it / sr)).toFloat() }
        },
    )

    /** A constant level per MIDI note: shows exactly which note sounds when. */
    private fun levelOf(midi: Int) = (midi - 30) / 100f
    private val levelBank = SampleBank(sr, HarmoniumBank.midiRange.associateWith { m -> FloatArray(clipFrames) { levelOf(m) } })

    private val notesOnly = RenderOptions(harmoniumGain = 1f, drone = false, click = false)

    private fun schedule(id: String, tempo: Int = 60, kala: Int = 1) =
        Schedule.of(VarisaiCatalog.byId(id)!!, PerformanceSettings(saHz = saC3, tempo = tempo, kala = kala))

    private fun renderAll(renderer: SampleRenderer, block: Int = 4096): FloatArray {
        val out = FloatArray(renderer.totalFrames.toInt())
        var pos = 0
        while (true) {
            val n = renderer.render(out, pos, minOf(block, out.size - pos))
            if (n == 0) break
            pos += n
        }
        assertEquals(renderer.totalFrames, pos.toLong())
        return out
    }

    @Test
    fun everyNoteIsAtItsJustIntonationPitch() {
        val s = schedule("plain-scale-1")
        val out = renderAll(SampleRenderer(sineBank, s, notesOnly))
        s.notes.forEach { n ->
            val from = ((n.startSeconds + 0.1) * sr).toInt()
            val to = ((n.startSeconds + n.durationSeconds - 0.1) * sr).toInt()
            val hz = frequencyByZeroCrossings(out, from, to)
            val cents = Pitch.cents(n.frequencyHz, hz)
            assertTrue(abs(cents) < 0.5, "${n.written} at ${n.startSeconds}s: $hz Hz vs ${n.frequencyHz} Hz ($cents cents)")
        }
    }

    @Test
    fun noteChangesHappenOnTheBeat() {
        val s = schedule("sarali-1")
        val out = renderAll(SampleRenderer(levelBank, s, notesOnly))
        s.notes.zipWithNext().filter { (a, b) -> a.playback.midi != b.playback.midi }.forEach { (a, b) ->
            val mid = (levelOf(a.playback.midi) + levelOf(b.playback.midi)) / 2
            val boundary = (b.startSeconds * sr).toInt()
            val window = (0.05 * sr).toInt()
            val rising = levelOf(b.playback.midi) > levelOf(a.playback.midi)
            val crossing = (boundary - window until boundary + window).first { t ->
                if (rising) out[t] >= mid else out[t] <= mid
            }
            val errorMs = (crossing - boundary) * 1000.0 / sr
            assertTrue(abs(errorMs) <= 1.0, "${b.written} at ${b.startSeconds}s: change ${errorMs}ms off the beat")
        }
    }

    @Test
    fun karvaiHoldsWithoutRestarting() {
        // Sarali 4: P held for two aksharas (4 s at tempo 30, longer than the 2.5 s clip).
        val s = schedule("sarali-4", tempo = 30)
        val pa = s.notes[4]
        val out = renderAll(SampleRenderer(sineBank, s, notesOnly))
        val from = ((pa.startSeconds + 0.05) * sr).toInt()
        val to = ((pa.startSeconds + pa.durationSeconds - 0.05) * sr).toInt()
        // Smooth: no sample-to-sample jump beyond a sine's steepest slope.
        val maxStep = 2 * PI * pa.frequencyHz * 0.5 / sr * 1.1
        for (t in from + 1 until to) assertTrue(abs(out[t] - out[t - 1]) <= maxStep, "jump at ${t.toDouble() / sr}s")
        // Steady: loudness stays within 0.5 dB across the loop points.
        val window = (0.05 * sr).toInt()
        val levels = (from until to - window step window).map { rmsDb(out, it, it + window) }
        assertTrue(levels.max() - levels.min() < 0.5, "level varies ${levels.min()}..${levels.max()} dB")
        assertTrue(abs(Pitch.cents(pa.frequencyHz, frequencyByZeroCrossings(out, from, to))) < 0.5)
    }

    @Test
    fun blockSizeDoesNotChangeTheOutput() {
        val s = schedule("janta-1", kala = 2)
        val options = RenderOptions(drone = true, click = true)
        val whole = renderAll(SampleRenderer(sineBank, s, options), block = Int.MAX_VALUE / 2)
        val chunked = renderAll(SampleRenderer(sineBank, s, options), block = 333)
        assertTrue(whole.contentEquals(chunked))
    }

    @Test
    fun lengthIsPerformancePlusRelease() {
        val s = schedule("sarali-1")
        val r = SampleRenderer(sineBank, s, notesOnly)
        assertEquals(32 * sr, r.performanceFrames)
        assertEquals((32 * sr + (0.25 * sr).toInt()).toLong(), r.totalFrames)
        val out = renderAll(r)
        assertEquals(0f, out.last(), 1e-3f)
        assertEquals(0, r.render(FloatArray(10)))
        r.reset()
        assertEquals(10, r.render(FloatArray(10)))
    }

    @Test
    fun droneFadesInAndOutAndSoundsThroughout() {
        val s = schedule("plain-scale-1")
        val out = renderAll(SampleRenderer(sineBank, s, RenderOptions(harmoniumGain = 0f, drone = true, droneGain = 1f)))
        assertEquals(0f, out.first(), 1e-6f)
        assertEquals(0f, out.last(), 1e-3f)
        val middle = rmsDb(out, 5 * sr, 10 * sr)
        assertTrue(middle > -20.0, "drone too quiet: $middle dB")
        // Dominant period is Sa's: Sa, Pa and S′ share Sa's period up to octave.
        val hz = frequencyByZeroCrossings(out, 5 * sr, 10 * sr)
        assertTrue(hz > 0)
    }

    @Test
    fun clicksMarkEveryBeatWithAccentsOnClaps() {
        val s = schedule("plain-scale-1")
        val out = renderAll(SampleRenderer(sineBank, s, RenderOptions(harmoniumGain = 0f, drone = false, click = true)))
        val peaks = s.beats.map { b ->
            val start = (b.startSeconds * sr).toInt()
            (start until start + (0.03 * sr).toInt()).maxOf { abs(out[it]) }
        }
        assertTrue(peaks.all { it > 0.05f }, "every beat has a click")
        val claps = s.beats.indices.filter { s.beats[it].beatInAvarta in listOf(0, 4, 6) }.map { peaks[it] }
        val others = s.beats.indices.filter { s.beats[it].beatInAvarta !in listOf(0, 4, 6) }.map { peaks[it] }
        assertTrue(claps.min() > others.max(), "claps should be accented")
        // Silence between clicks.
        assertEquals(0f, out[(0.5 * sr).toInt()], 1e-6f)
    }

    @Test
    fun clapCountAndWaveEachHaveTheirOwnClick() {
        val s = schedule("plain-scale-1")
        val out = renderAll(SampleRenderer(sineBank, s, RenderOptions(harmoniumGain = 0f, drone = false, click = true)))
        val n = (0.03 * sr).toInt()
        fun click(beat: Int): List<Float> {
            val start = (s.beats[beat].startSeconds * sr).toInt()
            return (start until start + n).map { out[it] }
        }
        // Adi: x 1 2 3 x v x v. The same action always sounds the same.
        assertEquals(click(1), click(2))
        assertEquals(click(5), click(7))
        assertEquals(click(0), click(4))
        val clap = click(0)
        val count = click(1)
        val wave = click(5)
        assertTrue(clap != count && count != wave && clap != wave, "three different clicks")
        assertTrue(clap.maxOf { abs(it) } > maxOf(count.maxOf { abs(it) }, wave.maxOf { abs(it) }), "the clap is loudest")
    }

    @Test
    fun fullMixNeverClips() {
        val s = schedule("dhattu-1", tempo = 120, kala = 4)
        val out = renderAll(SampleRenderer(sineBank, s, RenderOptions(drone = true, click = true)))
        assertTrue(out.all { abs(it) < 1f }, "peak ${out.maxOf { abs(it) }}")
    }

    private fun renderFrames(renderer: SampleRenderer, frames: Int, block: Int = 4096): FloatArray {
        val out = FloatArray(frames)
        var pos = 0
        while (pos < frames) {
            val n = renderer.render(out, pos, minOf(block, frames - pos))
            assertTrue(n > 0, "looping renderer stopped at $pos")
            pos += n
        }
        return out
    }

    @Test
    fun loopingRepeatsOnTheBeatWithoutAGap() {
        val s = schedule("sarali-1")
        val r = SampleRenderer(levelBank, s, notesOnly.copy(loop = true))
        assertEquals(Long.MAX_VALUE, r.totalFrames)
        val pass = r.performanceFrames
        val out = renderFrames(r, pass * 2 + sr)
        // Pass 2 sounds exactly like pass 1 once past the first boundary.
        for (t in sr until pass - sr) assertEquals(out[t], out[t + pass], 1e-6f, "frame $t")
        // Across the boundary the level never drops to silence: the last note
        // (S) crossfades into the first (S) with no gap.
        val sa = levelOf(s.notes.first().playback.midi)
        for (t in pass - sr / 10 until pass + sr / 10) assertTrue(out[t] > sa * 0.9f, "dip at ${t.toDouble() / sr}s: ${out[t]}")
        // Note changes in the second pass are still on the beat.
        val change = s.notes[1]
        val boundary = pass + (change.startSeconds * sr).toInt()
        val mid = (levelOf(s.notes[0].playback.midi) + levelOf(change.playback.midi)) / 2
        val crossing = (boundary - sr / 20 until boundary + sr / 20).first { out[it] >= mid }
        assertTrue(abs(crossing - boundary) * 1000.0 / sr <= 1.0)
    }

    @Test
    fun loopingIsSmoothAcrossTheBoundary() {
        val s = schedule("plain-scale-1")
        val r = SampleRenderer(sineBank, s, RenderOptions(harmoniumGain = 0.5f, drone = true, droneGain = 0.5f, loop = true))
        val out = renderFrames(r, r.performanceFrames * 2)
        val maxStep = 2 * PI * 600.0 * 1.5 / sr // generous bound for the sum of all voices
        val p = r.performanceFrames
        for (t in p - sr / 5 until p + sr / 5) assertTrue(abs(out[t] - out[t - 1]) < maxStep, "jump at ${t.toDouble() / sr}s")
    }

    @Test
    fun loopingBlockSizeDoesNotChangeTheOutput() {
        val s = schedule("janta-1", kala = 2)
        val options = RenderOptions(drone = true, click = true, loop = true)
        val frames = SampleRenderer(sineBank, s, options).performanceFrames * 2 + 1234
        val whole = renderFrames(SampleRenderer(sineBank, s, options), frames, block = frames)
        val chunked = renderFrames(SampleRenderer(sineBank, s, options), frames, block = 777)
        assertTrue(whole.contentEquals(chunked))
    }

    @Test
    fun passSecondsWrapsWhenLooping() {
        val s = schedule("plain-scale-1")
        val looping = SampleRenderer(sineBank, s, notesOnly.copy(loop = true))
        assertEquals(1.5, looping.passSeconds((16 * sr + 1.5 * sr).toLong()), 1e-9)
        val once = SampleRenderer(sineBank, s, notesOnly)
        assertEquals(17.5, once.passSeconds((17.5 * sr).toLong()), 1e-9)
    }

    @Test
    fun fadeOutStopsSmoothly() {
        val s = schedule("plain-scale-1")
        val r = SampleRenderer(sineBank, s, RenderOptions(loop = true))
        renderFrames(r, 3 * sr)
        r.fadeOut(0.08)
        val tail = FloatArray(sr)
        val n = r.render(tail)
        assertEquals((0.08 * sr).toInt(), n)
        assertTrue(abs(tail[n - 1]) < 0.01f, "ends near silence: ${tail[n - 1]}")
        assertEquals(0, r.render(tail))
        r.reset()
        assertTrue(r.render(tail) > 0)
    }

    @Test
    fun countInPlaysOneAvartaOfClicksBeforeTheFirstNote() {
        val s = schedule("plain-scale-1") // tempo 60: count-in is 8 s
        val r = SampleRenderer(levelBank, s, notesOnly.copy(countIn = true))
        assertEquals(8 * sr, r.countInFrames)
        assertEquals((8 * sr + 16 * sr + (0.25 * sr).toInt()).toLong(), r.totalFrames)
        val out = renderAll(r)
        // Count-in: a click at each beat and silence in between.
        for (beat in 0 until 8) {
            val start = beat * sr
            assertTrue((start until start + sr / 50).maxOf { abs(out[it]) } > 0.1f, "no click on count-in beat $beat")
            assertEquals(0f, out[start + sr / 2], 1e-6f)
        }
        // The first note starts exactly at the end of the count-in.
        assertEquals(levelOf(s.notes[0].playback.midi), out[8 * sr + sr / 2], 1e-6f)
        assertEquals(null, r.countInBeat(8L * sr))
        assertEquals(0, r.countInBeat(0))
        assertEquals(7, r.countInBeat(7L * sr + 10))
        assertEquals(-1.0, r.passSeconds(7L * sr), 1e-9)
        assertEquals(0.5, r.passSeconds(8L * sr + sr / 2), 1e-9)
    }

    @Test
    fun twoKalaiCountInTicksSixteenCounts() {
        val parser = VarisaiParser()
        val v = parser.parse("1\nkalai: 2\nS R G M | P D | N S′", VarisaiSet.SARALI).single()
        val s = Schedule.of(v, PerformanceSettings(saHz = saC3, tempo = 60))
        val r = SampleRenderer(levelBank, s, notesOnly.copy(countIn = true))
        // One cycle of count-in, but 16 half-second counts.
        assertEquals(8 * sr, r.countInFrames)
        val out = renderAll(r)
        for (count in 0 until 16) {
            val start = (count * 0.5 * sr).toInt()
            assertTrue((start until start + sr / 50).maxOf { abs(out[it]) } > 0.05f, "no click on count-in count $count")
        }
        // A second count is softer than the kriya it follows.
        val kriyaPeak = (0 until sr / 50).maxOf { abs(out[it]) }
        val secondPeak = (sr / 2 until sr / 2 + sr / 50).maxOf { abs(out[it]) }
        assertTrue(secondPeak < kriyaPeak * 0.8, "second count $secondPeak should be softer than the kriya $kriyaPeak")
        // The count-in tracks counts, not kriyas.
        assertEquals(0, r.countInBeat(0))
        assertEquals(1, r.countInBeat((0.5 * sr).toLong()))
        assertEquals(15, r.countInBeat((7.5 * sr).toLong()))
        assertEquals(null, r.countInBeat(8L * sr))
    }

    @Test
    fun countInPlaysOnceWhenLooping() {
        val s = schedule("sarali-1")
        val r = SampleRenderer(levelBank, s, notesOnly.copy(loop = true, countIn = true))
        val pass = r.performanceFrames
        val start = r.countInFrames
        val out = renderFrames(r, start + pass * 2 + sr)
        for (t in start + sr until start + pass - sr) assertEquals(out[t], out[t + pass], 1e-6f, "frame $t")
        assertEquals(1.5, r.passSeconds((start + pass + 1.5 * sr).toLong()), 1e-9)
        assertEquals(null, r.countInBeat((start + pass).toLong()))
    }

    @Test
    fun countInBlockSizeDoesNotChangeTheOutput() {
        val s = schedule("janta-1")
        val options = RenderOptions(drone = true, click = true, countIn = true)
        val whole = renderAll(SampleRenderer(sineBank, s, options), block = Int.MAX_VALUE / 2)
        val chunked = renderAll(SampleRenderer(sineBank, s, options), block = 555)
        assertTrue(whole.contentEquals(chunked))
    }

    @Test
    fun practiceLevelsScaleTheMix() {
        val loud = RenderOptions.practice(drone = true, click = false, loop = false, countIn = false, harmoniumLevel = 1f, droneLevel = 1f)
        val quiet = RenderOptions.practice(drone = true, click = false, loop = false, countIn = false, harmoniumLevel = 0.5f, droneLevel = 0f)
        assertEquals(loud.harmoniumGain / 2, quiet.harmoniumGain, 1e-6f)
        assertEquals(0f, quiet.droneGain)
        val clamped = RenderOptions.practice(drone = true, click = false, loop = false, countIn = true, harmoniumLevel = 7f, droneLevel = -1f)
        assertEquals(loud.harmoniumGain, clamped.harmoniumGain)
        assertEquals(0f, clamped.droneGain)
        assertTrue(clamped.countIn)
        // Everything at full level still never clips.
        val s = schedule("dhattu-1", tempo = 120, kala = 4)
        val out = renderAll(SampleRenderer(sineBank, s, loud.copy(click = true, countIn = true)))
        assertTrue(out.all { abs(it) < 1f }, "peak ${out.maxOf { abs(it) }}")
    }

    /** Synthetic tanpura loops: a pure tone at each loop's Sa, a whole number of periods long (so it wraps seamlessly). */
    private fun tanpuraBank(): SampleBank {
        val loops = com.baijum.purandara.domain.TanpuraBank.SA_MIDIS.associateWith { midi ->
            val hz = Pitch.midiToHz(midi)
            val periods = (2.0 * hz).toInt()
            val n = (periods * sr / hz).toInt()
            val exactHz = periods * sr.toDouble() / n
            FloatArray(n) { (0.3 * sin(2 * PI * exactHz * it / sr)).toFloat() }
        }
        val harmonium = HarmoniumBank.midiRange.associateWith { FloatArray(clipFrames) }
        return SampleBank(sr, harmonium, loops)
    }

    @Test
    fun tanpuraDroneSoundsAtSaAndWrapsSeamlessly() {
        // Sa = C#3 (49): played from the D3 loop slowed by a semitone.
        val s = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, PerformanceSettings(saHz = Pitch.midiToHz(49)))
        val r = SampleRenderer(tanpuraBank(), s, RenderOptions(harmoniumGain = 0f, drone = true, droneGain = 1f, droneStyle = DroneStyle.TANPURA))
        val out = renderAll(r)
        val hz = frequencyByZeroCrossings(out, sr, 10 * sr)
        assertTrue(abs(Pitch.cents(Pitch.midiToHz(49), hz)) < 1.0, "tanpura drone at $hz Hz")
        // The 2 s loop wraps several times; no sample-to-sample jump beyond a sine's slope.
        val maxStep = 2 * PI * Pitch.midiToHz(49) * 0.3 * TANPURA_LEVEL / sr * 1.2
        for (t in sr until 10 * sr) assertTrue(abs(out[t] - out[t - 1]) < maxStep, "jump at ${t.toDouble() / sr}s")
    }

    @Test
    fun droneOnlyPlaysTheTanpuraAloneWithoutEnding() {
        val s = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, PerformanceSettings(saHz = Pitch.midiToHz(49)))
        val r = SampleRenderer(tanpuraBank(), s, RenderOptions.droneOnly(droneLevel = 1f))
        assertEquals(Long.MAX_VALUE, r.totalFrames, "loops until faded out")
        // Well past one pass of the exercise: still the drone at Sa, and nothing else.
        val out = renderFrames(r, r.performanceFrames + 10 * sr)
        val from = r.performanceFrames
        val hz = frequencyByZeroCrossings(out, from, from + 5 * sr)
        assertTrue(abs(Pitch.cents(Pitch.midiToHz(49), hz)) < 1.0, "drone at $hz Hz")
        assertTrue(rmsDb(out, from, from + 5 * sr) > -30.0)
    }

    @Test
    fun withoutTanpuraLoopsTheHarmoniumDronePlays() {
        val s = schedule("plain-scale-1")
        val r = SampleRenderer(sineBank, s, RenderOptions(harmoniumGain = 0f, drone = true, droneGain = 1f))
        val out = renderAll(r)
        assertTrue(rmsDb(out, 5 * sr, 10 * sr) > -20.0, "falls back to the harmonium drone")
    }

    @Test
    fun missingSampleIsRejected() {
        val partial = SampleBank(sr, mapOf(48 to FloatArray(clipFrames)))
        assertFailsWith<IllegalArgumentException> { SampleRenderer(partial, schedule("plain-scale-1"), notesOnly) }
    }

    @Test
    fun tooShortSampleIsRejected() {
        assertFailsWith<IllegalArgumentException> { SampleBank(sr, mapOf(48 to FloatArray(sr / 2))) }
    }

    /** Frequency from linearly interpolated upward zero crossings in [from, to). */
    private fun frequencyByZeroCrossings(x: FloatArray, from: Int, to: Int): Double {
        val crossings = mutableListOf<Double>()
        for (t in max(from, 1) until to) {
            if (x[t - 1] < 0f && x[t] >= 0f) crossings += t - 1 + x[t - 1] / (x[t - 1] - x[t]).toDouble()
        }
        require(crossings.size >= 2) { "no periodic signal in $from..$to" }
        return (crossings.size - 1) * sr / (crossings.last() - crossings.first())
    }

    private fun rmsDb(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (t in from until to) sum += x[t].toDouble() * x[t]
        return 20 * log10(sqrt(sum / (to - from)) + 1e-12)
    }
}
