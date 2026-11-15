package com.baijum.purandara.pitch

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The SwiftF0 v0.3.0 model's contract and the ways the app runs it
 * (docs/plan-swiftf0-upgrade.md). The platforms only run the ONNX session
 * ([SwiftF0Model]); chunking, the silence gate, the pitch range and reading
 * frames are here, so both apps do exactly the same.
 *
 * Constants follow the reference `swift_f0/core.py` at v0.3.0.
 */
object SwiftF0 {
    /** The model's input rate. */
    const val SAMPLE_RATE = 16_000

    /** One output frame per [HOP] input samples; frame k is centred on sample k·[HOP]. */
    const val HOP = 256

    const val MIN_HZ = 46.875f
    const val MAX_HZ = 2093.75f

    /** A frame is voiced at this confidence or above. */
    const val VOICED_CONFIDENCE = 0.5f

    /**
     * The model answers digital silence with random voiced frames, so a frame
     * whose hop peaks below this gets confidence 0 (outside the model, as in
     * the reference).
     */
    const val SILENCE_PEAK = 1e-3f

    /** Context for an exact frame: the reference's 11 frames before, 10 after. */
    const val CHUNK_LEFT_FRAMES = 11
    const val CHUNK_AHEAD_FRAMES = 10

    /**
     * Frames per call when running a whole recording. Any size gives the same
     * frames (the overlap makes each exact); 250 (4 s) keeps the audio held
     * back while waiting for a chunk small.
     */
    const val CHUNK_FRAMES = 250

    /**
     * Live listening reads the frame [LIVE_AHEAD_FRAMES] from the end of a
     * buffer with [LIVE_LEFT_FRAMES] before it: on real voices fewer frames
     * after it read junk near the top of the range, and these agree with the
     * full context on 99% of frames (the plan's Results).
     */
    const val LIVE_LEFT_FRAMES = 6
    const val LIVE_AHEAD_FRAMES = 4
    const val LIVE_BUFFER_SAMPLES = (LIVE_LEFT_FRAMES + 1 + LIVE_AHEAD_FRAMES) * HOP

    private val SEMITONE = 2.0.pow(1.0 / 12)
    private const val MANDRA_PA = 0.75
    private const val TARA_PA = 3.0

    /**
     * The pitch range to search around [saHz]: mandra Pa to tara Pa, with a
     * semitone of margin, inside the model's range. Without a Sa (Find my Sa)
     * the model's full range. A pitch outside the range gets confidence 0
     * rather than a wrong octave.
     */
    fun range(saHz: Double?): PitchRange {
        if (saHz == null || saHz <= 0.0) return PitchRange.FULL
        val low = (saHz * MANDRA_PA / SEMITONE).toFloat().coerceIn(MIN_HZ, MAX_HZ)
        val high = (saHz * TARA_PA * SEMITONE).toFloat().coerceIn(MIN_HZ, MAX_HZ)
        return if (low < high) PitchRange(low, high) else PitchRange.FULL
    }

    /**
     * Sets [confidence] to 0 for frames whose hop of [audio] (starting at
     * [audioOffset] for frame 0) peaks below [SILENCE_PEAK].
     */
    fun silenceGate(audio: FloatArray, audioOffset: Int, audioEnd: Int, confidence: FloatArray) {
        for (k in confidence.indices) {
            val from = audioOffset + k * HOP
            if (from >= audioEnd) {
                confidence[k] = 0f
                continue
            }
            val to = min(from + HOP, audioEnd)
            var peak = 0f
            for (i in from until to) {
                val a = audio[i]
                val m = if (a < 0f) -a else a
                if (m > peak) peak = m
            }
            if (peak < SILENCE_PEAK) confidence[k] = 0f
        }
    }
}

/** The band SwiftF0 searches, in Hz. */
data class PitchRange(val minHz: Float, val maxHz: Float) {
    companion object {
        val FULL = PitchRange(SwiftF0.MIN_HZ, SwiftF0.MAX_HZ)
    }
}

/**
 * One SwiftF0 run: per frame the pitch (Hz) and confidence. The platforms
 * convert the model's float64 pitch to Float.
 */
class SwiftF0Output(val hz: FloatArray, val confidence: FloatArray)

/** The platform's ONNX session for the SwiftF0 v0.3.0 model. */
fun interface SwiftF0Model {
    /**
     * Runs the model on [audio16k] (16 kHz mono, -1..1) searching [range].
     * Returns null if the model failed (the callers then use YIN alone).
     */
    fun run(audio16k: FloatArray, range: PitchRange): SwiftF0Output?
}

/**
 * Downsamples a continuous stream to 16 kHz with the same filter as
 * [AudioResampler] (a centred 5-tap moving average, then linear
 * interpolation), carrying its state across calls, so a stream resampled in
 * pieces of any size gives the same samples as resampled whole (apart from
 * the last two input samples, held back until more arrive).
 */
