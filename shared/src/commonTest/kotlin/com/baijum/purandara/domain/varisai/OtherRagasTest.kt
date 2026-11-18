package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.SwaraLetter
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.practice.RunPlans
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The varisais and alankarams in other melakartas (docs/plan-other-ragas.md). */
class OtherRagasTest {
    /**
     * The melakarta number worked out from the swaras: M2 adds 36, then six
     * per R/G pair and one per D/N pair, both in the standard order. An
     * independent check on each raga's swaras (a typo gives another number).
     */
    private fun melaOf(raga: Raga): Int {
        val rg = listOf(
            Swara.R1 to Swara.G1, Swara.R1 to Swara.G2, Swara.R1 to Swara.G3,
            Swara.R2 to Swara.G2, Swara.R2 to Swara.G3, Swara.R3 to Swara.G3,
        ).indexOf(raga.swara(SwaraLetter.R) to raga.swara(SwaraLetter.G))
        val dn = listOf(
            Swara.D1 to Swara.N1, Swara.D1 to Swara.N2, Swara.D1 to Swara.N3,
            Swara.D2 to Swara.N2, Swara.D2 to Swara.N3, Swara.D3 to Swara.N3,
        ).indexOf(raga.swara(SwaraLetter.D) to raga.swara(SwaraLetter.N))
        assertTrue(rg >= 0 && dn >= 0, "${raga.name}: not a melakarta pair")
        val m = if (raga.swara(SwaraLetter.M) == Swara.M2) 36 else 0
        return m + rg * 6 + dn + 1
    }

    @Test
    fun theShortListAndTheirNumbers() {
        assertEquals(
            listOf(
                "Mayamalavagowla" to 15, "Todi" to 8, "Natabhairavi" to 20, "Keeravani" to 21,
                "Kharaharapriya" to 22, "Charukesi" to 26, "Harikambhoji" to 28, "Shankarabharanam" to 29,
                "Pantuvarali" to 51, "Shanmukhapriya" to 56, "Simhendramadhyamam" to 57, "Hemavati" to 58,
                "Latangi" to 63, "Vachaspati" to 64, "Kalyani" to 65,
            ),
            Ragas.melakartas.map { it.name to it.mela },
        )
        Ragas.melakartas.forEach { raga ->
            assertEquals(raga.mela, melaOf(raga), raga.name)
            assertEquals(raga.swaras + Swara.S, raga.arohanam.map { it.swara }, "${raga.name} ascends through its swaras")
            assertEquals(raga.arohanam.reversed(), raga.avarohanam, "${raga.name} is the same both ways")
            assertSame(raga, Ragas.builtIn(raga.key), "${raga.name} is one of the app's ragas")
        }
        assertSame(Raga.MAYAMALAVAGOWLA, VarisaiCatalog.ragas.first())
    }

    @Test
    fun everyVarisaiAndAlankaramWorksInEveryRaga() {
        val following = VarisaiCatalog.all.filter { it.set.followsChosenRaga }
        assertTrue(following.isNotEmpty())
        following.forEach { varisai ->
            val letters = varisai.avartas.flatMap { it.aksharas }.filterIsInstance<Akshara.Swaras>().flatMap { it.swaras }
            assertTrue(letters.all { it.variant == null }, "${varisai.id} writes no variant digits, so the raga decides each swara")
        }
        Ragas.melakartas.forEach { raga ->
            VarisaiCatalog.inRaga(raga).filter { it.set.followsChosenRaga }.forEach { varisai ->
                assertSame(raga, varisai.raga)
                assertTrue(!varisai.playableSaMidis.isEmpty(), "${varisai.id} fits some Sa")
                assertTrue(Shruti.SA_RANGE.first in varisai.playableSaMidis, "${varisai.id} fits the lowest Sa")
            }
        }
    }

    @Test
    fun gitamsSwarajatisAndVarnamsKeepTheirRagas() {
        val kalyani = VarisaiCatalog.inRaga(Raga.KALYANI)
        VarisaiCatalog.all.filter { !it.set.followsChosenRaga }.forEach { piece ->
            val same = kalyani.first { it.id == piece.id }
            assertSame(piece.raga, same.raga, piece.id)
        }
    }

