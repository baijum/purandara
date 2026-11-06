package com.baijum.purandara.singalong

import com.baijum.purandara.audio.RenderOptions
import com.baijum.purandara.audio.SampleBank
import com.baijum.purandara.audio.SampleRenderer
import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.pitch.VoiceSignals
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whole pipeline on audio: the real renderer is the "speaker" (drone +
 * harmonium + count-in clicks), the "mic" hears a fraction of it plus an
 * optional synthetic singer, and frames go through PitchPipeline,
 * SingAlongClock, bleed calibration and NoteScorer.
 */
class SingAlongEndToEndTest {
    private val sr = 44_100
    private val sa = Pitch.midiToHz(48)

    /** Harmonium stand-in: harmonic-rich tones at each MIDI pitch (so leakage is pitched like the real thing). */
    private val bank = SampleBank(
        sr,
        HarmoniumBank.midiRange.associateWith { midi ->
            val hz = Pitch.midiToHz(midi)
            FloatArray((2.5 * sr).toInt()) { i -> (0.4 * (1..6).sumOf { h -> sin(2 * PI * h * hz * i / sr) / h } / 2.45).toFloat() }
        },
    )

    private class Run(val session: SingAlongSession, val result: PassResult)

    private fun run(leakage: Float, singer: ((Double) -> Double?)?): Run {
        val schedule = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, PerformanceSettings(saHz = sa, tempo = 90))
        val renderer = SampleRenderer(bank, schedule, RenderOptions(drone = true, loop = false, countIn = true))
        // Render the speaker output, recording block levels as the platform would.
        val levels = OutputLevels(capacity = 4096)
        val speaker = FloatArray(renderer.totalFrames.toInt())
        var pos = 0
        val block = FloatArray(1024)
        while (true) {
            val n = renderer.render(block)
            if (n == 0) break
            block.copyInto(speaker, pos, 0, n)
            var sum = 0.0
            for (i in 0 until n) sum += block[i] * block[i]
            levels.record(pos.toLong(), sqrt(sum / n).toFloat())
            pos += n
        }
        val countIn = renderer.countInFrames.toDouble() / sr
        // The singer follows the exercise in performance time (after the count-in).
        val voice = singer?.let { s -> VoiceSignals.follow(speaker.size.toDouble() / sr, sr, amplitude = 0.25f) { t -> s(t - countIn) } }
        val mic = FloatArray(speaker.size) { i -> speaker[i] * leakage + (voice?.get(i) ?: 0f) }

        val clock = SingAlongClock(countIn, renderer.performanceFrames.toDouble() / sr, loop = false, 0.0, 0.0)
        val session = SingAlongSession(schedule, clock, sr, levels)
        val pipeline = PitchPipeline(sr)
        var start = 0
        while (start + PitchPipeline.WINDOW_SIZE <= mic.size) {
            pipeline.beginFrame() // no neural model in this test
            val window = mic.copyOfRange(start, start + PitchPipeline.WINDOW_SIZE)
            // Time the window by its centre.
            session.onFrame(pipeline.process(window, (start + PitchPipeline.WINDOW_SIZE / 2).toDouble() / sr))
            start += PitchPipeline.HOP_SIZE
        }
        return Run(session, session.finish()!!)
    }

    private fun singsSchedule(centsOff: Double): (Double) -> Double? {
        val schedule = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, PerformanceSettings(saHz = sa, tempo = 90))
        return { t -> schedule.notes.lastOrNull { it.startSeconds <= t && t < schedule.durationSeconds }?.frequencyHz?.let { it * 2.0.pow(centsOff / 1200) } }
    }

    @Test
    fun singerWithSpeakerLeakageScoresInTune() {
        val run = run(leakage = 0.1f, singer = singsSchedule(0.0))
        assertTrue(run.session.leakageRatio > 0.05, "leakage measured in the count-in: ${run.session.leakageRatio}")
        val summary = run.result.summary
        assertEquals(16, summary.judged)
        assertTrue(summary.inTune >= 15, "in tune ${summary.inTune}/16: ${run.result.marks.map { it.result }}")
    }

    @Test
    fun sharpSingerIsHeardAsSharpDespiteTheInTuneHarmonium() {
        val run = run(leakage = 0.1f, singer = singsSchedule(35.0))
        val judged = run.result.marks.mapNotNull { it.result as? NoteResult.Judged }
        assertTrue(judged.size >= 14, "judged ${judged.size}")
        assertTrue(judged.count { it.high && it.zone == Zone.OFF } >= 14, judged.toString())
    }

    @Test
    fun silentStudentWithHarmoniumLeakageIsNeverScoredAsSinging() {
        // The critical case: the mic hears only the app playing the exact targets.
        for (leakage in listOf(0.05f, 0.1f, 0.3f)) {
            val run = run(leakage = leakage, singer = null)
            val summary = run.result.summary
            assertEquals(0, summary.inTuneOrClose, "leakage $leakage scored as singing: ${run.result.marks.map { it.result }}")
            assertEquals(16, summary.noVoice, "leakage $leakage")
        }
    }

    @Test
    fun withHeadphonesThereIsNoLeakageAndSingingScores() {
        val run = run(leakage = 0f, singer = singsSchedule(0.0))
        assertEquals(0.0, run.session.leakageRatio, 1e-9)
        assertTrue(run.result.summary.inTune >= 15)
    }
}
