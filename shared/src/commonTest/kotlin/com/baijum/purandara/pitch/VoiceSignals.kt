package com.baijum.purandara.pitch

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic voice-like test signals: a harmonic series shaped by vowel
 * formants, with optional vibrato, breath noise, a soft onset and a weak
 * fundamental (typical of low male voices, and the classic cause of
 * octave errors).
 */
object VoiceSignals {
    /** Formants of an open "aa" vowel (Hz, bandwidth Hz). */
    private val FORMANTS = listOf(730.0 to 90.0, 1090.0 to 110.0, 2440.0 to 170.0)

    fun voice(
        f0: Double,
        seconds: Double,
        sampleRate: Int = 44_100,
        vibratoCents: Double = 0.0,
        vibratoHz: Double = 5.5,
        noiseDb: Double = Double.NEGATIVE_INFINITY,
        onsetSeconds: Double = 0.0,
        fundamentalLevel: Double = 1.0,
        seed: Int = 1,
        amplitude: Float = 0.3f,
    ): FloatArray {
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        val harmonics = (sampleRate / 2.5 / f0).toInt().coerceAtMost(40)
        val weights = DoubleArray(harmonics + 1) { h ->
            if (h == 0) 0.0 else formantGain(h * f0) / h * (if (h == 1) fundamentalLevel else 1.0)
        }
        var phase = 0.0
        var peak = 0.0
        val raw = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val cents = vibratoCents * sin(2 * PI * vibratoHz * t)
            val f = f0 * 2.0.pow(cents / 1200.0)
            phase += 2 * PI * f / sampleRate
            var v = 0.0
            for (h in 1..harmonics) v += weights[h] * sin(h * phase)
            raw[i] = v
            if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        }
        val random = Random(seed)
        val noiseRms = if (noiseDb.isInfinite()) 0.0 else amplitude * 10.0.pow(noiseDb / 20) / sqrt(2.0)
        val onset = (onsetSeconds * sampleRate).toInt()
        for (i in 0 until n) {
            val env = if (i < onset) i.toDouble() / onset else 1.0
            val noise = if (noiseRms > 0) (random.nextDouble() * 2 - 1) * noiseRms * sqrt(3.0) else 0.0
            out[i] = (raw[i] / peak * amplitude * env + noise).toFloat()
        }
        return out
    }

    /**
     * A voice that follows [frequencyAt] (Hz at time t, or null for silence),
     * e.g. a synthetic student singing along with a schedule.
     */
    fun follow(
        seconds: Double,
        sampleRate: Int = 44_100,
        amplitude: Float = 0.3f,
        frequencyAt: (Double) -> Double?,
    ): FloatArray {
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        var env = 0.0
        for (i in 0 until n) {
            val f = frequencyAt(i.toDouble() / sampleRate)
            // 10 ms attack/release so voice starts and stops without clicks.
            env = if (f != null) minOf(1.0, env + 100.0 / sampleRate) else maxOf(0.0, env - 100.0 / sampleRate)
            if (f != null) phase += 2 * PI * f / sampleRate
            if (env == 0.0) continue
            val f0 = f ?: 200.0
            var v = 0.0
            val harmonics = (sampleRate / 2.5 / f0).toInt().coerceAtMost(30)
            for (h in 1..harmonics) v += formantGain(h * f0) / h * sin(h * phase)
            out[i] = (v * env * amplitude / 1.6).toFloat()
        }
        return out
    }

    fun noise(seconds: Double, sampleRate: Int = 44_100, amplitude: Float = 0.1f, seed: Int = 2): FloatArray {
        val random = Random(seed)
        return FloatArray((seconds * sampleRate).toInt()) { ((random.nextFloat() * 2 - 1) * amplitude) }
    }

    /** Sum of Gaussian formant resonances, never quite zero. */
    private fun formantGain(freq: Double): Double =
        0.05 + FORMANTS.sumOf { (centre, bandwidth) -> exp(-((freq - centre) / bandwidth).pow(2) / 2) }

    /** Window of [size] samples starting at [start]. */
    fun FloatArray.window(start: Int, size: Int = 2048): FloatArray = copyOfRange(start, start + size)
}
