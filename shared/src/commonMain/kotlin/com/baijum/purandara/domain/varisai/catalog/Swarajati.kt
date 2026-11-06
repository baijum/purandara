package com.baijum.purandara.domain.varisai.catalog

/**
 * Swarajatis, with their words. Traditional compositions.
 *
 * Rara Venu Gopa Bala (Bilahari, Adi; composer not given by either source).
 * - Primary: karnatik.com (https://karnatik.com/c1735.shtml), swaras and words,
 *   written four to a beat (`beat: 4`); its words are aligned to the swaras by
 *   column on the page.
 * - Cross-check: Shivkumar Kalyanaraman's archive
 *   (shivkumar.org/music/varnams/raravenu-swarajati.htm), which writes length by
 *   letter case (a capital swara lasts two cells, `;` holds two); compared cell
 *   by cell with tools/notation/crosscheck_gitams.py.
 *   Expected output (2026-11-06): all 10 of its avartas match one of the
 *   app's, by letter and length (its octave marks don't survive in its HTML).
 * - Both say each charanam is sung first as swaras, then with the words
 *   (`+swaram`); after each charanam the pallavi returns.
 *
 * Sambasiva Yenave (Khamas, Adi; composer Chinnakrishna Dasar — karnatik.com
 * co1112 identifies him with A.K. Chinnakrishna Naidu, clarinet exponent, father
 * and guru of A.K.C. Natarajan (born 1931); no dates are recorded anywhere, but
 * that puts his death by the mid 1940s, safely public domain).
 * - Primary: *Sangeeta Bala Padam* Part 1 (English, A.S. Panchapakesa Iyer,
 *   Giri Trading), SWARAJATI-2, pp. 82–84 (photos supplied by the user,
 *   2026-11-06). Written two to a beat (`beat: 2`), with its own anupallavi
 *   (the web pages fold it into the pallavi) and a last charanam in khanda
 *   gati (`beat: 5`, five cells per beat).
 * - Cross-check: shivkumar.org/music/varnams/sambasiva-swarajati.htm (notes
 *   courtesy "(Rani) www.karnatic.com"; length by letter case as above).
 *   Cell-by-cell against the book: the pitch sequences agree through all 24
 *   avartas. Expected crosscheck_swarajatis.py output (2026-11-07): 20 of 22
 *   source avartas match one of the app's by letter and length — the two that
 *   differ are charanam 2's avarta 3 (the book re-strikes the held Pa and
 *   Dha, shivkumar.org holds them; the book is kept) and charanam 4's avarta
 *   1 (cell 15, above) — and the khanda-gati charanam matches by letter.
 * - Third source: a notation PDF on veenagana.org (Vasanthi Iyer),
 *   veenagana.org/wp-content/uploads/2017/12/SAMBASHIVA.pdf — differs from the
 *   other two at nine cells (listed in docs/plan-gitam-swarajati-varnam.md);
 *   not followed.
 *
 * Choices and fixes, each listed:
 * - Charanam 4, avarta 1, cell 15 ("ta" of "makutadha"): the book writes G;
 *   both web sources write M (shivkumar.org lowercase m, one cell). The book
 *   is kept; the variant is M.
 * - Pallavi, avarta 1: the book re-strikes Sa at cell 5 (a second S under the
 *   held syllable "sa") and puts "mba" on Ni at cell 7; shivkumar.org also
 *   re-strikes but sings "mba" on the re-struck Sa. Kept the book's swaras and
 *   the book's word placement.
 * - Charanam 1, avarta 1: the book writes "N - N" where shivkumar.org holds
 *   the Ni across cells 7-8; the pitches agree; shivkumar.org's hold is kept
 *   (it matches the book's own words, "dai" over two cells).
 * - Charanam 2, avarta 3: the book writes the scalar run as sixteen separate
 *   cells (P P P P, D D D D under the held "sun" and "na"); shivkumar.org
 *   holds the first Pa and Dha of each group. The book's separate cells are
 *   kept.
 * - Charanam 3, avarta 1 cell 1 and charanam 4, avarta 6 cell 1: the book and
 *   shivkumar.org have D; the veenagana PDF has N. D is kept.
 * - The book's raga header prints G2/N1, but a janyam of the 28th mela cannot
 *   take them and both web sources say G3/N2: encoded as standard Khamas
 *   (G3/N2). The notation body cannot settle it (it writes plain G and N).
 * - The book dots some S and R in the body (never other letters, not
 *   consistently enough to be octave marks; c2's scalar run S S S S is
 *   undotted while neighbouring rows dot their S and R). Undecoded; the piece
 *   reads as one octave, S to N, and is written without octave marks.
 * - Charanam 2, avarta 3: the book and the veenagana PDF sing "na ya ka"
 *   (nayaka); shivkumar.org's lyric has "nayana" there. The book's words.
 * - Charanam 2, avarta 4, cells 5-6: the book holds "da" over Dha where
 *   karnatik.com's meaning text and shivkumar.org's lyric read "maruvaka nA"
 *   the same way; shivkumar.org's lengths are kept (D held two cells).
 * - Charanam 4, avarta 7: the book splits the long syllables ("dh ya" for
 *   dhya); written one syllable per cell with holds ("dhya -").
 * - The book voices some stops as in Telugu print (ku, go, ba where
 *   shivkumar.org has gu, ko, pa); kept as the book prints them.
 * - Karvai placement follows the book where its columns are legible, else
 *   shivkumar.org's case-length expansion; the pitch sequences agree either
 *   way. Charanam 5 (khanda gati) follows the book throughout; its words put
 *   "tram" on the held cell right after "man", as the book prints.
 * - Each section ends with the book's "(sam)" — the return to the pallavi,
 *   written here as the `(pallavi)` repeat after each charanam.
 *
 * Conventions as in Gitam.kt: words are simple Latin, one syllable per cell,
 * `-` for a held syllable; the piece is one octave so no `′`/`ˌ` marks appear.
 */
