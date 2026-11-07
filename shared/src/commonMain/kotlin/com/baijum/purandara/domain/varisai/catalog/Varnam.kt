package com.baijum.purandara.domain.varisai.catalog

/**
 * Tana varnams, with their words. Traditional compositions, in the common
 * teaching order.
 *
 * All four are in Adi tala, written 16 cells to the avarta — one cell per
 * count of the two-kalai Adi (`beat: 2` writes two cells to each kriya;
 * `kalai: 2` counts each kriya twice on the strip and in the click). The
 * sahityam sits under the swaras, one token per cell, `-` where the previous
 * syllable is held.
 *
 * Sung order and speeds (the default settled in the plan for issue #4; it is
 * data, changeable without engine work): each purvanga section (pallavi,
 * anupallavi, muktayi swaram) is written at its own speed and repeated at
 * double speed — `(pallavi @2)` — while the charanam and each chitta swaram
 * are written `@2` and sung at that speed only, each chitta swaram returning
 * to the charanam (the sources print the return as "(San)"/"(Ma)"/"(Nee)").
 *
 * 1. Ninnukori (Mohanam; Ramnad Sreenivasa Iyengar, d. 1919), added
 *    2026-11-07. Sources:
 * - Primary: Shivkumar Kalyanaraman's archive PDF
 *   (shivkumar.org/music/varnams/ninnukori.pdf), swaras and words.
 * - Cross-check: rosemuralikrishnan.com's Ninnukori page (Dr. Rose
 *   Muralikrishnan's teaching site, from her own guide book) — its opening
 *   matches cell for cell (`G , G , R , , , S S R R G G R R | S R G R S R S D
 *   | S R G P G R S R ||`); the page is personal-use-only, so the comparison
 *   was made by hand, not by a script.
 *
 * Choices and fixes, each listed:
 * - The raga is the standard Mohanam, G3 (karnatik.com's raga page). Both
 *   sources' arohana/avarohana headers print "G2" — a header sloppiness; the
 *   notation bodies carry no digits and are what count.
 * - Avarta = 16 cells: one cell per two-kalai count. The sources' printed
 *   bars (a `|` at some avarta ends and at anga boundaries, `||` at others)
 *   are layout; here every avarta closes with `||`.
 * - A swara printed with a trailing hyphen (`G-`, `P-`, `S-`) is written as
 *   the plain swara: the hyphen is one cell wide in some lines and attached
 *   in others, and only the plain reading fits every avarta's 16 cells. The
 *   mark's meaning is unclear; flagged for the teacher.
 * - A hooked comma (the source's rest mark, lost in its HTML) is written as
 *   a rest `_`; a plain comma as karvai `,`. The hooked commas in the
 *   muktayi's third avarta (twice), the charanam's second (once) and chitta
 *   swaram 3's second (once) were identified from the PDF's rendered marks;
 *   the rest-vs-karvai reading is flagged for the teacher.
 * - Chitta swaram 4's second avarta prints its holds as a mix of commas and
 *   a hyphen (`S , , , ,, - S S`); all are written karvai.
 * - Bold letters are a Word artifact, ignored.
 * - Words: as the PDF prints them ("sree", "im" for the lim of "palimpa"),
 *   lowercased.
 *
 * Expected cross-check output (2026-11-07): rosemuralikrishnan.com's pallavi
 * opening matches by letter and hold; no script (the page is
 * personal-use-only). Still with one full source: all four varnams.
 *
 * 2. Evvari Bodhana (Abhogi; Patnam Subramania Iyer, d. 1902) — encoded from
 *    shivkumar.org/music/varnams/evvaribodhana.pdf; patantara.com's rendering
 *    (CC BY-NC-SA, images only) serves as the manual second source.
 *
 * 3. Sami Ninne Kori (Shankarabharanam; Veena Kuppayyar, d. 1860) — encoded
 *    from shivkumar.org/music/varnams/samininne.pdf; carnaticpedia.com's page
 *    needs a browser check (issue #7). The PDF underlines some Ns (its anya
 *    N2, most likely); pending the teacher's confirmation they are written
 *    as the raga's default N3.
 *
 * 4. Jalajaksha (Hamsadhwani; Manambuchavadi Venkatasubbayyar, d. 1860 per
 *    karnatik.com co1002) — encoded from shivkumar.org/music/varnams/
 *    jalajakshi.pdf; the NIOS Carnatic Music Book 2 has the second notation
 *    (issue #7).
 */
internal val VARNAM_NOTATION: String = """
1
title: Ninnukori
raga: mohanam
tala: adi
beat: 2
kalai: 2
[pallavi]
G , G , R , , , S S R R G G R R ||
" nin - nu - ko - - - ri - - - - - - - ||
S R G R S R S D S R G P G R S R ||
" yu - - - nna - - - nu - - - ra - - - ||
G P G G R S R G R R S D S R G R ||
" ni - - - - - ki - - - - - la - - - ||
G P G P D P D S D D P G D P G R ||
" lo - - ka - - na - - - - ya - - ka - ||
(pallavi @2)
[anupallavi]
G , G , P , , , G G P P D D P , ||
" nan - nu - pa - - - li - - - - im pa - ||
D S D D P G D P D G D P G R S R ||
" sa - - - - - ma - - ya - - mu - ra - ||
G G P P D P D S D S R S G R , S ||
" na - - - - - mee - - tha - - kru - - pa ||
D S R S , D P D S D , P G R S R ||
" - - ju - - - - - da - - - ra - - - ||
(anupallavi @2)
[muktayi swaram]
G , R G R S R , S R S G R G S R ||
S D S R G R G P G P D , P , , , ||
G P D P D S R G R _ G S _ R D , ||
S P , D S R G S , D P , G R S R ||
(muktayi swaram @2)
[charanam @2 +swaram]
G , G , G P G R R G P G P , , , ||
" san - nu - tha - - - nga - - - sree - - - ||
G G D D P _ G G D P G P G R S R ||
" ni - - - - - va - - - sa - - - - - ||
[chitta swaram 1 @2]
G , , , R , S , R , , , D , , , ||
R , D , S , , , R , , , G , R , ||
(charanam)
[chitta swaram 2 @2]
G , G R G R S R G , G P D D S R ||
G , G G D P G R G , G S D P G R ||
(charanam)
[chitta swaram 3 @2]
P , D D P , G R S , R G R , S D ||
S , R G P , D , S S _ D P , G R ||
(charanam)
[chitta swaram 4 @2]
S , R G R S R S D S D P G R S R ||
S , , , , , S S R R G G P P D D ||
S R G R G R D S R S R S P D S D ||
S D G P D P D P R S D P G R S R ||
(charanam)
""".trimIndent()
