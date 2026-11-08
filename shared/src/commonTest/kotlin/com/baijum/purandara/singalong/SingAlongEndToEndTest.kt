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

    private class Run(val session: SingAlongSession, val result: PassResult, val liveMarks: List<NoteMark>)

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
        val liveMarks = mutableListOf<NoteMark>()
        var start = 0
        while (start + PitchPipeline.WINDOW_SIZE <= mic.size) {
            pipeline.beginFrame() // no neural model in this test
            val window = mic.copyOfRange(start, start + PitchPipeline.WINDOW_SIZE)
            // Time the window by its centre.
            liveMarks += session.onFrame(pipeline.process(window, (start + PitchPipeline.WINDOW_SIZE / 2).toDouble() / sr)).newMarks
            start += PitchPipeline.HOP_SIZE
        }
        val result = session.finish()!!
        return Run(session, result, liveMarks)
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
    fun notesAreMarkedLiveAsTheyEndAndMatchThePassResult() {
        val run = run(leakage = 0.1f, singer = singsSchedule(0.0))
        // All but the final note(s) are judged while still playing; finish() adds the rest.
        assertTrue(run.liveMarks.size >= 15, "live marks ${run.liveMarks.size}")
        assertEquals(run.result.marks.take(run.liveMarks.size), run.liveMarks)
        assertEquals(16, run.result.marks.size)
    }

    @Test
    fun withHeadphonesThereIsNoLeakageAndSingingScores() {
        val run = run(leakage = 0f, singer = singsSchedule(0.0))
        assertEquals(0.0, run.session.leakageRatio, 1e-9)
        assertTrue(run.result.summary.inTune >= 15)
    }

    /**
     * Continuous repeat (issue #12): with the renderer looping, readings from
     * the second loop carry pass 1, the first pass completes when the second
     * begins, and both passes are scored in full.
     */
    @Test
    fun loopingSessionTagsReadingsWithTheLoopAndCompletesEachPass() {
        val schedule = Schedule.of(VarisaiCatalog.byId("plain-scale-1")!!, PerformanceSettings(saHz = sa, tempo = 90))
        val renderer = SampleRenderer(bank, schedule, RenderOptions(drone = true, loop = true, countIn = true))
        val loops = 2
        val levels = OutputLevels(capacity = 8192)
        val totalFrames = renderer.countInFrames + loops * renderer.performanceFrames
        val speaker = FloatArray(totalFrames)
        var pos = 0
        val block = FloatArray(1024)
        while (pos < totalFrames) {
            // The looping renderer always fills the block; keep only what fits.
            val n = minOf(renderer.render(block), totalFrames - pos)
            block.copyInto(speaker, pos, 0, n)
            var sum = 0.0
            for (i in 0 until n) sum += block[i] * block[i]
            levels.record(pos.toLong(), sqrt(sum / n).toFloat())
            pos += n
        }
        val countIn = renderer.countInFrames.toDouble() / sr
        val passSeconds = renderer.performanceFrames.toDouble() / sr
        val target = singsSchedule(0.0)
        val voice = VoiceSignals.follow(speaker.size.toDouble() / sr, sr, amplitude = 0.25f) { t ->
            if (t < countIn) null else target((t - countIn) % passSeconds)
        }
        val mic = FloatArray(speaker.size) { i -> speaker[i] * 0.1f + voice[i] }

        val clock = SingAlongClock(countIn, passSeconds, loop = true, 0.0, 0.0)
        val session = SingAlongSession(schedule, clock, sr, levels)
        val pipeline = PitchPipeline(sr)
        val completed = mutableListOf<PassResult>()
        var firstPassOne: LiveReading? = null
        var start = 0
        while (start + PitchPipeline.WINDOW_SIZE <= mic.size) {
            pipeline.beginFrame() // no neural model in this test
            val window = mic.copyOfRange(start, start + PitchPipeline.WINDOW_SIZE)
            val outcome = session.onFrame(pipeline.process(window, (start + PitchPipeline.WINDOW_SIZE / 2).toDouble() / sr))
            outcome.completedPass?.let { completed += it }
            if (outcome.live.pass == 1 && firstPassOne == null) firstPassOne = outcome.live
            start += PitchPipeline.HOP_SIZE
        }
        val final = session.finish()!!

        assertEquals(1, completed.size, "the first pass closes exactly once, when the second begins")
        assertEquals(0, completed[0].pass)
        assertEquals(16, completed[0].summary.judged)
        assertEquals(1, final.pass)
        assertEquals(16, final.summary.judged)
        val one = requireNotNull(firstPassOne) { "no reading was tagged with pass 1" }
        assertEquals(1, one.pass)
        val oneSeconds = requireNotNull(one.secondsInPass)
        assertTrue(oneSeconds in 0.0..<passSeconds, "pass-1 reading outside one pass: $oneSeconds")
    }
}
