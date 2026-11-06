// Ported from ukulele-companion (same author, MIT): AudioResampler.kt.
// Changes: instance class for any source rate (iOS microphones often run at
// 48 kHz), no lock.
package com.baijum.purandara.pitch

/**
 * Downsamples mono audio to 16 kHz for the SwiftF0 model.
 *
 * A 5-tap moving average attenuates content above the 8 kHz Nyquist limit
 * before linear interpolation (first null at sourceRate / 5: 8.8 kHz at
 * 44.1 kHz, 9.6 kHz at 48 kHz), enough for fundamentals up to ~2 kHz.
 *
 * One instance per stream: it reuses its buffers and is not thread-safe.
 */
class AudioResampler(private val sourceRate: Int, private val targetRate: Int = TARGET_RATE) {
    init {
        require(sourceRate >= targetRate) { "can only downsample: $sourceRate Hz -> $targetRate Hz" }
    }

    private val ratio = sourceRate.toDouble() / targetRate
    private var cachedInputSize = 0
    private var cachedFiltered = FloatArray(0)
    private var cachedOutput = FloatArray(0)

    /**
     * Returns [input] at [targetRate]. **The returned array is reused** by the
     * next call; consume it before calling again.
     */
    fun downsample(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        val n = input.size
        if (n != cachedInputSize) {
            cachedInputSize = n
            cachedFiltered = FloatArray(n)
            cachedOutput = FloatArray((n / ratio).toInt().coerceAtLeast(1))
        }
        movingAverage(input, cachedFiltered)
        val filtered = cachedFiltered
        val output = cachedOutput
        for (i in output.indices) {
            val sourcePos = i * ratio
            val base = sourcePos.toInt().coerceIn(0, filtered.lastIndex)
            val next = (base + 1).coerceAtMost(filtered.lastIndex)
            val frac = (sourcePos - base).toFloat()
            output[i] = filtered[base] * (1f - frac) + filtered[next] * frac
        }
        return output
    }

    private fun movingAverage(input: FloatArray, out: FloatArray) {
        val n = input.size
        if (n <= MA_HALF * 2) {
            input.copyInto(out, 0, 0, n)
            return
        }
        for (i in 0 until n) {
            val lo = (i - MA_HALF).coerceAtLeast(0)
            val hi = (i + MA_HALF).coerceAtMost(n - 1)
            var sum = 0f
            for (j in lo..hi) sum += input[j]
            out[i] = sum / (hi - lo + 1)
        }
    }

    companion object {
        /** SwiftF0 input rate. */
        const val TARGET_RATE = 16_000
        private const val MA_HALF = 2
    }
}
