# Plan: the varisais in your own raga

Status: **built** (2026-11-18), with the suggested answers to the open
questions. Notes from building: an own raga's progress also counts in a set
page's "In other ragas"; the iOS UI audit gained `longList` (element-less
Dynamic Type and clipped-text issues from rows loaded off screen in a list
taller than the screen are ignored, as for `longNotation`), and the raga
list is audited from its top.

## Why

The varisais can be sung in fifteen melakartas (`docs/plan-other-ragas.md`).
A student whose teacher uses another one (Chakravakam, Gowrimanohari,
Dharmavati …) can't follow along. *Your own raga* already exists in
Recordings (`docs/plan-recording-insights.md`, step 3): a name and the
swaras up and down, with checks. This plan lets those ragas be chosen for
the varisais too.

## Decisions (agreed with the user)

1. **Seven swaras only for the varisais.** Every varisai and alankaram is
   written with all seven letters, so only an own raga with all seven
   swaras can be chosen for them. Five- and six-swara ragas still work in
   Recordings; in the varisais' raga list they show, disabled, with the
   reason ("Needs all seven swaras for the varisais"). Nothing is invented
   for the missing swaras.
2. **One shared list.** A raga made in Recordings or from the varisais'
   raga list appears in both, with the same editor and the same saved file
   (`ragas.json`, where it is today). Editing or deleting it affects both.
3. **The editor names a melakarta.** A seven-swara raga that passes the
   checks is always one of the 72 melakartas, so the editor says which:
   "This is melakarta 16, Chakravakam", with **Use this name**. The name
   stays the student's to change.

## The checks (already in the app)

The editor offers one swara per letter (R₁, R₂ or R₃ for Ri, never two of
them), and `CustomRaga.problem` rejects:

- two swaras that are the same note (R₂ with G₁, R₃ with G₂, D₂ with N₁,
  D₃ with N₂);
- a swara below the one before it (R₃ with G₂, D₃ with N₂);
- a different swara for a letter up and down (G₂ up, G₃ down);
- fewer than five swaras, counting Sa.

These stay as they are. With all seven swaras they allow exactly the 72
melakartas, including the vivadi ones (Kanakangi's R₁ G₁, which are next to
each other on the keyboard but are different notes).

## Behaviour

- **The raga list for the varisais** (Android dialog, iOS list) gets a
  section **Your own ragas** under the fifteen: each own raga with its
  scale, the play button (hear it first) and an edit button. Seven-swara
  ragas can be chosen; the others are shown disabled with the reason. At
  the end, **Add your own raga…** opens the editor.
- **The editor** is the Recordings one. When the swaras make a melakarta it
  shows "This is melakarta 16, Chakravakam" and **Use this name**. When the
  scale is one of the fifteen already in the list it says so ("This is
  Kalyani, already in the list") and offers to choose that one instead.
- **Choosing an own raga** works like any of the fifteen: the notation,
  playback, sing-along, swara numbers, Shruti practice lines, run plans and
  progress all follow it. Ids carry its key (`sarali-3@own-1731900000000`).
- **Deleting** the raga chosen for the varisais goes back to
  Mayamalavagowla. Its practice history stays on the device but isn't shown
  (no raga to show it under).
- **Editing** an own raga's swaras keeps its key, so its varisai progress
  stays with it (open question 2).

## Design

- Shared:
  - `Melakartas`: the 72 names and `numberOf(swaras)` (the formula already
    in `OtherRagasTest`: M₂ adds 36, six per R/G pair, one per D/N pair),
    `nameOf(number)`. The fifteen built-in ragas keep their names
    (Todi, not Hanumatodi).
  - `CustomRaga.isSampurna` (all seven swaras both ways) and
    `CustomRaga.melakarta` (the number, or null).
  - `VarisaiCatalog.inRaga` for an own raga: computed and cached by key and
    swaras (the fifteen stay precomputed); `byId` resolves `@own-…` through
    `Ragas.byKey`, only for sampurna own ragas.
  - The setting's raga resolves through `Ragas.byKey`, so own keys work;
    unknown or no-longer-sampurna keys fall back to Mayamalavagowla.
- Both apps: `ragas.json` is loaded **at start-up**, before the practice
  settings resolve their raga (today it is loaded with Recordings, so an own
  raga chosen for the varisais would wrongly fall back). A small store owned
  by the app (Android: one instance shared by `PracticeViewModel` and
  `RecordingsViewModel`; iOS: the same for the two view models) loads and
  saves it and calls `Ragas.useOwn`.
- Accessibility: own rows read like the built-in ones ("Chakravakam,
  melakarta 16: Sa, Ri 1, …"); a disabled row says why; the edit button is
  labelled "Edit Chakravakam"; the mela suggestion is announced when it
  appears.

## Names of the 72

From karnatik.com's melakarta table, checked against a second source
(Wikipedia's Melakarta table, after Sambamoorthy, or *Ragas in Carnatic
Music* if the user has it), written in the app's simple Latin
("Chakravakam", "Gowrimanohari"). `numberOf` is tested against all 72
scales, and every name against both sources. Recorded in
`docs/resources.md`.

## Milestones

| | Scope | Tests |
|---|---|---|
| Y1 | Shared: `Melakartas`, `isSampurna`, own ragas in the catalog, ids and settings | commonTest: all 72 numbers and names; own raga ids, history and run keys; fallback on delete |
| Y2 | Android: start-up store, the list's own section, editor suggestion | unit + accessibility tests |
| Y3 | iOS: the same | XCTest + UI audits |
| Y4 | Guide (choosing your own raga for the varisais), Learn link, docs | site build |

## Questions (answered: the suggestions were accepted)

1. **Seven swaras both ways:** a raga with seven swaras in all but fewer
   going up (like Bilahari, whose arohana skips Ma and Ni) isn't allowed for
   the varisais, since their ascents would sing swaras the arohana leaves
   out. *Suggested: yes, both ways.*
2. **Editing a raga with varisai history:** keep the history with it
   (suggested; the editor says "Your practice in this raga stays with it"),
   or start fresh after a swara change?
3. **A scale that is one of the fifteen:** say so and offer the built-in one,
   but still allow saving your own (suggested), or don't allow the
   duplicate?
4. **Names' second source:** Wikipedia's table (suggested; the plan for the
   fifteen used it alongside karnatik.com and NIOS), or a book you have?
