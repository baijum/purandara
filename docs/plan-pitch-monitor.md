# Plan: Pitch monitor (second feature)

Status: **draft for review**. Open decisions are marked **[Decision]**.
Builds on the varisai player (`docs/plan.md`).

## 1. Goal

The student sings and sees and hears where their voice is relative to Sa, in
swaras, so they can find notes and hold them steadily. This is the "check"
step of the workbook's pitch-matching loop (chapter 5): listen → echo →
**check** → retry.

The workbook's pitch-monitor rules become requirements:

| Workbook rule | Requirement |
|---|---|
| "Wait for the pitch estimate to settle; voice onset can be breathy or unstable." | Ignore the onset; report a note only once it has **settled** (section 4.3). |
| "Check the octave as well as the note name." | Always show the sthayi: mandra, madhya or tara. |
| "Make sure the reading follows your voice, not the drone or a lingering keyboard note." | Keep the app's own drone and harmonium out of the reading (section 5). |
| "Use a broad beginner goal: get close, then improve gradually. Do not turn practice into a hunt for zero cents." | Broad zones (in tune / close / off) rather than a needle chasing every cent (section 4.4). |
| "For a gamaka or deliberate glide, a moving line can be musically correct." | Show a pitch **trace** over time, not only a needle, and never mark movement as an error. |
| Success score 3: "found it quickly and held it comfortably for about three seconds." | Measure time-to-settle and hold duration for each note. |

## 2. Phasing

| Phase | What | Why this order |
|---|---|---|
| **A** | A standalone pitch monitor screen: sing freely over an optional drone. | The smallest useful piece. It builds the detection, capture and display that B and C reuse. |
| **B** | Sing along in the player: your trace drawn against the expected swaras while a varisai plays, with a per-note summary afterwards. | Builds directly on A and the player. |
| **C** | Guided pattern cards (workbook cards A–F): the app plays a note, you echo it, it checks and tells you whether to retry. | Needs A's settle detection and B's expected-note comparison. |

This plan details **phase A** and sketches B and C enough to keep A's design compatible.

## 3. Reuse from the ukulele app

Same author and licence (MIT). Ported code gets a provenance note in its header.

| Piece | Source | Changes |
|---|---|---|
| YIN pitch detector (FFT difference function, CMND threshold, best local estimate, parabolic interpolation, continuity tracking, buffer reuse) | `ukulele-companion/shared/.../domain/PitchDetector.kt` | Range 55–1100 Hz (voice: low male mandra Pa to high female tara). Thresholds re-tuned on voice-like test signals. |
| Android capture (`AudioRecord`, 44.1 kHz mono, frame dropping when processing falls behind) | `app/.../audio/AudioCaptureEngine.kt` | Audio source chosen for echo cancellation (section 5). |
| iOS capture (`AVAudioEngine` input tap at the hardware rate, interruption handling) | `iosApp/.../Audio/AudioCaptureEngine.swift` | `.playAndRecord` so the drone can play; voice processing (section 5). |
| Median smoothing and noise gate ideas | `PitchMonitorStateMachine.kt` | Rewritten around swaras and settling. |

**Not carried over: chord detection.** The ukulele pitch monitor's chord
features (`AudioChordDetector`, `Chromagram`, `ArpeggioDetector`, the
simultaneous/arpeggio merge in `PitchMonitorStateMachine`, and the chroma
visualisation) are not needed. Carnatic vocal practice is monophonic, and the
drone is the app's own, so its pitches are already known.

**Not reused yet: the neural pitch supervisor (SwiftF0 via ONNX Runtime).** It
would add a third-party SDK, which needs discussion (AGENTS.md hard constraint).
Phase A ships with YIN alone; revisit if testing shows octave errors on low voices
**[Decision 4]**.

## 4. Shared logic (`shared/`, pure Kotlin)

### 4.1 Pipeline

```
mic frames ─▶ PitchDetector (YIN) ─▶ PitchTracker ─▶ VoiceReading
 (platform)       Hz + confidence      smoothing,       swara, octave, cents,
                                       swara mapping,   zone, settled?, trace
                                       settling, stats
```

- **Frames:** 2048-sample windows (46 ms at 44.1 kHz, enough for two periods at
  55 Hz) every 512 samples (about 86 readings per second).
- **Noise gate:** RMS below a threshold means silence, so no reading is shown.
- **Confidence gate:** YIN aperiodicity above the threshold means unvoiced or
  breathy, so the frame is dropped.

