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
 * Expected cross-check output (2026-11-07, tools/notation/crosscheck_varnams.py):
 * all four letter streams match shivkumar.org's HTML exactly (312, 317, 346
 * and 444 letters) — the HTML and the PDFs are the same source, so this
 * validates the hand transcription against an independent extraction path,
 * not a second source. The second sources for issue #7 remain the manual
 * checks listed above; the varnams stay effectively single-source.
 *
 * 2. Evvari Bodhana (Abhogi; Patnam Subramania Iyer, d. 1902), added
 *    2026-11-07. Sources:
 * - Primary: Shivkumar Kalyanaraman's archive PDF
 *   (shivkumar.org/music/varnams/evvaribodhana.pdf), swaras and words.
 * - Cross-check: patantara.com's Evvari Bodhana rendering (CC BY-NC-SA,
 *   images only) — a manual second source like the book photos; not yet
 *   compared (issue #7).
 *
 * Choices and fixes, as for Ninnukori, plus:
 * - The source's hyphenated swaras (`S-`, `-S`, `M-`) are written as plain
 *   swaras (only the plain reading fits every avarta's 16 cells); the
 *   mark's meaning is unclear, flagged for the teacher.
 * - Hooked commas (the rest mark) in the pallavi's second and fourth
 *   avartas, the anupallavi's fifth, the muktayi's tenth and twelfth and
 *   chitta swarams 1-4 (twice in the first pair and once in each of the
 *   rest) are written `_`; plain commas karvai.
 * - Word holds are placed from the PDF's column groups; at the hooked
 *   commas the exact hold layout is approximate, flagged for the teacher.
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

2
title: Evvari Bodhana
raga: abhogi
tala: adi
beat: 2
kalai: 2
[pallavi]
R , G , G R S , S R S S D M D , ||
" ev - va - ri - - - bo - - - dha - - - ||
M D S D , S D S R G _ M G G R S ||
" na - - - - - vi - - - - ni - - - - ||
R G M G , M R G S R _ D S R G M ||
" ee - - - - - la - - - - gu - - - - ||
D M D S , S D S D M _ G M G R S ||
" je - - - - - se - - - - vu - ra - - ||
(pallavi @2)
[anupallavi]
S S D D , M D D M G _ R G M R G ||
" ja - - va - - mu - - ga - - nan - - - ||
S R G M , M D M G R G M D D S , ||
" ne - - la - - ra - - - se - - - sha - ||
D S R S , R G M R G _ M G G R S ||
" cha - - le - - ndra - - - sree - - - - - ||
R G R S R D R S S D M D M G R S ||
" ven - - - - ka - - te - - swa - ra - - ||
(anupallavi @2)
[muktayi swaram]
R G M R , G S R G S , R S D M D ||
S D S R _ R G S R M _ D M G R S ||
R G M G , M D M D S _ R G S R G ||
M G R G , R S D R S _ D M G R S ||
(muktayi swaram @2)
[charanam @2 +swaram]
M , D M D , , , D S D M D D S , ||
" ma - ru - ba - - - - - - - ri - - - ||
D S D D M , G M G R S R G S R G ||
" ko - - - rva - ja - la - - nu - ra - - ||
[chitta swaram 1 @2]
M , , , , _ D , M , D , , _ S , ||
D , M , , _ G , R , S , , R , G ||
(charanam)
[chitta swaram 2 @2]
M D S D , M D M G R _ G M G R S ||
D S _ R G M D S _ D M G R S R G ||
(charanam)
[chitta swaram 3 @2]
D S D M G R G M D M G R G S R S ||
R G M D S R G R S D M G R S R G ||
(charanam)
[chitta swaram 4 @2]
S , , S , S R G S R _ R S D M D ||
S , _ S D R S D _ S D M _ G R S ||
R G M D , S R G M R _ G R S D S ||
R S _ D M D S D _ M G R S , R G ||
(charanam)

3
title: Sami Ninne Kori
raga: shankarabharanam
tala: adi
beat: 2
kalai: 2
[pallavi]
S , , , N S D N P _ M P G , M , ||
" sa - - - - - - - mi - - - ni - - - ||
P _ D N P _ D N S R S N D P D N ||
" nne - - - - - ko - - - - - - - ri - ||
S _ S D P M P , P M G R S R G S ||
" cha - la - - - ma - ru - - - lu - - - ||
N P D N S _ P M G R G M P _ D N ||
" ko - - - - - nna - - - di - ra - - - ||
(pallavi @2)
[anupallavi]
S _ D D P M D P , M G M P G M R ||
" tha - - - - - - ma - - sa - - - - - ||
G M P D D P M P D N S N S _ R G ||
" mu - - se - - - - - - - - ka - - - ||
M G R S S R S N D P D N S R S , ||
" da - - ya - - ju - - - da - - - ra - ||
S D P P , M G R S M G M P , D N ||
" - - - ku - - - - ma - - - ra - - - ||
(anupallavi @2)
[muktayi swaram]
S R N S D N S P D N S D P M P D ||
M P G M P R G M S R G M P D N S ||
R G M G R G R S N P D N S , P , ||
S N D P _ M G R S R , G M P D N ||
(muktayi swaram @2)
[charanam @2 +swaram]
P , D N S S N D P M G R G , M , ||
" nee - - - - ra - - ja - - - - - - - ||
P , M D , P S N D P M G M R G M ||
" kshi - - nee - - pai - - - - - - - - - ||
[chitta swaram 1 @2]
P , _ M , _ P , _ G , M _ R , G ||
S , _ R , _ N , _ S , R _ G , M ||
(charanam)
[chitta swaram 2 @2]
P D , P M G M P _ M G M R G , R ||
S N D N _ P D N S R _ N S R G M ||
(charanam)
[chitta swaram 3 @2]
M , D P M G M , P G M R G S , N ||
S M G R S N D N S R G M P _ G M ||
(charanam)
[chitta swaram 4 @2]
P D N P D N S R S G R , S N D N ||
S _ R N D P _ D M G R S _ R G M ||
P , M P G M R G S _ M G R S N P ||
D _ N S R S N , S R G M P _ D P ||
S , N R , S M G R G S , N P D N ||
S , S , N D P , M G R S _ R G M ||
(charanam)

4
title: Jalajaksha
raga: hamsadhwani
tala: adi
beat: 2
kalai: 2
[pallavi]
G , R , S , , , N S R G R R S N ||
" ja - la - ja - - - kshi - - - ni - - - ||
S R S S N P N S R P , N , S , R ||
" nne - - - - - da - - ba - - - si - - ||
G R S N S R P N S R G P G N P , ||
" cha - - la - - ma - - - ru - - - lu - ||
S N S R , S N P P G , R , S , R ||
" ko - - - - nna - - - di - - - ra - - ||
(pallavi @2)
[anupallavi]
P G R S N S R , G , R R S S N P ||
" che - - li - - ya - ne - - - la - - - ||
N S R G _ S G R G P _ N S , , , ||
" ra - - - - va de - - - - mi ra - - - ||
R S N P N S R G S R G P G R S N ||
" che - - lu - - vu - dai - - - na - - - ||
R R , S N P P G , R S N P N S R ||
" sree - - ven - - ka te - sa - - - - - - ||
(anupallavi @2)
[muktayi swaram]
G , R G R S R , N S R G R R S N ||
R _ N G R N S R P N S R G , _ P ||
G R S R _ G P N R G P N S R G P ||
P N S R _ G P N R G P N S , _ R ||
R S N P _ N G R S N _ P N S R G ||
P , N S _ R G R P N S R G R N R ||
G P G R S N S R S N P G R G P N ||
G R _ S N P R S _ N P G _ R S R ||
(muktayi swaram @2)
[charanam @2 +swaram]
N , , , , _ S , R , S N P P G R ||
" nee - - - - - sa - - - ti - - do - - ||
G , _ G R S R , R S N S R G , P ||
" ra - - - - - ne - - - - ga - - - na ||
[chitta swaram 1 @2]
N , _ P , _ G , , R , , S , N , ||
P , _ R , _ N , _ S , R _ G , P ||
(charanam)
[chitta swaram 2 @2]
N , P G R S R , G R S N P _ R N ||
_ G R N P N S R _ R G N S R G P ||
(charanam)
[chitta swaram 3 @2]
N P G R N G R N P N P S N R S G ||
R P G N P S N R N G R N R N P G ||
P N S R G G P N S R R G P N S N ||
G R S N P R S N P G R N S R G P ||
(charanam)
[chitta swaram 4 @2]
S , , , , _ N R S N P G R S R G ||
P , , , , _ S N P G R S R G P N ||
S R _ N R N P G P R _ G P G R S ||
R N _ G R P G N R G P N S , , , ||
N P G P , _ R G P N _ G P N S R ||
P N S R , G N S N G R R N , P N ||
G R _ N S R P G , R S N P N S R ||
S , _ P , _ N P G R S N S R G P ||
(charanam)
""".trimIndent()