    @Test
    fun idsCarryTheRagaExceptMayamalavagowla() {
        val sarali = VarisaiCatalog.inSet(VarisaiSet.SARALI).first { it.number == 3 }
        assertEquals("sarali-3", sarali.id)
        assertEquals("sarali-3", sarali.inRaga(Raga.MAYAMALAVAGOWLA).id)
        val inKalyani = sarali.inRaga(Raga.KALYANI)
        assertEquals("sarali-3@kalyani", inKalyani.id)
        assertEquals(inKalyani, VarisaiCatalog.byId("sarali-3@kalyani"))
        assertEquals(sarali, VarisaiCatalog.byId("sarali-3"))
        assertNull(VarisaiCatalog.byId("sarali-3@malahari"), "only the short list")
        assertNull(VarisaiCatalog.byId("sarali-3@nowhere"))
        assertEquals("gitam-1", VarisaiCatalog.byId("gitam-1")?.id)
        assertNull(VarisaiCatalog.byId("gitam-1@kalyani"), "gitams keep their own raga")
        assertEquals(VarisaiCatalog.inSet(VarisaiSet.SARALI).size, VarisaiCatalog.inSet(VarisaiSet.SARALI, Raga.KEERAVANI).size)
    }

    @Test
    fun historyIsKeptPerRaga() {
        val kalyani = VarisaiCatalog.inRaga(Raga.KALYANI)
        val history = PracticeHistory()
            .practised("sarali-2", nowMillis = 1)
            .practised("sarali-5@kalyani", nowMillis = 2)
        assertEquals("sarali-2", history.continueWith(VarisaiCatalog.all)?.id, "the newer Kalyani practice is passed over")
        assertEquals("sarali-5@kalyani", history.continueWith(kalyani)?.id)
        assertEquals("sarali-6@kalyani", history.nextUp(kalyani)?.id)
        assertEquals(1, history.practisedCount(VarisaiSet.SARALI, kalyani))
        assertEquals(1, history.practisedCount(VarisaiSet.SARALI, VarisaiCatalog.all))
        assertNull(history.continueWith(VarisaiCatalog.inRaga(Raga.KEERAVANI)))
    }

    @Test
    fun aSetPageCountsThePracticeInOtherRagas() {
        val history = PracticeHistory()
            .practised("sarali-1", nowMillis = 1)
            .practised("sarali-2", nowMillis = 2)
            .practised("sarali-3@kalyani", nowMillis = 3)
            .practised("gitam-1", nowMillis = 4)
        assertEquals(
            listOf(com.baijum.purandara.practice.RagaProgress(Raga.KALYANI, 1, 16)),
            history.practisedInOtherRagas(VarisaiSet.SARALI, Raga.MAYAMALAVAGOWLA),
        )
        assertEquals(
            listOf(com.baijum.purandara.practice.RagaProgress(Raga.MAYAMALAVAGOWLA, 2, 16)),
            history.practisedInOtherRagas(VarisaiSet.SARALI, Raga.KALYANI),
        )
        assertEquals(
            listOf(Raga.MAYAMALAVAGOWLA, Raga.KALYANI),
            history.practisedInOtherRagas(VarisaiSet.SARALI, Raga.KEERAVANI).map { it.raga },
        )
        assertTrue(history.practisedInOtherRagas(VarisaiSet.JANTA, Raga.KALYANI).isEmpty())
        assertTrue(history.practisedInOtherRagas(VarisaiSet.GITAM, Raga.KALYANI).isEmpty(), "gitams keep their own raga")
    }

    @Test
    fun aRagaPreviewIsItsPlainScaleUpAndDownOnce() {
        val schedule = com.baijum.purandara.audio.RagaPreview.schedule(Raga.KHARAHARAPRIYA, saHz = 130.81, tuning = com.baijum.purandara.domain.Tuning.JUST)
        assertEquals(
            listOf(Swara.S, Swara.R2, Swara.G2, Swara.M1, Swara.P, Swara.D2, Swara.N2, Swara.S) +
                listOf(Swara.S, Swara.N2, Swara.D2, Swara.P, Swara.M1, Swara.G2, Swara.R2, Swara.S),
            schedule.notes.map { it.note.swara },
        )
        val seconds = schedule.notes.last().let { it.startSeconds + it.durationSeconds }
        assertTrue(seconds in 10.0..11.0, "about 11 seconds, was $seconds")
        val options = com.baijum.purandara.audio.RagaPreview.options(harmoniumLevel = 0.75f, droneLevel = 0.5f)
        assertTrue(options.drone && !options.click && !options.loop && !options.countIn)
    }

