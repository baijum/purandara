package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.CustomRaga
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Melakartas
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.practice.RunPlans
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The varisais in the student's own raga, and the 72 melakartas (docs/plan-own-raga-varisais.md). */
class OwnRagaVarisaiTest {
    @AfterTest
    fun noOwnRagas() = Ragas.useOwn(CustomRagas())

    private val chakravakam = CustomRaga(
        "own-1", "My Chakravakam",
        listOf(Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N2),
        listOf(Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N2),
    )
    private val mohanam = CustomRaga(
        "own-2", "My Mohanam",
        listOf(Swara.R2, Swara.G3, Swara.P, Swara.D2),
        listOf(Swara.R2, Swara.G3, Swara.P, Swara.D2),
    )

    @Test
    fun everyMelakartaNumberRoundTripsThroughItsSwaras() {
        assertEquals(72, Melakartas.NAMES.size)
        assertEquals(72, Melakartas.NAMES.distinct().size)
        for (n in 1..72) {
            val s = Melakartas.swarasOf(n)
            assertEquals(n, Melakartas.numberOf(s[1], s[2], s[3], s[5], s[6]), "melakarta $n")
            assertEquals(s.map { it.semitone }, s.map { it.semitone }.sorted().distinct(), "melakarta $n rises with no repeated note")
        }
        assertEquals("Kanakangi", Melakartas.nameOf(1))
        assertEquals("Chakravakam", Melakartas.nameOf(16))
        assertEquals("Mechakalyani", Melakartas.nameOf(65))
        assertEquals("Rasikapriya", Melakartas.nameOf(72))
        assertEquals(listOf(Swara.S, Swara.R1, Swara.G1, Swara.M1, Swara.P, Swara.D1, Swara.N1), Melakartas.swarasOf(1))
        assertEquals(listOf(Swara.S, Swara.R3, Swara.G3, Swara.M2, Swara.P, Swara.D3, Swara.N3), Melakartas.swarasOf(72))
        assertNull(Melakartas.numberOf(Swara.R2, Swara.G1, Swara.M1, Swara.D1, Swara.N3), "R2 and G1 are the same note")
        assertNull(Melakartas.numberOf(Swara.R3, Swara.G2, Swara.M1, Swara.D1, Swara.N3), "G2 below R3")
    }

    @Test
    fun theAppsRagasAreTheirMelakartas() {
        Ragas.melakartas.forEach { raga ->
            assertEquals(raga.mela, Melakartas.numberOf(raga), raga.name)
            assertSame(raga, Melakartas.builtIn(raga.mela!!))
        }
        assertNull(Melakartas.builtIn(16))
        assertNull(Melakartas.numberOf(Raga.MOHANAM), "five swaras")
        assertFalse(Raga.BILAHARI.isSampurna, "Bilahari's arohanam leaves out Ma and Ni")
        assertTrue(Raga.KALYANI.isSampurna)
    }

    @Test
    fun theEditorNamesTheMelakarta() {
        assertEquals(16, chakravakam.melakarta)
        assertTrue(chakravakam.isSampurna)
        assertNull(mohanam.melakarta)
        assertFalse(mohanam.isSampurna)
        assertEquals(65, CustomRaga.melakartaOf(Melakartas.swarasOf(65).drop(1), Melakartas.swarasOf(65).drop(1)))
        val upOnly = listOf(Swara.R2, Swara.G3, Swara.P, Swara.D2)
        assertNull(CustomRaga.melakartaOf(upOnly, Melakartas.swarasOf(29).drop(1)), "Bilahari-like: not all seven going up")
    }

    @Test
    fun aSampurnaOwnRagaCanBeSungAndAFiveSwaraOneCannot() {
        Ragas.useOwn(CustomRagas(listOf(chakravakam, mohanam)))
        val own = assertNotNull(Ragas.forVarisais("own-1"))
        assertNull(Ragas.forVarisais("own-2"), "five swaras: not for the varisais")
        assertNull(Ragas.forVarisais("own-9"))
        assertSame(Raga.KALYANI, Ragas.forVarisais("kalyani"))

        val catalog = VarisaiCatalog.inRaga(own)
        val sarali = catalog.first { it.set == VarisaiSet.SARALI && it.number == 3 }
        assertEquals("sarali-3@own-1", sarali.id)
        val sounded = sarali.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }.map { it.swaraIn(sarali.raga) }.toSet()
        assertEquals(setOf(Swara.S, Swara.R1, Swara.G3, Swara.M1, Swara.P, Swara.D2, Swara.N2), sounded)
        assertEquals(sarali, VarisaiCatalog.byId("sarali-3@own-1"))
        assertNull(VarisaiCatalog.byId("sarali-3@own-2"))
        assertSame(catalog, VarisaiCatalog.inRaga(own), "cached while the raga is unchanged")
        assertEquals(VarisaiCatalog.all.map { it.raga }, VarisaiCatalog.inRaga(Ragas.own.first { it.key == "own-2" }).map { it.raga })

        val history = PracticeHistory().practised("sarali-3@own-1", 1)
        assertEquals("sarali-3@own-1", history.continueWith(catalog)?.id)
        assertEquals(1, history.practisedInOtherRagas(VarisaiSet.SARALI, Raga.KALYANI).single().practised)
        assertEquals("SARALI@own-1", RunPlans().planFor(VarisaiSet.SARALI, catalog).set)
    }

    @Test
    fun anEditedOwnRagaGetsANewCatalogAndADeletedOneIsGone() {
        Ragas.useOwn(CustomRagas(listOf(chakravakam)))
        val before = VarisaiCatalog.inRaga(Ragas.forVarisais("own-1")!!)
        val edited = chakravakam.copy(
            arohana = listOf(Swara.R1, Swara.G3, Swara.M2, Swara.P, Swara.D2, Swara.N2),
            avarohana = listOf(Swara.R1, Swara.G3, Swara.M2, Swara.P, Swara.D2, Swara.N2),
        )
        Ragas.useOwn(CustomRagas(listOf(edited)))
        val raga = Ragas.forVarisais("own-1")!!
        assertEquals(Swara.M2, raga.swara(SwaraLetter.M))
        val after = VarisaiCatalog.inRaga(raga)
        assertTrue(after !== before)
        assertSame(raga, after.first { it.set == VarisaiSet.SARALI }.raga)
        Ragas.useOwn(CustomRagas())
        assertNull(Ragas.forVarisais("own-1"))
        assertNull(VarisaiCatalog.byId("sarali-3@own-1"))
    }
}
