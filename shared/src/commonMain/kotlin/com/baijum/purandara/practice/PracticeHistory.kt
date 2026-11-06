package com.baijum.purandara.practice

import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.singalong.PassResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What the student has done with one exercise (stored on the device only). */
@Serializable
data class PracticeRecord(
    val varisaiId: String,
    val lastPracticedMillis: Long,
    val timesPracticed: Int,
    /** Best sing-along pass: notes in tune or close, out of [bestJudged] judged (0 = none yet). */
    val bestInTuneOrClose: Int = 0,
    val bestJudged: Int = 0,
) {
    val hasBest: Boolean get() = bestJudged > 0
}

/**
 * Practice history for the landing page (docs/plan-landing.md): what was
 * practised, when, and the best sing-along result. Immutable; platforms keep
 * the latest value and persist [encode]d text.
 */
class PracticeHistory(records: Collection<PracticeRecord> = emptyList()) {
    private val byId: Map<String, PracticeRecord> = records.associateBy { it.varisaiId }

    val records: Collection<PracticeRecord> get() = byId.values

    operator fun get(varisaiId: String): PracticeRecord? = byId[varisaiId]

    /** A full pass of [varisaiId] was heard to the end (once per playback). */
    fun practised(varisaiId: String, nowMillis: Long): PracticeHistory {
        val old = byId[varisaiId]
        val record = old?.copy(lastPracticedMillis = nowMillis, timesPracticed = old.timesPracticed + 1)
            ?: PracticeRecord(varisaiId, nowMillis, 1)
        return with(record)
    }

    /**
     * A sing-along pass of [varisaiId] was scored. It can become the best only
     * if at least half its notes were judged, so a pass that heard little
     * doesn't count; ties keep the earlier best.
     */
    fun scored(varisaiId: String, result: PassResult, nowMillis: Long): PracticeHistory {
        val judged = result.summary.judged
        if (judged == 0 || judged * 2 < result.marks.size) return this
        val good = result.summary.inTuneOrClose
        val old = byId[varisaiId] ?: PracticeRecord(varisaiId, nowMillis, 0)
        val better = !old.hasBest || good.toLong() * old.bestJudged > old.bestInTuneOrClose.toLong() * judged
        return if (better) with(old.copy(bestInTuneOrClose = good, bestJudged = judged)) else this
    }

    /** The most recently practised exercise, if any. */
    fun continueWith(catalog: List<Varisai>): Varisai? =
        byId.values.filter { it.timesPracticed > 0 }.maxByOrNull { it.lastPracticedMillis }
            ?.let { r -> catalog.firstOrNull { it.id == r.varisaiId } }

    /** The exercise after the most recently practised one, in teaching order (null at the end). */
    fun nextUp(catalog: List<Varisai>): Varisai? {
        val last = continueWith(catalog) ?: return null
        return catalog.getOrNull(catalog.indexOf(last) + 1)
    }

    fun practisedCount(set: VarisaiSet, catalog: List<Varisai>): Int =
        catalog.count { it.set == set && (byId[it.id]?.timesPracticed ?: 0) > 0 }

    fun encode(): String = json.encodeToString(byId.values.sortedBy { it.varisaiId })

    private fun with(record: PracticeRecord) = PracticeHistory(byId.values.filter { it.varisaiId != record.varisaiId } + record)

    override fun equals(other: Any?): Boolean = other is PracticeHistory && other.byId == byId

    override fun hashCode(): Int = byId.hashCode()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads [encode]d text; unreadable or missing text gives an empty history. */
        fun decode(text: String?): PracticeHistory = try {
            if (text.isNullOrBlank()) PracticeHistory() else PracticeHistory(json.decodeFromString<List<PracticeRecord>>(text))
        } catch (_: SerializationException) {
            PracticeHistory()
        } catch (_: IllegalArgumentException) {
            PracticeHistory()
        }
    }
}
