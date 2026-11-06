package com.baijum.purandara.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ShrutiTest {
    @Test
    fun namesAndKattai() {
        assertEquals("C3", Shruti.noteName(48))
        assertEquals("C♯3", Shruti.noteName(49))
        assertEquals("A3", Shruti.noteName(57))
        assertEquals("G2", Shruti.noteName(43))
        assertEquals("C4", Shruti.noteName(60))
        assertEquals("1", Shruti.kattai(48))
        assertEquals("1½", Shruti.kattai(49))
        assertEquals("4", Shruti.kattai(53))
        assertEquals("5", Shruti.kattai(55))
        assertEquals("6½", Shruti.kattai(58))
        assertEquals("7", Shruti.kattai(59))
    }

    @Test
    fun rangeAndDefault() {
        assertEquals(43..60, Shruti.SA_RANGE)
        assertEquals(48, Shruti.DEFAULT_SA_MIDI)
        assertEquals(130.8128, Shruti.saHz(48), 1e-4)
    }
}
