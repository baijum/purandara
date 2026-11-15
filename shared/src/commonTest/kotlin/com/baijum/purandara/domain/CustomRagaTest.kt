package com.baijum.purandara.domain

import com.baijum.purandara.domain.Swara.D1
import com.baijum.purandara.domain.Swara.D2
import com.baijum.purandara.domain.Swara.G1
import com.baijum.purandara.domain.Swara.G2
import com.baijum.purandara.domain.Swara.G3
import com.baijum.purandara.domain.Swara.M1
import com.baijum.purandara.domain.Swara.N2
import com.baijum.purandara.domain.Swara.N3
import com.baijum.purandara.domain.Swara.P
import com.baijum.purandara.domain.Swara.R1
import com.baijum.purandara.domain.Swara.R2
import com.baijum.purandara.domain.Swara.R3
import com.baijum.purandara.domain.varisai.VarisaiParseException
import com.baijum.purandara.domain.varisai.VarisaiParser
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.singalong.Display
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingInsights
import com.baijum.purandara.recording.RecordingLibrary
import com.baijum.purandara.recording.Scale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Your own raga" (docs/plan-recording-insights.md, step 3). */
class CustomRagaTest {
    /** Hindolam-like: S G2 M1 D1 N2 both ways. */
    private val hindolam = listOf(G2, M1, D1, N2)

    @AfterTest
    fun forgetOwnRagas() = Ragas.useOwn(CustomRagas())

    @Test
    fun aStraightRagaBecomesARaga() {
        val raga = assertNotNull(CustomRaga("own-1", "Hindolam", hindolam, hindolam).toRaga())
        assertEquals(listOf(Swara.S, G2, M1, D1, N2), raga.swaras)
        assertEquals("S G₂ M₁ D₁ N₂ S′", raga.arohanam.joinToString(" ") { Display.swaraLabel(it.swara, it.octave.offset) })
        assertEquals(listOf(Note(Swara.S, Octave.TARA), Note(N2), Note(D1), Note(M1), Note(G2), Note(Swara.S)), raga.avarohanam)
        assertEquals("own-1", raga.key)
    }

    @Test
    fun aDifferentAvarohanaIsKept() {
        // Bilahari's shape: S R2 G3 P D2 up, S′ N3 D2 P M1 G3 R2 S down.
        val raga = assertNotNull(CustomRaga("own-2", "Mine", listOf(R2, G3, P, D2), listOf(R2, G3, M1, P, D2, N3)).toRaga())
        assertEquals(6, raga.arohanam.size)
        assertEquals(8, raga.avarohanam.size)
        assertEquals(13, Scale.BOTH.expected(raga).size, "both shares the top Sa")
    }

    @Test
    fun theChecksSayWhatIsWrong() {
        assertEquals("Give the raga a name.", CustomRaga.problem(" ", hindolam, hindolam))
        assertEquals("Choose the swaras of the arohana.", CustomRaga.problem("X", emptyList(), hindolam))
        assertEquals(
            "In the arohana, R₃ and G₂ are the same note: choose one of them.",
            CustomRaga.problem("X", listOf(R3, G2, P, D1), hindolam),
        )
        assertEquals(
            "In the avarohana, G₁ is below R₃: choose a higher Ga or a lower Ri.",
            CustomRaga.problem("X", hindolam, listOf(R3, G1, P, D1)),
        )
        assertEquals(
            "Use the same Ni both ways (N₂ going up, N₃ coming down).",
            CustomRaga.problem("X", hindolam, listOf(G2, M1, D1, N3)),
        )
        assertEquals(
            "R₂ and G₁ are the same note: use one of them.",
            CustomRaga.problem("X", listOf(R2, M1, P, D1), listOf(G1, M1, P, D1)),
        )
        assertEquals("A raga needs at least five swaras, counting Sa.", CustomRaga.problem("X", listOf(R1, P), listOf(G3)))
        assertNull(CustomRaga.problem("X", hindolam, hindolam))
    }

    @Test
    fun namesMustBeNew() {
        val ragas = CustomRagas().save("own-1", "Hindolam", hindolam, hindolam)
        assertEquals("The app already has Mohanam: choose another name.", ragas.problem(null, "mohanam", hindolam, hindolam))
        assertEquals("You already have a raga called Hindolam.", ragas.problem(null, " hindolam ", hindolam, hindolam))
        assertNull(ragas.problem("own-1", "Hindolam", hindolam, hindolam), "editing keeps its own name")
        assertEquals(ragas, ragas.save("own-2", "Mohanam", hindolam, hindolam), "a raga with a problem isn't saved")
    }

    @Test
    fun saveEditDeleteAndRoundTrip() {
        var ragas = CustomRagas().save("own-2", "Zeta", hindolam, hindolam).save("own-1", "  alpha ", listOf(R1, G3, P, D1), listOf(R1, G3, P, D1))
        assertEquals(listOf("alpha", "Zeta"), ragas.ragas.map { it.name }, "trimmed, by name")
        ragas = ragas.save("own-2", "Zeta", hindolam, listOf(G2, M1, N2))
        assertEquals(listOf(G2, M1, N2), ragas["own-2"]!!.avarohana)
        assertEquals(ragas, CustomRagas.decode(ragas.encode()))
        assertEquals(listOf("own-1"), ragas.delete("own-2").ragas.map { it.key })
        assertEquals(CustomRagas(), CustomRagas.decode("{not json"))
        assertEquals(CustomRagas(), CustomRagas.decode(null))
    }

    @Test
    fun ownRagasAreFoundByKeyButNotByNotation() {
        Ragas.useOwn(CustomRagas().save("own-7", "Hindolam", hindolam, hindolam))
        assertEquals("Hindolam", Ragas.byKey("own-7")?.name)
        assertNull(Ragas.builtIn("own-7"))
        assertFailsWith<VarisaiParseException> { VarisaiParser().parse("1\nraga: own-7\nS R G M | P D | N S′ || S R G M | P D | N S′", VarisaiSet.SARALI) }
    }

    @Test
    fun aRecordingUsesAnOwnRagaAndForgetsADeletedOne() {
        val saved = CustomRagas().save("own-7", "Hindolam", hindolam, hindolam)
        Ragas.useOwn(saved)
        val lib = RecordingLibrary(listOf(Recording("r", 1, 10.0, saMidi = 48))).changeIntent("r", "own-7", Scale.AROHANA)
        val recording = lib["r"]!!
        assertEquals("Hindolam", recording.raga?.name)
        assertEquals(listOf("S", "G₂", "M₁", "D₁", "N₂", "S′"), RecordingInsights.referenceLabels(recording.raga!!, Scale.AROHANA))
        Ragas.useOwn(saved.delete("own-7"))
        assertNull(recording.raga, "deleted: default names")
        val forgotten = lib.forgetRaga("own-7")["r"]!!
        assertNull(forgotten.ragaKey)
        assertNull(forgotten.scale)
    }

    @Test
    fun theReadBack() {
        assertEquals("S′ N₂ D₁ M₁ G₂ S", CustomRaga.line(hindolam, descending = true))
        assertEquals("Sa, Ga 2, Ma 1, Dha 1, Ni 2, high Sa", CustomRaga.spokenLine(hindolam, descending = false))
        assertEquals("Pa", CustomRaga.spokenSwara(P))
        assertEquals(listOf(R1, R2, R3), CustomRaga.choices(SwaraLetter.R))
        assertTrue(CustomRaga.isOwn(CustomRaga.keyAt(123)))
    }
}