### 4.2 Swara mapping

- **Input:** a frequency, the student's Sa, the raga and the tuning (the same
  `PracticeSettings` as the player).
- **Output:**
  - the nearest swara of the raga (Mayamalavagowla: S R1 G3 M1 P D1 N3);
  - its octave (mandra / madhya / tara);
  - the cents deviation from that swara's target (`Tuning.JUST` or `EQUAL`);
  - the nearest of all 12 swarasthanas, so a sung R2 isn't silently snapped to R1.
- If the sung note is closer to a swarasthana outside the raga, show that
  swarasthana (e.g. "R2") with a hint that it isn't in Mayamalavagowla.

### 4.3 Settling

A note has **settled** when the smoothed pitch stays within ±`SETTLE_CENTS`
(default 20) of one swara for `SETTLE_MS` (default 300 ms).
- `onset → settled` records **time to settle**.
- While settled, the tracker accumulates **hold duration** and **steadiness**
  (standard deviation in cents).
- Leaving the window or silence ends the held note and emits a `HeldNote`
  summary: swara, octave, mean cents, steadiness, hold time and time to settle.
  Phases B and C build on these summaries.

### 4.4 Zones

| Zone | Default | Meaning |
|---|---|---|
| In tune | within ±10 cents of the target | "Good" |
| Close | within ±25 cents | "Nearly there", the workbook's broad beginner goal |
| Off | beyond ±25 cents | Show direction: "a little high" or "a little low" |

Defaults are adjustable later. A beginner/advanced preset is possible **[Decision 2]**.

### 4.5 Tests

- **Synthetic voice-like signals:** a harmonic series with formant-like
  weighting, 5–6 Hz vibrato, breathy noise and a soft onset, from 60 Hz to 1 kHz.
  - Steady pitch within ±2 cents.
  - No octave errors across the range, including weak fundamentals.
- **Settling:** a glide followed by a hold settles once and reports a time to
  settle within ±1 frame; vibrato of ±30 cents around a swara counts as settled
  (on its mean).
- **Mapping:** every swarasthana at every Sa from G2 to C4 in both tunings, at
  octave boundaries, and for notes outside the raga.
- **Drone bleed (simulated):** voice plus the app's drone mixed in at −10 dB.
  The reading must follow the voice, and silence plus drone only must not
  produce a reading (section 5).

## 5. Keeping the app's own sound out of the reading

The app plays a drone, and in phase B the whole exercise, through the
speaker. The microphone hears it. Three layers of defence, in order:

1. **Headphones** (best). Suggest them on the monitor screen when the drone is on
   and audio is going to the built-in speaker. Detect the output route on both
   platforms.
2. **Echo cancellation** on when playing through the speaker:
   - **Android:** `VOICE_COMMUNICATION` audio source, which turns on
     the platform's echo canceller on most devices, plus `AcousticEchoCanceler`
     where available. Fallback: `VOICE_RECOGNITION` (no AGC).
   - **iOS:** `inputNode.setVoiceProcessingEnabled(true)`, which cancels
     the engine's own output, with automatic gain control off
     (`isVoiceProcessingAGCEnabled = false`) so quiet singing isn't pumped.
   - Both may slightly colour the voice; pitch is robust to that. Measured on
     devices in phase A.
3. **Drone rejection in the tracker:**
   - The tracker knows the drone's exact pitches (Sa, Pa, upper Sa).
   - A reading that sits on a drone pitch with the low energy typical of
     bleed, while no voice onset was seen, is treated as "drone only" and
     not shown.
   - This is a safety net, not the main defence. Singing Sa over a Sa drone
     must still register.

**[Decision 1]:** default behaviour on the built-in speaker.
- *Proposed:* echo cancellation on, and suggest headphones.
- *Alternative:* switch the drone off while the mic is on unless headphones are
  connected (simplest and most reliable, but you lose the drone).

## 6. Screens (phase A)

Opened from the library ("Pitch monitor" above the exercise sets) on both
platforms.

- **Big reading:** the swara with its octave mark (e.g. `P`, `S′`, `Ṇ` for
  mandra), plus zone text ("In tune", "A little high") and a ± cents figure,
  smaller. **[Decision 3]:** show the Western note name too (e.g. "G3") as
  secondary text? The workbook uses both.
