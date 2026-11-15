package com.baijum.purandara.pitch

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwiftF0Test {
    // ── Range ────────────────────────────────────────────────────────

    @Test
    fun rangeIsMandraPaToTaraPaWithASemitoneOfMargin() {
        val r = SwiftF0.range(146.83)
        assertEquals(146.83 * 0.75 / 1.059463, r.minHz.toDouble(), 0.05)
        assertEquals(146.83 * 3 * 1.059463, r.maxHz.toDouble(), 0.05)
    }

    @Test
    fun noSaSearchesTheWholeModelRange() {
        assertEquals(PitchRange.FULL, SwiftF0.range(null))
        assertEquals(PitchRange(SwiftF0.MIN_HZ, SwiftF0.MAX_HZ), SwiftF0.range(0.0))
    }

    @Test
    fun rangeStaysInsideTheModelRange() {
        val high = SwiftF0.range(1000.0)
        assertEquals(SwiftF0.MAX_HZ, high.maxHz)
        val low = SwiftF0.range(40.0)
        assertEquals(SwiftF0.MIN_HZ, low.minHz)
    }

    // ── Silence gate ─────────────────────────────────────────────────

    @Test
    fun silenceGateZeroesQuietHopsOnly() {
        val audio = FloatArray(SwiftF0.HOP * 3)
        for (i in SwiftF0.HOP until 2 * SwiftF0.HOP) audio[i] = 0.01f
        audio[2 * SwiftF0.HOP + 5] = -0.002f
        val confidence = floatArrayOf(0.9f, 0.9f, 0.9f, 0.9f)
        SwiftF0.silenceGate(audio, 0, audio.size, confidence)
        assertEquals(listOf(0f, 0.9f, 0.9f, 0f), confidence.toList(), "negative peaks count; past the end is silent")
    }

    // ── Stream resampler ─────────────────────────────────────────────

    private fun signal(n: Int, rate: Int) = FloatArray(n) { i ->
        (0.4 * sin(2 * PI * 220.0 * i / rate) + 0.2 * sin(2 * PI * 3100.0 * i / rate)).toFloat()
    }

    private fun streamed(x: FloatArray, rate: Int, pieces: List<Int>): FloatArray {
        val r = StreamResampler(rate)
        val out = ArrayList<Float>()
        var at = 0
        var p = 0
        while (at < x.size) {
            val n = minOf(pieces[p++ % pieces.size], x.size - at)
            val y = r.process(x, at, n)
            for (i in 0 until r.lastCount) out.add(y[i])
            at += n
        }
        return out.toFloatArray()
    }

    @Test
    fun streamedResamplingMatchesTheWholeSignal() {
        for (rate in listOf(48_000, 44_100)) {
            val x = signal(20_000, rate)
            val whole = AudioResampler(rate).downsample(x).copyOf()
            val stream = streamed(x, rate, listOf(512, 37, 1, 4096, 300))
            assertTrue(abs(stream.size - whole.size) <= 1, "about the same length at $rate: ${stream.size} vs ${whole.size}")
            val interior = minOf(stream.size, whole.size) - 2
            for (i in 0 until interior) assertEquals(whole[i], stream[i], 1e-5f, "sample $i at $rate")
        }
    }

    @Test
    fun streamedResamplingDoesNotDependOnPieceSizes() {
        val x = signal(9_000, 48_000)
        val reference = streamed(x, 48_000, listOf(9_000))
        runBlocking {
            checkAll(30, Arb.int(1..3000), Arb.int(1..700)) { a, b ->
                val s = streamed(x, 48_000, listOf(a, b))
                assertEquals(reference.toList(), s.toList())
            }
        }
    }

    // ── Chunker ──────────────────────────────────────────────────────

    /**
     * A stand-in for the model that depends only on the audio at each frame,
     * with the real model's edge effect: the first and last frame of any
     * input are unvoiced. Chunking must never keep an edge frame except at
     * the true ends of the recording.
     */
    private class FakeModel(val failing: Boolean = false) : SwiftF0Model {
        var calls = 0
        var lastRange: PitchRange? = null

        override fun run(audio16k: FloatArray, range: PitchRange): SwiftF0Output? {
            calls++
            lastRange = range
            if (failing) return null
            val frames = max(1, audio16k.size / SwiftF0.HOP)
            val hz = FloatArray(frames) { k -> 100f + 1000f * abs(audio16k[k * SwiftF0.HOP]) }
            val confidence = FloatArray(frames) { k -> if (k == 0 || k == frames - 1) 0.1f else 0.9f }
            return SwiftF0Output(hz, confidence)
        }
    }

    private fun audio(n: Int) = FloatArray(n) { i -> 0.1f + 0.5f * ((i * 7919) % 9973) / 9973f }

    private fun chunked(x: FloatArray, chunkFrames: Int, piece: Int, model: SwiftF0Model = FakeModel()): SwiftF0Track {
        val c = SwiftF0Chunker(model, PitchRange.FULL, chunkFrames)
        var at = 0
        while (at < x.size) {
            val n = minOf(piece, x.size - at)
            c.feed(x, at, n)
            at += n
        }
        return c.finish()
    }

    @Test
    fun chunkedFramesAreTheWholeRecordingsFrames() {
        val x = audio(SwiftF0.HOP * 600 + 100)
        val frames = x.size / SwiftF0.HOP
        for ((chunk, piece) in listOf(1 to 1000, 7 to 333, 250 to 4096, 10_000 to 50_000)) {
            val t = chunked(x, chunk, piece)
            assertEquals(frames, t.size, "one frame per hop (chunk $chunk)")
            for (f in 0 until frames) {
                val edge = f == 0 || f == frames - 1
                if (edge) {
                    assertEquals(0f, t.hz[f], "frame $f is an edge of the recording (chunk $chunk)")
                } else {
                    assertEquals(100f + 1000f * x[f * SwiftF0.HOP], t.hz[f], 1e-3f, "frame $f (chunk $chunk)")
                    assertEquals(0.9f, t.confidence[f], "frame $f (chunk $chunk)")
                }
            }
        }
    }

    @Test
    fun chunkingRunsTheModelOncePerChunk() {
        val model = FakeModel()
        chunked(audio(SwiftF0.HOP * 1000), SwiftF0.CHUNK_FRAMES, 4096, model)
        assertEquals(4, model.calls)
    }

    @Test
    fun audioShorterThanAHopGivesOneFrame() {
        val t = chunked(audio(100), SwiftF0.CHUNK_FRAMES, 100)
        assertEquals(1, t.size)
    }

    @Test
    fun aFailedModelMarksItsFrames() {
        val t = chunked(audio(SwiftF0.HOP * 40), 16, 512, FakeModel(failing = true))
        assertTrue(t.confidence.all { it == SwiftF0Track.FAILED })
        assertTrue(t.hz.all { it == 0f })
    }

    @Test
    fun silentStretchesHaveNoPitch() {
        val x = audio(SwiftF0.HOP * 100)
        for (i in SwiftF0.HOP * 40 until SwiftF0.HOP * 60) x[i] = 0f
        val t = chunked(x, 16, 1000)
        for (f in 40 until 60) assertEquals(0f, t.hz[f], "frame $f is digital silence")
        assertTrue(t.hz[30] > 0f && t.hz[70] > 0f)
    }

    @Test
    fun theRangeIsPassedToTheModel() {
        val model = FakeModel()
        val range = SwiftF0.range(146.83)
        SwiftF0Chunker(model, range).apply { feed(audio(5000)) }.finish()
        assertEquals(range, model.lastRange)
    }

    @Test
    fun trackLooksUpTheNearestFrame() {
        val t = SwiftF0Track(FloatArray(10), FloatArray(10))
        assertEquals(0, t.indexAt(0.0))
        assertEquals(3, t.indexAt(3 * 0.016 + 0.007))
        assertEquals(9, t.indexAt(100.0))
        assertEquals(-1, SwiftF0Track(FloatArray(0), FloatArray(0)).indexAt(1.0))
    }

    // ── Live buffer ──────────────────────────────────────────────────

    @Test
    fun liveInputWaitsForAFullBufferThenKeepsTheNewestAudio() {
        val live = SwiftF0Live(48_000)
        val hop = FloatArray(512) { 0.2f }
        var hops = 0
        while (live.input() == null) {
            live.push(hop)
            hops++
        }
        assertEquals(SwiftF0.LIVE_BUFFER_SAMPLES, live.input()!!.size)
        assertTrue(hops * 512 / 3 >= SwiftF0.LIVE_BUFFER_SAMPLES, "filled only once enough audio arrived")
        live.push(FloatArray(512) { 0.7f })
        val input = live.input()!!
        assertEquals(0.7f, input.last(), 1e-4f, "newest audio last")
        assertEquals(0.2f, input.first(), 1e-4f)
    }

    @Test
    fun liveReadsTheFrameFourFromTheEnd() {
        val live = SwiftF0Live(48_000)
        live.range = PitchRange(80f, 400f)
        val input = FloatArray(SwiftF0.LIVE_BUFFER_SAMPLES) { 0.3f }
        val frames = SwiftF0.LIVE_BUFFER_SAMPLES / SwiftF0.HOP
        val hz = FloatArray(frames) { 150f + it }
        val confidence = FloatArray(frames) { 0.9f }
        val result = assertNotNull(live.read(input, SwiftF0Output(hz, confidence)))
        assertEquals(150.0 + frames - 1 - SwiftF0.LIVE_AHEAD_FRAMES, result.frequencyHz, 1e-3)

        confidence[frames - 1 - SwiftF0.LIVE_AHEAD_FRAMES] = 0.3f
        assertNull(live.read(input, SwiftF0Output(hz, confidence)), "low confidence is no pitch")
        confidence[frames - 1 - SwiftF0.LIVE_AHEAD_FRAMES] = 0.9f
        hz[frames - 1 - SwiftF0.LIVE_AHEAD_FRAMES] = 900f
        assertNull(live.read(input, SwiftF0Output(hz, confidence)), "outside the range is no pitch")
        assertNull(live.read(FloatArray(input.size), SwiftF0Output(FloatArray(frames) { 150f }, FloatArray(frames) { 0.9f })), "silence")
    }
}
