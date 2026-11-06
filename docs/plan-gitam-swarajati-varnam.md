# Plan: More gitams, then swarajatis, then varnams

Status: **in progress** (2026-11-06). Building what two sources allow, and
the engine work, while Q1 (a second source for the rest) is open.

Progress:
- G1 (part): Analekara (Suddha Saveri), karnatik.com and shivkumar.org.
  One note differs between them (the first "ta - la di - nam": `P , M` on
  karnatik.com, kept; `P , P` on shivkumar.org); see `Gitam.kt`.
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
  shivkumar.org cell for cell (all 10 avartas). **Sambasiva waits**:
  karnatik.com's notation has no bars or octave marks, so shivkumar.org is
  its only clean source.

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
| Sambasiva Yenave | Khamas, Adi | ✅ notation (other layout) | ✅ | Chinnakrishna Dasar (dates to check) |
| **Tana varnams** | | | | |
| Ninnukori | Mohanam, Adi | ❌ | ✅ | Ramnad (Poochi) Srinivasa Iyengar, d. 1919 ✅ |
| Evvari Bodhana | Abhogi, Adi | ❌ | ✅ | Patnam Subramania Iyer, d. 1902 ✅ |
| Sami Ninne Kori | Shankarabharanam, Adi | ❌ (lyrics only) | ✅ | Veena Kuppayyar, d. 1860 ✅ |
| Jalajaksha | Hamsadhwani, Adi | ❌ (lyrics only) | ✅ | Manambuchavadi Venkatasubbayyar / Patnam Subramania Iyer ✅ |

**What this means**

- Only **Analekara, Rara Venu and Sambasiva** have two web sources today.
- Everything else, **including every varnam**, rests on shivkumar.org alone.
  Its pages are Word documents with conventions that vary from page to page.
  Some write octaves by letter case, some put two speeds on one line, some
  use underlining, so each page needs its convention worked out.
- A second source is needed for those pieces. The obvious one is the
  *Sangeeta Bala Padam* series: Part 1 already confirmed Sri Gananatha (Q1).

Composers: India's copyright lasts 60 years after the composer's death.
Everyone above died before 1965, except where marked "to check". Credits go
in ATTRIBUTION.md, as before.

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
