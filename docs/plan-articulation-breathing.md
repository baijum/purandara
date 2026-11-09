# Plan: articulation, recovery and phrase practice

Status: **implemented for review, 2026-11-10**. The user approved all four
ideas in stages, with the tanpura continuing during breaks. Articulation,
recovery and listen–repeat code is implemented on both platforms; final
integration verification is in progress. Whole-avarta reviewed-phrase
infrastructure is implemented, but the catalog intentionally contains no
approved entries: teacher review is still needed before any boundaries are
released. Sub-avarta phrasing is deferred. Actual listening and physical
microphone/route acceptance remain open. The user authorized opening a PR
with these verification limits recorded.

Verification so far: **348 shared tests, 32 Android unit tests and 47 Android
instrumented tests pass**, including real AudioTrack output with synthetic
microphone input. Android assemble, lint, detekt and the 16-page site build
pass. iOS builds; final exact-source verification passed **48 unit tests and
3 targeted UI/audit tests** (settings, listen–repeat, timed recovery). An
earlier full run passed 46 unit tests and 20 of 21 UI tests; its sole failure
was settings-test navigation with Repeat off, corrected and passed in the
final recheck. All 21 UI scenarios therefore have passing evidence across
runs, not one final green full-suite run. Shorter named settings pages replace
the long iOS Form that failed the scrolled Dynamic Type audit, and a short
scrollable Rest screen avoids crowded recovery controls. Audits ran on iOS
27.2 with all categories and filters unchanged.
Actual harmonium listening, real microphone route checks and teacher
acceptance have not been performed.

## Why

The harmonium currently uses a 30 ms legato crossfade. A small optional gap
may help distinguish successive notes, especially repeated swaras. That is
an articulation preference, **not a breathing pause or a model of spuritham**.

Breathing support is different: learners need control over recovery between
attempts and, later, manageable teacher-reviewed practice units. Continuous
accompaniment does not require completing an exercise on one breath. Do not
infer breaths from notes, printed lines, barlines or lyric syllables.

