package com.baijum.purandara.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise real AudioTrack write/stop/release races; route selection itself needs hardware. */
@RunWith(AndroidJUnit4::class)
class VarisaiAudioPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val player = VarisaiAudioPlayer(instrumentation.targetContext)
    private val sampleRate = 44_100
    private val silence = FloatArray(sampleRate * 2)
    private val bank = SampleBank(sampleRate, HarmoniumBank.midiRange.associateWith { silence })

    private fun renderer() = SampleRenderer(
        bank,
        Schedule.of(VarisaiCatalog.all.first(), PracticeSettings().performance()),
        RenderOptions.droneOnly(0f),
    )

    @Test
    fun immediateStopUnblocksWritesAndAllowsReplacementWithoutDraining() {
        repeat(5) {
            val rendered = CountDownLatch(1)
            val finished = CountDownLatch(1)
            instrumentation.runOnMainSync {
                player.onRendered = { _, _ -> rendered.countDown() }
                player.play(renderer()) { finished.countDown() }
            }
            assertTrue("worker started rendering", rendered.await(2, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                player.stop(immediate = true)
                assertFalse(player.isPlaying)
            }
            assertTrue("hard stop did not wait for the endless drone to drain", finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun stopBeforePlayAndOldWorkerReleaseCannotAffectAReplacement() {
        repeat(10) {
            val oldFinished = CountDownLatch(1)
            val replacementFinished = CountDownLatch(1)
            instrumentation.runOnMainSync {
                player.play(renderer()) { oldFinished.countDown() }
                player.stop(immediate = true)
                player.play(renderer()) { replacementFinished.countDown() }
                player.stop(immediate = true)
            }
            assertTrue(oldFinished.await(2, TimeUnit.SECONDS))
            assertTrue(replacementFinished.await(2, TimeUnit.SECONDS))
        }
    }
}
