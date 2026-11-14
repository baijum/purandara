package com.baijum.purandara.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mono 16-bit samples for the [Recorder]: the microphone, or a debug synthetic singer. */
interface RecorderInput {
    val sampleRate: Int

    fun start()

    /** Fills [buffer]; the number of samples read, or a negative error. Blocks until audio arrives. */
    fun read(buffer: ShortArray): Int

    /** Stops and releases; unblocks a pending [read]. */
    fun stop()
}

/**
 * The microphone with as little processing as the device allows
 * (docs/plan-recordings.md): the unprocessed source where supported, else
 * voice recognition, and automatic gain control off. No drone plays while
 * recording, so no echo cancellation is needed.
 */
class MicInput(private val audioManager: AudioManager) : RecorderInput {
    override val sampleRate: Int = SAMPLE_RATE
    private var record: AudioRecord? = null
    private var agc: AutomaticGainControl? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord.Builder()
            .setAudioSource(if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, SAMPLE_RATE)) // about half a second
            .build()
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(r.audioSessionId)?.also { it.enabled = false }
        record = r
        r.startRecording()
    }

    override fun read(buffer: ShortArray): Int = record?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1

    override fun stop() {
        record?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            it.release()
        }
        record = null
        agc?.release()
        agc = null
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}

/**
 * Debug only: sings Sarali Varisai 1 up and down (S R1 G3 M1 P D1 N3 S′ and
 * back) at [saHz], [centsOff] off, half a second a note, in real time; so a
 * recording can be made on an emulator without a voice.
 */
class SyntheticSingerInput(private val saHz: Double, private val centsOff: Double) : RecorderInput {
    override val sampleRate: Int = 44_100
    @Volatile private var running = false
    private var phase = 0.0
    private var frame = 0L
    private var startNanos = 0L

    override fun start() {
        running = true
        startNanos = System.nanoTime()
    }

    override fun read(buffer: ShortArray): Int {
        if (!running) return -1
        for (i in buffer.indices) {
            val t = frame.toDouble() / sampleRate
            val step = (t / NOTE_SECONDS).toInt() % SARALI.size
            val hz = saHz * SARALI[step] * 2.0.pow(centsOff / CENTS_PER_OCTAVE)
            phase += 2 * PI * hz / sampleRate
            var v = 0.0
            for (h in 1..HARMONICS) v += sin(h * phase) / h
            buffer[i] = (LEVEL * v / HARMONIC_SUM * Short.MAX_VALUE).toInt().toShort()
            frame++
        }
        val dueNanos = startNanos + frame * NANOS_PER_SECOND / sampleRate
        val sleepMs = (dueNanos - System.nanoTime()) / NANOS_PER_MS
        if (sleepMs > 0) Thread.sleep(sleepMs)
        return if (running) buffer.size else -1
    }

    override fun stop() {
        running = false
    }

    private companion object {
        val SARALI = listOf(1.0, 16 / 15.0, 5 / 4.0, 4 / 3.0, 1.5, 8 / 5.0, 15 / 8.0, 2.0).let { it + it.reversed() }
        const val NOTE_SECONDS = 0.5
        const val HARMONICS = 6
        const val LEVEL = 0.2
        const val HARMONIC_SUM = 2.45
        const val CENTS_PER_OCTAVE = 1200.0
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}

/**
 * Records [source] into an AAC .m4a [file] on its own thread
 * (docs/plan-recordings.md). [onLevel] reports the level (RMS, 0..1) and
 * seconds recorded about 20 times a second; [onFinished] reports how many
 * seconds were saved, or null when nothing usable was (both on the
 * recording thread). Stops by itself at [maxSeconds].
 */
class Recorder(
    private val source: RecorderInput,
    private val file: File,
    private val maxSeconds: Int,
    private val onLevel: (rms: Float, seconds: Double) -> Unit,
    private val onFinished: (seconds: Double?) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread(::run, "recorder").also { it.start() }
    }

    /** Asks the recording to end; [onFinished] follows once the file is complete. */
    fun stop() {
        running = false
        source.stop()
    }

    /** Stops and waits for the file to be finished. */
    fun stopAndWait() {
        stop()
        thread?.join()
        thread = null
    }

