package com.baijum.purandara.recording

import com.baijum.purandara.domain.Raga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingLibraryTest {
    private fun recording(id: String, created: Long, ragaKey: String? = null) =
        Recording(id = id, createdMillis = created, durationSeconds = 12.5, saMidi = 50, ragaKey = ragaKey)

    @Test
    fun newestFirst() {
        val lib = RecordingLibrary().add(recording("a", 1)).add(recording("b", 3)).add(recording("c", 2))
        assertEquals(listOf("b", "c", "a"), lib.recordings.map { it.id })
    }

    @Test
    fun autoTitleNamesRagaSaAndDate() {
        val r = recording("a", 1)
        assertEquals("Sa D · 15 Nov, 6:42 pm", r.title("15 Nov, 6:42 pm"))
        val withRaga = r.copy(ragaKey = Raga.MAYAMALAVAGOWLA.key)
        assertEquals("Mayamalavagowla · Sa D · 15 Nov, 6:42 pm", withRaga.title("15 Nov, 6:42 pm"))
        assertEquals("Sa C♯ · x", r.copy(saMidi = 49).title("x"))
    }

    @Test
    fun renameAndClearingTheNameGoesBackToTheAutoTitle() {
        var lib = RecordingLibrary().add(recording("a", 1))
        lib = lib.rename("a", "  Sarali 3, slow  ")
        assertEquals("Sarali 3, slow", lib["a"]?.title("d"))
        lib = lib.rename("a", "   ")
        assertNull(lib["a"]?.customTitle)
        assertEquals("Sa D · d", lib["a"]?.title("d"))
    }

    @Test
    fun choosingARagaLaterUpdatesTheAutoTitle() {
        val lib = RecordingLibrary().add(recording("a", 1)).changeRaga("a", Raga.MALAHARI.key)
        assertEquals(Raga.MALAHARI, lib["a"]?.raga)
        assertEquals("Malahari · Sa D · d", lib["a"]?.title("d"))
        assertNull(lib.changeRaga("a", "no-such-raga")["a"]?.ragaKey)
        assertNull(lib.changeRaga("a", null)["a"]?.raga)
    }

    @Test
    fun changeSaClampsTheFineTune() {
        val r = RecordingLibrary().add(recording("a", 1)).changeSa("a", 53, 80)["a"]!!
        assertEquals(53, r.saMidi)
        assertEquals(50, r.saCents)
    }

    @Test
    fun deleteRemovesOnlyThatRecording() {
        val lib = RecordingLibrary().add(recording("a", 1)).add(recording("b", 2)).delete("a")
        assertEquals(listOf("b"), lib.recordings.map { it.id })
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val lib = RecordingLibrary().add(recording("a", 1, Raga.MOHANAM.key)).add(recording("b", 2)).rename("b", "Mine")
        assertEquals(lib, RecordingLibrary.decode(lib.encode()))
    }

    @Test
    fun unreadableIndexIsEmpty() {
        assertTrue(RecordingLibrary.decode(null).isEmpty)
        assertTrue(RecordingLibrary.decode("").isEmpty)
        assertTrue(RecordingLibrary.decode("{not json").isEmpty)
        assertTrue(RecordingLibrary.decode("[{\"id\":1}]").isEmpty)
    }

    @Test
    fun filesWithoutARecordingAreStrayAndRecordingsWithoutFilesAreDropped() {
        val lib = RecordingLibrary().add(recording("a", 1)).add(recording("b", 2))
        val files = listOf("a.m4a", "a.pitch", "b.m4a", "c.m4a", "c.pitch", "recordings.json")
        assertEquals(listOf("a"), lib.withFiles(files).recordings.map { it.id })
        assertEquals(listOf("c.m4a", "c.pitch"), lib.strayFiles(files))
    }
}
