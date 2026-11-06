# Plan: More gitams, then swarajatis, then varnams

Status: **in progress** (2026-11-06). Building what two sources allow, and
the engine work, while Q1 (a second source for the rest) is open.

Progress:
- G1 (part): Analekara (Suddha Saveri), karnatik.com and shivkumar.org.
  One note differs between them (the first "ta - la di - nam": `P , M` on
  karnatik.com, kept; `P , P` on shivkumar.org); see `Gitam.kt`.
- **G1 complete (2026-11-07): Kamalajadala (Kalyani, Misra Chapu), Re Re
  Sri Ramachandra (Arabhi) and Sri Ramachandra (Bhairavi, Dhruva) added from
  shivkumar.org alone — a single source, accepted by the user's decision
  (issue #1).** New engine work: Misra Chapu (a chapu tala, its angas 3 + 4
  per ThaaLam's model — the source's own bars agree) and swara variants (a
  digit after a letter, e.g. Bhairavi's `D1` beside its `D2`, allowed per
  raga, shown as a subscript). New ragas: Kalyani, Arabhi, Bhairavi. The
  pieces' octave and variant marks survive only in the pages' PDFs (the HTML
  loses them); the crosscheck script compares these three by letter stream,
  which matches exactly.
- The **highest Sa per piece** (from V1) came forward: Analekara climbs to
  upper Ma. `Varisai.playableSaMidis`; both players explain and offer the
  nearest Sa, and Play stays off until then.
