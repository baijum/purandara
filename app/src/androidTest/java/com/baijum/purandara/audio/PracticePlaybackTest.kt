package com.baijum.purandara.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.practice.PracticeMode
import com.baijum.purandara.practice.RecoveryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real output worker + fresh synthetic microphones, not a mocked UI countdown/scheduler. */
@RunWith(AndroidJUnit4::class)
class PracticePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sampleRate = 44_100
    private val silence = FloatArray(sampleRate * 2)
    private val bank = SampleBank(sampleRate, HarmoniumBank.midiRange.associateWith { silence })

    @Test
    fun oneListenSingPairCompletesAndNeverCapturesTheModel() {
        val settings = PracticeSettings(tempo = 120, kala = 4, countIn = false, practiceMode = PracticeMode.LISTEN_REPEAT)
        runPractice(settings, expectedResults = 1, expectedCaptures = 1)
    }

    @Test
    fun twoScoredAttemptsUseFreshMicsAndStopCancelsTheWaitingThird() {
        val settings = PracticeSettings(tempo = 120, kala = 4, loop = true, recoveryMode = RecoveryMode.TIMED, recoverySeconds = 1)
        runPractice(settings, expectedResults = 2, expectedCaptures = 2)
    }

    private fun runPractice(settings: PracticeSettings, expectedResults: Int, expectedCaptures: Int) {
        val player = VarisaiAudioPlayer(instrumentation.targetContext)
        val completed = CountDownLatch(1)
        val captures = AtomicInteger()
        val results = AtomicInteger()
        var sawListen = false
        var sawResponse = false
        var sawRest = false
        lateinit var practice: PracticePlayback
        instrumentation.runOnMainSync {
            practice = PracticePlayback(player, { null }, { target ->
                captures.incrementAndGet()
                SyntheticSingerMicSource(0.0, target)
            }, PracticePlayback.Callbacks(
                onProgress = { snapshot, heard, _ ->
                    when (snapshot.phaseAt(heard)) {
                        PracticePhase.LISTEN -> { sawListen = true; assertFalse(practice.hasCapture) }
                        PracticePhase.YOUR_TURN -> sawResponse = true
                        PracticePhase.RECOVERY -> sawRest = true
                        else -> Unit
                    }
                },
                onOutcome = { _, _ -> },
                onResult = { _, _ ->
                    if (results.incrementAndGet() == expectedResults && settings.loop) {
                        // A Stop from a heard completion observer must cancel the prepared next mic.
                        practice.stop()
                        player.stop(immediate = true)
                        completed.countDown()
                    }
                },
                onCompleted = { completed.countDown() },
            ))
            val schedule = Schedule.of(VarisaiCatalog.byId("sarali-1")!!, settings.performance())
            practice.start(bank, schedule, settings, feedback = true, automaticRestart = false)
        }
        try {
            assertTrue("scored segments finished", completed.await(45, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                practice.stop()
                player.stop(immediate = true)
                assertEquals(expectedResults, results.get())
                assertEquals(expectedCaptures, captures.get())
                if (settings.practiceMode == PracticeMode.LISTEN_REPEAT) {
                    assertTrue(sawListen)
                    assertTrue(sawResponse)
                } else assertTrue(sawRest)
                assertFalse(player.isPlaying)
            }
            Thread.sleep(200)
            assertEquals("cancelled preparation never restarts capture", expectedCaptures, captures.get())
        } finally {
            instrumentation.runOnMainSync { practice.stop(); player.stop(immediate = true) }
        }
    }
}
