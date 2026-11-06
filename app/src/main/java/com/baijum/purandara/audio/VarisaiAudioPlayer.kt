package com.baijum.purandara.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Streams a [SampleRenderer] to an [AudioTrack] on a dedicated thread.
 *
 * The renderer runs ahead of the speaker; [positionFrames] reports what the
 * hardware has actually played, so the on-screen highlight matches the sound.
 * Not thread-safe: call from the main thread.
 */
class VarisaiAudioPlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var session: Session? = null
    private var focusRequest: AudioFocusRequest? = null

    /** True from [play] until playback finishes or [stop] is called. */
    val isPlaying: Boolean get() = session?.finished == false

    /**
     * Starts [renderer] from its beginning. [onFinished] runs on the playback
     * thread when a non-looping performance has been fully heard, or when
     * audio focus is lost.
     */
    fun play(renderer: SampleRenderer, onFinished: () -> Unit) {
        stop()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    session?.requestStop()
                }
            }
            .build()
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onFinished()
            return
        }
        focusRequest = focus
        renderer.reset()
        session = Session(renderer, onFinished).also { it.start() }
    }

    /** Stops with a short fade, waiting for the playback thread to end. */
    fun stop() {
        session?.let {
            it.requestStop()
            it.join()
        }
        session = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Frames the hardware has played since [play], or null when idle. */
    fun positionFrames(): Long? = session?.takeUnless { it.finished }?.playedFrames()

    private inner class Session(private val renderer: SampleRenderer, private val onFinished: () -> Unit) {
        @Volatile private var stopRequested = false

        @Volatile var finished = false
            private set

        private val track: AudioTrack
        private val thread = Thread(::run, "varisai-audio")

        init {
            val minBytes = AudioTrack.getMinBufferSize(
                renderer.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(renderer.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBytes, BUFFER_FRAMES * Float.SIZE_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        fun start() = thread.start()

        fun join() = thread.join()

        fun requestStop() {
            stopRequested = true
        }

        /** Frames played; the head position is an unsigned 32-bit counter. */
        fun playedFrames(): Long = track.playbackHeadPosition.toLong() and UINT32_MASK

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = FloatArray(CHUNK_FRAMES)
            var written = 0L
            var stopping = false
            try {
                track.play()
                while (true) {
                    if (stopRequested && !stopping) {
                        renderer.fadeOut()
                        stopping = true
                    }
                    val n = renderer.render(buffer)
                    if (n == 0) break
                    var off = 0
                    while (off < n) {
                        val w = track.write(buffer, off, n - off, AudioTrack.WRITE_BLOCKING)
                        if (w < 0) error("AudioTrack.write failed: $w")
                        off += w
                    }
                    written += n
                }
                // Let the last buffered audio play out before reporting the end.
                while (playedFrames() < written && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Thread.sleep(DRAIN_POLL_MS)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "playback failed", e)
            } finally {
                finished = true
                track.stop()
                track.release()
                onFinished()
            }
        }
    }

    private companion object {
        const val TAG = "VarisaiAudioPlayer"
        const val CHUNK_FRAMES = 1024
        const val BUFFER_FRAMES = 4096
        const val DRAIN_POLL_MS = 10L
        const val UINT32_MASK = 0xFFFF_FFFFL
    }
}