- Vara Veena, Kamala Jadala and Re Re Sri Ramachandra wait for a second source.
- S1 (part): `beat:` (cells per tala beat), cell-based rows
  (`Tala.rowStartsFor`), `[name +swaram]` sections, `short:` titles. **Swara
  variants (`N2`) are not built yet**: no sourced piece needs them so far
  (Rara Venu's notation marks none); they come with the Bhairavi and
  Anandabhairavi gitams.
- S2 (part): Rara Venu Gopa Bala (Bilahari) from karnatik.com, matching
  shivkumar.org cell for cell (all 10 avartas). **Sambasiva Yenave (Khamas)
  added** (2026-11-07): from *Sangeeta Bala Padam* Part 1 (pp. 82–84),
  cross-checked against shivkumar.org and the veenagana.org PDF (the arbitrated
  spots are listed in `Swarajati.kt`'s header). Its anupallavi is its own
  section, and its last charanam is in khanda gati — new engine work: a
  section-scoped `beat: 5` override, per-avarta gati in the schedule and the
  notation rows (`cellsPerBeatIn`), and `beat: 5` allowed by the parser.
- V2 done early: **Practise one section** (`Varisai.sections`,
  `Schedule.ofSection`, keeping the piece's avarta numbers). Both players
  offer "Section: Whole piece" on pieces with sections; it plays once, or
  loops with Repeat on; a section alone doesn't count as practising the piece
  or as its best result.
- Not built yet, waiting on a varnam source and Q4/Q5: per-section speed
  (`(pallavi @2)`), rests, the two-kalai strip, swara variants.

These continue the beginner path after the Malahari gitams
(`docs/plan-alankaram-gitam.md`):

1. **More gitams**, in new ragas.
2. **Swarajatis**: longer pieces whose charanams are sung first as swaras and
   then as words.
3. **Tana varnams**: sung in two speeds, with muktayi and chitta swarams.

Each stage ships on its own, and everything lands on both apps.

## Sources: the deciding constraint

The rule from the first gitams stands: **two independent sources agree, or
the piece waits.** Notation is never written from memory.

What the two web sources have (checked 2026-11-06):

| Piece | Raga, tala | karnatik.com | shivkumar.org | Composer (public domain?) |
|---|---|---|---|---|
| **Gitams** | | | | |
| Analekara | Suddha Saveri, Tisra Triputa | ✅ notation | ✅ | (not given) |
| Vara Veena | Mohanam, Rupaka | ❌ links to shivkumar.org | ✅ | Appayya Dikshitar ✅ |
| Kamala Jadala | Kalyani; karnatik.com says Tisra Triputa, shivkumar.org says Misra Chapu | ❌ | ✅ | Purandara Dasa ✅ |
| Re Re Sri Ramachandra | Arabhi, Tisra Triputa | ❌ | ✅ | Paidala Gurumurti Sastri ✅ |
| Sri Ramachandra Shrita | Bhairavi, Dhruva | ❌ | ✅ | Purandara Dasa ✅ |
| Kamala Sulochana | Anandabhairavi, Adi | ❌ (no notation) | ❌ | Purandara Dasa ✅ |
| **Swarajatis** | | | | |
| Rara Venu Gopa Bala | Bilahari, Adi | ✅ notation | ✅ | (not given) |
| Sambasiva Yenave | Khamas, Adi | ✅ notation (other layout) | ✅ (+ *Sangeeta Bala Padam* Part 1, pp. 82–84) | Chinnakrishna Dasar ✅ (dates undocumented; see below) |
| **Tana varnams** | | | | |
| Ninnukori | Mohanam, Adi | ❌ | ✅ (+ rosemuralikrishnan.com, below) | Ramnad (Poochi) Srinivasa Iyengar, d. 1919 ✅ |
| Evvari Bodhana | Abhogi, Adi | ❌ | ✅ (+ patantara.com, below) | Patnam Subramania Iyer, d. 1902 ✅ |
| Sami Ninne Kori | Shankarabharanam, Adi | ❌ (lyrics only) | ✅ (+ carnaticpedia.com, below) | Veena Kuppayyar, d. 1860 ✅ |
| Jalajaksha | Hamsadhwani, Adi | ❌ (lyrics only) | ✅ (+ NIOS book 2, below) | Manambuchavadi Venkatasubbayyar / Patnam Subramania Iyer ✅ |

**Further sources (researched 2026-11-07, issue #1).**

- **NIOS *Carnatic Music* Book 2** (National Institute of Open Schooling,
  Government of India; free PDF at
  nios.ac.in/media/documents/Carnatic_Music_243/). A complete independent
  textbook with notation for the beginner repertoire. It has **Vara Veena**
  (Mohanam, Rupaka — syllable pairs per beat with tala markers) and the
  **Jalajaksha** varnam (Hamsadhwani, Adi — full, swaras and words), each of
  which with shivkumar.org makes **two independent sources**. It also has
  **Analekara** (a third source, useful for issue #2's variant note), a
  Shankarabharanam jatiswaram and a Khamas swarajati. Its headers are sloppy
  (it prints Mohanam as S R₂ G₂ P D₂ S and Analekara's raga as "Sudha
  Dhanyasi, janya of the 28th mela"); its notation bodies are what count.
- **rosemuralikrishnan.com** (Dr. Rose Muralikrishnan's teaching site, from
  her own guide book): full **Ninnukori** notation — pallavi, anupallavi,
  muktayi swaram, charanam, four charana swarams — a second source with
  shivkumar.org.
- **patantara.com**: has **Evvari Bodhana** (Abhogi, Adi) under CC
  BY-NC-SA. Renders notation as images (not machine-readable), so it serves
  the way the book photos did: a manual second source to compare against.
- **carnaticpedia.com**: a **Sami Ninne Kori** notation page exists; the site
  renders by JavaScript and needs a manual/browser check before counting.
- **musicmaster.in**: a Kamala Sulochana page whose notation is a JavaScript
  embed (not extractable); lyrics and scale only in text.
- The Scribd uploads of these pieces (reresrirama, kamalajathala,
  Srirama-Bhairavi-Geetham …) use shivkumar.org's file naming and are
  re-uploads of its PDFs — **not independent sources**.

**Still with one clean source each:** Kamala Jadala (also issue #3's tala
question), Re Re Sri Ramachandra, Sri Ramachandra Shrita, and Kamala
Sulochana. The NIOS book's gitam chapter stops at Vara Veena; the varnam
chapter covers only Jalajaksha and a Vasanta varnam.

**Update (2026-11-07):** the user accepted shivkumar.org as the single
source for Kamala Jadala, Re Re Sri Ramachandra and Sri Ramachandra Shrita,
and all three are now in the app (gitam-6/7/8) — Kamala Jadala in Misra
Chapu (issue #3: the source's own bars mark the chapu angas 3 + 4, which is
how the app builds it; karnatik.com's Tisra Triputa remains the alternative
if the teacher says so). **Kamala Sulochana is now in the app too (gitam-9,
Anandabhairavi, Adi)**: the user supplied the "Gurukulam & SF" geetham-notes
collection (a complete gitam book with notation) plus a staff-and-sargam
rendering of the piece, which agree bar for bar (two mandra-Ni readings left
as the collection has them, for the teacher). The same collection gives
second sources for gitam-7 and gitam-8 and a third for Vara Veena, with the
differences listed in `Gitam.kt`'s header: it sets Re Re in Rupaka (the
chosen source: Tisra Triputa), Kamalajadala in Tisra Triputa an octave
higher (the chosen source: Misra Chapu, middle octave), and reads Sri
Ramachandra's both Dhaivatas as the plain D2 (the chosen source sings the
D1 variant).

**Khamas G₂/N₁ — not a misprint after all.** The NIOS book's ragalakshana
section also prints Khamas as S M₁ G₂ M₁ P D₂ N₁ S (bhashanga, anya Kakali
Ni) — the same reading as *Sangeeta Bala Padam*'s header — while
karnatik.com and shivkumar.org print the common S M₁ G₃ M₁ P D₂ N₂ S. Two
books vs two web sites: this is a genuine two-tradition difference (the
app's Sambasiva is encoded the web way). The user's teacher should say which
Khamas they sing before more Khamas pieces are encoded.

**What this means**

- **Analekara, Rara Venu and Sambasiva** have two sources (Sambasiva's second
  is now *Sangeeta Bala Padam* Part 1, not a web page).
- Everything else, **including every varnam**, rests on shivkumar.org alone.
  Its pages are Word documents with conventions that vary from page to page.
  Some write octaves by letter case, some put two speeds on one line, some
  use underlining, so each page needs its convention worked out.
- A second source is needed for those pieces. The obvious one is the
  *Sangeeta Bala Padam* series: Part 1 already confirmed Sri Gananatha (Q1).

Composers: India's copyright lasts 60 years after the composer's death.
Everyone above died before 1965. Credits go in ATTRIBUTION.md, as before.

**Sambasiva's composer (checked 2026-11-06, issue #1).** karnatik.com
identifies Chinnakrishna Dasar as A.K. Chinnakrishna Naidu, also a clarinet
exponent; his son and pupil was the clarinetist A.K.C. Natarajan (born 1931,
Sangeetha Kalanidhi 2008), who took up the clarinet after his father's
death. No birth or death dates are recorded anywhere, but that places the
death around the early-to-mid 1940s at the latest — well inside the
public-domain rule either way. (If "Dasar" the composer and the Trichy
clarinetist are two people, the composer is placed in the post-Trinity 19th
century, which is also safely public domain.)

**A candidate third web source (found 2026-11-06).** veenagana.org (Vasanthi
Iyer's veena-teaching site) has a two-page notation PDF of Sambashiva:
Khamas, Adi, credited to Chinna Krishnadasar, with beat bars, octave marks
and the words under the swaras, and the same structure as shivkumar.org
(pallavi, four charanams, the last marked khanda gati, five notes per beat).
shivkumar.org credits its own Sambasiva notes to "(Rani) www.karnatic.com",
a different transcriber, so the two are independent. Not yet checked cell by
cell, and the user decides which sources count.

**Cross-check of the two (done 2026-11-06, cell by cell against both
originals).** Structure, raga, tala, composer and 24 avartas (16 cells each;
the khanda-gati avartas 40 cells) agree: pallavi 4, charanam 1: 2, charanam
2: 4, charanam 3: 2, charanam 4: 10, khanda gati: 2. Twelve cells of 432
differ, in seven spots:

| # | Where | veenagana PDF | shivkumar.org |
|---|---|---|---|
| 1 | Pallavi av1, cells 5–6 | karvai (sA held; "mba" on N at 7) | S re-struck ("mba" on S) |
| 2 | Charanam 1 av2, cells 7–8 | P M | m g |
| 3 | Charanam 3 av2, cell 9 | M (re-strikes the held M) | p |
| 4 | Charanam 4 av2, cell 11 | P | m |
| 5 | Charanam 4 av3, cell 12 | N | s |
| 6 | Charanam 4 av4, cells 9–12 | D N D P | p d p m |
| 7 | Charanam 4 av8, cells 5–7 | N N D | R R S |

(the khanda-gati charanam and all of charanam 2 agree exactly; a few other
rows differ only in where the karvai sits, with the same pitches.)

**Arbitrated by *Sangeeta Bala Padam* Part 1 (2026-11-06).** The user
photographed the book's Sambasiva (SWARAJATI-2, pp. 82–84: Khamās, Ādi,
"Janyam of 28th Mēḷa", credited by the piece's first line, no composer
named). Re-checked all three sources cell by cell from the photos. The book
sides with shivkumar.org on every pitch difference above and on two the
first pass missed — charanam 3 av1 cell 1 (book D, shivkumar d, PDF N) and
charanam 4 av6 cell 1 (book D, shivkumar d, PDF N). Full picture:

| # | Where | Book (SBP) | shivkumar.org | veenagana PDF |
|---|---|---|---|---|
| 1a | Pallavi av1, cell 5 | Ṡ re-struck | S re-struck | karvai (sā held) |
| 1b | Pallavi av1, words | mba on N at 7 | mba on S at 5 | mba on N at 7 (matches book) |
| 2 | C1 av2, cells 7–8 | M G | m g | P M |
| 3 | C3 av1, cell 1 | D | d | N |
| 4 | C3 av2, cell 9 | P | p | M |
| 5 | C4 av2, cell 11 | M | m | P |
| 6 | C4 av3, cell 12 | Ṡ | s | N |
| 7 | C4 av4, cells 9–12 | P D P M | p d p m | D N D P |
| 8 | C4 av6, cell 1 | D | d | N |
| 9 | C4 av8, cells 5–7 | Ṙ(hold) Ṡ | R R s | N(hold) D |
| 10 | C4 av1, cell 15 | **G** | m | M |

So two independent sources (the book and shivkumar.org) agree on the pitch
sequence everywhere except **one cell** (charanam 4 av1 cell 15: book G,
both webs M/m). The veenagana PDF is the outlier at nine of the ten spots —
still useful as a third check, but not the deciding source. Per the rule
(two independent sources agree), **Sambasiva is sourced**; the one cell, the
pallavi word placement, and the notes below go to the user before encoding.

Book-specific notes:

- **Anupallavi is its own section** (pallavi 2 avartas + anupallavi 2), where
  both web pages fold it into the pallavi. Matches the app's section model.
- The raga header prints **Ārō: Ṡ M₁ G₂ M₁ P D₂ N₁ Ṡ / Ava: Ṡ N₁ D₂ P M₁ G₂ R₂ S**
  — G₂ and N₁, while both web sources say G₃/N₂, and a janyam of the 28th
  mēḷa cannot take G₂/N₁. Almost certainly a misprint; the notation body just
  writes G and N and cannot settle it. Keep the app's Khamas (G₃/N₂, with N₃
  as the variant) unless the user's teacher says otherwise.
- The body dots (above some Ṡ/Ṙ only, never on other letters, not consistent
  enough to be octave marks — e.g. c2's scalar run S S S S is undotted while
  neighbouring rows dot their Ṡ/Ṙ) could not be decoded from the photos; they
  don't change the pitch sequence. The piece reads as one octave, S to N.
  Worth asking the user what the dots mean in this book.
- Each section ends with "(sām)" — the (saamba) refrain back to the pallavi,
  as on shivkumar.org.
- Charanam 5 is the khanda-gati charanam and matches both web sources
  exactly. Stage 2's `beat:` needs a per-section override (5 cells per beat
  there), not just a piece-wide header.
- Words: the book has "vanucu" (c2), matching shivkumar.org; the PDF garbles
  it ("ni dhiya nu chu"). Book's sahityam is the cleaner of the three.

## Stage 1: more gitams (new ragas, little engine work)

**Pieces, in teaching order:**

- Vara Veena (Mohanam)
- Analekara (Suddha Saveri)
- Kamala Jadala (Kalyani)
- Re Re Sri Ramachandra (Arabhi)

**Deferred:**

- **Sri Ramachandra Shrita (Bhairavi)** and **Kamala Sulochana
  (Anandabhairavi)**: both use one letter in two forms (Bhairavi's D₁ and D₂;
  Anandabhairavi's *anya swaras*). That needs the variant notation from
  stage 2.

**What it needs**

- **Ragas:** Mohanam (S R₂ G₃ P D₂, no M or N), Suddha Saveri (S R₂ M₁ P D₂),
  Kalyani (S R₂ G₃ M₂ P D₂ N₃), and Arabhi (ascent S R₂ M₁ P D₂ S′, descent
  S′ N₃ D₂ P M₁ G₃ R₂ S).
  - The `Raga` model already allows missing letters, so these are data only.
  - Pitch is checked against the samples as before.
- **Misra Chapu** (7 beats, 3 + 2 + 2), only if the chosen source writes
  Kamala Jadala that way.
  - Chapu talas are counted differently from suladi talas.
  - Add `AngaKind.CHAPU` (from ThaaLam's model) with its own strip labels.
- **Catalog:** these continue the Gitam set (gitam-5 to gitam-8), with the
  same grammar, tests and cross-check script.

## Stage 2: swarajatis

**Pieces:** Rara Venu Gopa Bala (Bilahari) and Sambasiva Yenave (Khamas),
both in Adi tala.

**What it needs**

1. **Several swaras per beat.** Swarajatis are written two (or four) swaras
   to a beat, as separate letters.
   - A header `beat: 2` groups every two written swaras into one tala beat.
   - The schedule and the kala setting then work on top of it, as today.
   - The parser checks the counts, so a missing swara is an error.
2. **Rows by cells, not beats.** A 16-cell Adi avarta doesn't fit one phone
   row. `Tala.rowStarts` becomes a cell-based rule that wraps at an anga
   break (laghu | drutam drutam). The existing tests cover the old cases.
3. **Swara variants (anya swaras).** Bilahari sometimes takes N₂ instead of
   its N₃, and Khamas sometimes N₃ instead of its N₂.
   - A digit after a letter (`N2`) overrides the raga's default for that
     note. The raga lists which variants it allows; any other is an error.
   - The screen shows a subscript ("N₂").
   - This also unlocks the Bhairavi and Anandabhairavi gitams.
4. **Swaram, then sahityam.** Each charanam is sung in swaras, then to the
   same tune with words.
   - New grammar `[charanam 1 sahityam = charanam 1]`: a section that reuses
     another's notation, followed only by word lines.
   - It is expanded when the piece is parsed, as `(pallavi)` is today, so
     playback and sing-along follow the sung order.
5. **Mandra below lower Dha.** Rara Venu reaches lower Ni and Dha (`n. d.`).
   This is already supported, and the playable-range test covers every Sa.
6. **Set:** a new `SWARAJATI` set after GITAM.

## Stage 3: tana varnams

**Pieces, in a common teaching order:**

- Ninnukori (Mohanam)
- Evvari Bodhana (Abhogi)
- Sami Ninne Kori (Shankarabharanam)
- Jalajaksha (Hamsadhwani)

All are in Adi tala, often counted in two kalai.

**What it needs**

1. **Sections at different speeds within one piece.**
   - A varnam's first half (pallavi, anupallavi, muktayi swaram) is sung in
     first speed, then again in second. The charanam and each chitta swaram
     follow, each chitta swaram returning to the charanam.
   - Grammar: a repeat may carry a speed, `(pallavi @2)`.
   - The schedule gives each avarta its own speed factor. Tala beats are
     worked out from time rather than from the notation, so two written
     avartas at @2 fill one tala cycle.
   - This is the largest engine change. It needs its own schedule and
     renderer tests (timing at every kala, beats always whole cycles).
2. **Rests and starting off the beat.** Some passages start after the first
   beat.
   - Add a rest token `_`: silence, unlike a karvai.
   - The parser still rejects a karvai at the start.
3. **Two-kalai Adi.** Many teachers count a varnam's Adi tala with two counts
   per beat (16 per cycle).
   - A header `kalai: 2` splits each beat on the strip into its kriya and a
     second count, and the click follows.
   - ThaaLam already models kalai.
4. **Practise one section.** A varnam takes several minutes.
   - The player offers a section list (Pallavi, Anupallavi, Muktayi swaram,
     Charanam, Chitta swaram 1…) to start from, or to loop.
   - It uses the existing loop, with the section's avartas as the pass.
   - Gitams and swarajatis get it too.
5. **Range per piece.** Varnams climb to upper Ga, Ma or Pa. The harmonium
   samples end at D5, so a high Sa can't play every note.
   - Each piece gets a computed highest Sa.
   - The player says "This varnam needs Sa A♯3 or lower" and offers the
     nearest Sa, instead of failing.
   - Tests check every piece at every Sa it allows.
6. **Gamakas.** Varnams are where the gamakas are learned, and the harmonium
   plays plain notes.
   - The guide says sing-along marks are a rough guide here, and suggests
     Gentle strictness.
   - The varnam set's description says to learn the gamakas from a teacher.
7. **Set:** a new `VARNAM` set after SWARAJATI.

## Milestones

| # | Milestone | Done when |
|---|---|---|
| G1 | Mohanam, Suddha Saveri, Kalyani, Arabhi (+ Misra Chapu if needed); gitams 5–8 | jvmTest (cross-check against both sources), both apps play them, audits pass |
| S1 | Shared: `beat:` grouping, cell-based rows, swara variants, sahityam sections | Parser, schedule and renderer tests; existing pieces unchanged |
| S2 | Swarajatis in both apps; Bhairavi and Anandabhairavi gitams if sourced | Both apps play them with words; audits pass |
| V1 | Shared: per-section speed, rests, two-kalai strip, per-piece Sa ceiling | Timing tests at every kala; every piece playable at every allowed Sa |
| V2 | Practise one section (both apps) | Start or loop any section; a11y tests |
| V3 | Varnams in both apps; website guide pages | Both apps play them in two speeds; audits pass; site updated |

Each milestone ends with a commit to main, an iPad install, and the docs and
website guide updated.

## Open questions

Tracked as GitHub issues: a second source #1, Analekara's variant note #2,
Kamala Jadala's tala #3, varnam speeds #4, two-kalai counting #5, and the
varnam engine work #6.

1. **Second source (needed before G1, S2 and V3).**
   - Does *Sangeeta Bala Padam* Part 1 have Vara Veena, Analekara, Kamala
     Jadala and Re Re Sri Ramachandra, and the two swarajatis?
   - Is there a Part 2 (or another book you trust) with the varnams?
   - A photo of the contents page is enough to plan the transcription.
2. **Which varnams.** Ninnukori, Evvari Bodhana, Sami Ninne Kori and
   Jalajaksha are the usual first four. Does your teacher use a different
   order or different varnams?
3. **Kamala Jadala's tala.** The sources disagree: karnatik.com says Tisra
   Triputa, shivkumar.org says Misra Chapu. Both have 7 beats but are counted
   differently. What does your teacher count?
4. **Varnam speeds.** Should the app sing the first half in first speed then
   second, and the second half in second speed only? Or each section in both
   speeds?
5. **Two-kalai counting.** Should the varnams' Adi tala strip show 16 counts
   (two per beat), or 8 beats with the notation four swaras to a beat?
6. **Practise one section.** Should this land with the varnams (V2), or
   earlier, for the gitams too?
