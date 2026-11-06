package com.baijum.purandara.ui.player

import com.baijum.purandara.domain.Tala
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNotationTest {
    @Test
    fun angasAreSeparatedAndKarvaiIsSpoken() {
        val sarali4 = VarisaiCatalog.byId("sarali-4")!!
        assertEquals("S R G M, P hold, S R", spoken(sarali4.avartas[0], Tala.ADI, "hold"))
        assertEquals("S R G M, P D, N S′", spoken(sarali4.avartas[1], Tala.ADI, "hold"))
    }

    @Test
    fun pairsStayTogether() {
        val janta1 = VarisaiCatalog.byId("janta-1")!!
        assertEquals("SS RR GG MM, PP DD, NN S′S′", spoken(janta1.avartas[0], Tala.ADI, "hold"))
    }
}
