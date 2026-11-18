# Plan: "How it went" for recordings

Status: **in progress** (2026-11-15). Steps 1–3 are built. Step 3, *Your
own raga*: `domain/CustomRaga.kt` (`CustomRaga`, `CustomRagas`, the checks),
`Ragas.useOwn` / `Ragas.builtIn`, `RecordingLibrary.forgetRaga`; the editor
on both apps (Android `RagaEditor.kt` + `RagaEditing`, iOS
`RagaEditorView.swift`) and the guide. Step 2:
*What are you singing?*, the swaras on screen while recording and *How it
went* on both apps, the guide). Step 1 (shared analysis):
`recording/RecordingInsights.kt` (`Scale`, `Insights`, `SwaraInsight`,
`Closeness`), `Recording.scale` and `RecordingLibrary.changeIntent`,
`Shruti.choiceNear`, and `TanpuraProfile.heard` (a tanpura switch with no
tanpura in the first seconds reads the recording as without).

Tuned on the student's arohana (Sa A♯2, 2026-11-15; its `.pitch` kept off
the repo): the fitted Sa is 30¢ below A♯2, and against it Ga +24¢, Ma −17¢,
Ni +20¢, the rest in tune, the same with *Arohana* chosen as singing freely.
Changes that took: a swara's cents are the duration-weighted **median** of
its pieces (a drift beside a held note doesn't pull it); a piece joins the
previous swara only if sung straight on (no breath of 0.15 s) **and** no
nearer the next swara (a Ri sung straight after Sa is Ri); short notes are
movement when singing freely too; steadiness measures each note's held
middle (0.1 s off each end) against the Strictness's **close** band (the
in-tune band called every real held note "wavered").

Follows [`plan-recordings.md`](plan-recordings.md) and
[`plan-recordings-tanpura.md`](plan-recordings-tanpura.md). A recording shows
the swaras heard; this adds what a student asks next: *was I in tune, which
notes were off, and did I sing what I meant to?* The student tells the app
what they're singing, before or after recording, and each recording gets a
short **How it went** section.

It came from reading the student's own arohana by hand (2026-11-15):
every note was 20–35¢ flat against A♯2, which meant a Sa 25¢ lower, not
every note off. Against that Sa the notes were within about 10¢ except Ma
(−20¢), and Ma, sung between Ga and Ma, was split into two notes because the
app didn't know Ma was meant.

## What the student decided (2026-11-15)

