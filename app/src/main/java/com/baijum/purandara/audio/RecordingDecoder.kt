package com.baijum.purandara.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.baijum.purandara.pitch.PitchRange
import com.baijum.purandara.pitch.SwiftF0Model
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.RecordingAnalyzer
import com.baijum.purandara.singalong.PitchPipeline
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes a saved recording and analyses its pitch (docs/plan-recordings.md):
 * the .m4a back to PCM, mixed to mono, through the shared [RecordingAnalyzer].
 * [model] loads SwiftF0 (null for YIN alone; closed afterwards if
 * [AutoCloseable]), searching [range]; [tanpura] when the student's own
 * tanpura played (SwiftF0's pitch alone); [onProgress] gets the fraction done.
 * Blocking: call off the main thread.
 */
object RecordingDecoder {
    @Throws(IOException::class)
    fun analyse(file: File, model: () -> SwiftF0Model?, range: PitchRange, tanpura: Boolean, onProgress: (Float) -> Unit): PitchTrack {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("no audio in ${file.name}")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            return Session(extractor, codec, format, Analysis(model, range, tanpura), onProgress).run()
        } catch (e: IllegalStateException) {
            throw IOException("could not decode ${file.name}", e) // includes MediaCodec.CodecException
        } finally {
            decoder?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private class Session(
        private val extractor: MediaExtractor,
        private val codec: MediaCodec,
        format: MediaFormat,
        private val analysis: Analysis,
        private val onProgress: (Float) -> Unit,
    ) {
        private val durationMicros = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        private var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private var analyzer: RecordingAnalyzer? = null
        private var swiftF0: SwiftF0Model? = null
        private var mono = FloatArray(0)
        private val info = MediaCodec.BufferInfo()
        private var inputDone = false
        private var outputDone = false

        fun run(): PitchTrack {
            try {
                while (!outputDone) {
                    if (!inputDone) feedInput()
                    takeOutput()
                }
                // finish() runs SwiftF0's last chunk, so the model must still be open.
                val track = analyzer?.finish() ?: PitchTrack.Builder(PitchPipeline.HOP_SIZE.toDouble() / sampleRate, 0.0).build()
                onProgress(1f)
                return track
            } finally {
                (swiftF0 as? AutoCloseable)?.close()
            }
        }

        private fun feedInput() {
            val index = codec.dequeueInputBuffer(TIMEOUT_MICROS)
            if (index < 0) return
            val size = extractor.readSampleData(checkNotNull(codec.getInputBuffer(index)), 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
            }
        }

        private fun takeOutput() {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            if (index < 0) return
            if (info.size > 0) analyse(index)
            codec.releaseOutputBuffer(index, false)
            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        }

        private fun analyse(index: Int) {
            val buffer = codec.getOutputBuffer(index) ?: return
            val a = analyzer ?: newAnalyzer().also { analyzer = it }
            val bytes = buffer.duplicate().apply {
                position(info.offset)
                limit(info.offset + info.size)
            }
            val shorts = bytes.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
            val frames = shorts.remaining() / channels
            if (mono.size < frames) mono = FloatArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += shorts.get(f * channels + c)
                mono[f] = sum / channels / SHORT_SCALE
            }
            a.feed(mono, frames)
            if (durationMicros > 0) onProgress((info.presentationTimeUs.toFloat() / durationMicros).coerceIn(0f, 1f))
        }

        private fun newAnalyzer(): RecordingAnalyzer {
            val m = analysis.model()
            swiftF0 = m
            return RecordingAnalyzer(sampleRate, m, analysis.range, analysis.tanpura)
        }
    }

    /** How to analyse: the model, its search range, and whether a tanpura played. */
    private class Analysis(val model: () -> SwiftF0Model?, val range: PitchRange, val tanpura: Boolean)

    private const val TIMEOUT_MICROS = 10_000L
    private const val SHORT_SCALE = 32768f
}
