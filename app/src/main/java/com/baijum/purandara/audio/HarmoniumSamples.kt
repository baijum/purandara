package com.baijum.purandara.audio

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.baijum.purandara.domain.HarmoniumBank
import com.baijum.purandara.domain.TanpuraBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/** Decodes the bundled harmonium notes and tanpura loops (FLAC assets) into a [SampleBank]. */
object HarmoniumSamples {
    /** Sample rate of the prepared files (tools/samples/prepare_harmonium.py). */
    const val SAMPLE_RATE = 44_100

    /** Decodes everything in parallel, off the main thread. */
    suspend fun load(assets: AssetManager): SampleBank = withContext(Dispatchers.Default) {
        coroutineScope {
            val harmonium = HarmoniumBank.midiRange.map { midi ->
                async { midi to decode(assets, HarmoniumBank.assetPath(midi)) }
            }
            val tanpura = TanpuraBank.SA_MIDIS.map { midi ->
                async { midi to decode(assets, TanpuraBank.assetPath(midi)) }
            }
            SampleBank(SAMPLE_RATE, harmonium.awaitAll().toMap(), tanpura.awaitAll().toMap())
        }
    }

    /** Decodes one FLAC asset to mono float PCM. */
    fun decode(assets: AssetManager, path: String): FloatArray {
        val extractor = MediaExtractor()
        assets.openFd(path).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        try {
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            check(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == SAMPLE_RATE) { "$path: unexpected sample rate" }
            check(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) { "$path: expected mono" }
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, path)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, path: String): FloatArray {
        val out = FloatList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var floatOutput = false
        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    floatOutput = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (floatOutput) {
                        val fb = buffer.asFloatBuffer()
                        while (fb.hasRemaining()) out.add(fb.get())
                    } else {
                        val sb = buffer.asShortBuffer()
                        while (sb.hasRemaining()) out.add(sb.get() / SHORT_SCALE)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        check(out.size > 0) { "$path decoded to nothing" }
        return out.toArray()
    }

    /** Growable float buffer without boxing. */
    private class FloatList {
        private var data = FloatArray(INITIAL_CAPACITY)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }

    private const val TIMEOUT_US = 10_000L
    private const val SHORT_SCALE = 32768f
    private const val INITIAL_CAPACITY = 128 * 1024
}
