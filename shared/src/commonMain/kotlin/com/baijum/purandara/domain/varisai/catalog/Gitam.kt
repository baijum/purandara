package com.baijum.purandara.domain.varisai.catalog

/**
 * The gitams, with their words. Traditional compositions.
 *
 * Gitams 1-4 are the Malahari pillari gitams of Purandara Dasa (16th century,
 * public domain); gitam 5 is Analekara (Suddha Saveri).
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
 * The cross-check script's expected output (2026-11-07): Kunda Gaura matches
 * both sources; Sri Gananatha differs from karnatik.com only in the refrain
 * (below); Analekara differs from karnatik.com only in the settled "ta - la
 * di - nam" avarta (below), and from shivkumar.org only in holds that it
 * writes as a repeated swara, or a line its HTML wraps (a stray "S",
 * "R , M P , , ,", a stray ",").
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
 *   One note differed between the sources, consistently on each: the first
 *   "ta - la di - nam" was `P , M | D , | D P` on karnatik.com and
 *   `P , P | D D | D P` on shivkumar.org. Settled 2026-11-07 by the user's
 *   teacher for `P , P` (issue #2): every other source found — eviolinguru.com
 *   and meanjalisudheer.com sheets, the Gurukulam Academy collection and the
 *   NIOS Carnatic Music Book 2, p. 54 — agrees, on both occurrences, leaving
 *   karnatik.com's `P , M` the lone variant. The hold is karnatik.com's
 *   karvai (`D ,`), as in the parallel "ra - na di - nam" phrase.
 * - shivkumar.org labels Kereya Neeranu and Padumanabha "Chathurasra Jathi"
 *   Triputa, but its notation is 3 + 2 + 2 (Tisra), as karnatik.com says.
 *
 * Gitams 6-8 (added 2026-11-07) each have a single source, accepted by the
 * user's decision (issue #1): Shivkumar Kalyanaraman's archive, adapted from
 * Chitraveena Ravikiran's *Perfecting Carnatic Music, Level 1*. The notation
 * is taken from each page's PDF, which carries the octave and variant marks
 * its HTML loses. karnatik.com has lyrics only for all three. The pages mark
 * no sections, so these gitams have none; words are the pages', lowercased.
 *
 * - Kamalajadala (Kalyani, Tisra Triputa): the tala was Misra Chapu until
 *   2026-11-07, when the user settled issue #3 in favour of Tisra Triputa —
 *   karnatik.com's listing and the collection PDF (gitam 9's source) both say
 *   Tisra Triputa, and shivkumar.org's own header equates the names
 *   ("Misra Chapu (Triputa Talam)"); Misra Chapu is the common-practice
 *   rendering of the same 7-akshara cycle, so the swaras are unchanged. The
 *   source's bars mark the chapu angas as 3 + 4; listed as the variant.
 *   All madhya. Its last line reads "D P - M" where the parallel phrase
 *   (avarta 4), the words and the 7-akshara avarta give "D P M"; the typo is
 *   corrected. "(a)su" (the elided a of "kaasura") is written "su".
 * - Re Re Sri Ramachandra (Arabhi, Tisra Triputa): on this page lowercase
 *   means the mandra octave (on other pages it is length); its mandras are
 *   written with `ˌ`. The page's avarohana header says N2/G2, but the raga
 *   is the standard Arabhi (N3/G3, per karnatik.com's raga page). Avarta 18's
 *   words row is one hold short in the print; the missing "-" is supplied.
 *   The bold letters are a Word artifact, ignored; the final "ja nu" (for
 *   -jana) is as printed.
 * - Sri Ramachandra (Bhairavi, Chaturasra Dhruva): the piece sings both
 *   Dhaivatas; D1 is written as a variant of the raga's D2 (the digit
 *   syntax), and the source's explicit D2s are the plain default D. The
 *   arohana header's "G2M2" is a typo for G2 M1. All madhya.
 *
 * The crosscheck script's expected output for gitams 6-8 (2026-11-07): the
 * letter streams match shivkumar.org exactly (70, 131 and 95 letters) — the
 * script compares these three by letter alone, since the pages' octave and
 * variant marks (read from the PDFs) survive nowhere in their HTML.
 *
 * Gitam 9, Kamala Sulochana (Anandabhairavi, Adi; Purandara Dasa), added
 * 2026-11-07 — the sanchari geetham that had no notation source anywhere
 * (issue #1). Sources:
 * - Primary: the "Gurukulam & SF" geetham-notes collection (geetham_notes.pdf,
 *   supplied by the user; provenance page/site to be confirmed), pp. 26-28:
 *   complete swaras and words, in Adi tala, with Anandabhairavi's arohana
 *   written in the 16-swarasthana naming (its "G1"/"N1" are the modern G2/N2;
 *   its arohana header also mislabels the mela as 22nd Kharaharapriya).
 * - Cross-check: a staff-and-sargam rendering of the same piece (also
 *   supplied by the user) — it agrees bar for bar, with two exceptions listed
 *   below; and vignanam.org's lyrics for the sahityam. No online notation
 *   source exists, so crosscheck_gitams.py skips this piece.
 *
 * Choices and fixes, each listed:
 * - Tala: Adi (the collection PDF and karnatik.com's lyrics page agree); the
 *   staff rendering labels it "Eka Tala 4/4", whose 4-beat bars are the
 *   half-avartas of the same music.
 * - Avartas 8 and 10 (the "sankalita"/"tilaka dhare" phrases, G G R N S): the
 *   collection PDF writes plain N (madhya Ni) both times; the staff rendering
 *   dots it below both times (mandra Ni). The PDF is kept; the teacher may
 *   prefer the mandra reading.
 * - Avarta 16 repeats avarta 5's phrase; the PDF writes avarta 5's Ni with a
 *   mandra dot (Nˌ) and avarta 16's plain — both are encoded as the PDF has
 *   them, again for the teacher to settle.
 * - The second time through "maraala gaamini karihara madhye" and "bimbaana
 *   vidu mandalare" (avartas 13-18) repeats avartas 3-6's swaras; the PDF has
 *   them identical except avarta 16's N (above).
 * - Avarta 12's last cell: the PDF writes plain S (madhya), but the staff
 *   rendering dots it (tara) and only that reading joins avarta 13's tara
 *   phrase without a leap of more than an octave; written S′.
 * - Words: as the PDF prints them ("se" for the sha of "shaayya", "jha" in
 *   "ghana jha ga"), lowercased; the elided "naaM" is split "na am".
 *
 * The same collection PDF (see gitam 9) also gives second sources for three
 * of the pieces above, with differences for the teacher to settle:
 * - Kamalajadala: it calls the tala Tisra Triputa (as does karnatik.com; the
 *   chosen source's PDF has Misra Chapu — settled as Tisra Triputa, issue #3),
 *   and writes the piece an octave higher, from tara Sa down to mandra Dha,
 *   where the chosen source stays in the middle octave.
 * - Re Re Sri Ramachandra: it sets the piece in (Chaturasra) Rupaka — six
 *   aksharas to the avarta — where the chosen source has Tisra Triputa
 *   (seven). The melody's letters agree.
 * - Sri Ramachandra: its letter stream matches the chosen source, but it
 *   writes both Dhaivatas as the plain default (D2), where the chosen source
 *   sings the second avarta's first Dhaivata as D1 — the variant kept here.
 * - Vara Veena: a third source (with shivkumar.org's PDF and the NIOS book)
 *   for when that gitam is encoded.
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
P , P | D , | D P || P , P | M P | D P ||
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

6
title: Kamalajadala
raga: kalyani
tala: triputa-3
S S S N D N S ||
" ka ma la ja - da la ||
N D P D P M P ||
" vi ma la su na ya na ||
G M P P D D N ||
" ka ri va ra da ka ru ||
D P M P G R S ||
" na - mbu dhe - - - ||
D D D G G G , ||
" ka ru na sha ra dhe - ||
M P , M G R S ||
" ka ma - la - - - ||
R , , S , , , ||
" kan - - tha - - - ||
, , , , , , , ||
" - - - - - - - ||
G M P M P D P ||
" ke - shi na ra ka - ||
N D P D P M P ||
" su ra vi bhe - da na ||
G M P P D D N ||
" va ra dha ve - la a ||
D P M P G R S ||
" pu ra su ro - tta ma ||



7
title: Re Re Sri Ramachandra
raga: arabhi
tala: triputa-3
P , P M M P , ||
" re - re sri - ra - ||
M G R S R M G ||
" - - ma chan - - - ||
R R S S D R S ||
" - - - - - - - ||
R , , R , S R ||
" ndra - - - - ra ghu ||
M G R R S S , ||
" vam - sha ti la ka - ||
P M M P , P , ||
" ra - gha ven - dra - ||
P M P M G R R ||
" a - - - - - - ||
M G R S R S S ||
" a - - - - - - ||
S Dˌ R S R S S ||
" a - - - - - - ||
Dˌ S , Dˌ Dˌ Dˌ Pˌ ||
" a - - sri tha ja na ||
Pˌ Mˌ Pˌ Dˌ S S , ||
" po - sha ku - re - ||
R S R M G R R ||
" si - - tha - - ma ||
M G R M M P M ||
" no - - ran - ja nu ||
P , P P , P , ||
" re - re dhi - ra - ||
P M P D S S R ||
" ra - va na - su ra ||
M G R S R S S ||
" an - tha ku - re - ||
S D R S R S S ||
" a - - yi ya yi ya ||
D S , D D D P ||
" a - - yi ya yi ya ||
P M P D S S , ||
" a - - yi ya re - ||
S , S D D D P ||
" dhi - na ja na man - ||
P M P M G R R ||
" da - ru ma - ma va ||

8
title: Sri Ramachandra
raga: bhairavi
tala: dhruva-4
G R G M P , M G R G M P M , ||
" sri - ra - ma - chan - dra - sri tha pa - ||
P D N N D1 P M N D1 P M G R S ||
" - ri ja - - tha sa ma - - - - - stha ||
S R S P M P G R G M G G R S ||
" kal - - ya - na gu na - bhi ra - - ma ||
R R G G M M G G R G M P M M ||
" si - tha - mu kha am - - - bo - ru ha ||
P D D N N S P D N S R G R S ||
" san - - - - cha ri - - - - - - ka ||
N R S G R S N N D1 M P D N S ||
" ni ran - tha ram - man - ga la ma - - ta ||
P D1 P S N S P D1 M P G , R S ||
" no - - tu - - - - - - - - - - ||
9
title: Kamala Sulochana
raga: anandabhairavi
tala: adi
N D N S′ S′ , N S′ ||
" ka ma la su lo - cha na ||
G′ R′ S′ N N D P M ||
" vi ma la ta ta - ki ni ||
P P P D N D P M ||
" ma ra - la ga - mi ni ||
M P M P G R S , ||
" ka ri ha ra ma - dhe - ||
S , Nˌ , S G G M ||
" bim - ba - - - na na ||
G M P M G R S , ||
" vi du man - da la re - ||
P , M G M , G R ||
" chan - da na kun - ku ma ||
G , R N S , S , ||
" san - cha li ta - - - ||
P P M G M M G R ||
" pa ri ma la ka - stu ri ||
G G R N S , S , ||
" ti la ka dha re - re - ||
S G R G M G M , ||
" jaa - - ji se - yya - ||
M N D N P D N S′ ||
" ka cha ku cha gha na jha ga ||
G′ R′ S′ N N D P M ||
" na am - - bho - - ja ||
P P P D N D P M ||
" ma ra - la ga - mi ni ||
M P M P G R S , ||
" ka ri ha ra ma - dhe - ||
S , N , S G G M ||
" bim - ba - - - na na ||
G M P M G R S , ||
" vi du man - da la re - ||

"""
