package com.baijum.purandara.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
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

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null
    private var focusRequest: AudioFocusRequest? = null
    private var expectedOutputId: Int? = null

    /** Main-thread callback, once when an active track's actual output changes. */
    var onOutputChanged: (() -> Unit)? = null

    /** True from [play] until playback finishes or [stop] is called. */
    val isPlaying: Boolean get() = session?.finished == false

    /**
     * Starts [renderer] from its beginning. [onFinished] runs on the playback
     * thread when a non-looping performance has been fully heard, or when
     * audio focus is lost.
     */
    fun play(renderer: SampleRenderer, automaticRestart: Boolean = false, onFinished: () -> Unit) {
        stop()
        if (!automaticRestart) expectedOutputId = null // only a user start acknowledges a new output
        var ownedSession: Session? = null
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    ownedSession?.takeIf { session === it }?.requestStop()
                }
            }
            .build()
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onFinished()
            return
        }
        focusRequest = focus
        renderer.reset()
        val next = Session(renderer, onFinished)
        ownedSession = next
        session = next
        next.start()
    }

    /** Normal stops fade; a changed output stops immediately, without waiting on a lost route. */
    fun stop(immediate: Boolean = false) {
        val previous = session
        session = null // invalidate queued route/focus callbacks before teardown
        previous?.let {
            it.detachRouteListener()
            if (immediate) it.hardStop() else {
                it.requestStop()
                it.awaitFade()
            }
        }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Frames the hardware has played since [play], or null when idle. */
    fun positionFrames(): Long? = session?.takeUnless { it.finished }?.playedFrames()

    /**
     * Called on the playback thread after each rendered block with the frame
     * it starts at and its RMS level (sing-along uses this to model leakage).
     */
    @Volatile var onRendered: ((startFrame: Long, rms: Float) -> Unit)? = null

    /**
     * The rendered frame that was leaving the speaker at [nanoTime]
     * ([System.nanoTime] clock), or null when idle. Uses the hardware
     * presentation timestamp when available, so output latency is included.
     * Safe to call from any thread.
     */
    fun outputFrameAt(nanoTime: Long): Long? = session?.takeUnless { it.finished }?.frameAt(nanoTime)

    private inner class Session(private val renderer: SampleRenderer, private val onFinished: () -> Unit) {
        @Volatile private var stopRequested = false
        @Volatile private var immediateStop = false
        private val trackLock = Any()
        private var released = false
        private val renderCallback = onRendered
        private val routeChanges = RoutedOutputChanges(expectedOutputId)
        private val routeListener = AudioRouting.OnRoutingChangedListener { routing -> observeRoute(routing) }

        private fun observeRoute(routing: AudioRouting) {
            val changed = synchronized(trackLock) {
                if (session !== this || finished || released) return
                routeChanges.observe(routing.routedDevice?.id, SystemClock.elapsedRealtime()).also { expectedOutputId = routeChanges.baselineDeviceId }
            }
            if (changed) {
                hardStop() // stop/flush unblocks a blocking write and prevents a drain
                onOutputChanged?.invoke()
            }
        }

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

        fun start() {
            track.addOnRoutingChangedListener(routeListener, mainHandler)
            thread.start()
        }

        fun detachRouteListener() {
            synchronized(trackLock) {
                if (!released) track.removeOnRoutingChangedListener(routeListener)
            }
        }

        fun awaitFade() {
            thread.join(STOP_FADE_TIMEOUT_MS)
            if (thread.isAlive) hardStop() // a vanished route may never consume the fade/drain
        }

        fun requestStop() {
            stopRequested = true
        }

        fun hardStop() {
            immediateStop = true
            synchronized(trackLock) {
                if (!released) {
                    try {
                        track.pause()
                        track.flush()
                        track.stop()
                    } catch (e: IllegalStateException) {
                        // Never initialised, or its server side died with the route.
                        Log.w(TAG, "hard stop on an unusable track", e)
                    }
                }
            }
        }

        /** Frames played; the head position is an unsigned 32-bit counter. */
        fun playedFrames(): Long = track.playbackHeadPosition.toLong() and UINT32_MASK

        private val timestamp = AudioTimestamp()

        fun frameAt(nanoTime: Long): Long? = synchronized(timestamp) {
            try {
                if (track.getTimestamp(timestamp)) {
                    timestamp.framePosition + (nanoTime - timestamp.nanoTime) * renderer.sampleRate / NANOS_PER_SECOND
                } else {
                    // No hardware timestamp yet: the play head lags the speaker by
                    // roughly the buffer, which is close enough for the first frames.
                    playedFrames() - track.bufferSizeInFrames / 2
                }
            } catch (_: IllegalStateException) {
                null // released concurrently
            }
        }

        private fun writeBlock(buffer: FloatArray, size: Int) {
            var offset = 0
            while (offset < size && !immediateStop) {
                val written = track.write(buffer, offset, size - offset, AudioTrack.WRITE_BLOCKING)
                if (immediateStop) return
                check(written > 0) { "AudioTrack.write failed: $written" }
                offset += written
            }
        }

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = FloatArray(CHUNK_FRAMES)
            var written = 0L
            var stopping = false
            try {
                synchronized(trackLock) {
                    if (!immediateStop) track.play()
                }
                mainHandler.post {
                    observeRoute(track)
                }
                while (!immediateStop) {
                    if (stopRequested && !stopping) {
                        renderer.fadeOut()
                        stopping = true
                    }
                    val n = renderer.render(buffer)
                    if (n == 0 || immediateStop) break
                    renderCallback?.invoke(written, rms(buffer, n))
                    writeBlock(buffer, n)
                    written += n
                }
                // Let the last buffered audio play out before reporting the end.
                while (!immediateStop && playedFrames() < written && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Thread.sleep(DRAIN_POLL_MS)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "playback failed", e)
            } finally {
                finished = true
                synchronized(trackLock) {
                    track.removeOnRoutingChangedListener(routeListener)
                    try {
                        track.stop()
                    } finally {
                        track.release()
                        released = true
                    }
                }
                onFinished()
            }
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L

        fun rms(buffer: FloatArray, n: Int): Float {
            var sum = 0.0
            for (i in 0 until n) sum += buffer[i] * buffer[i]
            return kotlin.math.sqrt(sum / n).toFloat()
        }

        const val TAG = "VarisaiAudioPlayer"
        const val CHUNK_FRAMES = 1024
        const val BUFFER_FRAMES = 4096
        const val STOP_FADE_TIMEOUT_MS = 500L
        const val DRAIN_POLL_MS = 10L
        const val UINT32_MASK = 0xFFFF_FFFFL
    }
}
