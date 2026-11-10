# Plan: a Learn section on the website

## Context

The website's guide (`site/pages/guide/`) explains the app. Learners also
need the music itself: what Sa and shruti are, what the swaras and their
variants are, how Adi tala is counted, how the speeds work, how to practise
singing in tune, and what each varisai set trains. Most beginner material
online is scattered, ad-heavy or assumes a teacher in the room.

The owner's personal Mayamalavagowla workbook
(`~/baijum/mayamalavagowla/notes.md`, docs/resources.md 2.10) already covers
much of this for one raga. It is the starting outline for the section, not
a text to publish.

What the user decided (2026-11-10):
1. **A separate Learn section**, beside the app guide, not inside it.
2. **No day-by-day plan** (the workbook's 14-day progression stays out).
3. **Nothing quoted from the workbook.** Every page is written fresh in the
   site's voice; the workbook only suggests topics and order. Personal
   material (the keyboard model, the owner's range, baseline recordings,
   trackers, worksheets) and the workbook's notation are left out.

Outcome: a beginner can read the Learn section in order, understand what
they are practising and why, and go from each page to the part of the app
that practises it.

This plan was agreed and built on 2026-11-10 (stages L1–L5). Built as planned, except:

- The search page's old address (`guide/search.html`) forwards with a small
  script and a meta refresh, keeping the query.
- *Practise this* and *Further reading* sections are left out of the search
  index, so link lists don't outrank real sections.
- Start here gained an *In this section* list of the pages in reading order.
- Wide tables (kattai, the key chart, the Adi tala hand) scroll sideways in a
  labelled, focusable region on phones.
- The guide's "Building up speed" moved to `learn/speeds.html` and lost two
  unsourced claims ("traditionally three speeds", typical teacher tempos);
  the guide keeps a two-line pointer with the same anchor.
- Kattai has one outside source (karnatik glossary: 5 = G); the full mapping
  is the app's, confirmed by the owner (2026-11-11).
- `build.py` now fails on any broken internal link or anchor.
- VoiceOver on Safari reads the swara symbols (R₁, S′, Ṇ) acceptably
  (checked by the owner, 2026-11-11), so no spoken forms were added.

## Tanpura follow-up (2026-11-11, issue #33)

The drone section now spells out the app recording's string octaves:
lower Pa, Sa, Sa, lower Sa. Sangtar's tuning instructions and Toss Levy's
frequency table independently support that layout in Hindustani practice;
the page explicitly notes the conflicting NIOS and karnatik descriptions.
Neither is declared an error, and the layout is not presented as a
universal Carnatic rule. Exact sources and their limits are recorded in
`resources.md` §5.1.

Ma/Ni first-string alternatives are corroborated for Hindustani practice,
not yet for a Carnatic teaching claim, so they stay in the research notes.
The Carnatic-specific octave disagreement remains unresolved; jivari and
plucking rhythm were not part of this change.

## Pages

In reading order (`site/pages/learn/`). Each page ends with **Practise this
in Purandara** (links into the app guide) and **Further reading** (the
sources it was checked against that a learner can open).

| # | File | Title | Covers |
|---|---|---|---|
| 1 | `index.html` | Start here | What practice involves: drone, swaras, tala, the graded lessons (varisai → alankaram → gitam → swarajati → varnam); why a teacher still matters (gamakas, phrasing); how to read this section |
| 2 | `shruti.html` | Shruti and Sa | Sa is movable; the drone (pa SA SA sa) as home; choosing a comfortable Sa and octave; kattai numbers; Sa–Pa–Sa′ as the first exercise; changing Sa keeps every relationship (a short chart for keyboard players: the Mayamalavagowla scale from five tonics) |
| 3 | `swaras.html` | The seven swaras | Sa Ri Ga Ma Pa Dha Ni; Sa and Pa fixed, the others with variants; the twelve positions and their sixteen names (R₁ R₂/G₁ …), shown on a one-octave keyboard figure; the three octaves (mandra, madhya, tara) and how the app marks them (Ṣ, S, S′); arohanam and avarohanam |
| 4 | `mayamalavagowla.html` | Mayamalavagowla, the first raga | Its scale and variants; melakarta 15; the step pattern 1-3-1-2-1-3-1 semitones (close Ri and Dha, two wide steps); hearing the wide steps; why the plain scale is only a skeleton (gamakas, phrases); just vs keyboard tuning (links to the Tuning page) |
| 5 | `tala.html` | Adi tala | Akshara, avarta; angas (laghu, drutam); the hand: clap, finger counts, clap-wave, clap-wave (x 1 2 3 x v x v); counting before singing; 4 + 2 + 2, not 4/4; a note that other talas are built from the same angas |
| 6 | `speeds.html` | The speeds (kalas) | Same beat, more swaras: 1, 2, 4 (and 8) to a beat; traditionally three speeds, the fourth for later; how to step up; what to listen for (even swaras, notes between hand movements, relaxed upper Sa); the swaras-per-second table and the gradual path now in the guide's "Building up speed" |
| 7 | `singing-in-tune.html` | Singing in tune | Listen, echo, check, retry; using a pitch display without chasing every wobble (wait for the note to settle, check the octave, gamakas move by design); when the voice slides (smaller steps, listen longer); voice care (stop for strain, rest when hoarse); a 15-minute and a 5-minute session shape; a short troubleshooting table |
| 8 | `varisais.html` | The varisai sets | What each set trains and in what order: Sarali (how the figures vary against the plain scale), Janta (paired swaras; no pushing air), Vakra Janta (zig-zags), Dhattu (leaps without sliding), Madhya Sthayi (the middle register), then alankarams. Numbering follows the app's catalog. No notation (the app has it) |
| 9 | `glossary.html` | Words used here | Akshara, anga, arohanam, avarohanam, avarta, drutam, gamaka, janya, kala, karvai, kattai, laghu, melakarta, raga, shruti, sthayi, swara, tala, varisai … each one line, linking to its page |

Not in scope for now: other ragas' theory pages, the 72 melakartas in full,
gamaka technique, audio examples on the site, a day-by-day plan.

## Writing and checking

- **Voice:** the guide's: plain words for musicians, Carnatic terms
  explained on first use, short paragraphs, tables for numbers, what to do
  and then why. Defer to the teacher where practice differs.
- **Every factual claim is checked against two independent sources** from
  docs/resources.md (the notation rule, applied to theory): karnatik.com's
  guided tour, glossary and tala table; Shivkumar's basics; the NIOS
  textbook; Sambamoorthy's *South Indian Music*; Wikipedia only for
  vocabulary. Where sources differ (e.g. what counts as "first" or how many
  speeds), say so on the page rather than picking silently. Shivkumar's
  basics credit karnatik/Ravikiran material, so they do not count as
  independent of karnatik.com (resources.md, Articulation and breathing).
- **Facts about the app come from the code:** ranges and defaults
  (`Schedule.TEMPO_RANGE`, `Shruti`), swara ratios (`Swara.ratio`),
  octave marks (`Display.kt`), the varisai sets and their numbering
  (`catalog/`).
- **Frequencies and tuning:** keyboard (equal-tempered) values are fine as
  a map, but the page says the app's tanpura tuning places some swaras a
  few cents away and links to the Tuning page. `site/pages/guide/tuning.html`
  stays the one place for ratios.
- The workbook is not cited or named on the public site (it is private).
  Its role is recorded here and in resources.md 2.10.

## Site changes (`site/build.py`, templates)

- A `LEARN` list beside `GUIDE` (file, sidebar label, reading order); a
  generic section loop builds both, each with its own sidebar title and
  previous/next links.
- Masthead navigation: **Learn**, **Guide**, Get the app, Source code. The
  landing page gets a short Learn panel linking to *Start here*.
- **Search covers both sections.** The search page moves to the site root
  (`search.html`; the old `guide/search.html` becomes a one-line redirect
  page, since it has been live briefly). Results show their section ("Learn
  › Adi tala › The hand"). The sidebar box appears in both sections.
- The guide's "Building up speed" section moves to `learn/speeds.html`;
  the player guide and settings keep one sentence linking to it (the
  `#building-speed` anchor stays on the guide page as a short pointer).
- The one-octave keyboard figure is an inline SVG written by hand in
  `swaras.html` (and reused, with Mayamalavagowla's keys marked, in
  `mayamalavagowla.html`), with a text alternative that lists the keys.

## Accessibility

- As for the guide: one `h1`, headings in order, `th scope` on tables,
  labelled controls, no colour-only meaning.
- **How screen readers read swara symbols** (R₁, S′, Ṣ, G₃): check with
  VoiceOver on Safari before stage L2 is merged. If they read badly
  ("R subscript one"), wrap them so they are read as "Ri one", "upper Sa"
  (e.g. `<span aria-label>` on an `abbr`-style element), and use the same
  wording as the app's spoken notation (`accessibility.html`).
- The keyboard SVG has `role="img"` and an accessible name, plus the same
  information in a table next to it.

## Stages

| Stage | What | Checks |
|---|---|---|
| L1 | Site structure: `LEARN`, sections in the sidebar and masthead, search across both and moved to the root, Start here page | build; search finds Learn pages; old search URL redirects; screenshots desktop and narrow |
| L2 | Shruti and Sa, The seven swaras (with the keyboard figure), Mayamalavagowla | two sources per claim (listed in the commit message); VoiceOver reading of swara symbols |
| L3 | Adi tala, The speeds (move "Building up speed") | links from the guide still work |
| L4 | Singing in tune, The varisai sets, Glossary | glossary links resolve; varisai numbering matches the catalog |
| L5 | AGENTS.md (status, site row), README docs index, the `site` skill (Learn pages), resources.md 2.10 (the workbook as outline for Learn) | build |

The user reviews each stage's pages before the next stage starts.
