package com.baijum.purandara.viewmodel

import com.baijum.purandara.data.RecordingStore
import com.baijum.purandara.domain.CustomRaga
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Swara
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * "Your own raga" for [RecordingsViewModel] (docs/plan-recording-insights.md,
 * step 3): opens and closes the editor, saves and deletes the student's
 * ragas. [reread] reads the open recording again (its raga changed),
 * [choose] makes a new raga the choice on the screen below, and [forget]
 * clears a deleted raga from recordings and the Record screen.
 */
class RagaEditing(
    private val store: RecordingStore,
    private val state: MutableStateFlow<RecordingsUiState>,
    private val reread: () -> Unit,
    private val choose: (String) -> Unit,
    private val chooseForVarisais: (String) -> Unit,
    private val forget: (String) -> Unit,
) {
    /** Opens the editor for [key], or for a new raga with null; [forVarisais] from the varisais' raga list. */
    fun edit(key: String?, forVarisais: Boolean = false) {
        state.update { it.copy(ragaEditor = RagaEditor(key?.takeIf { k -> it.customRagas[k] != null }, forVarisais)) }
    }

    fun close() = state.update { it.copy(ragaEditor = null) }

    /** Saves the editor's raga, when it passes the checks, and closes the editor. */
    fun save(name: String, arohana: List<Swara>, avarohana: List<Swara>) {
        val editor = state.value.ragaEditor ?: return
        val ragas = state.value.customRagas
        if (ragas.problem(editor.key, name, arohana, avarohana) != null) return
        val key = editor.key ?: CustomRaga.keyAt(System.currentTimeMillis())
        use(ragas.save(key, name, arohana, avarohana))
        if (editor.key == null) {
            if (editor.forVarisais) chooseForVarisais(key) else choose(key)
        }
    }

    /** Deletes the editor's raga; what used it goes back to default names and singing freely. */
    fun delete() {
        val key = state.value.ragaEditor?.key ?: return
        use(state.value.customRagas.delete(key))
        forget(key)
    }

    private fun use(ragas: CustomRagas) {
        store.saveRagas(ragas)
        Ragas.useOwn(ragas)
        state.update { it.copy(customRagas = ragas, ragaEditor = null) }
        reread()
    }
}
