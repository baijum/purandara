package com.baijum.purandara.data

import android.content.Context
import android.util.Log
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingLibrary
import com.baijum.purandara.recording.Scale
import java.io.File
import java.io.IOException

/**
 * Recordings on the device (docs/plan-recordings.md): `recordings/` in the
 * app's private files holds each `<id>.m4a` and `<id>.pitch`, and the index
 * `recordings.json`. Nothing here ever leaves the device.
 */
class RecordingStore(context: Context) {
    val folder: File = File(context.filesDir, FOLDER)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** The Record screen's "What are you singing?": a raga key (null: singing freely) and scale, remembered. */
    var intentRaga: String?
        get() = preferences.getString(KEY_INTENT_RAGA, null)
        set(value) = preferences.edit().putString(KEY_INTENT_RAGA, value).apply()

    var intentScale: Scale?
        get() = preferences.getString(KEY_INTENT_SCALE, null)?.let { name -> Scale.entries.firstOrNull { it.name == name } }
        set(value) = preferences.edit().putString(KEY_INTENT_SCALE, value?.name).apply()

    /** Ragas chosen lately, most recent first (shown at the top of the raga list). */
    var recentRagas: List<String>
        get() = preferences.getString(KEY_RECENT_RAGAS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = preferences.edit().putString(KEY_RECENT_RAGAS, value.joinToString(",")).apply()

    /** Puts [ragaKey] first among the recent ragas (keeping [RECENT] of them); returns the new list. */
    fun noteRecentRaga(ragaKey: String): List<String> =
        (listOf(ragaKey) + recentRagas.filter { it != ragaKey }).take(RECENT).also { recentRagas = it }

    /** The Record screen's "External tanpura playing" switch, remembered for next time. */
    var externalTanpura: Boolean
        get() = preferences.getBoolean(KEY_TANPURA, false)
        set(value) = preferences.edit().putBoolean(KEY_TANPURA, value).apply()
    private val index = File(folder, RecordingLibrary.INDEX_FILE_NAME)
    private val ragasFile = File(folder, CustomRagas.FILE_NAME)

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

    /** The student's own ragas (`ragas.json` beside the index); none if missing or damaged. */
    fun loadRagas(): CustomRagas = CustomRagas.decode(
        try {
            ragasFile.takeIf { it.isFile }?.readText()
        } catch (e: IOException) {
            Log.w(TAG, "could not read the ragas", e)
            null
        },
    )

    fun saveRagas(ragas: CustomRagas) {
        try {
            folder.mkdirs()
            val tmp = File(folder, "${CustomRagas.FILE_NAME}.tmp")
            tmp.writeText(ragas.encode())
            if (!tmp.renameTo(ragasFile)) throw IOException("rename failed")
        } catch (e: IOException) {
            Log.e(TAG, "could not save the ragas", e)
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
        const val PREFERENCES = "recordings"
        const val KEY_TANPURA = "external_tanpura"
        const val KEY_INTENT_RAGA = "intent_raga"
        const val KEY_INTENT_SCALE = "intent_scale"
        const val KEY_RECENT_RAGAS = "recent_ragas"
        const val RECENT = 3
    }
}
