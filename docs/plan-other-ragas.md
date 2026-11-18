# Plan: varisais in other ragas

Status: **approved** (2026-11-18), with the suggested answers to the open
questions. O1–O4 done (shared, Android, iOS, docs and site).

## Why

The varisais and alankarams train the voice on one scale, Mayamalavagowla.
After the varisais, many teachers have students sing the same exercises in
other melakartas to train the ear on different swaras: the app's Learn page
already says so (`site/pages/learn/mayamalavagowla.html`). Today the app
can't do this, because every graded exercise is fixed to
`VarisaiCatalog.raga`.

## Decisions (agreed with the user)

1. **A short list, not all 72.** Seven melakartas, chosen because they are
   common and none is vivadi (no R3 next to G1, D3 next to N1, or other
   swaras that sound the same note):

   | Mela | Raga | R | G | M | D | N | Note |
   |---|---|---|---|---|---|---|---|
   | 15 | Mayamalavagowla | R1 | G3 | M1 | D1 | N3 | the default, as today |
   | 20 | Natabhairavi | R2 | G2 | M1 | D1 | N2 | new |
   | 21 | Keeravani | R2 | G2 | M1 | D1 | N3 | new |
   | 22 | Kharaharapriya | R2 | G2 | M1 | D2 | N2 | new |
   | 28 | Harikambhoji | R2 | G3 | M1 | D2 | N2 | new |
   | 29 | Shankarabharanam | R2 | G3 | M1 | D2 | N3 | already in the app (varnam) |
   | 65 | Kalyani | R2 | G3 | M2 | D2 | N3 | already in the app (gitam) |

   No new swara positions or tuning ratios are needed: every swara above
   already has its `Swara.ratio` (`docs/tuning.md`).
2. **Varisais and alankarams**, meaning every set written in Mayamalavagowla
   today: Plain scale, Sarali, Madhya Sthayi, Janta, Vakra Janta, Dhattu and
   Alankaram. Gitams, swarajatis and varnams always stay in their own ragas.
3. **One choice for the whole app**, set beside the Sa on Home and kept
   until it's changed, the same way the Sa works.
4. **Practice history is kept per raga.** Sarali 3 in Kalyani has its own
   practised mark, best sing-along score and run continue point. Home's
   Continue, Next up and set progress show the current raga.

## Added later: eight more (2026-11-18)

At the user's request: Todi (8), Charukesi (26), Pantuvarali (51),
Shanmukhapriya (56), Simhendramadhyamam (57), Hemavati (58), Latangi (63)
and Vachaspati (64), none vivadi, fifteen in all. Concert names as for
Shankarabharanam and Kalyani (the melakarta list's Hanumatodi and
Kamavardhini are noted in the code and on the Learn page). Sources in
`docs/resources.md`. The list keeps Mayamalavagowla first, then melakarta
order; the recordings' raga list grows to 25.

## Behaviour

- **Choosing.** On Android, Home's Your Sa card gets a **Raga for the
  varisais** dropdown under the Sa. On iOS it is its own section on Home just
  below Your Sa (in the Sa section, the row pushed Your Sa's controls off the
  first screen at the largest text sizes and failed the Dynamic Type audit).
  It opens a list of the seven ragas, Mayamalavagowla first ("the usual raga for the
  varisais"), then the others by mela number. Each row shows the arohanam
  (`S R₂ G₂ M₁ P D₁ N₂ S′`), and its accessibility label reads the swaras
  out in full (`CustomRaga.spokenLine`).
- **What changes.** Every varisai and alankaram plays, displays, scores and
  checks its Sa range in the chosen raga. The notation letters stay the same
  (S R G M P D N), and the player's raga line names the chosen raga. Set
  pages and the exercise list show the raga in their header, so it's clear
  which scale is about to play.
- **Gitams, swarajatis and varnams** are not affected. Their set pages keep
  showing each piece's own raga.
- **Sa range.** `playableSaMidis` is worked out per piece and raga, because
  N3 against N2 can move the top note by a semitone. Where the current Sa
  doesn't fit, the player offers the nearest one, as it does today.
- **History.** Records for Mayamalavagowla keep their current ids
  (`sarali-3`), so existing history is unaffected. Other ragas use
  `sarali-3@kalyani`. Runs keep their continue point per set and raga.

## Design

- `Ragas.melakartas`: the seven ragas in the table, in display order. The
  four new ragas are added to `Raga` and `Ragas.all`, which also adds them to
  the recordings' "What are you singing?" list (17 ragas instead of 13).
- `Varisai.inRaga(raga)`: the same piece with another raga. The catalog's
  varisais and alankarams use no variant digits (`D1`), so every letter maps
  through the raga. A test checks this stays true for all seven ragas.
- `Varisai.id` carries the raga (`@<raga key>`) when it isn't the default,
  so `PracticeHistory`, `VarisaiCatalog.byId` and the apps' routes follow it
  without change. `PracticeHistory.continueWith` only considers the catalog
  it's given (the current raga's). `RunPlans` keys a plan by
  `RunPlans.keyFor(set, raga)` (`SARALI@kalyani`), taken from the catalog.
- The setting is stored with the other practice settings on each platform
  (default `mayamalavagowla`). An unknown key falls back to the default.
- `VarisaiCatalog.inRaga(raga)` (or a parameter to `inSet`) is what the set
  pages, Home and runs read from.

## Checking the scales

The scales follow AGENTS.md's two-source rule. Each raga's swaras are
checked against karnatik.com's melakarta list
(<https://www.karnatik.com/melas.shtml>) and a second source, Wikipedia's
Melakarta table (after Sambamoorthy), and recorded in `docs/resources.md`.
Both agreed for all seven (2026-11-18). The follow-up check
(`docs/plan-raga-followups.md` F4) found independent second sources for all
seven: the NIOS books for five, Raga Surabhi for Natabhairavi and Keeravani;
details in `docs/resources.md`. A test also works out each raga's mela number from its
swaras (M2 adds 36, plus 6 × the R/G pair index plus the D/N pair index plus
1) and compares it with the table, which catches a mistyped swara.

## Milestones

| | Scope | Tests |
|---|---|---|
| O1 | Shared: the four new ragas, `Ragas.melakartas`, `Varisai.inRaga`, `practiceKey` in history and runs, the catalog in a raga | commonTest: mela numbers, every varisai and alankaram parses and fits a Sa range in each raga, history keys (old ids unchanged) |
| O2 | Android: the Raga row and list, the setting, set pages, player and runs following it | unit + `AccessibilityTest` (the list's labels, 48dp rows) |
| O3 | iOS: the same | XCTest + UI audit of the raga list |
| O4 | Docs and site: the guide (choosing a raga for the varisais), a line on the Learn Mayamalavagowla page linking to it, README, AGENTS.md, `docs/resources.md` | site build |

## Answered questions (the suggestions were accepted)

1. **Shruti practice's swara lines** use `VarisaiCatalog.raga` today.
   Should they follow the chosen raga? *Suggested: yes*, so holding Ga in
   Kharaharapriya shows G2.
2. **The player's settings sheet:** should it show the raga as text only,
   with the change made on Home? *Suggested: text only*, so there is one
   place to change it.
3. **Learn page:** is a short page on the other six ragas (their step
   patterns, like the Mayamalavagowla page) wanted now, or later?
   `docs/plan-learn.md` leaves other ragas' theory out of scope.
   *Suggested: later.*
