# Plan: repeat this cycle

Status: **approved** (2026-11-17), with the suggested answers to the open
questions. R1 (shared) and R2 (Android) done.

## Why

While singing along to Sarali varisai the user drifts on a particular swara
and wants to go over that spot without starting the exercise again. Today the
only ways are Stop and Play (back to the start) or practising a whole section.
A teacher would say "again, from the start of the line": go over the tala
cycle until it's right, then carry on.

## Decisions (agreed with the user)

1. **One button, no tappable swaras.** The player gets **Repeat this cycle**.
   Notation cells stay as they are (tapping single swaras to hear them alone
   was option B; it can come later if this isn't enough).
2. **The unit is one tala cycle at the chosen speed**, the `CycleBlock` of
   `NotationLayout.forPractice`: what the student sees as one block of
   notation and one sweep of the tala strip. It is not the written avarta:
   at the second and third speeds a cycle holds more than one.
3. **The tala and the tanpura never stop.** Repeating and continuing happen
   on cycle boundaries, so there is no pause, no count-in and the beat stays
   in place. This is the "without breaking the loop" part.
4. **The same attempt.** Repeating doesn't start a new attempt. The
   practised count is unchanged (one per Play), but an attempt that repeated
   a cycle **doesn't count toward the best sing-along score**: like a section,
   it isn't a straight performance of the whole piece.
5. **Sing-along keeps listening** while the cycle repeats, and the marks for
   that cycle are redrawn on each pass, so you can see whether the swara is
   coming right.
6. **Everywhere the player is used:** varisais, alankarams, gitams,
   swarajatis, varnams, sections and practice runs, on Android and iOS.

## Behaviour

- **Repeat this cycle** is offered while the exercise is playing, with
  Between repeats on Continuous (or Repeat off), and in practice runs. It's
  disabled during the count-in. It isn't offered with Timed break or When
  ready, nor in listen–repeat: those modes already stop between attempts and
  play through the practice transport, which keeps each attempt's length
  fixed. (A later step can bring it there if it's missed.)
- **Which cycle.** The one being played, unless the button is pressed within
  the first beat of a cycle, in which case it's the cycle just finished
  (you usually notice the mistake as the next cycle begins), but never
  back into the previous pass, whose sing-along result is already complete.
  The chosen
  cycle's block is marked "Repeating" in the notation at once.
- **When it starts.** At the end of the cycle now playing. If the chosen
  cycle is the one playing, it simply plays again; if it's the one just
  finished, the current cycle plays to its end and then playback goes back
  one cycle. Commands only change audio not yet rendered (the transport's
  rule), so nothing already queued is cut off.
- **While repeating** the button becomes **Continue**, and the player shows
  "Repeating cycle 3 of 8" (with the pass count: "3rd time"). The highlight,
  tala strip and sing-along follow the repeated cycle as normal.
- **Continue** finishes the current pass and goes straight on into the
  following cycle; the rest of the exercise, its repeats and the
  between-repeats setting behave as before. On the last cycle, Continue ends
  the attempt as the exercise would have ended.
- **Stop** works as now, at any time.
- Changing speed, Sa, section or exercise stops the player as it does today,
  which also ends repeating.

### Practice runs

A run's step (one varisai at one kala) is extended by the repeats; the run
doesn't advance while a cycle repeats, and its remaining-time estimate grows
by the repeated cycles. After Continue the run carries on from that point in
the step. The run's saved continue point is unaffected.

## How it works

The transport and renderer already loop a whole pass seamlessly (the head
and tail voices at the seam, the drone separate from the exercise renderer:
`docs/plan-articulation-breathing.md`, stage 2). This generalises the loop
from "the whole pass" to "a window of the pass":

- **Shared, `Schedule`**: `cycleSeconds` (every tala cycle takes the same
  time, whatever the gati or a section's speed), `cycles` and `cycleAt`.
  Tested for every catalog piece at kalas 1–3: the cursor at each cycle's
  start is the first cell of that `NotationLayout` block.
- **Shared, `SampleRenderer`**: output is a list of *spans*, each playing the
  performance with one shift. A span plays the notes that start in its
  stretch whole (a legato crossfade into its first note sounds before it
  starts); notes still sounding where it ends fade out over at least 10 ms.
  `repeatCycle(cycle)` and `continueAfterRepeat()` run on the render worker
  and decide the next jump at a cycle boundary, never changing rendered
  audio. Without repeats there is one span and the output is bit-identical
  to before. `cycleToRepeat(frame)` applies the first-beat rule. Without
  looping the drone is lengthened by the repeats.
- **Shared, `CycleRepeats`**: the jumps (output frame, total shift), written
  by the worker, read from any thread: `passSeconds`, `performanceFrame`,
  `passAt` and `cycleInPassAt` map heard frames back to the exercise, so the
  highlight and tala strip work unchanged.
- **Shared, sing-along**: `SingAlongClock.forRenderer` maps heard time
  through the repeats. When time goes back within a pass, `SingAlongSession`
  judges the notes that ended at the jump, forgets what was sung from the
  repeated cycle's start, and reports `FrameOutcome.rewoundToSeconds` so the
  trace from there is dropped; marks are judged afresh and replace the old.
- **History**: the player remembers the passes with a repeated cycle and
  never records one as a best score.
- **Android**: `CycleRepeatControl` (owned by `PracticeViewModel`) sends the
  commands and keeps the repeat state in the `StateFlow`;
  the button sits with Play/Stop in the player (a new `ic_repeat_one.xml`
  vector drawable beside the existing `ic_play`/`ic_stop`, with text). Runs: `RunController` treats a repeated cycle as extra time in
  the current step.
- **iOS**: the same in `PracticeViewModel.swift` and `PlayerView`
  (`repeat.1` SF Symbol in an `HStack` with text); `RunUI` likewise. No
  Kotlin on the real-time thread: the command goes through the existing
  serial render queue.

## Accessibility

- The button has a text label, not only an icon: "Repeat this cycle" /
  "Continue". Hint: "Plays this tala cycle again until you continue."
- When repeating starts, announce "Repeating cycle 3"; on Continue, announce
  "Continuing". (Android `LiveRegion` / `announceForAccessibility`
  equivalent in Compose; iOS `AccessibilityNotification.Announcement`.)
- The "Repeating" mark on the notation block is part of the block's spoken
  label, not only a colour.
- Disabled state is exposed (TalkBack "disabled", VoiceOver "dimmed"), and
  the button keeps a 48 dp / 44 pt target at large text.
- `AccessibilityTest` and the iOS audit cover the player with the button in
  both states.

## Steps

1. **R1, shared:** cycle times in `NotationLayout`; the loop window in
   `SampleRenderer`; `repeatCycle` / `continueFromCycle` in
   `PracticeTransport`; history rule. Tests: window seams (block-size
   invariance, no clicks at the seam, articulation at the seam, rests and
   karvai at cycle edges), cursor and scoring time through repeats, the
   "within the first beat" rule, continue on the last cycle, every catalog
   piece at every kala.
2. **R2, Android:** view model, button, announcement, runs; unit and
   instrumented tests (real `AudioTrack` with the synthetic singer).
3. **R3, iOS:** view model, button, announcement, runs; unit and UI/audit
   tests.
4. **R4, docs and site:** the guide's player page (`site/pages/guide/`),
   README feature list, AGENTS.md (built features, plans list, code map).

## Open questions (with suggested answers)

1. **"Within the first beat" for the previous cycle:** one beat (count) of
   the tala, at any speed? *Suggested: yes; at slow speeds that is about a
   second, enough to react.*
2. **A limit on repeats?** *Suggested: none; it repeats until Continue or
   Stop.*
3. **Should Continue go back to the start of the repeated cycle once more,
   with a count-in, instead of carrying straight on?** *Suggested: no;
   carrying on is what "without breaking the loop" asks for.*