class StreamResampler(sourceRate: Int, private val targetRate: Int = SwiftF0.SAMPLE_RATE) {
    init {
        require(sourceRate >= targetRate) { "can only downsample: $sourceRate Hz -> $targetRate Hz" }
    }

    private val ratio = sourceRate.toDouble() / targetRate
    private val raw = FloatArray(RAW_HISTORY)
    private var rawCount = 0L
    private var previousFiltered = 0f
    private var filteredCount = 0L
    private var outCount = 0L
    private var out = FloatArray(0)

    /**
     * Resamples the next [count] samples of [input] (from [offset]). Returns
     * the new output samples; **the array is reused** by the next call, and
     * only its first [lastCount] entries are valid.
     */
    fun process(input: FloatArray, offset: Int = 0, count: Int = input.size): FloatArray {
        val needed = (count / ratio).toInt() + 2
        if (out.size < needed) out = FloatArray(needed)
        var produced = 0
        for (i in offset until offset + count) {
            raw[(rawCount % RAW_HISTORY).toInt()] = input[i]
            rawCount++
            val k = rawCount - 1 - HALF
            if (k < 0) continue
            val filtered = average(k)
            if (filteredCount > 0) {
                var pos = outCount * ratio
                while (pos < k) {
                    val frac = (pos - (k - 1)).toFloat()
                    out[produced++] = previousFiltered * (1f - frac) + filtered * frac
                    outCount++
                    pos = outCount * ratio
                }
            }
            previousFiltered = filtered
            filteredCount = k + 1
        }
        lastCount = produced
        return out
    }

    /** How many samples the last [process] produced. */
    var lastCount = 0
        private set

    private fun average(k: Long): Float {
        val lo = max(0L, k - HALF)
        val hi = k + HALF
        var sum = 0f
        for (j in lo..hi) sum += raw[(j % RAW_HISTORY).toInt()]
        return sum / (hi - lo + 1)
    }

    private companion object {
        const val HALF = 2
        const val RAW_HISTORY = 2 * HALF + 1
    }
}

/**
 * SwiftF0 frames for a whole recording: [hz] (0 = no pitch) and
 * [confidence] per frame, every [SwiftF0.HOP] samples at 16 kHz. A frame
 * where the model failed has confidence -1.
 */
class SwiftF0Track(val hz: FloatArray, val confidence: FloatArray) {
    val size: Int get() = hz.size

    /** The frame nearest [seconds], or -1 if there are none. */
    fun indexAt(seconds: Double): Int {
        if (hz.isEmpty()) return -1
        return (seconds * SwiftF0.SAMPLE_RATE / SwiftF0.HOP).roundToInt().coerceIn(0, hz.size - 1)
    }

    companion object {
        const val FAILED = -1f
    }
}

/**
 * Runs SwiftF0 over a recording as it is decoded, in chunks of
 * [chunkFrames] frames with the reference overlap
 * ([SwiftF0.CHUNK_LEFT_FRAMES] before, [SwiftF0.CHUNK_AHEAD_FRAMES] after),
 * so every frame is computed exactly as the model would over the whole
 * recording at once. Holds at most about one chunk of 16 kHz audio.
 */
