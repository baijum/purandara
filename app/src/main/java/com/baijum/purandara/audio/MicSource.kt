package com.baijum.purandara.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission
import com.baijum.purandara.singalong.PitchPipeline
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * A stream of microphone windows for sing-along: [PitchPipeline.WINDOW_SIZE]
 * samples every [PitchPipeline.HOP_SIZE], each with the [System.nanoTime] at
 * which its centre was captured.
 */
interface MicSource {
    val sampleRate: Int

    /**
     * Starts capturing on a dedicated thread. [onWindow] runs on that thread;
     * the array is reused, so consume it before returning.
     */
    fun start(onWindow: (window: FloatArray, captureNanos: Long) -> Unit)

    /** Stops capturing and waits for the thread to end. */
    fun stop()
}

/**
 * The device microphone via [AudioRecord].
 *
 * @param echoCancellation use the voice-communication source plus the
 *   platform echo canceller, for when the app plays through the speaker
 *   (docs/plan-pitch-monitor.md, section 5). Automatic gain control is
 *   turned off where possible so quiet singing isn't pumped.
 * @param onFailed runs on the capture thread when capture ends while still
 *   running (the input route vanished, the record died), so the listener
 *   isn't left waiting on a silent mic. Not called after [stop].
 */
class AudioRecordMicSource(
    private val echoCancellation: Boolean,
    private val onFailed: () -> Unit = {},
) : MicSource {
    override val sampleRate: Int = SAMPLE_RATE

    @Volatile private var running = false
    private var thread: Thread? = null
    private val recordLock = Any()
    private var activeRecord: AudioRecord? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(onWindow: (FloatArray, Long) -> Unit) {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord.Builder()
            .setAudioSource(
                if (echoCancellation) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION,
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, PitchPipeline.WINDOW_SIZE * Float.SIZE_BYTES * 4))
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        val effects = buildList {
            if (echoCancellation && AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }?.let(::add)
            }
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(record.audioSessionId)?.also { it.enabled = false }?.let(::add)
            }
        }
        running = true
        activeRecord = record
        thread = Thread({ capture(record, onWindow, effects) }, "sing-along-mic").also { it.start() }
    }

    private fun capture(record: AudioRecord, onWindow: (FloatArray, Long) -> Unit, effects: List<android.media.audiofx.AudioEffect>) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val window = FloatArray(PitchPipeline.WINDOW_SIZE)
        val hop = FloatArray(PitchPipeline.HOP_SIZE)
        val timestamp = AudioTimestamp()
        var framesRead = 0L
        try {
            synchronized(recordLock) {
                if (running) record.startRecording()
            }
            while (readHop(record, hop)) {
                window.copyInto(window, 0, hop.size, window.size)
                hop.copyInto(window, window.size - hop.size)
                framesRead += hop.size
                if (framesRead < window.size) continue // window not full yet
                val centre = framesRead - window.size / 2
                val nanos = if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    timestamp.nanoTime + (centre - timestamp.framePosition) * NANOS_PER_SECOND / SAMPLE_RATE
                } else {
                    System.nanoTime() - (framesRead - centre) * NANOS_PER_SECOND / SAMPLE_RATE
                }
                onWindow(window, nanos)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "capture failed", e)
            if (running) onFailed()
        } finally {
            effects.forEach { it.release() }
            synchronized(recordLock) {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
                activeRecord = null
            }
        }
    }

    /** Fills [hop]; false once stopped. */
    private fun readHop(record: AudioRecord, hop: FloatArray): Boolean =
        fillHop(hop, { running }, { offset, count -> record.read(hop, offset, count, AudioRecord.READ_BLOCKING) })

    override fun stop() {
        running = false
        synchronized(recordLock) {
            activeRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        } // stop unblocks READ_BLOCKING, including when the route disappeared
        thread?.join()
        thread = null
    }

    private companion object {
        const val TAG = "AudioRecordMicSource"
        const val SAMPLE_RATE = 44_100
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}

/**
 * Fills [hop] from [read] (offset, count → samples read or a negative error);
 * false once [isRunning] turns false. A read that returns nothing is a
 * transient hiccup (a route or focus change) and is retried after [pause];
 * only [maxEmptyReads] in a row, or an error code, fail the capture.
 */
internal fun fillHop(
    hop: FloatArray,
    isRunning: () -> Boolean,
    read: (offset: Int, count: Int) -> Int,
    maxEmptyReads: Int = MAX_EMPTY_READS,
    pause: () -> Unit = { Thread.sleep(EMPTY_READ_PAUSE_MS) },
): Boolean {
    var off = 0
    var emptyReads = 0
    while (off < hop.size && isRunning()) {
        val n = read(off, hop.size - off)
        if (!isRunning()) return false
        check(n >= 0) { "AudioRecord.read failed: $n" }
        if (n == 0) {
            check(++emptyReads <= maxEmptyReads) { "AudioRecord.read returned nothing $emptyReads times" }
            pause()
            continue
        }
        emptyReads = 0
        off += n
    }
    return isRunning()
}

/** About 200 ms of empty reads before the capture counts as lost. */
private const val MAX_EMPTY_READS = 40
private const val EMPTY_READ_PAUSE_MS = 5L

/**
 * Debug only: a synthetic singer that sings whatever [frequencyAtFrame]
 * says for the playback frame being heard, offset by [centsOff]. Lets the
 * emulator exercise the whole sing-along path without a real voice.
 */
class SyntheticSingerMicSource(
    private val centsOff: Double,
    private val frequencyAtNanos: (Long) -> Double?,
) : MicSource {
    override val sampleRate: Int = 44_100

    @Volatile private var running = false
    private var thread: Thread? = null

    override fun start(onWindow: (FloatArray, Long) -> Unit) {
        running = true
        thread = Thread({
            val window = FloatArray(PitchPipeline.WINDOW_SIZE)
            val hopNanos = PitchPipeline.HOP_SIZE * 1_000_000_000L / sampleRate
            var phase = 0.0
            var next = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val hz = frequencyAtNanos(now)?.let { it * 2.0.pow(centsOff / 1200) }
                window.copyInto(window, 0, PitchPipeline.HOP_SIZE, window.size)
                for (i in window.size - PitchPipeline.HOP_SIZE until window.size) {
                    if (hz == null) {
                        window[i] = 0f
                        continue
                    }
                    phase += 2 * PI * hz / sampleRate
                    var v = 0.0
                    for (h in 1..HARMONICS) v += sin(h * phase) / h
                    window[i] = (LEVEL * v / HARMONIC_SUM).toFloat()
                }
                onWindow(window, now)
                next += hopNanos
                val sleepMs = (next - System.nanoTime()) / 1_000_000
                if (sleepMs > 0) Thread.sleep(sleepMs)
            }
        }, "synthetic-singer").also { it.start() }
    }

    override fun stop() {
        running = false
        thread?.join()
        thread = null
    }

    private companion object {
        const val HARMONICS = 6
        const val LEVEL = 0.2
        const val HARMONIC_SUM = 2.45 // 1 + 1/2 + … + 1/6
    }
}