    @Test
    fun swaraNumbersShowTheRagasSwaraWhereWanted() {
        val sarali = VarisaiCatalog.byId("sarali-1")!!
        val inKharaharapriya = sarali.inRaga(Raga.KHARAHARAPRIYA)
        val gitam = VarisaiCatalog.inSet(VarisaiSet.GITAM).first()
        assertNull(SwaraNumbers.OTHER_RAGAS.ragaFor(sarali), "Mayamalavagowla reads as written")
        assertSame(Raga.KHARAHARAPRIYA, SwaraNumbers.OTHER_RAGAS.ragaFor(inKharaharapriya))
        assertNull(SwaraNumbers.OTHER_RAGAS.ragaFor(gitam), "a gitam's own raga is not an 'other' raga")
        assertSame(gitam.raga, SwaraNumbers.ALWAYS.ragaFor(gitam))
        assertSame(Raga.MAYAMALAVAGOWLA, SwaraNumbers.ALWAYS.ragaFor(sarali))
        assertNull(SwaraNumbers.OFF.ragaFor(inKharaharapriya))

        val g = WrittenSwara(SwaraLetter.G)
        assertEquals("2", g.digitIn(Raga.KHARAHARAPRIYA))
        assertEquals("3", g.digitIn(Raga.MAYAMALAVAGOWLA))
        assertNull(g.digitIn(null))
        assertNull(WrittenSwara(SwaraLetter.S).digitIn(Raga.KALYANI), "S has no number")
        assertNull(WrittenSwara(SwaraLetter.P).digitIn(Raga.KALYANI), "P has no number")
        assertEquals("1", WrittenSwara(SwaraLetter.D, variant = Swara.D1).digitIn(null), "a written variant always shows")
        assertEquals("1", WrittenSwara(SwaraLetter.D, variant = Swara.D1).digitIn(Raga.KALYANI), "the written variant wins")
        assertEquals("low N2", WrittenSwara(SwaraLetter.N, Octave.MANDRA).spoken(Raga.KHARAHARAPRIYA))
        assertEquals("S′", WrittenSwara(SwaraLetter.S, Octave.TARA).spoken(Raga.KHARAHARAPRIYA))

        val layout = NotationLayout.of(inKharaharapriya, 1)
        assertEquals(layout.spoken(0, "hold"), layout.spoken(0, "hold", null))
        assertEquals("S R2 S R2, S R2, G2 M1", layout.spoken(0, "hold", Raga.KHARAHARAPRIYA))
        assertEquals("S R S R, S R, G M", layout.spoken(0, "hold"))
    }

    @Test
    fun runPlansArePerRaga() {
        val kalyani = VarisaiCatalog.inRaga(Raga.KALYANI)
        val plain = RunPlans().planFor(VarisaiSet.SARALI, VarisaiCatalog.all)
        assertEquals("SARALI", plain.set)
        val inKalyani = RunPlans().planFor(VarisaiSet.SARALI, kalyani)
        assertEquals("SARALI@kalyani", inKalyani.set)
        assertTrue(inKalyani.entries.all { it.varisaiId.endsWith("@kalyani") })
        val saved = RunPlans().with(inKalyani.copy(nextIndex = 4))
        assertEquals(4, saved.planFor(VarisaiSet.SARALI, kalyani).nextIndex)
        assertEquals(0, saved.planFor(VarisaiSet.SARALI, VarisaiCatalog.all).nextIndex)
        assertNotNull(saved.get(VarisaiSet.SARALI, Raga.KALYANI))
        assertNull(saved[VarisaiSet.SARALI])
        assertEquals("GITAM", RunPlans.keyFor(VarisaiSet.GITAM, Raga.KALYANI))
    }
}
