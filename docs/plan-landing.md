# Plan: a more useful landing page

Status: **approved** (2026-11-06) with the defaults under "Decisions". L1–L3 done.

The landing page today is a plain list titled "Exercises", grouped by set,
with "Mayamalavagowla · Adi tala" above it. Each row shows the title and the
first avarta in small grey monospaced text. It says nothing about where the
student left off, what to practise next, what a set trains, or which Sa is in
use.

Chosen by the user: **Continue / next up**, a **Sa and tanpura card**,
**progress per exercise**, **set descriptions**, and **swara contour rows**.
Everything lands on both apps (feature parity).

## Layout (top to bottom)

```
Purandara                                  ⚙
Mayamalavagowla · Adi tala

┌ Continue ─────────────────────────────────┐
│ Sarali Varisai 5         ▁▂▃▄▁▂▃▄▅▆▇█     │
│ Yesterday · best 14/16                 ▶  │
│ Next up: Sarali Varisai 6              ›  │
└───────────────────────────────────────────┘
┌ Your Sa ──────────────────────────────────┐
│ C3 · 1 kattai                     Change  │
│ [ ▶ Tanpura ]   tune your voice first     │
└───────────────────────────────────────────┘

SARALI VARISAI                      5 of 16 practised
A short figure set against the plain ascent and descent:
steady pitch, even timing.
  Sarali Varisai 1   ▁▂▃▄▅▆▇█▇▆▅▄▃▂▁    ✓ 15/16
  Sarali Varisai 2   ▁▂▁▂▁▂▃▄▅▆▇█▇▆     ✓
  Sarali Varisai 3   ▁▂▃▁▂▃▁▂▃▄▅▆▇█
  …
```

## Pieces

### 1. Practice history (shared + platform storage)

- `PracticeRecord(varisaiId, lastPracticedMillis, timesPracticed, bestInTuneOrClose, bestJudged)`.
- **Practised** = at least one full pass heard to the end (with or without
  sing-along). Opening an exercise, or stopping in the count-in, doesn't count.
- **Best** comes only from sing-along pass summaries: the highest
  in-tune-or-close ratio among passes where at least half the notes were judged
  (so a pass that heard little can't become "best").
- `PracticeHistory` (commonMain, pure logic, unit tested): record a pass,
  `continueWith()` (most recently practised), `nextUp()` (the exercise after
  the last practised one in teaching order; none after the final exercise),
  `practisedCount(set)`.
- Storage: one small JSON blob per platform (SharedPreferences / UserDefaults),
  encoded and decoded in shared code. On the device only, no network.

### 2. Continue / next up card

- Shown once anything has been practised. Before that, a first-run variant:
  "Start with the Plain scale" leading to the first exercise.
- Row 1 opens the last exercise; row 2 opens the next one.
- Relative day ("Today", "Yesterday", "3 days ago"), and the best score if any.

### 3. Sa and tanpura card

- Shows the current Sa (`C3 · 1 kattai`); **Change** opens the existing Sa
  choice list, the same setting as the player's.
- **Tanpura** plays the drone alone at the current Sa and drone volume, looping
  until stopped (a drone-only render: the existing tanpura loop with no
  schedule). It stops when an exercise starts or the app leaves the screen.
  Purpose: settle the voice on Sa before practising.

### 4. Set descriptions (shared text, from the workbook)

| Set | Line |
|---|---|
| Plain scale | The seven swaras up and down: settle each one against the tanpura. |
| Sarali Varisai | A short figure set against the plain ascent and descent: steady pitch, even timing. |
| Madhya Sthayi Varisai | Longer figures across the octave: later, teacher-guided studies. |
| Janta Varisai | Each swara sung as a pair: clean, even repeated notes. |
| Vakra Janta Varisai | Paired notes in zigzags: find the next note without sliding. |
| Dhattu Varisai | Leaps over a note and back: reach a distant pitch without sliding. |

Plus "n of m practised" beside each set heading.

### 5. Swara contour rows

- Shared `Contour.of(varisai)`: the exercise's pitch shape as a list of steps
  (scale degree with octave, one per akshara; karvai extends the previous step).
  It covers the whole exercise (all avartas), so different exercises look
  different. Unit tested.
- Drawn as a small line in the accent colour, about 40 pt high, the width of
  the row's free space. Purely visual: screen readers keep reading the title,
  the first avarta's notation (as now), and the progress ("practised, best 15
  of 16").
- The grey notation text goes away visually. VoiceOver/TalkBack still read it,
  so the existing "Sarali Varisai 4, S R G M P , S R" label stays.

## Accessibility

- Cards are headings plus buttons with clear labels ("Continue Sarali Varisai
  5", "Play tanpura", "Change Sa, currently C3, 1 kattai").
- Progress ticks are SF Symbols / Material icons with labels, not ✓ characters.
- At accessibility text sizes the contour hides and the cards stack, as the
  pitch lane does.
- Android `AccessibilityTest` and iOS `performAccessibilityAudit()` cover the
  new screen; `-resetSettings` also clears the history so UI tests start fresh.

## Milestones

| | Scope | Tests |
|---|---|---|
| L1 ✅ | Shared: `PracticeHistory` + JSON codec, `Contour`, set descriptions, drone-only render | commonTest |
| L2 ✅ | Android: landing page, history recording from the player, tanpura card | unit + `AccessibilityTest` |
| L3 ✅ | iOS: the same | XCTest + UI audits |

## Decisions (approved defaults)

1. **"Practised"** = a full pass heard to the end (not just opened).
2. **"Best"** only from sing-along, among passes that judged at least half the notes.
3. **Next up** = the exercise after the last one practised, in teaching order.
4. **Tanpura button** loops until stopped, at the current Sa and drone volume.
5. **Contour** covers the whole exercise; the notation text stays for screen readers only.
6. Set description wording above.
