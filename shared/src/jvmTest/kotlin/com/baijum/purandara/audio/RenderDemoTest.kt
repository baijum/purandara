package com.baijum.purandara.audio

import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

/**
 * Opt-in listening check: renders exercises with the real harmonium samples
 * through [SampleRenderer] into WAV files in shared/build/render-demo.
 *
 *     PURANDARA_RENDER_DEMO=1 ./gradlew :shared:jvmTest --tests '*RenderDemoTest*'
 *
 * Needs `ffmpeg` on PATH to decode FLAC (development only; the apps decode
 * with the platform's own codecs). Skipped unless the variable is set.
 */
class RenderDemoTest {
    private val sampleRate = 44_100

    @Test
    fun renderListeningDemos() {
        if (System.getenv("PURANDARA_RENDER_DEMO") != "1") return
        val audio = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "audio") }.first { it.isDirectory }
        val bank = SampleBank(
            sampleRate,
            HarmoniumBank.midiRange.associateWith { decode(File(audio, HarmoniumBank.assetPath(it))) },
            com.baijum.purandara.domain.TanpuraBank.SA_MIDIS.associateWith {
                decode(File(audio, com.baijum.purandara.domain.TanpuraBank.assetPath(it)))
            },
        )
        val out = File("build/render-demo").apply { mkdirs() }

        val demos = listOf(
            Triple("plain-scale-1", PerformanceSettings(saHz = Pitch.midiToHz(49), tempo = 75), RenderOptions(droneStyle = DroneStyle.HARMONIUM)),
            Triple("plain-scale-1", PerformanceSettings(saHz = Pitch.midiToHz(49), tempo = 75), RenderOptions(drone = false)),
            Triple("sarali-4", PerformanceSettings(saHz = Pitch.midiToHz(49), tempo = 75), RenderOptions(click = true)),
            Triple("plain-scale-1", PerformanceSettings(saHz = Pitch.midiToHz(57), tempo = 60, kala = 3), RenderOptions()),
            Triple("janta-1", PerformanceSettings(saHz = Pitch.midiToHz(57), tempo = 60), RenderOptions(click = true)),
            Triple("sarali-4", PerformanceSettings(saHz = Pitch.midiToHz(49), tempo = 75), RenderOptions(droneStyle = DroneStyle.TANPURA)),
            Triple("plain-scale-1", PerformanceSettings(saHz = Pitch.midiToHz(57), tempo = 60), RenderOptions(droneStyle = DroneStyle.TANPURA)),
        )
        for ((id, settings, options) in demos) {
            val renderer = SampleRenderer(bank, Schedule.of(VarisaiCatalog.byId(id)!!, settings), options)
            val pcm = FloatArray(renderer.totalFrames.toInt())
            renderer.render(pcm)
            val name = "$id-sa${Pitch.nearestMidi(settings.saHz)}-kala${settings.kala}" +
                (if (options.click) "-click" else "") + (if (options.drone) "" else "-nodrone") +
                (if (options.droneStyle == DroneStyle.TANPURA) "-tanpura" else "") + ".wav"
            writeWav(File(out, name), pcm)
            println("render-demo: ${File(out, name).absolutePath}")
        }
    }

    private fun decode(file: File): FloatArray {
        val process = ProcessBuilder("ffmpeg", "-v", "error", "-i", file.path, "-f", "f32le", "-ac", "1", "-ar", "$sampleRate", "-")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val bytes = process.inputStream.readBytes()
        check(process.waitFor() == 0) { "ffmpeg failed on $file" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun writeWav(file: File, pcm: FloatArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort((it.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        file.writeBytes(header.array() + data.array())
    }
}
