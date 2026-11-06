package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.SwaraLetter

/**
 * One level of an exercise's melodic shape: from [start] to [end] (fractions
 * of the whole exercise, 0..1) at [degree] scale steps above Sa (S = 0 … N = 6,
 * upper S = 7, lower N = -1).
 */
data class ContourStep(val start: Double, val end: Double, val degree: Int)

/** The pitch shape of a whole exercise, for the small line drawn in the exercise list. */
object Contour {
    /**
     * Steps in order. Each akshara's swaras share it evenly; a karvai extends
     * the previous step, so held notes read as long flat runs.
     */
    fun of(varisai: Varisai): List<ContourStep> {
        val total = varisai.aksharaCount.toDouble()
        if (total == 0.0) return emptyList()
        val steps = mutableListOf<ContourStep>()
        var index = 0
        for (avarta in varisai.avartas) {
            for (akshara in avarta.aksharas) {
                val start = index / total
                val end = (index + 1) / total
                when (akshara) {
                    is Akshara.Karvai -> steps.lastOrNull()?.let { steps[steps.lastIndex] = it.copy(end = end) }
                    is Akshara.Swaras -> {
                        val width = (end - start) / akshara.swaras.size
                        akshara.swaras.forEachIndexed { i, swara ->
                            steps += ContourStep(start + i * width, start + (i + 1) * width, degree(swara))
                        }
                    }
                }
                index++
            }
        }
        return steps
    }

    /** Scale steps above Sa. */
    fun degree(swara: WrittenSwara): Int = swara.letter.ordinal + SwaraLetter.entries.size * swara.octave.offset
}
