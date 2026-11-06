package com.baijum.purandara.domain.varisai.catalog

/**
 * The four Malahari pillari gitams of Purandara Dasa, with their words.
 * Traditional compositions (16th century, public domain).
 *
 * Sources (docs/plan-alankaram-gitam.md, "Source review" and "Cross-check"):
 * - Primary: karnatik.com (by Rani): Sri Gananatha https://karnatik.com/c1806.shtml,
 *   Kunda Gaura https://karnatik.com/c1807.shtml, Kereya Neeranu
 *   https://karnatik.com/c1808.shtml, Padumanabha https://karnatik.com/c1809.shtml.
 *   Section order and swaras as there.
 * - Cross-check: Shivkumar Kalyanaraman's archive (shivkumar.org/music/varnams),
 *   compared avarta by avarta with tools/notation/crosscheck_gitams.py.
 * - Cross-check for Sri Gananatha: Sangeeta Bala Padam, Part 1 (English
 *   edition, Giri Trading Agency, ISBN 81-7950-877-3), Gitam 1, p. 59: its
 *   syllable-per-swara words are used for that gitam.
 *
 * The cross-check script's expected output (2026-11-06): Kunda Gaura matches
 * both sources; Sri Gananatha differs from karnatik.com only in the refrain
 * (below); every other difference is a hold that shivkumar.org writes as a
 * repeated swara, or a line its HTML wraps (a stray "S", "R , M P , , ,").
 *
 * Conventions: the sites' lowercase letters (middle octave) are capitals
 * here, their capitals (upper octave) carry `′`, and their trailing dot
 * (lower octave) is `ˌ`. Words are simple Latin, one syllable per akshara,
 * `-` where a syllable is held (the sites' `.`).
 *
 * Choices and fixes, each listed rather than made silently:
 * - Sri Gananatha refrain (lambodara): `S , | R M G R` from shivkumar.org and
 *   the book; karnatik.com has `S R | M , G R` (a pathantaram).
 * - Holds: shivkumar.org sometimes repeats a swara under a held syllable
 *   (`S D D` under "pa ram -"); karnatik.com's karvai (`S D ,`) is kept.
 * - Sri Gananatha charanam 3: karnatik.com splits the words "sa r | vO t ta ma";
 *   the book's "sa - | rvo - tta ma" is used.
 * - Kunda Gaura "d R | R s d p": the lowercase s (middle Sa, a fall of more
 *   than an octave from upper Ri, then straight back up to Dha) is taken as
 *   a missing capital: R′ S′ D P, like Sri Gananatha's R′ S′ | D P.
 *   shivkumar.org doesn't mark the upper octave, so it can't decide this.
 * - Padumanabha pallavi: karnatik.com's words "naa. | ma ." are a typo for
 *   "na - | bha -" (shivkumar.org).
 * - Words that the sites write under two swaras as one ("naatha", "swaroo.",
 *   "shikshaka") are split at syllables.
 * - The one lower-octave note is Padumanabha's `R S Dˌ` (both sources agree);
 *   the doubtful `S D ,` and `D P D S` are middle octave in both.
 * - Analekara (karnatik.com https://karnatik.com/c2415.shtml; shivkumar.org
 *   analekara-geetham.htm): karnatik.com writes the repeat "(sakala…
 *   parigatamnam)" out but its page stops mid-line; the repeat is taken to
 *   end at "parigatamnam", as its lyrics say, so the gitam ends on upper Sa.
 *   Its sections are named by their opening words (it has no pallavi).
 *   One note differs between the sources, consistently on each: the first
 *   "ta - la di - nam" is `P , M | D , | D P` on karnatik.com (kept, the
 *   primary source) and `P , P | D D | D P` on shivkumar.org. Open for a
 *   teacher or book to settle.
 * - shivkumar.org labels Kereya Neeranu and Padumanabha "Chathurasra Jathi"
 *   Triputa, but its notation is 3 + 2 + 2 (Tisra), as karnatik.com says.
 */