- **Trace:** the last ~8 seconds of pitch, scrolling left, on a vertical
  axis of swara lines (S R G M P D N S′ across about 1.5 octaves around
  the student's range):
  - in-tune stretches drawn solid and the rest lighter;
  - silence leaves a gap;
  - gamakas show as movement and are not judged.
- **Held-note chips:** the last few `HeldNote`s ("P held 3.2 s, steady").
  This is the workbook's success score, made visible.
- **Controls:** drone on/off (from practice settings), and a start/stop
  microphone button.
- **Permission:** microphone access is requested only when the student taps
  Start, with a one-line reason. If refused, a short explanation and a link to
  settings. The rest of the app works without it.

### 6.1 Accessibility

A core requirement, since the reading is otherwise visual-only:
- **Settled-note announcement** (optional, on by default when a screen
  reader is running): when a note settles, announce it once, e.g. "Pa, in tune"
  or "Ga, a little low". Throttled (at most one per second, and never repeat
  the same note while it's held), as the ukulele tuner does.
- **Haptic** tick when a note settles in tune, where supported.
- **The trace** is one element whose value summarises recent singing: "Last
  note: Pa, held 3 seconds, steady".
- **The big reading** is a live value only when focused.
- Reduce motion: the trace jumps instead of scrolling smoothly.

### 6.2 Privacy

- Audio is processed in memory and never stored or sent anywhere.
- No recording in phase A. Recording is a separate future feature with
  its own consent.
- The permission rationale and the privacy note say exactly that.
- Platform declarations:
  - Android: `RECORD_AUDIO`, with the microphone marked as not required.
  - iOS: `NSMicrophoneUsageDescription`.

## 7. Phases B and C (outline)

- **B, sing along in the player:**
  - Draw the sung trace over the notation rows, or under the tala strip.
  - After each pass, a per-note summary: which swaras were close, which
    were sharp or flat, and time to settle.
  - Uses the `Schedule`'s note events as targets, shifted by the measured
    audio and mic latency. Latency is calibrated once with a click and
    loopback, or estimated per device.
  - Requires the section 5 defences, because the harmonium plays while you sing.
- **C, guided pattern cards:** card A (Sa–Pa–Sa′) through card F.
  - Each step: play the target → silence → the student sings → check
    (settled within the zone?) → "good", or "listen again".
  - Scores map to the workbook's 0–3 success score.
  - The mic only listens while the reference is silent, which sidesteps
    echo entirely.

## 8. Milestones (phase A)

| # | Milestone | Done when |
|---|---|---|
| P1 | Port YIN to `shared/`; voice-range tuning and synthetic tests | ±2 cents, no octave errors on the synthetic suite, 55–1100 Hz |
| P2 | `PitchTracker`: smoothing, swara mapping, settling, zones, `HeldNote`, drone rejection | All section 4.5 tests pass on JVM, Android host and iOS |
| P3 | Android: permission, capture with echo cancellation, monitor screen | Works on the emulator with a synthetic mic input; on-device check of drone bleed with and without headphones |
| P4 | iOS: same | Same, plus the accessibility audit passes |
| P5 | Accessibility polish: announcements, haptics, trace summary | TalkBack/VoiceOver pass; instrumented and UI tests |

## 9. Decisions

1. **Drone on the built-in speaker while singing:** echo cancellation plus a headphone suggestion (proposed), or drone off unless headphones are connected?
2. **Zones:** ±10 cents in tune and ±25 cents close (proposed), and whether a stricter preset is wanted later.
3. **Western note name:** show it as secondary text next to the swara (proposed: yes, small)?
4. **Neural pitch supervisor:** leave it out of phase A (proposed), or bring in ONNX Runtime now?
5. **Order:** phase A first (proposed), or go straight to sing-along in the player (B)?

## 10. Risks

| Risk | Mitigation |
|---|---|
| Octave errors on low male voices (strong 2nd harmonic) | YIN continuity tracking; synthetic tests with weak fundamentals; neural supervisor as a fallback option |
| Echo cancellation behaves differently across Android devices | Headphone suggestion; drone rejection safety net; test on at least two physical devices |
| Echo cancellation colours or ducks quiet singing | Turn AGC off where possible; measure; allow switching echo cancellation off when using headphones |
| The emulator and simulator can't test a real voice | Inject WAV fixtures through a debug audio source; on-device checks are part of P3/P4 |
