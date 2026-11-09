package com.baijum.purandara.domain.varisai

/** A practice unit is not necessarily a breath point in a continuous performance. */
enum class PhraseReview {
    PENDING,
    APPROVED,
}

/** Authored whole-avarta boundaries, with a separate musical review before release. */
data class PracticePhrase(
    val id: String,
    val varisaiId: String,
    val label: String,
    val firstAvarta: Int,
    val lastAvarta: Int,
    val sourceNotes: String,
    val review: PhraseReview,
    val reviewedBy: String,
    val reviewNotes: String,
) {
    init {
        require(id.isNotBlank() && varisaiId.isNotBlank() && label.isNotBlank()) { "a phrase needs an identity and label" }
        require(firstAvarta >= 0 && lastAvarta >= firstAvarta) { "invalid phrase avartas" }
        require(sourceNotes.isNotBlank()) { "a phrase needs boundary sources" }
        require(review != PhraseReview.APPROVED || (reviewedBy.isNotBlank() && reviewNotes.isNotBlank())) {
            "an approved phrase needs its reviewer and musical review"
        }
    }

    /** Structural checks supplement, but cannot replace, review of words and phrasing. */
    fun validateFor(varisai: Varisai) {
        require(varisai.id == varisaiId) { "phrase $id belongs to $varisaiId, not ${varisai.id}" }
        require(lastAvarta < varisai.avartas.size) { "phrase $id extends past the piece" }
        val first = varisai.avartas[firstAvarta]
        require(first.aksharas.firstOrNull() != Akshara.Karvai) { "a phrase cannot begin inside a held note" }
        require(first.words?.firstOrNull() != HELD) { "a phrase cannot begin inside a held syllable" }
        val following = varisai.avartas.getOrNull(lastAvarta + 1)
        require(following?.aksharas?.firstOrNull() != Akshara.Karvai) { "a phrase cannot end inside a held note" }
        require(following?.words?.firstOrNull() != HELD) { "a phrase cannot end inside a held syllable" }
    }
}

/** No musical boundary is approved merely because it is a line, section or tala ending. */
object PracticePhrases {
    // Add entries only with recorded source and teacher review; no approvals are inferred.
    private val phrases: List<PracticePhrase> = emptyList()

    fun forVarisai(varisai: Varisai): List<PracticePhrase> = phrases.filter {
        it.varisaiId == varisai.id && it.review == PhraseReview.APPROVED
    }.onEach { it.validateFor(varisai) }
}
