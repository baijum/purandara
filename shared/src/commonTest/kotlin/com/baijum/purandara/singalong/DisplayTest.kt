package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Swara
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayTest {
    @Test
    fun labels() {
        assertEquals("S", Display.swaraLabel(Swara.S))
        assertEquals("R₁", Display.swaraLabel(Swara.R1))
        assertEquals("S′", Display.swaraLabel(Swara.S, 1))
        assertEquals("Ṇ₃", Display.swaraLabel(Swara.N3, -1))
    }

    @Test
    fun marks() {
        assertNull(Display.markGlyph(null))
        assertNull(Display.markGlyph(NoteResult.TooShort))
        assertEquals("○", Display.markGlyph(NoteResult.NoVoice))
        assertEquals("✓", Display.markGlyph(NoteResult.Judged(5.0, 0, null)))
        assertEquals("↑", Display.markGlyph(NoteResult.Judged(20.0, 0, null)))
        assertEquals("↓", Display.markGlyph(NoteResult.Judged(-40.0, 0, null)))
        assertEquals("⇣", Display.markGlyph(NoteResult.Judged(0.0, -1, null)))
        assertEquals(Display.MarkTone.NEAR, Display.markTone(NoteResult.Judged(20.0, 0, null)))
        assertEquals(Display.MarkTone.OFF, Display.markTone(NoteResult.Judged(0.0, -1, null)))
        assertEquals(Display.MarkTone.NEUTRAL, Display.markTone(NoteResult.NoVoice))
    }
}
