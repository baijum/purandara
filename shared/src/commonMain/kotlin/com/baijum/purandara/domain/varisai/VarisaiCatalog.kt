package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.varisai.catalog.ALANKARAM_NOTATION
import com.baijum.purandara.domain.varisai.catalog.DHATTU_VARISAI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.GITAM_NOTATION
import com.baijum.purandara.domain.varisai.catalog.JANTA_VARISAI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.MADHYA_STHAYI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.SARALI_VARISAI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.SWARAJATI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.VAKRA_JANTA_VARISAI_NOTATION
import com.baijum.purandara.domain.varisai.catalog.VARNAM_NOTATION

/**
 * All bundled exercises, in teaching order. Parsed once, on first use.
 *
 * Each exercise carries its own raga and tala; the varisais and alankarams
 * are written in [raga], the gitams in Malahari. [inRaga] gives the catalog
 * with the varisais and alankarams in one of [Ragas.melakartas] instead
 * (docs/plan-other-ragas.md).
 */
object VarisaiCatalog {
    /** The raga the varisais and alankarams are written in, and the default choice. */
    val raga: Raga = Raga.MAYAMALAVAGOWLA

    /** The app's ragas the varisais and alankarams can be sung in. */
    val ragas: List<Raga> get() = Ragas.melakartas

    /** [ragas], then the student's own that the varisais can be sung in (all seven swaras both ways). */
    val allRagas: List<Raga> get() = ragas + Ragas.own.filter { it.isSampurna }

    /** The plain ascent and descent (the workbook's Card G), one swara per akshara. */
    private const val PLAIN_SCALE_NOTATION = """
1
S R G M P D N S′
S′ N D P M G R S
"""

    val all: List<Varisai> by lazy {
        val parser = VarisaiParser()
        listOf(
            VarisaiSet.PLAIN_SCALE to PLAIN_SCALE_NOTATION,
            VarisaiSet.SARALI to SARALI_VARISAI_NOTATION,
            VarisaiSet.MADHYA_STHAYI to MADHYA_STHAYI_NOTATION,
            VarisaiSet.JANTA to JANTA_VARISAI_NOTATION,
            VarisaiSet.VAKRA_JANTA to VAKRA_JANTA_VARISAI_NOTATION,
            VarisaiSet.DHATTU to DHATTU_VARISAI_NOTATION,
            VarisaiSet.ALANKARAM to ALANKARAM_NOTATION,
            VarisaiSet.GITAM to GITAM_NOTATION,
            VarisaiSet.SWARAJATI to SWARAJATI_NOTATION,
            VarisaiSet.VARNAM to VARNAM_NOTATION,
        ).flatMap { (set, text) -> parser.parse(text, set) }
    }

    private val byRaga: Map<String, List<Varisai>> by lazy {
        ragas.associate { raga -> raga.key to all.map { it.inRaga(raga) } }
    }

    /** The student's own ragas' catalogs, by key, with the raga they were made for (rebuilt when it changes). */
    private var byOwnRaga: Map<String, Pair<Raga, List<Varisai>>> = emptyMap()

    /**
     * All exercises, with the varisais and alankarams in [raga]: one of
     * [ragas], or a sampurna raga of the student's own
     * (docs/plan-own-raga-varisais.md). Any other raga gives [all].
     */
    fun inRaga(raga: Raga): List<Varisai> {
        byRaga[raga.key]?.let { return it }
        if (!raga.isSampurna) return all
        byOwnRaga[raga.key]?.takeIf { it.first === raga }?.let { return it.second }
        val catalog = all.map { it.inRaga(raga) }
        byOwnRaga = byOwnRaga + (raga.key to (raga to catalog))
        return catalog
    }

    fun inSet(set: VarisaiSet): List<Varisai> = all.filter { it.set == set }

    /** The exercises of [set], the varisais and alankarams in [raga]. */
    fun inSet(set: VarisaiSet, raga: Raga): List<Varisai> = inRaga(raga).filter { it.set == set }

    /**
     * The exercise with [id], including one in another raga (`sarali-4@kalyani`);
     * null when unknown.
     */
    fun byId(id: String): Varisai? {
        val ragaKey = id.substringAfter(Varisai.RAGA_SEPARATOR, "")
        val chosen = if (ragaKey.isEmpty()) raga else Ragas.forVarisais(ragaKey) ?: return null
        return inRaga(chosen).firstOrNull { it.id == id }
    }
}
