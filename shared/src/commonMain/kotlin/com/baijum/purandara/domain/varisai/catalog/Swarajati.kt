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
 * Conventions as in Gitam.kt: the sites' lowercase letters are plain here,
 * their capitals carry `′`, their trailing dot is `ˌ`; words are simple Latin,
 * one syllable per cell, `-` for a held syllable.
 *
 * Fixes, each listed:
 * - Charanam 3, last line: karnatik.com writes `G R S N d p` with a capital N
 *   (upper Ni), a leap of more than an octave down to middle Dha in a plain
 *   descent; taken as middle Ni.
 * - Charanam 1: karnatik.com writes `g , , , g , r g` and `p , , , p , d p`
 *   with a held syllable under the second g and p ("laa. . . . ."); they are
 *   held, as shivkumar.org writes them (`G , , , , , R G`).
 * - Words the page spreads over two swaras ("shee" over `n. d.`, "ksha" over
 *   `p ,`) are put on the first, with the second held.
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
"""
