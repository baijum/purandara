// Ported from ukulele-companion (same author, MIT): NeuralPitchSupervisor.kt.
// Changes: the SwiftF0 v0.3.0 model (audio, fmin and fmax in; float64 pitch
// and confidence out); the chunking, silence gate and frame reading are the
// shared SwiftF0 code (docs/plan-swiftf0-upgrade.md); model from the
// repo-root models/ folder (an Android assets source directory).
package com.baijum.purandara.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.pitch.SwiftF0Output
import java.nio.FloatBuffer

/**
 * Runs the SwiftF0 v0.3.0 pitch model (ONNX) on 16 kHz audio.
 *
 * Deliberately tolerant: if the model's output is not what is expected it
 * returns null, and the callers keep using YIN alone. The session is safe to
 * share between threads (ONNX Runtime sessions are), but each caller should
 * be one stream.
 *
 * @param threads ONNX Runtime threads: 1 for live listening (it runs beside
 *   the audio), more for analysing a recording.
 */
class NeuralPitchSupervisor(assets: AssetManager, threads: Int = 1) : SwiftF0Model, AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val bytes = assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        try {
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(threads)
            session = env.createSession(bytes, options)
        } finally {
            options.close()
        }
        require(session.inputNames.containsAll(INPUTS) && session.outputNames.containsAll(OUTPUTS)) {
            "unexpected SwiftF0 model: inputs ${session.inputNames}, outputs ${session.outputNames}"
        }
    }

    override fun run(audio16k: FloatArray, range: PitchRange): SwiftF0Output? {
        if (audio16k.isEmpty()) return null
        val inputs = mutableMapOf<String, OnnxTensor>()
        return try {
            inputs[INPUT_AUDIO] = OnnxTensor.createTensor(env, FloatBuffer.wrap(audio16k), longArrayOf(1L, audio16k.size.toLong()))
            inputs[INPUT_FMIN] = scalar(range.minHz)
            inputs[INPUT_FMAX] = scalar(range.maxHz)
            session.run(inputs).use { outputs ->
                val pitch = floats(outputs.get(OUTPUT_PITCH).orElse(null)?.value)
                val confidence = floats(outputs.get(OUTPUT_CONFIDENCE).orElse(null)?.value)
                if (pitch == null || confidence == null || pitch.size != confidence.size) null else SwiftF0Output(pitch, confidence)
            }
        } catch (_: OrtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun scalar(value: Float): OnnxTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(value)), longArrayOf())

    override fun close() {
        session.close()
    }

    companion object {
        const val MODEL_ASSET = "swift_f0_v0.3.0.onnx"
        private const val INPUT_AUDIO = "audio"
        private const val INPUT_FMIN = "fmin"
        private const val INPUT_FMAX = "fmax"
        private const val OUTPUT_PITCH = "pitch"
        private const val OUTPUT_CONFIDENCE = "confidence"
        private val INPUTS = setOf(INPUT_AUDIO, INPUT_FMIN, INPUT_FMAX)
        private val OUTPUTS = setOf(OUTPUT_PITCH, OUTPUT_CONFIDENCE)

        /**
         * An output tensor's values as floats: the model gives pitch as
         * float64 and confidence as float32, each shaped [1, frames].
         */
        @VisibleForTesting
        internal fun floats(value: Any?): FloatArray? = when (value) {
            is FloatArray -> value
            is DoubleArray -> FloatArray(value.size) { value[it].toFloat() }
            is Array<*> -> if (value.size == 1) floats(value[0]) else null
            else -> null
        }
    }
}