internal val GITAM_NOTATION: String = """
1
title: Sri Gananatha
raga: malahari
tala: rupaka-4
[charanam 1]
M P | D S′ S′ R′ || R′ S′ | D P M P ||
" sri - | ga na na tha || sin du | - ra var na ||
R M | P D M P || D P | M G R S ||
" ka ru | na sa ga ra || ka ri | va da - na ||
[pallavi]
S , | R M G R || S R | G R S , ||
" lam - | bo - da ra || la ku | mi ka ra - ||
R M | P D M P || D P | M G R S ||
" am - | ba - su ta || a ma | ra vi nu ta ||
S , | R M G R || S R | G R S , ||
" lam - | bo - da ra || la ku | mi ka ra - ||
[charanam 2]
M P | D S′ S′ R′ || R′ S′ | D P M P ||
" sid dha | cha - ra na || ga na | se - vi ta ||
R M | P D M P || D P | M G R S ||
" sid dhi | vi na ya ka || te - | na mo na mo ||
(pallavi)
[charanam 3]
M P | D S′ S′ R′ || R′ S′ | D P M P ||
" sa ka | la vi dya - || - di | pu - ji ta ||
R M | P D M P || D P | M G R S ||
" sa - | rvo - tta ma || te - | na mo na mo ||
(pallavi)

2
title: Kunda Gaura
raga: malahari
tala: rupaka-4
[charanam 1]
D P | M G R S || R M | P D M P ||
" kun da | gau - - ra || gau - | ri - va ra ||
D R′ | R′ S′ D P || D P | M G R S ||
" man di | ra - - ya || ma - | na ma ku ta ||
[pallavi]
S , | R , R , || D P | M G R S ||
" man - | da - ra - || ku su | ma - ka ra ||
S R | M , G R || S R | G R S , ||
" ma ka | ran - dam - || va - | si tu va - ||
[charanam 2]
D P | M G R S || R M | P D M P ||
" he - | ma ku - ta || sim - | ha - sa na ||
D R′ | R′ S′ D P || D P | M G R S ||
" vi ru | pa - - ksha || ka ru | na - ka ra ||
(pallavi)
[charanam 3]
D P | M G R S || R M | P D M P ||
" chan da | ma - - ma || man - | da - ki ni ||
D R′ | R′ S′ D P || D P | M G R S ||
" man di | ra - - ya || ma - | na ma ku ta ||
(pallavi)

3
title: Kereya Neeranu
raga: malahari
tala: triputa-3
[charanam 1]
D S′ S′ | D P | M P || D D P | M M | P , ||
" ke re ya | ni - | ra nu || ke re ge | chal - | li - ||
D D S′ | D P | M P || D D P | M G | R S ||
" va ra va | pa de | da va || ran - te | ka - | ni ro ||
[pallavi]
S R R | S R | S R || D D P | M G | R S ||
" ha ri ya | ka ru | na do || la - da | bha - | gya va ||
D P D | S , | D P || D D P | M G | R S ||
" ha ri sa | mar - | pa ne || ma - di | ba du | ki ro ||
[charanam 2]
D S′ S′ | D P | M P || D D P | M M | P , ||
" sri - pu | ran - | da ra || vi tha la | ra - | ya - ||
D D S′ | D P | M P || D D P | M G | R S ||
" cha ra na | ka ma | la va || nam - bi | ba du | ki ro ||
(pallavi)

4
title: Padumanabha
raga: malahari
tala: triputa-3
[pallavi]
R S Dˌ | S , | S , || M G R | M M | P , ||
" pa du ma | na - | bha - || pa ra ma | pu ru | sha - ||
S D , | D P | M P || D D P | M G | R S ||
" pa ran - | jo - | - ti || swa ru - | pa - | - - ||
R S Dˌ | S , | S , || M G R | M M | P , ||
" vi dhu ra | van - | dya - || vi ma la | cha ri | ta - ||
S D , | D P | M P || D D P | M G | R S ||
" vi han - | ga - | - di || ro - ha | na - | - - ||
[anupallavi]
P M P | D S′ | D S′ || R′ S′ D | D S′ | D P ||
" u da dhi | ni va | - sa || u ra ga | sha ya | na - ||
D , P | P , | P M || R M M | P , | , , ||
" un - na | ton - | na ta || ma hi - | ma - | - - ||
D D P | P , | P M || R , M | M G | R S ||
" ya du ku | lo - | tta ma || yag - nya | rak - | sha ka ||
S , S | D D | D P || P , P | M G | R S ||
" yag - nya | shik - | sha ka || ra - ma | na - | - ma ||
(pallavi)
[charanam]
D S′ , | D P | M P || D D P | M G | R S ||
" vi bhi - | sha na | pa - || la ka - | na mo | na mo ||
D S′ , | D P | M P || D D P | M G | R S ||
" i bha - | va ra | da - || ya ka - | na mo | na mo ||
P M P | D S′ | D S′ || R′ S′ D | D S′ | D P ||
" shu bha - | pra da | su ma || no - ra | da - | - su ||
D D P | P , | P M || R M M | P , | P , ||
" ren - dra | ma - | no - || ran - ja | na - | - - ||
D D P | P , | P M || R , M | M G | R S ||
" a bhi na | va - | - pu || ran - da | ra - | - vi ||
S , S | D D | D P || P , P | M G | R S ||
" tha - la | bhal - | la re || ra - ma | na - | - ma ||
(pallavi)

5
title: Analekara
raga: suddha-saveri
tala: triputa-3
[analekara]
R′ M′ R′ | R′ S′ | D S′ || S′ , S′ | D P | M P ||
" a - sa | le - | ka ra || un - ni | po - | la di ||
[sakala]
D D S′ | D , | D P || P M R | D , | D P ||
" sa ka la | sha - | stra pu || ra - na | di - | nam - ||
P , M | D , | D P || P , P | M P | D P ||
" ta - la | di - | nam - || ta - la | pa ri | ga tu ||
P M R | S R | S R || P M P | S R | S R ||
" re - - | - - | - - || re - - | - - | - - ||
P P D | P P | M R || R S R | M , | M , ||
" re - - | - - | - - || se - tu | va - | ha - ||
D P D | S′ , | S′ , ||
" pa ri ga | tam - | nam - ||
[jata juta]
R′ R′ S′ | D P | M P ||
" ja ta - | ju - | - ta ||
(sakala)
"""