| Question | Decided |
|---|---|
| What can be chosen | **What are you singing?** *Free singing* (the default), or a **raga's arohana, avarohana or both**. Later steps: a **piece from the app** (varisai, alankaram, gitam), then **typed swaras**. A varisai or alankaram chosen there is the one in the raga chosen for the varisais, saved by its id (`sarali-3@kalyani`), so "How it went" compares the right swaras (docs/plan-raga-followups.md F7). |
| When | **Before** recording (remembered like the tanpura switch) **or after** (changed on the recording's screen, at once: no re-analysis). |
| Ragas | **The app's 13 ragas**, with the **recent ones on top**, plus **Your own raga** (a name, an arohana and an avarohana from the swara positions), saved on the device. No search for now; no melakarta catalogue. |
| While recording | The swaras to sing **stay on screen** as a reference (no live feedback). |
| Cents | **Numbers and words** ("Ma −20¢, a little flat"), with **precision from the Strictness setting** (Gentle ±20¢, Steady ±15¢, Precise ±10¢; one meaning across the app). |
| Not included | Scores or grades, progress over time, judging gamakas. |

## How it works for the student

**Record screen.** Under the Sa line, *What are you singing?* opens a choice:
*Free singing*, or a raga (recent first, then the app's ragas, then the
student's own, and *Add your own raga…*), then *Arohana*, *Avarohana* or
*Arohana and avarohana*. While recording, the swaras to sing are shown in a
large line, for example `S R₁ G₃ M₁ P D₁ N₃ S′`, as a reference only.

**The recording's screen**, under the swaras heard, gains **How it went**:

1. **Your Sa.** "Your notes fit a Sa 25¢ lower than A♯2 · Use this Sa".
   Offered when the best-fitting Sa differs by more than the Strictness's
   in-tune band; one tap sets the fine-tune (`changeSa`), as with the
   tanpura's Sa. Shown with *Free singing* too.
2. **Swara by swara.** With a choice: the swaras meant, each matched to what
   was sung, as a row of chips:
   `S ✓ · R₁ ✓ · G₃ +10¢ · M₁ −20¢ · P ✓ · D₁ +13¢ · N₃ ✓ · S′ ✓`
   (cents for every swara, coloured by closeness: an inline tick measured as
   clipped text in the iOS audit; *not heard* for a swara missed, and
   *extra* notes listed after). With *Free singing*: each
   swara heard, with its median cents. Measured against the fitted Sa when
   one is offered, so the numbers are about intervals, not the starting
   pitch ("against your Sa" is said so).
3. **In words.** One or two plain sentences: "Ma was a little flat (−20¢)
   and Dha a little sharp (+13¢); the rest were in tune." Words follow the
   Strictness bands: *in tune* (inside the in-tune band), *a little
   flat/sharp* (inside the close band), *flat/sharp* (beyond).
4. **Steadiness** of held notes: "Sa held steady for 2.5 s" when a held
   note's middle stayed within the Strictness's close band (90% of its
   frames); otherwise "Ri wavered (±33¢)". Only notes held at least 0.5 s; gamakas
   aren't judged.
5. **No tanpura heard**, when the tanpura switch was on but its first
   seconds were quiet: "No tanpura was heard in the first 3 seconds, so the
   recording was read without it." (The analysis then reads it as without
   the switch.)

## Analysis (`shared/`, pure Kotlin)

All from the saved pitch frames and the transcription; nothing new is
recorded, and changing the choice, Sa or Strictness updates at once.

- **`RecordingIntent`**: `Free`, or `Scale(raga, Arohana | Avarohana |
  Both)`; later `Piece(varisaiId)` and `Typed(notation)`. Expands to the
  expected swara sequence (arohana `S R₁ G₃ M₁ P D₁ N₃ S′`, avarohana
  reversed, both = up then down, sharing the top S′).
- **Matching expected to heard**: a sequence alignment (edit distance)
  between the expected swaras and the heard notes, so a missed or extra note
  doesn't throw off the rest. Notes shorter than 0.25 s next to a matched
  note of a neighbouring swara are treated as *movement* (a scoop into Sa,
  a drift after R₁), not as extra notes. A heard note within a semitone
  (±150¢) of the expected swara matches it, and its cents are measured
  from the expected swara, so Ma sung 45¢ flat is "Ma −45¢" rather than a
  sharp Ga.
- **Fitted Sa**: the cents offset that minimises the matched notes' median
  absolute error, searched over ±50¢ (the fine-tune range); with *Free
  singing*, over the heard notes against their nearest swaras. Offered as a
  (MIDI, cents) Sa choice, like `TanpuraProfile.suggestedSa`.
- **Steadiness**: per held note, the spread (median absolute deviation) of
  its frames' cents.
- **Words**: a small builder from the matched notes and the Strictness;
  the same text drives VoiceOver and TalkBack.

**Your own raga** (`CustomRaga`, step 3): a name, and for the arohana
and the avarohana one choice per swara letter: Ri *none / R₁ / R₂ / R₃*,
Ga *none / G₁ / G₂ / G₃*, Ma *none / M₁ / M₂*, Pa *none / P*, Dha
*none / D₁ / D₂ / D₃*, Ni *none / N₁ / N₂ / N₃* (Sa is always there). The
avarohana is *the same coming down* unless the student turns that off. So a
custom raga goes straight up and straight down: vakra (zigzag) scales such
as Khamas wait for typed swaras (step 5).

- **Checks** (one plain sentence shown under the choices, Save waits for
  them): a name, not one of the app's ragas or the student's others; the
  arohana and avarohana each rise in pitch letter by letter (R₃ then G₂ is
  the same note, G₁ below R₂); a letter uses the same swara both ways; no two
  letters on one note (R₂ up, G₁ down); at least five swaras counting Sa, as
  for the app's ragas.
- **Stored** on the device as `ragas.json` beside the recordings index, keys
  `own-<time created>` so a deleted raga's key is never reused. The shared
  `Ragas.byKey` finds the student's ragas too (notation still names only the
  app's), so recordings, the recent list and *What are you singing?* need no
  change.
- **Where**: the raga list gains *Your ragas* (after the app's) and *Add your
  own raga…*; with one of yours chosen, *Edit raga* opens it again. The
  editor is a full screen: name, Arohana, *Same coming down*, Avarohana
  (when different), the two read back as swaras, Save, and *Delete* (asked
  first; recordings that used it go back to default names and singing
  freely).
- Editing a raga changes how its recordings are read, at once (nothing is
  analysed again: the swara names and *How it went* come from the saved
  pitch).

`Recording` gains `intent` (serialised; default *Free singing*). The Record
screen's choice is remembered like the tanpura switch.

## Accessibility

- The chips are one element per swara: "Ma, 20 cents flat", "Ga, in tune",
  "Pa, missed"; the sentence is one element.
- The offer's button reads "Use your Sa, A♯2 minus 25 cents".
- The reference line on the Record screen is one element: "Sing: Sa, Ri,
  Ga, Ma, Pa, Dha, Ni, high Sa".
- The custom raga editor: on Android each letter's choices are radio chips
  read "Ri 2" (selected or not) and "Ri: None"; on iOS each letter is a row
  ("Ri, Ri 2") opening a checkmarked list. The arohana and avarohana are read
  back as a sentence ("Up: Sa, Ga 2, Ma 1, Dha 1, Ni 2, high Sa"). The iOS
  sheet's Cancel and Save are symbols (text bar buttons cap Dynamic Type).
- Audits and `AccessibilityTest` cover the new pieces on both apps.

## Tests

- `commonTest`: intents expand correctly (arohana, avarohana, both, a raga
  whose arohana and avarohana differ); alignment with a missed note, an
  extra note, a scoop, a flat Ma between Ga and Ma; the fitted Sa on a
  track sung 25¢ flat (finds −25¢) and on one in tune (no offer); words at
  each Strictness; steadiness on steady and wavering holds; custom raga
  validation and round trip.
- The student's arohana recordings as fixtures (their `.pitch` files, kept
  out of the repo unless the student agrees): the fitted Sa near −25¢, Ma
  reported flat.
- UI tests on both apps for the choice, the reference line and How it went.

## Website and docs

The Recordings guide gains *What are you singing?* and *How it went*, and
the settings page notes that Strictness applies to recordings too.

## Build order

1. Shared: `RecordingIntent` (Free, Scale), alignment, fitted Sa, words,
   steadiness, the no-tanpura case; tests.
2. Both apps: *What are you singing?* (raga list with recent first), the
   reference line while recording, How it went on the recording's screen.
3. *Your own raga* (editor and storage) on both apps.
4. Pieces from the app as the intent.
5. Typed swaras (in the app's notation grammar).
