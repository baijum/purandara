package com.baijum.purandara.domain.varisai

import com.baijum.purandara.domain.Octave
import com.baijum.purandara.domain.SwaraLetter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VarisaiParserTest {
    private val parser = VarisaiParser()

    private fun parseOne(text: String): Varisai = parser.parse(text.trimIndent(), VarisaiSet.SARALI).single()

    @Test
    fun parsesSingleSwaraAksharasAcrossAvartas() {
        val v = parseOne(
            """
            4
            S R G M | P , | S R || S R G M | P D | N S′
            """,
        )
        assertEquals(4, v.number)
        assertEquals("sarali-4", v.id)
        assertEquals("Sarali Varisai 4", v.title)
        assertEquals(2, v.avartas.size)
        assertEquals("S R G M P , S R", v.avartas[0].toString())
        assertEquals("S R G M P D N S′", v.avartas[1].toString())
        assertEquals(Akshara.Karvai, v.avartas[0].aksharas[5])
    }

    @Test
    fun taraMarkAppliesToThePrecedingSwara() {
        val v = parseOne(
            """
            1
            S′S′ NN DD PP | MM GG | RR SS
            """,
        )
        val first = assertIs<Akshara.Swaras>(v.avartas[0].aksharas[0])
        assertEquals(listOf(WrittenSwara(SwaraLetter.S, Octave.TARA), WrittenSwara(SwaraLetter.S, Octave.TARA)), first.swaras)
        val last = assertIs<Akshara.Swaras>(v.avartas[0].aksharas[7])
        assertEquals(listOf(WrittenSwara(SwaraLetter.S), WrittenSwara(SwaraLetter.S)), last.swaras)
    }

    @Test
    fun multiSwaraTokenIsOneAkshara() {
        val v = parseOne(
            """
            1
            SRGM PDNS′ S′NDP MGRS SRGM PDNS′ S′NDP MGRS
            """,
        )
        assertEquals(8, v.aksharaCount)
        assertEquals(4, assertIs<Akshara.Swaras>(v.avartas[0].aksharas[1]).swaras.size)
    }

    @Test
    fun parsesSeveralExercisesAndIgnoresBlankLines() {
        val all = parser.parse(
            "1\nS R G M P D N S′\n\n\n2\nS′ N D P M G R S\nS R G M P D N S′\n",
            VarisaiSet.DHATTU,
        )
        assertEquals(listOf(1, 2), all.map { it.number })
        assertEquals(listOf(1, 2), all.map { it.avartas.size })
        assertTrue(all.all { it.set == VarisaiSet.DHATTU })
    }

    @Test
    fun barsAreLayoutOnly() {
        val a = parseOne("1\nS R G M | P D | N S′")
        val b = parseOne("1\nS R G M P D N S′")
        val c = parseOne("1\nS R G M|P D|N S′")
        assertEquals(a.avartas, b.avartas)
        assertEquals(a.avartas, c.avartas)
    }

    @Test
    fun wrongAksharaCountReportsLine() {
        val e = assertFailsWith<VarisaiParseException> {
            parser.parse("1\nS R G M | P D | N S′\nSS , RR , GG | SS RR | GG MM", VarisaiSet.JANTA)
        }
        assertEquals(3, e.line)
        assertTrue("9 aksharas" in e.message!!, e.message)
    }

    @Test
    fun unknownCharacterReportsLineAndColumn() {
        val e = assertFailsWith<VarisaiParseException> { parser.parse("1\nS R G X P D N S′", VarisaiSet.SARALI) }
        assertEquals(2, e.line)
        assertEquals(7, e.column)
        assertTrue("'X'" in e.message!!, e.message)
    }

    @Test
    fun rejectsLeadingKarvai() {
        assertFailsWith<VarisaiParseException> { parser.parse("1\n, R G M P D N S′", VarisaiSet.SARALI) }
    }

    @Test
    fun karvaiMayOpenALaterLine() {
        // Janta 7 style: a later line may start an avarta after a held note... but
        // never the first akshara of an exercise. Within a line a karvai after a swara is fine.
        val v = parseOne("1\nS , S R , R G G || S S R R G G M M")
        assertEquals(Akshara.Karvai, v.avartas[0].aksharas[1])
    }

    @Test
    fun rejectsNotationBeforeANumber() {
        val e = assertFailsWith<VarisaiParseException> { parser.parse("S R G M P D N S′", VarisaiSet.SARALI) }
        assertEquals(1, e.line)
    }

    @Test
    fun rejectsExerciseWithoutNotation() {
        assertFailsWith<VarisaiParseException> { parser.parse("1\n2\nS R G M P D N S′", VarisaiSet.SARALI) }
    }

    @Test
    fun rejectsStrayOrRepeatedOctaveMarks() {
        assertFailsWith<VarisaiParseException> { parser.parse("1\n′S R G M P D N S", VarisaiSet.SARALI) }
        assertFailsWith<VarisaiParseException> { parser.parse("1\nS′′ R G M P D N S", VarisaiSet.SARALI) }
    }

    @Test
    fun rejectsAsciiApostropheInsteadOfPrime() {
        assertFailsWith<VarisaiParseException> { parser.parse("1\nS R G M P D N S'", VarisaiSet.SARALI) }
    }

    @Test
    fun headersSetTalaRagaAndTitle() {
        val v = parseOne(
            """
            2
            tala: rupaka-4
            raga: malahari
            title: Sri Gananatha
            Mˌ Pˌ | Dˌ S R M ||
            """,
        )
        assertEquals("rupaka-4", v.tala.key)
        assertEquals("Malahari", v.raga.name)
        assertEquals("Sri Gananatha", v.title)
        val first = (v.avartas[0].aksharas[0] as Akshara.Swaras).swaras.single()
        assertEquals(WrittenSwara(SwaraLetter.M, Octave.MANDRA), first)
        assertEquals("M\u0323", first.toString())
        assertEquals("low M low P, low D S R M", v.avartas[0].spoken(v.tala, "hold"))
    }

    @Test
    fun avartaLengthFollowsTheExerciseTala() {
        val e = assertFailsWith<VarisaiParseException> {
            parseOne(
                """
                1
                tala: dhruva-4
                S R G M | G R | S R G R | S R G ||
                """,
            )
        }
        assertTrue("13 aksharas; Dhruva tala needs 14" in e.message!!, e.message)
        val ok = parseOne("1\ntala: eka-4\nS R G M || R G M P")
        assertEquals(2, ok.avartas.size)
    }

    @Test
    fun unknownHeadersValuesAndLettersOutsideTheRagaAreErrors() {
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: dhruva\nS R G M") }
        assertFailsWith<VarisaiParseException> { parseOne("1\nraga: kalyani\nS R G M | P D | N S′ || S R G M | P D | N S′") }
        val e = assertFailsWith<VarisaiParseException> {
            parseOne("1\nraga: malahari\nS R G M | P D | N S′ || S R G M | P D | D S′")
        }
        assertEquals(3, e.line)
        assertTrue("Malahari has no N" in e.message!!, e.message)
        assertFailsWith<VarisaiParseException> { parseOne("1\nS R G M | P D | N S′ ||\ntala: eka-4") }
        assertFailsWith<VarisaiParseException> { parser.parse("tala: eka-4\n1\nS R G M", VarisaiSet.SARALI) }
    }

    @Test
    fun mandraMarkMustFollowASwaraOnce() {
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nˌS R G M") }
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nSˌˌ R G M") }
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nS′ˌ R G M") }
    }

    @Test
    fun sectionsRepeatsAndWords() {
        val v = parseOne(
            """
            1
            tala: eka-4
            [charanam]
            S R G M || R G M P
            " sa ri - ga || ri·ga ma pa ma
            [pallavi]
            M , G R ||
            " lam - bo da ||
            (pallavi)
            """,
        )
        assertEquals(listOf("Charanam", null, "Pallavi", "Pallavi"), v.avartas.map { it.section })
        assertEquals(listOf("sa", "ri", "-", "ga"), v.avartas[0].words)
        assertEquals("ri ga ma pa ma", v.avartas[1].spokenWords())
        assertEquals(v.avartas[2].copy(section = null), v.avartas[3].copy(section = null))
        assertEquals("sa ri ga", v.avartas[0].spokenWords())
    }

    @Test
    fun wordsMustMatchTheLineAboveAndSectionsMustExist() {
        val tooFew = assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nS R G M\n\" sa ri ga") }
        assertTrue("3 words for 4 aksharas" in tooFew.message!!, tooFew.message)
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\n[pallavi]\n\" sa ri ga ma") }
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nS R G M\n\" a b c d\n\" a b c d") }
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nS R G M\n(pallavi)") }
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\n[p]\nS R G M\n[p]\nS R G M") }
    }

    @Test
    fun beatWritesSeveralAksharasToATalaBeatAndSwaramComesFirst() {
        val v = parseOne(
            """
            1
            tala: eka-4
            beat: 2
            [charanam 1 +swaram]
            S , R G | M , P S′ ||
            " sa - ri ga | ma - pa sa ||
            """,
        )
        assertEquals(2, v.beat)
        assertEquals(listOf("Charanam 1 · swaram", "Charanam 1 · sahityam"), v.avartas.map { it.section })
        assertEquals(null, v.avartas[0].words)
        assertEquals(v.avartas[0].aksharas, v.avartas[1].aksharas)
        // Holds inside a beat aren't read out; angas still are.
        assertEquals("S R G M P S′", v.avartas[0].spoken(v.tala, "hold"))
        val e = assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nbeat: 2\nS R G M") }
        assertTrue("needs 8" in e.message!!, e.message)
        assertFailsWith<VarisaiParseException> { parseOne("1\ntala: eka-4\nbeat: 3\nS R G M") }
    }
}