internal val SWARAJATI_NOTATION: String = """
1
title: Rara Venu Gopa Bala
short: Rara Venu
raga: bilahari
tala: adi
beat: 4
[pallavi]
S , , R | G , P , | D , S′ , | N , D , | P , D P | M G R S | R S Nˌ Dˌ | S , , , ||
" ra - - ra | ve - nu - | go - pa - | ba - la - | ra - ji ta | sad - gu na | ja ya shi - | la - - - ||
[anupallavi]
S , , R | G , P , | M , , G | P , D , | R′ , , S′ | N , , D | P , , M | G , , R ||
" sa - - ra | sa - ksha - | ne - - ra | me - mi - | ma - - ru | ba - - ri | kor - - va | le - - ra ||
[charanam 1 +swaram]
S , , R | G , G , | G , , , | , , R G | P , , P | P , P , | P , , , | , , D P ||
" nan - - da | go - pa - | la - - - | - - ne - | nen - - du | po - ja - | la - - - | - - ni - ||
S′ , , S′ | S′ , S′ , | G′ R′ S′ N | N D P , | P D P M | G G R , | G P M G | R S R G ||
" vin - - du | ra - ra - | sa da ma la | ma di lo - | mu da ma la | ra ga na - | ke du ru ga | ga di ya ra ||
(pallavi)
[charanam 2 +swaram]
P P P , | R R R , | G P M G | G , , , | G P M G | M G R S | R G R S | S , , , ||
" pa lu ma - | ru nu ga - | ra va mu na | nin - - - | pi la chi na | pa lu ka vu | na lu ga ku | ra - - - ||
R S Nˌ Dˌ | S , , , | M G R G | P , , , | D P D R′ | S′ , , , | R′ S′ N D | P M G R ||
" ka ri va ra | da - - - | ma ri ma ri | na - - - | a dha ra mu | gro - - - | la ra ka ni | ka ra mu ga ||
(pallavi)
[charanam 3 +swaram]
P , , , | M G R G | D , , , | M G R G | P , , , | M G R G | P , P , | P , , , ||
" ra - - - | na ga da ra | ra - - - | mu ra ha ra | ra - - - | bha va ha ra | ra - ve - | ra - - - ||
G′ , , , | R′ S′ N D | R′ , , , | R′ S′ N D | S′ , , , | R′ S′ N D | S′ , S′ , | S′ , , , ||
" i - - - | ma gu va nu | i - - - | la la la nu | i - - - | so ga si ni | che - ko - | ra - - - ||
G′ , R′ S′ | R′ , R′ , | R′ , , , | R′ , S′ N | D , D , | D , , , | P , M G | G , G , ||
" ko - ri ka | lim - pon - | da - - - | den - da mu | ni - yan - | da - - - | che - re nu | ni - chen - ||
G , , , | S R G D | P , , , | R′ S′ R′ G′ | S′ , , , | G′ R′ S′ N | D P M G | R S R G ||
" ta - - - | ma ru va ku | ra - - - | ka ra mu la | che - - - | ma ri ma ri | ni nu sha ra | na ne da ra ||
(pallavi)
2
title: Sambasiva Yenave
short: Sambasiva
raga: khamas
tala: adi
beat: 2
[pallavi]
S , , , | S , N , | D , P , | , M G , ||
" sa - - - | - - mba - | shi - va - | - ya na - ||
M , , , | , , G , | M , P , | D , N , ||
" ve - - - | - - ra - | ji - ta - | gi - ri - ||
[anupallavi]
S , , R | N , , S | D , , N | P , , D ||
" sa - - mba | vi - - ma | no - - ha | ra - - pa ||
M , , P | M , , G | M , , P | D , , N ||
" ra - - tpa | ra - - kri | pa - - ka | ra - - shri ||
[charanam 1 +swaram]
S , R , | S N N , | S , N D | D , N , ||
" ni - ve - | gu ru dai - | vam - ba ni | ye - ve - ||
D P P , | D , M G | S M , G | M P D N ||
" la nu se - | vim - pu chu | sa da - ma | di ni shi va ||
(pallavi)
[charanam 2 +swaram]
S R S N | S , , , | N S N D | N , , , ||
" pa ra ma da | ya - - - | ni dhi va nu | chu - - - ||
D N D P | D , , , | P D P M | P , , , ||
" ma ru va ka | na - - - | hri da ya mu | na - - - ||
S S S S | M M M M | P P P P | D D D D ||
" ma ha de va | ma ha pra bho | sun - da ra | na - ya ka ||
N S N S | N , D P | D P M G | M P D N ||
" su ra va ra | da - ya ka | bha va bha ya | ha ra shi va ||
(pallavi)
[charanam 3 +swaram]
D S N D | P M G M | P , , , | P D N D ||
" sthi ra ma dhu | ra pu ra mu | na - - - | va ra mu lo ||
P M G G | M , , , | P N D N | D P D N ||
" sa gu ha ru | ni - - - | ni ra ta mu | nu ta la chi ||
(pallavi)
[charanam 4 +swaram]
S , , , | , , S N | N D D P | P M G G ||
" shri - - - | - - shu bha | ka ra sha shi | ma ku ta dha ||
M , , , | , , P D | N D M G | M P D N ||
" ra - - - | - - ja ya | vi ja ya tri | pu ra ha ra ||
S M G S | S , S , | S R S S | N , N , ||
" shri ta ja na | lo - la - | dbhu ta gu na | shi - la - ||
N S N D | D , D , | P D P M | P , P , ||
" kri ta nu ta | ba - la - | pa ti tu ni | lo - la - ||
S M , M | G P , P | M D , D | P N , N ||
" mu dam - ba | la ran - ga | pa da - bja | mu lan - du ||
D R , R | N S , S | N S N D | P , , , ||
" pa dam - bu | lu jer - chu | pa shu pa ti | ni - - - ||
M , P M | P , D P | D , N D | N , S N ||
" jnya - na mu | dhya - na mu | sna - na mu | pa - na mu ||
S , R S | R , S N | S R S , | N D P M ||
" da - na mu | ma - na mu | a bhi ma - | na ma nu chu ||
G M P D | N S N R | S , , , | S R N S ||
" ka ni ka ra | mu na cha ra | nam - - - | bu lu ka nu ||
D N P D | M , , , | D P M G | M P D N ||
" go nu sru tu | lan - - - | nu tu la sha | ra na nu chu ||
(pallavi)
[charanam 5 +swaram]
beat: 5
S , R S , | N , D N , | S , N D , | P , , , , | P , D N , | D , P M , | P , M G , | M , , , , ||
" sa - ra sa - | re - ku ni - | na - ma man tram | - - - - - | ko - ri na - | nu - ni pa - | da - bja man tram | - - - - - ||
M , G M , | P , M P , | D , P D , | N , D N , | S , R S , | N , D N , | S , N D , | M , P D , ||
" da - su dau - | chi - nni kri - | shnu - ni ki - | dik - ku ni - | ve - ya ni - | shok - ka na - | thu - ni nam - | mu - ko ni - ||
(pallavi)
"""
