// Ported from ukulele-companion (same author, MIT): NeuralPitchSupervisor.kt.
// Changes: shared AudioResampler at the capture rate; model from the
// repo-root models/ folder (an Android assets source directory).
package com.baijum.purandara.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.baijum.purandara.pitch.AudioResampler
import com.baijum.purandara.pitch.NeuralPitchResult
import java.nio.FloatBuffer

/**
 * Runs the SwiftF0 pitch model (ONNX) on microphone windows.
 *
 * Deliberately tolerant: if the model output is not what is expected it
 * returns null, and the caller keeps using YIN alone. One instance per
 * capture stream; call [estimate] from the capture thread only.
 */
class NeuralPitchSupervisor(assets: AssetManager, captureSampleRate: Int) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val resampler = AudioResampler(captureSampleRate)

    init {
        val bytes = assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        try {
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(1)
            session = env.createSession(bytes, options)
        } finally {
            options.close()
        }
        inputName = session.inputNames.first()
    }

    /** SwiftF0's estimate for the middle of [window] (capture-rate samples), or null. */
    fun estimate(window: FloatArray): NeuralPitchResult? {
        val samples16k = resampler.downsample(window)
        if (samples16k.isEmpty()) return null
        val shape = longArrayOf(1L, samples16k.size.toLong())
        return try {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(samples16k), shape).use { input ->
                session.run(mapOf(inputName to input)).use { outputs ->
                    if (outputs.size() == 0) return null
                    val pitch = extractFloatSeries(outputs[0].value) ?: return null
                    val confidence = if (outputs.size() >= 2) extractFloatSeries(outputs[1].value) else null
                    parseMiddleEstimate(pitch, confidence)
                }
            }
        } catch (_: OrtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val MODEL_ASSET = "swift_f0_model.onnx"
        const val MIN_FREQ_HZ = 46.875
        const val MAX_FREQ_HZ = 2093.75
        const val MIN_CONFIDENCE = 0.50

        @VisibleForTesting
        internal fun extractFloatSeries(value: Any?): FloatArray? = when (value) {
            is FloatArray -> value
            is Array<*> -> flatten(value)
            else -> null
        }

        private fun flatten(array: Array<*>): FloatArray? {
            if (array.isEmpty()) return FloatArray(0)
            return when (array[0]) {
                is FloatArray -> {
                    val rows = array.filterIsInstance<FloatArray>()
                    val out = FloatArray(rows.sumOf { it.size })
                    var pos = 0
                    rows.forEach { row ->
                        row.copyInto(out, pos)
                        pos += row.size
                    }
                    out
                }
                is Number -> array.mapNotNull { (it as? Number)?.toFloat() }.toFloatArray()
                else -> null
            }
        }

        /** The model emits a pitch/confidence series; take its middle frame. */
        @VisibleForTesting
        internal fun parseMiddleEstimate(pitch: FloatArray, confidence: FloatArray?): NeuralPitchResult? {
            if (pitch.isEmpty()) return null
            val idx = pitch.size / 2
            val hz = pitch[idx].toDouble()
            val conf = confidence?.getOrNull(idx.coerceAtMost(confidence.lastIndex))?.toDouble() ?: 0.0
            if (hz !in MIN_FREQ_HZ..MAX_FREQ_HZ || conf < MIN_CONFIDENCE) return null
            return NeuralPitchResult(hz, conf)
        }
    }
}
