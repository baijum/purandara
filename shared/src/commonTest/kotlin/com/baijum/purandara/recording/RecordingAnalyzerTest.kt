package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.pitch.PitchDetector
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.pitch.SwiftF0Output
import com.baijum.purandara.pitch.VoiceSignals
import com.baijum.purandara.recording.RecordingTestTracks.SA_HZ
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic voice → analyser → transcriber, as the apps run it after Stop. */
class RecordingAnalyzerTest {
    private val rate = 44_100

    /**
     * Stands in for SwiftF0: YIN on 64 ms around each 16 kHz frame, times
     * [factor] (2 = a model that hears everything an octave up).
     */
    private class YinModel(val factor: Float = 1f) : SwiftF0Model {
        var calls = 0

        override fun run(audio16k: FloatArray, range: PitchRange): SwiftF0Output {
            calls++
            val frames = maxOf(1, audio16k.size / SwiftF0.HOP)
            val detector = PitchDetector()
            val hz = FloatArray(frames)
            val confidence = FloatArray(frames)
            for (k in 0 until frames) {
                val from = (k * SwiftF0.HOP - 512).coerceAtLeast(0)
                val to = (from + 1024).coerceAtMost(audio16k.size)
                if (to - from < 1024) continue
                val p = detector.detect(audio16k.copyOfRange(from, to), SwiftF0.SAMPLE_RATE) ?: continue
                hz[k] = p.frequencyHz.toFloat() * factor
                confidence[k] = 0.95f
            }
            return SwiftF0Output(hz, confidence)
        }
    }

    private fun analyse(audio: FloatArray, chunk: Int, model: SwiftF0Model? = null, tanpura: Boolean = false): PitchTrack {
        val analyzer = RecordingAnalyzer(rate, model, SwiftF0.range(SA_HZ), tanpura)
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
        assertEquals(analyse(audio, 4096, YinModel()), analyse(audio, 777, YinModel()))
    }

    private fun sarali(): Pair<List<Swara>, FloatArray> {
        val swaras = listOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D1, Swara.N3)
        val audio = VoiceSignals.follow(0.5 + swaras.size * 0.4 + 0.5, rate) { t ->
            val i = ((t - 0.5) / 0.4).toInt()
            if (t < 0.5 || i >= swaras.size) null else SA_HZ * swaras[i].ratio
        }
        return swaras to audio
    }

    @Test
    fun aQuietVoiceIsStillRead() {
        // An iPad's built-in mic without gain control: a sung voice at an RMS
        // of about 0.006, below live listening's silence level of 0.01.
        val (swaras, loud) = sarali()
        val sung = loud.filter { it != 0f }
        val rms = kotlin.math.sqrt(sung.sumOf { it.toDouble() * it } / sung.size).toFloat()
        val quiet = FloatArray(loud.size) { loud[it] / rms * 0.006f }
        val track = analyse(quiet, 1000)
        val voiced = (0 until track.size).count { track.frequencyAt(it) != null }
        assertTrue(voiced > track.size / 2, "most frames have a pitch: $voiced of ${track.size}")
        val t = SwaraTranscriber.transcribe(track, SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertEquals(swaras, t.notes.map { it.swara })
    }

    @Test
    fun withSwiftF0EveryWindowStillGetsAFrame() {
        val (_, audio) = sarali()
        val model = YinModel()
        val track = analyse(audio, chunk = 1000, model = model)
        assertEquals(audio.size / 512 - 3, track.size, "windows wait for SwiftF0 but none are lost")
        assertEquals(1, model.calls, "a 4 s recording is one chunk")
    }

    @Test
    fun aModelThatAgreesReadsTheSameSwaras() {
        val (swaras, audio) = sarali()
        val t = SwaraTranscriber.transcribe(analyse(audio, 1000, YinModel()), SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertEquals(swaras, t.notes.map { it.swara })
    }

    @Test
    fun aConfidentVoiceIsNotMovedByAModelAnOctaveOff() {
        val (swaras, audio) = sarali()
        val t = SwaraTranscriber.transcribe(analyse(audio, 1000, YinModel(factor = 2f)), SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertEquals(swaras, t.notes.map { it.swara })
        assertEquals(setOf(0), t.notes.map { it.octave }.toSet(), "YIN is sure, so no octave correction")
    }

    @Test
    fun aFailingModelLeavesYinAlone() {
        val (_, audio) = sarali()
        val failing = SwiftF0Model { _, _ -> null }
        assertEquals(analyse(audio, 1000), analyse(audio, 1000, failing))
    }

    @Test
    fun withATanpuraThePitchIsSwiftF0s() {
        val (_, audio) = sarali()
        // A model that always hears 200 Hz: with a tanpura its pitch is taken as is.
        val fixed = SwiftF0Model { a, _ -> val n = a.size / SwiftF0.HOP; SwiftF0Output(FloatArray(n) { 200f }, FloatArray(n) { 0.9f }) }
        val track = analyse(audio, 1000, fixed, tanpura = true)
        val pitches = (0 until track.size).mapNotNull { track.frequencyAt(it) }.toSet()
        assertEquals(setOf(200.0), pitches)
        assertEquals(analyse(audio, 1000).size, track.size, "a frame per window, as without")
    }

    @Test
    fun withATanpuraButNoModelYinStillReads() {
        val (swaras, audio) = sarali()
        val t = SwaraTranscriber.transcribe(analyse(audio, 1000, model = null, tanpura = true), SA_HZ, Raga.MAYAMALAVAGOWLA, Tuning.JUST)
        assertEquals(swaras, t.notes.map { it.swara })
    }
}
