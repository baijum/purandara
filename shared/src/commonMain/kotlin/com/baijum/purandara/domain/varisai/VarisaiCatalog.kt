package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Raga
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
 * are all in [raga], the gitams in Malahari.
 */
object VarisaiCatalog {
    /** The raga of the varisais and alankarams (and of Shruti practice's swara lines). */
    val raga: Raga = Raga.MAYAMALAVAGOWLA

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

    fun inSet(set: VarisaiSet): List<Varisai> = all.filter { it.set == set }

    fun byId(id: String): Varisai? = all.firstOrNull { it.id == id }
}