class SwiftF0Chunker(
    private val model: SwiftF0Model,
    private val range: PitchRange,
    private val chunkFrames: Int = SwiftF0.CHUNK_FRAMES,
) {
    init {
        require(chunkFrames >= 1) { "chunkFrames must be at least 1" }
    }

    private var buffer = FloatArray(0)
    private var bufferLength = 0
    private var bufferStart = 0L // absolute index of buffer[0]
    private var totalSamples = 0L
    /** Pitch per frame so far (only indices below [framesReady] are set; the array grows). */
    var hz = FloatArray(INITIAL_FRAMES)
        private set

    /** Confidence per frame, as [hz]. */
    var confidence = FloatArray(INITIAL_FRAMES)
        private set

    /** Frames computed so far (frame indices below this are final). */
    var framesReady = 0
        private set

    /** Feeds the next [count] 16 kHz samples of [samples] (from [offset]). */
    fun feed(samples: FloatArray, offset: Int = 0, count: Int = samples.size) {
        ensureBuffer(bufferLength + count)
        samples.copyInto(buffer, bufferLength, offset, offset + count)
        bufferLength += count
        totalSamples += count
        while ((totalSamples / SwiftF0.HOP) >= framesReady + chunkFrames + SwiftF0.CHUNK_AHEAD_FRAMES) {
            runChunk(framesReady, framesReady + chunkFrames, final = false)
        }
    }

    /** Runs what's left and returns every frame (at least one). */
    fun finish(): SwiftF0Track {
        val frames = max(1L, totalSamples / SwiftF0.HOP).toInt()
        while (framesReady < frames) {
            val end = min(framesReady + chunkFrames, frames)
            runChunk(framesReady, end, final = end == frames)
        }
        return SwiftF0Track(hz.copyOf(framesReady), confidence.copyOf(framesReady))
    }

    private fun runChunk(start: Int, end: Int, final: Boolean) {
        val left = max(0, start - SwiftF0.CHUNK_LEFT_FRAMES)
        val from = (left.toLong() * SwiftF0.HOP - bufferStart).toInt()
        val to = if (final) bufferLength else ((end + SwiftF0.CHUNK_AHEAD_FRAMES).toLong() * SwiftF0.HOP - bufferStart).toInt()
        val audio = buffer.copyOfRange(from, to)
        val output = model.run(audio, range)
        ensureFrames(end)
        for (f in start until end) {
            val k = f - left
            if (output == null) {
                hz[f] = 0f
                confidence[f] = SwiftF0Track.FAILED
            } else if (k < output.hz.size && k < output.confidence.size) {
                hz[f] = output.hz[k]
                confidence[f] = output.confidence[k]
            } else {
                hz[f] = 0f
                confidence[f] = 0f
            }
        }
        if (output != null) {
            val gate = FloatArray(end - start) { confidence[start + it] }
            SwiftF0.silenceGate(audio, (start - left) * SwiftF0.HOP, audio.size, gate)
            for (i in gate.indices) {
                confidence[start + i] = gate[i]
                if (gate[i] < SwiftF0.VOICED_CONFIDENCE) hz[start + i] = 0f
            }
        }
        framesReady = end
        // Keep the audio the next chunk needs: from its left context on.
        val keepFrom = max(0, end - SwiftF0.CHUNK_LEFT_FRAMES).toLong() * SwiftF0.HOP
        val drop = (keepFrom - bufferStart).toInt().coerceIn(0, bufferLength)
        if (drop > 0) {
            buffer.copyInto(buffer, 0, drop, bufferLength)
            bufferLength -= drop
            bufferStart += drop
        }
    }

    private fun ensureBuffer(size: Int) {
        if (buffer.size < size) buffer = buffer.copyOf(max(size, buffer.size * 2))
    }

    private fun ensureFrames(size: Int) {
        if (hz.size < size) {
            val n = max(size, hz.size * 2)
            hz = hz.copyOf(n)
            confidence = confidence.copyOf(n)
        }
    }

    private companion object {
        const val INITIAL_FRAMES = 1024
    }
}

/**
 * Live listening: a rolling [SwiftF0.LIVE_BUFFER_SAMPLES] buffer at 16 kHz,
 * fed every hop of capture audio, and the frame read from each model run
 * ([SwiftF0.LIVE_AHEAD_FRAMES] from the end, about 70 ms old). Because the
 * reading lags, live arbitration uses it only to correct octaves
 * (`PitchPipeline(..., octaveOnly = true)`). One instance per capture stream.
 */
class SwiftF0Live(captureRate: Int) {
    private val resampler = StreamResampler(captureRate)
    private val ring = FloatArray(SwiftF0.LIVE_BUFFER_SAMPLES)
    private var written = 0L

    /** The band to search; follows the chosen Sa. */
    var range: PitchRange = PitchRange.FULL

    /** Feeds the next [count] capture-rate samples (call for every hop, not only when the model runs). */
    fun push(samples: FloatArray, offset: Int = 0, count: Int = samples.size) {
        val out = resampler.process(samples, offset, count)
        for (i in 0 until resampler.lastCount) {
            ring[(written % ring.size).toInt()] = out[i]
            written++
        }
    }

    /** The model input (oldest first), or null until the buffer has filled once. */
    fun input(): FloatArray? {
        if (written < ring.size) return null
        val start = (written % ring.size).toInt()
        return FloatArray(ring.size) { ring[(start + it) % ring.size] }
    }

    /**
     * The reading from [output] of a run on [input]: a pitch, or null for no
     * pitch (quiet, unvoiced, outside [range]).
     */
    fun read(input: FloatArray, output: SwiftF0Output): NeuralPitchResult? {
        val k = output.hz.size - 1 - SwiftF0.LIVE_AHEAD_FRAMES
        if (k < 0 || k >= output.confidence.size) return null
        val confidence = floatArrayOf(output.confidence[k])
        SwiftF0.silenceGate(input, k * SwiftF0.HOP, input.size, confidence)
        val hz = output.hz[k]
        if (confidence[0] < SwiftF0.VOICED_CONFIDENCE || hz < range.minHz || hz > range.maxHz) return null
        return NeuralPitchResult(hz.toDouble(), confidence[0].toDouble())
    }
}