    @Suppress("TooGenericExceptionCaught") // MediaCodec and MediaMuxer throw IllegalStateException and IOException alike
    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val rate = source.sampleRate
        var frames = 0L
        var writer: AacWriter? = null
        try {
            source.start()
            val w = AacWriter(file, rate)
            writer = w
            val chunk = ShortArray(CHUNK)
            val maxFrames = maxSeconds.toLong() * rate
            while (running && frames < maxFrames) {
                val n = source.read(chunk)
                if (n <= 0) break
                w.write(chunk, n)
                frames += n
                onLevel(rms(chunk, n), frames.toDouble() / rate)
            }
            w.endOfStream()
        } catch (e: Exception) {
            Log.e(TAG, "recording failed", e)
        } finally {
            source.stop()
            val saved = writer?.close() == true
            onFinished(if (saved && frames >= rate / 2) frames.toDouble() / rate else null)
        }
    }

    private fun rms(chunk: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) {
            val v = chunk[i] / SHORT_SCALE
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }

    private companion object {
        const val TAG = "Recorder"
        const val CHUNK = 2048 // samples: about 46 ms at 44.1 kHz
        const val SHORT_SCALE = 32768.0
    }
}

/** 16-bit mono PCM → AAC-LC in an MPEG-4 file, with MediaCodec and MediaMuxer. */
private class AacWriter(file: File, private val sampleRate: Int) {
    private val encoder: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val muxer: MediaMuxer
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var started = false
    private var frames = 0L

    init {
        encoder.configure(
            MediaFormat.createAudioFormat(MIME, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        encoder.start()
        muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun write(chunk: ShortArray, count: Int) {
        queue(chunk, count, endOfStream = false)
        frames += count
        drain(endOfStream = false)
    }

    fun endOfStream() {
        queue(ShortArray(0), 0, endOfStream = true)
        drain(endOfStream = true)
    }

    /** Releases everything; true when the file was finished properly. */
    @Suppress("TooGenericExceptionCaught")
    fun close(): Boolean {
        var ok = started
        try {
            encoder.stop()
        } catch (e: Exception) {
            Log.w(TAG, "encoder stop", e)
        }
        encoder.release()
        try {
            if (started) muxer.stop()
        } catch (e: Exception) {
            Log.w(TAG, "muxer stop", e)
            ok = false
        }
        try {
            muxer.release()
        } catch (e: Exception) {
            Log.w(TAG, "muxer release", e)
        }
        return ok
    }

    /** Hands [count] samples to the encoder, draining its output while it has no free input. */
    private fun queue(chunk: ShortArray, count: Int, endOfStream: Boolean) {
        var index = encoder.dequeueInputBuffer(TIMEOUT_MICROS)
        var tries = 0
        while (index < 0) {
            check(++tries <= MAX_WAITS) { "encoder input stalled" }
            drain(endOfStream = false)
            index = encoder.dequeueInputBuffer(TIMEOUT_MICROS)
        }
        val buffer = checkNotNull(encoder.getInputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.clear()
        for (i in 0 until count) buffer.putShort(chunk[i])
        val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        encoder.queueInputBuffer(index, 0, count * 2, frames * MICROS / sampleRate, flags)
    }

    /** Encoder output → muxer; the muxer starts once the encoder reports its format. */
    private fun drain(endOfStream: Boolean) {
        var waits = 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_MICROS else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    check(++waits <= MAX_WAITS) { "encoder never finished" }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                }
                index >= 0 -> {
                    writeSample(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun writeSample(index: Int) {
        val buffer = encoder.getOutputBuffer(index)
        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        val hasData = started && !config && info.size > 0
        if (buffer != null && hasData) muxer.writeSampleData(track, buffer, info)
        encoder.releaseOutputBuffer(index, false)
    }

    private companion object {
        const val TAG = "AacWriter"
        const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        const val BIT_RATE = 96_000
        const val MAX_INPUT_BYTES = 16_384
        const val MICROS = 1_000_000L
        const val TIMEOUT_MICROS = 10_000L
        const val MAX_WAITS = 200 // about two seconds
    }
}
