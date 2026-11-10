# Plan: explain the written-pace speed settings

Agreed 2026-11-11 for issue #35. The source evidence is in
`resources.md` §3.5; issue #34 already made fourth-speed practice optional.

## Scope

Speed settings 1–4 multiply the written pace by 1×, 2×, 4× and 8×.
They are not universal individual-swara counts. Janta 1–6 and both Vakra
Janta exercises use two-swara cells, giving 2/4/8/16 swaras per tala beat.
Janta 7 has single-swara cells and holds instead. Holds extend a note;
rests are silent. Neither creates a note onset, and mixed passages need
not have a constant count.

Keep playback timing, notation, persisted kala values, ranges, defaults
and fourth-speed availability unchanged. No new dependencies or density
inference API: reuse `PerformanceSettings.aksharasPerBeat` for the selected
written-pace multiplier.

## Implementation

- Both players show the selected multiplier beside Speed and an accessible
  About speed button. It opens scrollable help (Material dialog / SwiftUI
  sheet), keeping long explanations outside the fixed player area.
- Keep numbered kala choices and their actions. Speak the multiplier as
  “times the written pace”; support large text and accessible dismissal.
- Extend existing shared scheduling tests across all four settings for the
  plain scale, paired Janta and the Janta 7 hold exception. Check every
  onset, counts, duration, repetitions and unchanged tala beat spacing.
- Extend platform accessibility tests for the multiplier and help view,
  without weakening audit rules.
- Explain paired speeds and the Janta 7 exception in Learn and the guide;
  distinguish a held place from a new swara. Keep the optional-fourth
  guidance and teaching-source uncertainty.

## Verification

Run shared/Android tests, assemble, lint and detekt; Android connected
accessibility tests; and the iOS Xcode test suite on an explicit simulator
ID. Check normal and large-text player/help layouts on both platforms.
Build the website (including its link checks), preview the changed sections,
and run `git diff --check`. Record actual outcomes below; unavailable tests
are not passes.

## Results (2026-11-11)

Built as planned. Both players show the selected multiplier and open
About speed directly beside the control, without a settings detour. The
Android info icon is an original vector drawable. No production scheduler,
notation, preference or audio code changed.

- `:shared:jvmTest`: 359 tests, no failures, including the all-kala plain,
  paired-Janta and Janta 7 regressions.
- Android unit tests: 32 tests, no failures; `assembleDebug`, `lintDebug`
  and `detekt` passed.
- Final `:app:connectedDebugAndroidTest`: 50 tests, no failures. The
  accessibility class also passed separately (36 tests). An initial new
  text matcher incorrectly expected text hidden by replacement semantics;
  the spoken heading remains asserted. The hardware-Back test needed to
  wait until the dialog was displayed and idle before sending the key.
- Android help also passed with the emulator's actual system font scale
  set to 2.0 (restored to 1.0 afterwards). A composition-local font override
  alone enlarged the player but not the separate dialog window. Normal and
  system-large captures confirm the dialog scrolls and Done stays visible.
- iOS full suite on iPhone 18 Pro / iOS 27.2: 72 of 73 tests passed. The
  only failure was the new normal-size help Dynamic Type audit reporting
  text clipping. Added line spacing and small vertical padding to the help
  body, without changing audit rules. The subsequent focused run passed
  all four tests: normal help audit, largest-text help audit and scrolling,
  multiplier selection/persistence, and the player audit. The remaining
  full-suite tests were not rerun after this help-only spacing adjustment.
- Reviewed Android and iOS player/help screenshots at normal and large
  text sizes, including the end of the scrollable explanation.
- Website build/link checks passed (27 pages, 147 search entries); reviewed
  the paired-note section and linked guide/Janta pages in the browser.
  `git diff --check` passed.

No new dependencies, completion gates or speed restrictions. Fourth speed
remains available; teacher/source counting conventions remain qualified.
