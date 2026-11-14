package com.baijum.purandara.recording

import com.baijum.purandara.singalong.PitchFrame

/**
 * The analysed pitch of a whole recording (docs/plan-recordings.md): one
 * frame every [hopSeconds], the first at [offsetSeconds]. Saved next to the
 * audio so changing Sa or raga re-reads the swaras without re-analysing.
 */
class PitchTrack(
    val hopSeconds: Double,
    val offsetSeconds: Double,
    private val hz: FloatArray,
    private val rms: FloatArray,
) {
    init {
        require(hopSeconds > 0.0 && hopSeconds.isFinite()) { "hop must be positive" }
        require(hz.size == rms.size) { "one level per pitch" }
    }

    val size: Int get() = hz.size

    val durationSeconds: Double get() = if (size == 0) 0.0 else offsetSeconds + size * hopSeconds

    fun timeAt(index: Int): Double = offsetSeconds + index * hopSeconds

    /** Pitch of frame [index], or null when unvoiced. */
    fun frequencyAt(index: Int): Double? = hz[index].takeIf { it > 0f }?.toDouble()

    fun rmsAt(index: Int): Float = rms[index]

    /** Index of the frame nearest [seconds], clamped to the track. */
    fun indexAt(seconds: Double): Int =
        if (size == 0) 0 else ((seconds - offsetSeconds) / hopSeconds).toInt().coerceIn(0, size - 1)

    /** The versioned binary form stored as `<id>.pitch` (little-endian). */
    fun encode(): ByteArray {
        val out = ByteArray(HEADER_BYTES + size * FRAME_BYTES)
        var at = 0
        MAGIC.forEach { out[at++] = it }
        at = putInt(out, at, VERSION)
        at = putLong(out, at, hopSeconds.toRawBits())
        at = putLong(out, at, offsetSeconds.toRawBits())
        at = putInt(out, at, size)
        for (i in 0 until size) {
            at = putInt(out, at, hz[i].toRawBits())
            at = putInt(out, at, rms[i].toRawBits())
        }
        return out
    }

    override fun equals(other: Any?): Boolean = other is PitchTrack && other.hopSeconds == hopSeconds &&
        other.offsetSeconds == offsetSeconds && other.hz.contentEquals(hz) && other.rms.contentEquals(rms)

    override fun hashCode(): Int = 31 * hz.contentHashCode() + rms.contentHashCode()

    /** Collects frames as they are analysed, without boxing each one. */
    class Builder(private val hopSeconds: Double, private val offsetSeconds: Double) {
        private var hz = FloatArray(INITIAL_CAPACITY)
        private var rms = FloatArray(INITIAL_CAPACITY)
        private var count = 0

        fun add(frame: PitchFrame) {
            if (count == hz.size) {
                hz = hz.copyOf(count * 2)
                rms = rms.copyOf(count * 2)
            }
            hz[count] = frame.frequencyHz?.toFloat() ?: 0f
            rms[count] = frame.rms
            count++
        }

        fun build(): PitchTrack = PitchTrack(hopSeconds, offsetSeconds, hz.copyOf(count), rms.copyOf(count))
    }

    companion object {
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'T'.code.toByte(), 'R'.code.toByte(), 'K'.code.toByte())
        private const val VERSION = 1
        private const val HEADER_BYTES = 4 + 4 + 8 + 8 + 4
        private const val FRAME_BYTES = 8
        private const val INITIAL_CAPACITY = 1024

        /** Reads [encode]d bytes; null if they are not a track this version understands. */
        fun decode(bytes: ByteArray): PitchTrack? {
            if (bytes.size < HEADER_BYTES || (0 until 4).any { bytes[it] != MAGIC[it] }) return null
            if (getInt(bytes, 4) != VERSION) return null
            val hop = Double.fromBits(getLong(bytes, 8))
            val offset = Double.fromBits(getLong(bytes, 16))
            val count = getInt(bytes, 24)
            if (count < 0 || bytes.size.toLong() != HEADER_BYTES + count.toLong() * FRAME_BYTES) return null
            if (!(hop > 0.0) || !hop.isFinite() || !offset.isFinite()) return null
            val hz = FloatArray(count)
            val rms = FloatArray(count)
            var at = HEADER_BYTES
            for (i in 0 until count) {
                hz[i] = Float.fromBits(getInt(bytes, at)).takeIf { it.isFinite() } ?: 0f
                rms[i] = Float.fromBits(getInt(bytes, at + 4)).takeIf { it.isFinite() } ?: 0f
                at += FRAME_BYTES
            }
            return PitchTrack(hop, offset, hz, rms)
        }

        private fun putInt(out: ByteArray, at: Int, value: Int): Int {
            for (i in 0 until 4) out[at + i] = (value ushr (8 * i)).toByte()
            return at + 4
        }

        private fun putLong(out: ByteArray, at: Int, value: Long): Int {
            for (i in 0 until 8) out[at + i] = (value ushr (8 * i)).toByte()
            return at + 8
        }

        private fun getInt(bytes: ByteArray, at: Int): Int =
            (0 until 4).fold(0) { acc, i -> acc or ((bytes[at + i].toInt() and 0xFF) shl (8 * i)) }

        private fun getLong(bytes: ByteArray, at: Int): Long =
            (0 until 8).fold(0L) { acc, i -> acc or ((bytes[at + i].toLong() and 0xFF) shl (8 * i)) }
    }
}
