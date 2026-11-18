# Plan: follow-ups to the varisais in other ragas

Status: **approved** (2026-11-18), with the suggested answers to the open
questions (F6 after F1–F3). All done (F1–F7). Notes from building: the
Android picker became a dialog; F3 lists only the other ragas, since the
page already shows the current one; with swara numbers on, each swara reads
apart ("S R2 G2 M1"); on iOS a lone lower-octave swara now shows its number
too (it was hidden before, unlike Android), with the dot under the letter
and number together. After the first iOS CI run, the iOS Home raga row became
one line (the explanation moved to its hint) to keep Home's first screen
short at large text, and the recordings test scrolls to Mayamalavagowla in
the longer raga list.
Follows `docs/plan-other-ragas.md` (built). Seven ideas, in the order
suggested for building; each lands on both apps, with the guide kept in step.

| | Idea | Size |
|---|---|---|
| F1 | Hear a raga before choosing it | medium |
| F2 | Show the swara variants in the notation | medium |
| F3 | Progress in each raga on a set's page | small |
| F4 | Check the seven scales against NIOS | small (no code) |
| F5 | Retake the guide's Home screenshot | small (site only) |
| F6 | Learn page: the other six ragas | medium (site only) |
| F7 | Recordings: a piece from the app, in its raga | later, with recording insights step 4 |

## F1. Hear a raga before choosing it

**Why.** The raga list shows each scale, but a beginner choosing between
Kharaharapriya and Natabhairavi learns more from hearing the two.

**Behaviour.**
- Each row of the raga list gets a **Play** button (a speaker icon) beside
  the name. It plays the raga's arohanam and avarohanam once, at your Sa and
  tuning, with the tanpura under it: the plain scale in that raga.
- Tapping it again, playing another raga, choosing a raga or leaving the
  list stops it. Choosing a raga is still a tap on the row itself.
- If the home tanpura is playing it stops first (one sound at a time, as
  elsewhere in the app).

**Design.**
- Nothing new in shared: the preview is `VarisaiCatalog.byId("plain-scale-1")`
  `.inRaga(raga)` rendered by the existing `SampleRenderer` with a
  `Schedule` at a fixed tempo (suggested: 90 bpm, first kala, no count-in,
  no click) and the drone. Both view models already render a drone-only
  schedule for the tanpura; this is the same path with notes.
- Android: the dropdown can't hold a second button per item well, so the
  picker becomes a dialog (or bottom sheet) list, with a row to choose and a
  play button to listen. iOS: a button in each `VarisaiRagaList` row,
  with its own `.buttonStyle(.borderless)` (buttons sharing a list row need
  their own style; AGENTS.md).
- Accessibility: the button is labelled "Play Kalyani" / "Stop Kalyani"
  and its state is announced; 48 dp / 44 pt targets; nothing moves, so
  reduced motion is not affected.

## F2. Show the swara variants in the notation

**Why.** In another raga the notation still reads `S R G M`, so nothing on
screen says that G is now G₂. Showing the subscript makes the change
visible while singing.

**Behaviour.**
- A setting, **Swara numbers in the notation**: *Off*, *In other ragas*
  (default), *Always*. With it on, the notation shows `R₂ G₂` where it now
  shows `R G`; S and P stay plain, octave marks stay as they are.
- It applies to the varisais and alankarams in any raga and, with
  *Always*, to every piece (gitams included).
- Screen readers: with it on, the avarta sentence reads the numbers too
  ("Avarta 1: Sa, Ri 2, Ga 2, Ma 1"). Open question 3.

**Design.**
- Shared: one function giving a notation cell's text with or without the
  number (`Display.swaraLabel` already makes `R₂`), and the matching
  speech in `NotationLayout`. Tests for both.
- Width: a subscript makes a cell wider. The notation cells already shrink
  to fit their column (by design; the audit ignores their Dynamic Type
  warnings), so the layout doesn't change, but at the third and fourth
  speeds the swaras get smaller. Check by screenshot at every speed on a
  small phone (iPhone 17e) and the iPad mini, and at the largest text.
- iOS: the lone lower-octave swara's dot is drawn as a shape (AGENTS.md);
  check that a subscript and that dot sit together (`Ḍ₁`).

