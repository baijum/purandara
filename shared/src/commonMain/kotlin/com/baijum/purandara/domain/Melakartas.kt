package com.baijum.purandara.domain

/**
 * The 72 melakartas: every scale with all seven swaras, one form of each in
 * rising order, no two the same note (docs/plan-own-raga-varisais.md). The
 * number follows from the swaras (Govindacharya's order): Ma decides the
 * half (M₂ adds 36), then six per Ri–Ga pair and one per Dha–Ni pair.
 *
 * Names from karnatik.com's melakarta table, checked against Wikipedia's
 * Melakarta table (after Sambamoorthy), written in the app's simple Latin
 * (docs/resources.md). These are the names of the melakarta list; the
 * app's own ragas keep their concert names (Todi for 8, Pantuvarali for 51).
 */
object Melakartas {
    const val COUNT: Int = 72

    private val RG = listOf(
        Swara.R1 to Swara.G1, Swara.R1 to Swara.G2, Swara.R1 to Swara.G3,
        Swara.R2 to Swara.G2, Swara.R2 to Swara.G3, Swara.R3 to Swara.G3,
    )
    private val DN = listOf(
        Swara.D1 to Swara.N1, Swara.D1 to Swara.N2, Swara.D1 to Swara.N3,
        Swara.D2 to Swara.N2, Swara.D2 to Swara.N3, Swara.D3 to Swara.N3,
    )
    private const val HALF = 36
    private const val PER_RG = 6

    val NAMES: List<String> = listOf(
        "Kanakangi", "Ratnangi", "Ganamurti", "Vanaspati", "Manavati", "Tanarupi",
        "Senavati", "Hanumatodi", "Dhenuka", "Natakapriya", "Kokilapriya", "Rupavati",
        "Gayakapriya", "Vakulabharanam", "Mayamalavagowla", "Chakravakam", "Suryakantam", "Hatakambari",
        "Jhankaradhwani", "Natabhairavi", "Keeravani", "Kharaharapriya", "Gowrimanohari", "Varunapriya",
        "Mararanjani", "Charukesi", "Sarasangi", "Harikambhoji", "Dheerashankarabharanam", "Naganandini",
        "Yagapriya", "Ragavardhini", "Gangeyabhushani", "Vagadheeswari", "Shulini", "Chalanata",
        "Salagam", "Jalarnavam", "Jhalavarali", "Navaneetam", "Pavani", "Raghupriya",
        "Gavambhodi", "Bhavapriya", "Shubhapantuvarali", "Shadvidamargini", "Suvarnangi", "Divyamani",
        "Dhavalambari", "Namanarayani", "Kamavardhini", "Ramapriya", "Gamanashrama", "Vishwambari",
        "Shyamalangi", "Shanmukhapriya", "Simhendramadhyamam", "Hemavati", "Dharmavati", "Neetimati",
        "Kantamani", "Rishabhapriya", "Latangi", "Vachaspati", "Mechakalyani", "Chitrambari",
        "Sucharitra", "Jyotiswarupini", "Dhatuvardhani", "Nasikabhushani", "Kosalam", "Rasikapriya",
    )

    /** Melakarta [number]'s name in the melakarta list ("Chakravakam" for 16). */
    fun nameOf(number: Int): String = NAMES[number - 1]

    /**
     * The melakarta number of a scale with [r], [g], [m], [d] and [n] (Sa and
     * Pa understood), or null when it isn't one (two forms that are the same
     * note, or out of order).
     */
    fun numberOf(r: Swara, g: Swara, m: Swara, d: Swara, n: Swara): Int? {
        val rg = RG.indexOf(r to g)
        val dn = DN.indexOf(d to n)
        val half = when (m) {
            Swara.M1 -> 0
            Swara.M2 -> HALF
            else -> return null
        }
        if (rg < 0 || dn < 0) return null
        return half + rg * PER_RG + dn + 1
    }

    /** The melakarta number of [raga], or null unless it has all seven swaras in melakarta form. */
    fun numberOf(raga: Raga): Int? {
        if (raga.letters.size != SwaraLetter.entries.size) return null
        return numberOf(
            raga.swara(SwaraLetter.R), raga.swara(SwaraLetter.G), raga.swara(SwaraLetter.M),
            raga.swara(SwaraLetter.D), raga.swara(SwaraLetter.N),
        )
    }

    /** Melakarta [number]'s swaras, Sa to Ni. */
    fun swarasOf(number: Int): List<Swara> {
        require(number in 1..COUNT) { "melakarta $number outside 1..$COUNT" }
        val i = number - 1
        val (r, g) = RG[(i % HALF) / PER_RG]
        val (d, n) = DN[i % PER_RG]
        return listOf(Swara.S, r, g, if (i < HALF) Swara.M1 else Swara.M2, Swara.P, d, n)
    }

    /** The app's own raga for melakarta [number] (Kalyani for 65), if it has one. */
    fun builtIn(number: Int): Raga? = Ragas.melakartas.firstOrNull { it.mela == number }
}
