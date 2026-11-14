package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.pitch.VoiceSignals
import com.baijum.purandara.recording.RecordingTestTracks.SA_HZ
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic voice → analyser → transcriber, as the apps run it after Stop. */
class RecordingAnalyzerTest {
    private val rate = 44_100

    private fun analyse(audio: FloatArray, chunk: Int): PitchTrack {
        val analyzer = RecordingAnalyzer(rate, RecordingAnalyzer.NEURAL_EVERY_WINDOW, neural = null)
        var at = 0
        val buffer = FloatArray(chunk)
        while (at < audio.size) {
            val n = minOf(chunk, audio.size - at)
            audio.copyInto(buffer, 0, at, at + n)
            analyzer.feed(buffer, n)
            at += n
        }
        return analyzer.finish()
    }

    @Test
    fun aSungSaraliIsReadAsItsSwaras() {
        val swaras = listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3)
        val noteSeconds = 0.4
        val silence = 0.5
        val audio = VoiceSignals.follow(silence + swaras.size * noteSeconds + silence, rate) { t ->
            val i = ((t - silence) / noteSeconds).toInt()
            if (t < silence || i >= swaras.size) null else SA_HZ * swaras[i].ratio
        }
        val track = analyse(audio, chunk = 1000)
        assertEquals(audio.size / 512 - 3, track.size, "a frame per hop once the first window is full")
        val t = SwaraTranscriber.transcribe(track, SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertEquals(swaras, t.notes.map { it.swara })
        assertEquals(silence, t.notes.first().startSeconds, 0.06)
    }

    @Test
    fun chunkSizeDoesNotChangeTheTrack() {
        val audio = VoiceSignals.follow(1.0, rate) { SA_HZ * 2.0.pow(it) }
        assertEquals(analyse(audio, 4096), analyse(audio, 777))
    }
}
