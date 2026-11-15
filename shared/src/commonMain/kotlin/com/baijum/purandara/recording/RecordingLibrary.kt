package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Shruti
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One saved recording (docs/plan-recordings.md). The audio is `<id>.m4a` and
 * the analysed pitch `<id>.pitch`, next to the library's index.
 *
 * @property saMidi, saCents the Sa the swaras are read against (changeable later).
 * @property ragaKey the raga chosen for naming, or null for default names.
 * @property customTitle the student's own title; null shows the [autoTitle].
 * @property analysisVersion the [RecordingLibrary.ANALYSIS_VERSION] that made the `.pitch` file.
 *   Defaults to the first version: the index leaves out values equal to the
 *   default, so a default of the current version would make every old
 *   recording look up to date after a bump. New recordings pass
 *   [RecordingLibrary.ANALYSIS_VERSION].
 */
@Serializable
data class Recording(
    val id: String,
    val createdMillis: Long,
    val durationSeconds: Double,
    val saMidi: Int,
    val saCents: Int = 0,
    val ragaKey: String? = null,
    val customTitle: String? = null,
    val analysisVersion: Int = RecordingLibrary.FIRST_ANALYSIS_VERSION,
) {
    /** True when the `.pitch` file predates the current analysis and should be redone from the audio. */
    val needsAnalysis: Boolean get() = analysisVersion < RecordingLibrary.ANALYSIS_VERSION

    val raga: Raga? get() = ragaKey?.let { Ragas.byKey(it) }

    val saHz: Double get() = Shruti.saHz(saMidi, saCents)

    val audioFileName: String get() = "$id.$AUDIO_EXTENSION"
    val pitchFileName: String get() = "$id.$PITCH_EXTENSION"

    /** The title shown: the student's own, or the [autoTitle] with [dateLabel] (the locale's date and time). */
    fun title(dateLabel: String): String = customTitle ?: autoTitle(dateLabel)

    /** "Mayamalavagowla · Sa D · 15 Nov, 6:42 pm"; without a raga, "Sa D · 15 Nov, 6:42 pm". */
    fun autoTitle(dateLabel: String): String =
        listOfNotNull(raga?.name, "Sa ${Shruti.noteName(saMidi).trimEnd { it.isDigit() || it == '-' }}", dateLabel)
            .joinToString(" · ")

    companion object {
        const val AUDIO_EXTENSION = "m4a"
        const val PITCH_EXTENSION = "pitch"
    }
}

/**
 * The student's recordings, newest first. Immutable, like `PracticeHistory`:
 * platforms keep the latest value and persist [encode]d text as
 * `recordings.json`; the audio and pitch files live beside it.
 */
class RecordingLibrary(recordings: Collection<Recording> = emptyList()) {
    val recordings: List<Recording> = recordings.distinctBy { it.id }.sortedByDescending { it.createdMillis }

    val isEmpty: Boolean get() = recordings.isEmpty()

    operator fun get(id: String): Recording? = recordings.firstOrNull { it.id == id }

    fun add(recording: Recording): RecordingLibrary = RecordingLibrary(recordings.filter { it.id != recording.id } + recording)

    /** Sets the title; blank goes back to the automatic title. */
    fun rename(id: String, title: String): RecordingLibrary =
        update(id) { it.copy(customTitle = title.trim().ifEmpty { null }) }

    fun changeSa(id: String, midi: Int, cents: Int): RecordingLibrary =
        update(id) { it.copy(saMidi = midi, saCents = cents.coerceIn(Shruti.FINE_TUNE_RANGE)) }

    /** Sets the raga by key, or none with null (an unknown key also means none). */
    fun changeRaga(id: String, ragaKey: String?): RecordingLibrary =
        update(id) { it.copy(ragaKey = ragaKey?.takeIf { key -> Ragas.byKey(key) != null }) }

    /** Marks the recording's `.pitch` file as made by the current analysis. */
    fun analysed(id: String): RecordingLibrary = update(id) { it.copy(analysisVersion = ANALYSIS_VERSION) }

    fun delete(id: String): RecordingLibrary = RecordingLibrary(recordings.filter { it.id != id })

    /** Keeps only recordings whose audio and pitch files are among [fileNames] (the folder's contents). */
    fun withFiles(fileNames: Collection<String>): RecordingLibrary {
        val present = fileNames.toSet()
        return RecordingLibrary(recordings.filter { it.audioFileName in present && it.pitchFileName in present })
    }

    /** Recording files in [fileNames] that no recording owns (left by a crash or a failed save). */
    fun strayFiles(fileNames: Collection<String>): List<String> {
        val owned = recordings.flatMap { listOf(it.audioFileName, it.pitchFileName) }.toSet()
        val extensions = setOf(Recording.AUDIO_EXTENSION, Recording.PITCH_EXTENSION)
        return fileNames.filter { it.substringAfterLast('.', "") in extensions && it !in owned }
    }

    fun encode(): String = json.encodeToString(recordings)

    private fun update(id: String, change: (Recording) -> Recording): RecordingLibrary =
        RecordingLibrary(recordings.map { if (it.id == id) change(it) else it })

    override fun equals(other: Any?): Boolean = other is RecordingLibrary && other.recordings == recordings

    override fun hashCode(): Int = recordings.hashCode()

    companion object {
        /**
         * Bump when the analysis changes enough that old `.pitch` files should
         * be redone. 2: SwiftF0 v0.3 over the whole recording, and YIN no longer
         * discards quiet voices (docs/plan-swiftf0-upgrade.md).
         */
        const val ANALYSIS_VERSION = 2

        /** The version of recordings saved before the field was written. */
        const val FIRST_ANALYSIS_VERSION = 1

        /** Longest recording, in seconds (10 minutes). */
        const val MAX_SECONDS = 600

        /** When the record screen warns that the limit is near. */
        const val WARN_SECONDS = 570

        const val INDEX_FILE_NAME = "recordings.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** Reads [encode]d text; unreadable or missing text gives an empty library. */
        fun decode(text: String?): RecordingLibrary = try {
            if (text.isNullOrBlank()) RecordingLibrary() else RecordingLibrary(json.decodeFromString<List<Recording>>(text))
        } catch (_: SerializationException) {
            RecordingLibrary()
        } catch (_: IllegalArgumentException) {
            RecordingLibrary()
        }
    }
}
