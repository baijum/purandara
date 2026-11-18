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

    /** The ragas the varisais and alankarams can be sung in. */
    val ragas: List<Raga> get() = Ragas.melakartas

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

    /** All exercises, with the varisais and alankarams in [raga] (one of [ragas]; others give [all]). */
    fun inRaga(raga: Raga): List<Varisai> = byRaga[raga.key] ?: all

    fun inSet(set: VarisaiSet): List<Varisai> = all.filter { it.set == set }

    /** The exercises of [set], the varisais and alankarams in [raga]. */
    fun inSet(set: VarisaiSet, raga: Raga): List<Varisai> = inRaga(raga).filter { it.set == set }

    /**
     * The exercise with [id], including one in another raga (`sarali-4@kalyani`);
     * null when unknown.
     */
    fun byId(id: String): Varisai? {
        val ragaKey = id.substringAfter(Varisai.RAGA_SEPARATOR, "")
        val chosen = if (ragaKey.isEmpty()) raga else Ragas.melakarta(ragaKey) ?: return null
        return inRaga(chosen).firstOrNull { it.id == id }
    }
}
