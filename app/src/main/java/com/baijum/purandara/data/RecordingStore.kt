package com.baijum.purandara.data

import android.content.Context
import android.util.Log
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingLibrary
import java.io.File
import java.io.IOException

/**
 * Recordings on the device (docs/plan-recordings.md): `recordings/` in the
 * app's private files holds each `<id>.m4a` and `<id>.pitch`, and the index
 * `recordings.json`. Nothing here ever leaves the device.
 */
class RecordingStore(context: Context) {
    val folder: File = File(context.filesDir, FOLDER)
    private val index = File(folder, RecordingLibrary.INDEX_FILE_NAME)

    /** The saved library, without entries whose files are gone; stray files are removed. */
    fun load(): RecordingLibrary {
        folder.mkdirs()
        val text = try {
            if (index.isFile) index.readText() else null
        } catch (e: IOException) {
            Log.w(TAG, "could not read the recordings index", e)
            null
        }
        val files = folder.list()?.toList().orEmpty()
        val library = RecordingLibrary.decode(text).withFiles(files)
        library.strayFiles(files).forEach { File(folder, it).delete() }
        return library
    }

    /** Writes the index atomically (a temporary file, then a rename). */
    fun save(library: RecordingLibrary) {
        try {
            folder.mkdirs()
            val tmp = File(folder, "${RecordingLibrary.INDEX_FILE_NAME}.tmp")
            tmp.writeText(library.encode())
            if (!tmp.renameTo(index)) throw IOException("rename failed")
        } catch (e: IOException) {
            Log.e(TAG, "could not save the recordings index", e)
        }
    }

    fun audioFile(id: String): File = File(folder, "$id.${Recording.AUDIO_EXTENSION}")

    fun pitchFile(id: String): File = File(folder, "$id.${Recording.PITCH_EXTENSION}")

    @Throws(IOException::class)
    fun writePitch(id: String, track: PitchTrack) {
        val tmp = File(folder, "$id.tmp")
        tmp.writeBytes(track.encode())
        if (!tmp.renameTo(pitchFile(id))) throw IOException("rename failed")
    }

    fun readPitch(id: String): PitchTrack? = try {
        PitchTrack.decode(pitchFile(id).readBytes())
    } catch (e: IOException) {
        Log.w(TAG, "could not read pitch for $id", e)
        null
    }

    fun deleteFiles(id: String) {
        audioFile(id).delete()
        pitchFile(id).delete()
    }

    private companion object {
        const val TAG = "RecordingStore"
        const val FOLDER = "recordings"
    }
}