## F3. Progress in each raga on a set's page

**Why.** The history is kept per raga, but a set's page shows only the
current raga's progress, so work in another raga seems to disappear.

**Behaviour.** The page already shows the current raga's progress ("3 of
16 practised"); under it, one line for the other ragas with any practice:
"In other ragas: Mayamalavagowla 16 of 16 · Kalyani 3 of 16". Nothing is
shown when no other raga has progress. Home's set rows stay as they are (the
current raga only), to keep the first screen short.

**Design.** Shared: `PracticeHistory.practisedInOtherRagas(set, current)` → raga and count,
from the ids (`sarali-3@kalyani`); tests. Each app adds one text line.

## F4. Check the seven scales against NIOS

**Why.** The scales were checked against karnatik.com and Wikipedia's
Melakarta table. The site's rule treats Wikipedia as vocabulary only; the
NIOS textbook is the usual independent second source.

**Steps.** Download NIOS Carnatic Music Book 1 (about 23 MB), extract the
text (`pdftotext -layout`), and compare its melakarta chart (or the
ragalakshanas of these seven) with `OtherRagasTest`. Record the result in
`docs/resources.md` and `docs/plan-other-ragas.md`. If NIOS has no chart,
use *Ragas in Carnatic Music* or *South Indian Music* if the user has a copy,
or say which source remains.

## F5. Retake the guide's Home screenshot

`site/static/images/app-landing.jpg` predates the iOS *Raga for the
varisais* section, and `app-set.jpg` predates the "In raga
Mayamalavagowla" line on a varisai set's page. Retake both from the
iPhone 17 Pro simulator (`-resetSettings YES`; `-open set-SARALI` for the
set page), 600 px JPEG, and update their alt text.

## F6. Learn page: the other six ragas

**Why.** The guide lists the seven scales; Learn explains Mayamalavagowla
only. A short page helps a beginner hear what changes.

**Content.** `learn/other-ragas.html`, after `mayamalavagowla.html`: what a
melakarta is (link back), then for each of the six its swaras, its step
pattern in semitones and the keyboard figure (as on the Mayamalavagowla
page), and one line on how it differs from Mayamalavagowla ("Ga and Ni a
semitone lower"). No claims about mood or use without two sources (NIOS and
karnatik.com; plan-learn's rule). Ends with *Practise this in Purandara*
(the guide section and F1's preview) and *Further reading*. Add it to
`LEARN` in `site/build.py`, Start here's list and the glossary.

## F7. Recordings: a piece from the app, in its raga

When recording insights step 4 ("a piece from the app" in *What are you
singing?*, `docs/plan-recording-insights.md`) is planned, a varisai or
alankaram chosen there should be the one in the current raga, saved by its
id (`sarali-3@kalyani`), so "How it went" compares the right swaras. Nothing
to build now; a note in that plan.

## Milestones

| | Scope | Tests |
|---|---|---|
| F4 | NIOS check; docs | — |
| F3 | Shared count + one line on each set page | commonTest; Android `AccessibilityTest`, iOS audit of the set page |
| F1 | Preview playback; Android dialog picker; iOS row buttons; guide | commonTest for the preview schedule; Android and iOS accessibility tests of the list |
| F2 | The setting, cell text and speech; both apps; guide (settings, accessibility) | commonTest; screenshots at every speed; UI audits |
| F5, F6 | Site | site build, search check, screenshots |
| F7 | A note in `docs/plan-recording-insights.md` | — |

## Questions (answered: the suggestions were accepted)

1. **F1 tempo and length:** the preview at 90 bpm, up and down once
   (about 11 s)? Or the player's own tempo?
2. **F1 on Android:** a dialog list (suggested), or a full screen like the
   iOS list?
3. **F2 speech:** read the numbers ("Ri 2") when they are shown
   (suggested), or keep the speech as letters?
4. **F2 default:** *In other ragas* (suggested), *Off*, or *Always*?
5. **F3 on Home:** keep Home's rows to the current raga (suggested), or
   show the other ragas there too?
6. **F6:** is a Learn page on these ragas wanted now, or after F1–F3?