The research in [resources.md](resources.md#articulation-and-breathing)
supports these distinctions, but establishes neither optimal gap milliseconds
nor universal recovery seconds. Numerical presets below are product choices
to validate, not Carnatic prescriptions.

## Defaults and invariants

- Everything works offline, without new SDKs, tracking or network services.
- Each stage lands on Android and iOS, with accessibility and guide updates.
- Existing defaults remain: connected harmonium, one-shot playback and seamless
  continuous repeat when the existing repeat switch is on.
- Note separation never changes note onsets, scoring targets, cursor timing,
  tempo, tala, notation or performance duration.
- Karvai is sustained sound; explicit rests retain their written timing.
- No one-breath completion requirement or enforced endurance target.
- No new recordings or notation transcriptions are needed initially. Credit any
  later bundled source material in ATTRIBUTION.md in the same change.

## Stage 1: harmonium note separation

Add **Harmonium notes: Connected / Lightly separated** to practice settings.
Default Connected preserves the existing envelope at ordinary note joins.
Explain: "Changes the harmonium guide, not tala timing or where you breathe."

Lightly separated starts notes at their scheduled onset, with the preceding
unheld sound faded out early. Start listening trials at **5% of the preceding
unheld note's duration, capped at 25 ms**. Bound the envelope fades to the
remaining sounding time, including the shortest paired notes. The preset is
internal, not a user millisecond slider.

Use actual adjacency, including the continuous-loop seam. Do not anticipate
a note into a leading or intervening rest, or let an ordinary crossfade spill
into a written rest. Preserve the final release at a genuine performance
ending. Do not retrigger at hold cells; initially exempt notes extended by
karvai from artificial shortening, using notation/cursor metadata rather than
assuming a long event is held. Drone, click and guide-off behavior are unchanged.

### Implementation

- Shared `HarmoniumArticulation` and `RenderOptions.articulation`; extend
  `RenderOptions.practice` explicitly for Swift callers.
- Reuse `SampleRenderer` and the existing `Schedule`; no platform delays and
  no articulation field in `PerformanceSettings`.
- Persist choices in Android `PracticePreferences` and iOS
  `PracticeSettingsStore`; missing/unknown choices fall back to Connected.
- Reuse existing setting-change restart behavior.

### Acceptance

Shared tests cover envelopes, repeated/changing notes, holds, leading/trailing
rests, shortest notes, loop seams, unchanged timing and block-size invariance.
Platform tests cover settings and their accessible values. Audition actual
harmonium samples against current playback and reduced overlap, at several
speeds; teacher/listener acceptance remains a separate gate from automated
tests. Do not describe the preset as authentic vocal articulation.

## Stage 2: recovery between attempts, with continuous tanpura

Retain Repeat as a separate switch. Add **Between repeats: Continuous / Timed
break / When ready**, effective only with Repeat on. Timed breaks offer
**1–30 seconds**, with **5 seconds** as the initial remembered value. These
are convenience values. When ready has no deadline.

An attempt is one complete scheduled performance, including the existing
internal repetitions needed to fill tala cycles. Sections are shorter
user-selected practice units, not automatically approved breath phrases.

Sequence: **perform → release → recovery → count-in → next attempt**.

- Start the break after the harmonium release, not at render-ahead completion.
- Keep the configured tanpura sounding; if drone is off, recovery is silent.
- Stop exercise clicks, cursor and tala highlight during recovery.
- Preserve the completed result; expose Continue and Stop. Timed recovery
  also offers Wait for me, which cancels automatic continuation.
- Count-in is additional to the break duration. Every scored attempt receives
  fresh calibration/count-in; unscored playback honors its count-in setting.

### Shared transport, not repeated player restarts

Introduce a narrow PCM-source contract and `PracticeTransport`, retaining one
continuous `RenderOptions.droneOnly` renderer and replaceable exercise
renderers with their own drone disabled. Reuse decoded `SampleBank` clips.
Keep the current seamless looping path for Continuous.

Use frame-addressed count-in, performance, release and recovery boundaries.
Split render blocks at exact boundaries and preserve block-size invariance.
Add an internal unclipped `SampleRenderer` entry point; sum once, then apply
the transport stop envelope and clip once. Preallocate scratch buffers.

The render position schedules future audio; the heard output-frame clock
controls visible phase and completion. Continue/readiness commands target
future unrendered frames, never audio already queued. Platform UI timers do
not independently schedule attempts.

Android's output worker and iOS's existing serial render queue own transport
mutation. Main-thread actions send commands and receive immutable snapshots.
Use run/attempt generation IDs to reject stale callbacks. Segment completion
is separate from transport termination; distinguish natural completion,
requested stop, interruption and failure. Only natural completion repeats.
Preserve iOS `.dataConsumed` refill and latency handling. No Kotlin runs on
AVFoundation's real-time thread.

### Scoring and capture

Give each scored attempt a fresh pipeline/session, created from renderer
frame counts. Map its local clock to an explicit transport-frame origin,
applying existing input/output latency compensation exactly once. Reuse one
transport-wide `OutputLevels` history of final mixed output, including drone;
do not erase already-rendered count-in levels at the next attempt.

Exclude recovery/listening from scoring and calibration. Drain relevant mic
windows before exactly-once finalization; make `finish()` idempotent. Preserve
manual-stop partial-result behavior deliberately and distinguish it from a
completed attempt. Readiness failure extends drone-only waiting, never blocks
the render thread.

Android can stop capture during recovery, then prepare a new mic with a
readiness handshake before count-in. iOS keeps a stable recording graph and
tap router throughout scored practice; discard break/listen buffers before
copying or inference, and reset window history for the next attempt. Do not
switch audio-session categories or echo cancellation between attempts.

**iOS privacy:** maintaining uninterrupted output may leave the microphone
connection and privacy indicator active during breaks. No break audio is
analyzed, stored or sent. Do not claim the hardware is off. Stop/leaving
practice tears down capture normally.

### State, history and cancellation

Keep explicit phases and an overall attempt number in shared policy, surfaced
by both ViewModels. Preserve one practised count per logical Play session and
whole-piece best scores only for whole-piece attempts, not sections/phrases.

Stop, navigation and output changes cancel every pending continuation and
stop the drone. Backgrounding during recovery cancels automatic continuation;
foregrounding requires explicit Continue. Retain existing fresh-session resume
for sounding playback. Never automatically acknowledge an output change.

Settings changed while waiting apply to the next attempt without starting it
early. Recompute changed recovery deadlines from the original break start;
if already elapsed, require Continue instead of surprising immediate sound.
A Sa change stops the session rather than retuning the recovery drone.

## Stage 3: teacher-reviewed phrase practice

Add bundled metadata with stable phrase/exercise IDs, label, explicit
boundaries, review/source notes and approval status. Initially support a
small reviewed set of contiguous whole-avarta ranges. Reuse the range builder
behind `Schedule.ofSection`, preserving piece-wide notation positions and
whole-cycle scheduling rules.

Represent whole piece, section and reviewed phrase explicitly. Do not use
`schedule.section == null` to infer whole-piece history eligibility. Show a
phrase picker only when approved entries exist; make the selected extent
visible and accessible. Reuse Stage 2 repetition/recovery.

**Release gate:** teacher review covers phrase endings, sahitya word integrity,
holds, speeds and whether cycle-filling repetitions are appropriate. No entry
is invented from formatting or silently treated as reviewed. A complete short
exercise may be a practice unit without being called a breath phrase.

Sub-avarta phrases need a later explicit cell/slot span design, original tala
phase and count-in/pickup support, plus validation against splitting holds or
words. Do not approximate them by filtering events or padding with invented
rests. Expose only after timing tests and musical review.

## Stage 4: listen, then sing

Reuse the transport and reviewed practice-unit selection:

**Listen → release/recovery → count-in → Your turn → result/recovery**.

- The model uses harmonium even if the response guide is disabled, and is not
  scored. Response defaults to tanpura and the configured click, with an
  explicit response-guide option for harmonium support.
- Feedback uses existing microphone permission/settings. Without feedback,
  provide an unscored response interval without requesting microphone access.
- Initially support whole short exercises and approved whole-avarta phrases;
  expand with Stage 3 boundary support.
- Repeat off runs one listen–sing pair then stops. Repeat on repeats that pair
  with recovery settings; no automatic advance to an unrelated exercise.
- Clearly label Listen, Rest, Count-in and Your turn. Normal playback remains
  the default mode.

## Verification and delivery

Finish each stage on both platforms before proceeding. Update the settings,
player and sing-along website guides with shipped behavior only.

Run shared JVM and Android unit tests, assembleDebug, lintDebug and detekt.
Run connected Android accessibility tests with an emulator and Xcode unit/UI
audits using an available simulator's explicit destination ID. Regenerate the
Xcode project if Swift files are added. Build the site with `python3
site/build.py`.

Extend existing renderer/scheduler, sing-along end-to-end, persistence,
output-change and playback-recovery tests. New transport tests cover mixed
output continuity, boundaries inside buffers, readiness, cancellation, stale
commands, exactly-once results/history and latency/frame origins. Synthetic
singers exercise at least two attempts and a listen–sing pair.

Test real microphone routes on speaker, wired headphones and Bluetooth,
especially iOS silent-output/recording transitions. OS interruption can break
the drone; do not promise continuity through an engine reset. Verify
TalkBack/VoiceOver, labelled selected values, reachable Continue/Stop, large
targets, Dynamic Type, reduced motion and no countdown announcements each
second. If hardware, listening or teacher review is unavailable, record that
gate as pending rather than claiming verification.
