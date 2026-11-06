package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Swara

/** Display helpers shared by both apps, so labels and marks match exactly. */
object Display {
    private val SUBSCRIPTS = listOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')

    /**
     * Carnatic label: the variant as a subscript and the octave as a mark,
     * e.g. "R₁", "S′" (tara), "Ṇ"-style dot below for mandra.
     */
    fun swaraLabel(swara: Swara, octave: Int = 0): String {
        val sub = swara.symbol.drop(1).map { SUBSCRIPTS[it - '0'] }.joinToString("")
        val mark = when {
            octave < 0 -> "̣" // combining dot below
            octave > 0 -> "′"
            else -> ""
        }
        return swara.symbol.take(1) + mark + sub
    }

    /** Mark under a judged swara: ✓ in tune, ↑/↓ high/low, ⇣/⇡ octave, ✕ other swara, ○ not heard; null for none. */
    fun markGlyph(result: NoteResult?): String? = when (result) {
        null, NoteResult.TooShort -> null
        NoteResult.NoVoice -> "○"
        is NoteResult.Judged -> when {
            result.sung != null -> "✕"
            result.octaveShift < 0 -> "⇣"
            result.octaveShift > 0 -> "⇡"
            result.zone == Zone.IN_TUNE -> "✓"
            result.high -> "↑"
            else -> "↓"
        }
    }

    /** How a mark should be coloured. */
    enum class MarkTone { GOOD, NEAR, OFF, NEUTRAL }

    fun markTone(result: NoteResult?): MarkTone = when {
        result !is NoteResult.Judged -> MarkTone.NEUTRAL
        result.sung != null || result.octaveShift != 0 -> MarkTone.OFF
        result.zone == Zone.IN_TUNE -> MarkTone.GOOD
        result.zone == Zone.CLOSE -> MarkTone.NEAR
        else -> MarkTone.OFF
    }
}
