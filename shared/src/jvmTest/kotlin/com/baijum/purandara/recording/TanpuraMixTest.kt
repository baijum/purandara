package com.baijum.purandara.recording

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.TanpuraBank
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.VoiceSignals
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * A real tanpura under a sung Sarali (docs/plan-recordings-tanpura.md): the
 * bundled tanpura loop at Sa A♯2 mixed under the synthetic voice at three
 * levels, through the shared analyser and the transcriber with and without
 * the tanpura profile. jvmTest can't run ONNX, so this is the YIN fallback
 * only, and it reports rather than asserts: YIN locks onto the tanpura (its
 * low Sa is a sub-octave of the sung Sa), which is why recordings with a
 * tanpura take SwiftF0's pitch (results in docs/plan-recordings-tanpura.md).
 *
 * Development only, needs `ffmpeg` on PATH:
 *
 *     PURANDARA_TANPURA_MIX=1 ./gradlew :shared:jvmTest --tests '*TanpuraMixTest*' -i
 */
class TanpuraMixTest {
    private val rate = 44_100
    private val saMidi = 46
    private val swaras = listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3, Swara.S)

    @Test
    fun aRealTanpuraUnderASungSarali() {
        if (System.getenv("PURANDARA_TANPURA_MIX") != "1") return
        val sa = Pitch.midiToHz(saMidi)
        val tanpura = decode(File(rootDir(), "audio/" + TanpuraBank.assetPath(saMidi)))
        val listen = TanpuraProfile.LISTEN_SECONDS
        // A note per 0.5 s with a 0.3 s breath after each, after the tanpura alone.
        val seconds = listen + swaras.size * 0.8 + 0.5
        val voice = VoiceSignals.follow(seconds, rate) { t ->
            val s = t - listen
            val i = (s / 0.8).toInt()
            if (s < 0 || i >= swaras.size || s - i * 0.8 >= 0.5) null else sa * swaras[i].ratio * if (i == swaras.size - 1) 2 else 1
        }
        val sung = voice.filter { it != 0f }
        val voiceRms = sqrt(sung.sumOf { it.toDouble() * it } / sung.size)
        val looped = FloatArray(voice.size) { tanpura[it % tanpura.size] }
        val tanpuraRms = sqrt(looped.sumOf { it.toDouble() * it } / looped.size)
        val expected = listOf("S", "R₁", "G₃", "M₁", "P", "D₁", "N₃", "S′")
        val results = mutableMapOf<Int, List<String>>()
        for (db in listOf(-6, 0, 6)) {
            val gain = (voiceRms / tanpuraRms * 10.0.pow(db / 20.0)).toFloat()
            val mix = FloatArray(voice.size) { voice[it] + looped[it] * gain }
            val analyzer = RecordingAnalyzer(rate, null, SwiftF0.range(sa))
            mix.toList().chunked(4096).forEach { analyzer.feed(it.toFloatArray(), it.size) }
            val track = analyzer.finish()
            val plain = SwaraTranscriber.transcribe(track, sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST).notes.map { it.label }
            val profile = TanpuraProfile.from(track)
            val withProfile = SwaraTranscriber.transcribe(track, sa, Raga.MAYAMALAVAGOWLA, Tuning.JUST, profile).notes.map { it.label }
            println("tanpura-mix: tanpura ${if (db >= 0) "+" else ""}$db dB vs voice")
            println("tanpura-mix:   profile rms ${profile?.rms}, Sa ${profile?.saHz?.let { "%.2f".format(it) }}, other ${profile?.otherHz?.let { "%.2f".format(it) }}, offer ${profile?.suggestedSa(sa)}")
            println("tanpura-mix:   without: ${plain.joinToString(" ")}")
            println("tanpura-mix:   with:    ${withProfile.joinToString(" ")}")
            results[db] = withProfile
        }
        println("tanpura-mix: expected  ${expected.joinToString(" ")}; YIN matches at ${results.filterValues { it == expected }.keys} dB")
    }

    private fun rootDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "audio").isDirectory }

    private fun decode(file: File): FloatArray {
        val process = ProcessBuilder("ffmpeg", "-v", "error", "-i", file.path, "-f", "f32le", "-ac", "1", "-ar", "$rate", "-")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val bytes = process.inputStream.readBytes()
        check(process.waitFor() == 0) { "ffmpeg failed on $file" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}
