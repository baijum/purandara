# Plan: Sing along with pitch feedback (second feature)

Status: **decisions made, implementation starting**. Builds on the varisai
player (`docs/plan.md`).

## 1. Goal

While a varisai plays, the student sings along and sees how close each swara
was. Afterwards they get a short summary of which notes need attention. This
is the "check" step of the workbook's pitch-matching loop (chapter 5), applied
to the exercises.

The workbook's pitch-monitor rules become requirements:

| Workbook rule | Requirement |
|---|---|
| "Wait for the pitch estimate to settle; voice onset can be breathy or unstable." | Judge each note on its settled middle, not its onset (section 4.4). |
| "Check the octave as well as the note name." | Report an octave mismatch separately from tuning ("an octave low"). |
| "Make sure the reading follows your voice, not the drone or a lingering keyboard note." | Keep the app's own drone and harmonium out of the reading (section 5). The harmonium plays the very note being sung, so this is critical. |
| "Get close, then improve gradually. Do not turn practice into a hunt for zero cents." | Broad zones: in tune ±10 cents, close ±25 cents (section 4.3). |
| "A moving line can be musically correct." | Show the sung trace; never mark movement inside a note as an error. Only its settled pitch is judged. |
| "Use the keyboard only when a note is unclear; do not play every note under your voice once the shape is familiar." | A **harmonium guide** setting: all notes, or off (drone and click only). |

## 2. Decisions (2026-11-06)

| # | Decision |
|---|---|
| 1 | On the built-in speaker: echo cancellation on, and suggest headphones. |
| 2 | Zones: in tune ±10 cents, close ±25 cents. |
| 3 | Show the Western note name small, beside the swara. |
| 4 | **Include the neural pitch checker** (SwiftF0 on ONNX Runtime) from the start. This is an approved third-party SDK; see section 3.1. |
| 5 | **Build sing-along in the player first.** No standalone monitor screen for now. Its building blocks (detector, tracker) are built here, so a standalone screen is cheap to add later. |

## 3. What is reused, and what isn't

From `ukulele-companion` (same author, MIT). Ported files carry a provenance note.

| Piece | Source | Changes |
|---|---|---|
| YIN detector (FFT difference function, CMND threshold, best local estimate, parabolic interpolation, continuity tracking, cached buffers) | `shared/.../domain/PitchDetector.kt` | Range 55–1100 Hz for voice; thresholds re-tuned on voice-like test signals. |
| `AudioResampler` (anti-aliased, cached buffers) | `shared/.../domain/AudioResampler.kt` | None expected (44.1/48 kHz → 16 kHz for SwiftF0). |
| `NeuralArbitrator` + `NeuralPitchResult` (frame gating, result TTL, failure fallback, consistency window, octave correction) | `shared/.../domain/NeuralArbitrator.kt` | Retune only if voice tests show a need. |
| Neural supervisors (model loading off the main thread, inference) | `app/.../domain/NeuralPitchSupervisor.kt`, `iosApp/.../Audio/NeuralPitchSupervisor.swift` | Package names; same always-on-with-fallback behaviour. |
| SwiftF0 model (`swift_f0_model.onnx`, ~398 KB) | lars76/swift-f0 | Kept once at repo-root `models/`, packaged into both apps like `audio/` (Android assets dir, iOS bundle folder). |
| Microphone capture (Android `AudioRecord`; iOS `AVAudioEngine` tap; frame dropping; interruptions) | `AudioCaptureEngine.kt` / `.swift` | Echo cancellation (section 5); iOS session `.playAndRecord` so playback continues. |
| iOS ONNX Runtime setup script (checksummed xcframework download) | `iosApp/setup_onnxruntime.sh` | Same approach; vendored download URL changed to official sources. |

**Not carried over:**
- **Chord detection.** That means `AudioChordDetector`, `Chromagram`,
  `ArpeggioDetector`, the simultaneous/arpeggio merge in
  `PitchMonitorStateMachine`, and the chroma view. Carnatic vocal practice is
  monophonic, and the drone and harmonium pitches are the app's own, so they're
  known.
- **The ukulele tuner UI.**

### 3.1 New third-party dependency (approved)

| What | Licence | Where | Size |
|---|---|---|---|
| ONNX Runtime (Microsoft) | MIT | Android AAR (`com.microsoft.onnxruntime:onnxruntime-android`); iOS xcframework via the C API, downloaded by `iosApp/setup_onnxruntime.sh` at build time (checksummed; git-ignored) | Several MB per ABI. Measure and record in the release notes. |
| SwiftF0 model (lars76) | MIT | Bundled with the app; runs fully offline | ~398 KB |

Both are recorded in `AGENTS.md` under approved exceptions and credited in
`ATTRIBUTION.md`. The download happens at **build** time only. The app itself
never touches the network.

## 4. Shared logic (`shared/`, pure Kotlin)

### 4.1 Pipeline

```
mic frames ─▶ YIN ──────────────┐
 (platform)                      ├─▶ NeuralArbitrator ─▶ PitchTracker ─▶ readings + trace
          ─▶ resample 16 kHz ─▶ SwiftF0 (platform ONNX, every 5th frame)
                                                            │
                         Schedule (expected notes) ─▶ NoteScorer ─▶ per-note marks, pass summary
```

- **Frames:** 2048-sample YIN windows (46 ms at 44.1 kHz) every 512 samples
  (~86 readings per second). SwiftF0 runs on every 5th frame, as in the ukulele app.
- **Gates:** an RMS noise gate, and a YIN aperiodicity gate for breathy or
  unvoiced frames.

### 4.2 Swara mapping

Frequency + Sa + raga + tuning (the player's `PracticeSettings`) → nearest
swarasthana, octave, cents from target, and Western note name. A note outside
the raga is named, not snapped: a sung R2 in Mayamalavagowla shows as R2.

### 4.3 Zones

| Zone | Range | Shown as |
|---|---|---|
| In tune | within ±10 cents | "In tune", solid mark |
| Close | within ±25 cents | "Close", lighter mark plus ↑/↓ |
| Off | beyond ±25 cents | "High" or "low" with ↑/↓ |
| No voice | not enough settled readings | Hollow mark |

Colour is never the only carrier: arrows and words go with it.

### 4.4 Scoring each note (`NoteScorer`)

For each `NoteEvent` in the schedule:

1. **Timing.** Shift its window by the **output latency**: the student hears
   the note late. Readings carry their capture time minus the **input latency**.
   Both come from the platform; section 6 covers calibration.
2. **Settled middle.** Skip the first `max(120 ms, 30%)` of the note, and judge
   the rest. Karvai-extended notes give more to judge.
3. **Result.** The median of the remaining readings gives the zone and the
   cents. The octave is compared separately: right swara, wrong octave is
   "an octave low/high", not "off".
4. **Too fast to judge.** Notes shorter than 250 ms (e.g. third and fourth
   kala at normal tempo) get no mark. The trace is still drawn.

**Pass summary:** after each pass (or on Stop):
- "14 of 16 notes in tune or close";
- up to three notes needing attention, e.g. "Ri₁ sharp in 3 of 4", "Dha₁ low";
- any octave issues.

### 4.5 Tests

- **Detector:** synthetic voice-like signals (harmonic series with formant
  weighting, 5–6 Hz vibrato, breath noise, soft onset) from 60 Hz to 1 kHz.
  Steady pitch within ±2 cents, no octave errors, including weak fundamentals
  (YIN alone and arbitrated).
- **Scorer:** synthetic "sung" performances built from a `Schedule`:
  - perfectly in tune;
  - consistently sharp by 30 cents;
  - one note flat;
  - sung an octave low;
  - late onsets;
  - silence on some notes;
  - with vibrato.
  Each must give the expected marks and summary.
- **Bleed:** voice plus the app's drone and harmonium mixed at −10/−20 dB. The
  reading follows the voice. Harmonium alone, with no voice, must score as
  "no voice", not "in tune" (section 5).

## 5. Keeping the app's own sound out of the reading

The harmonium plays the target note through the speaker. If the mic hears it,
the student would look perfect while silent. Defences, in order:

1. **Headphones.** Detect the output route; while sing-along is on and audio
   goes to the built-in speaker, show a one-line suggestion.
2. **Echo cancellation** when playing through the speaker:
   - **Android:** the `VOICE_COMMUNICATION` source, plus `AcousticEchoCanceler`
     where available.
   - **iOS:** `inputNode.setVoiceProcessingEnabled(true)` with
     `isVoiceProcessingAGCEnabled = false`.
3. **Count-in calibration.** Sing-along forces the count-in on. The student is
   silent during it, so the **leakage ratio** (mic level ÷ the app's own output
   level) is measured then. During the pass, the leakage expected at each moment
   is that ratio times the current output level (harmonium included, though it
   is silent in the count-in). Sound must be ≥ 2× that (≈ 6 dB) to count as voice
   (`BleedModel`).
4. **Harmonium guide off** (setting): only drone and click play, and the student
   sings alone. This is the workbook's own progression, and it removes the
   worst bleed. Suggested in the pass summary when bleed was detected.
5. **Bleed check in the scorer.** If a note's readings match the reference
   exactly but the level is no higher than the calibrated bleed, mark it "no
   voice". This is a safety net, not the main defence.

## 6. Latency

**As built on Android (S4):** each mic window's capture time comes from
`AudioRecord.getTimestamp`, and `AudioTrack.getTimestamp` maps that moment to
the output frame then leaving the speaker. Both are hardware timestamps on the
same monotonic clock, so input and output latency are accounted for directly and
the shared clock runs with zero extra latency. The estimates below remain the
fallback when timestamps are unavailable.


- **Output latency:** Android `AudioTrack` timestamps; iOS
  `AVAudioSession.outputLatency + ioBufferDuration`. Already used for the highlight.
- **Input latency:** iOS `AVAudioSession.inputLatency`; Android estimate from
  `AudioRecord` timestamps where available, or a per-device default.
- **Human lag:** absorbed by judging only the settled middle of each note.
- **Calibration** (later, if needed): a one-time "sing Sa with the click"
  measurement stored per device. Not planned unless testing shows scoring
  windows are off.

## 7. Screens

In the player (both platforms):
- **Sing along toggle:** a microphone button in the top bar. Microphone
  permission is requested on first use, with a one-line reason; if refused,
  a short explanation and a link to settings. Everything else works without it.
- **Pitch lane** under the tala strip:
  - the student's pitch for the current avarta as a trace, over a staircase of
    the expected swaras;
  - horizontal swara lines labelled S R G M P D N S′;
  - silence leaves a gap.
- **Notation cell marks:** after a note passes, its cell shows its mark (zone
  plus arrow; hollow for "no voice"). Marks clear at the next pass.
- **Live reading** (small, beside the lane): the current swara with octave,
  zone word and cents, plus the Western note name small (decision 3).
- **Pass summary card** after each pass or Stop, as in section 4.4.
- **Harmonium guide** setting in the practice settings sheet: "All notes" or "Off".

### 7.1 Accessibility

- **No announcements during the pass.** They'd talk over the music and the
  student's own voice.
- **At the end of each pass,** the summary is announced once ("14 of 16
  notes in tune or close; Ri₁ sharp").
- **Avarta rows:** each row's spoken sentence gains the marks after the
  pass, e.g. "Avarta 1: S in tune, R high, G in tune…".
- **The pitch lane** is one element whose value is the live reading,
  read only when focused.
- **Haptic** tick when a note lands in tune, optional.
- Reduce motion: the lane jumps instead of scrolling.

### 7.2 Privacy

- Audio is processed in memory, never stored or sent anywhere. No recording.
- The permission text says so.
- Android: `RECORD_AUDIO`, with the microphone marked not required. iOS:
  `NSMicrophoneUsageDescription`.

## 8. Milestones

| # | Milestone | Done when |
|---|---|---|
| S1 ✅ | Port YIN, `AudioResampler`, `NeuralArbitrator` to `shared/`; voice-range tuning; synthetic detector tests | Done: all 67 ported tests plus 8 voice tests pass on JVM, Android host and iOS; within ±2 cents 60 Hz–1 kHz, no octave errors. Found and fixed a sign error in the ported parabolic interpolation (up to ~32 cents) |
| S2 ✅ | `PitchTracker` + `NoteScorer` + pass summary (shared) | Done as `PitchPipeline`, `SwaraMapper`, `SingAlongClock`, `BleedModel`, `NoteScorer`, `SingAlongSession`: 21 unit + 4 end-to-end audio tests pass; a silent student with harmonium leakage scores "no voice" |
| S3 ✅ | Neural runtime on both platforms: bundle SwiftF0, ONNX Runtime AAR and iOS xcframework script, supervisors, CI | Done: model runs on emulator and simulator (within a semitone, 98–659 Hz, 44.1/48 kHz); release APK limited to ARM ABIs (60.7 MB universal, 31.5 MB AAB, was ~5 MB before ONNX Runtime) |
| S4 ✅ | Android sing-along: permission, capture with echo cancellation, pitch lane, cell marks, summary, guide setting | Done: verified on the Pixel 9 emulator with the debug synthetic singer (in tune → ✓ marks; +35 cents → all ↑ and the right summary); permission refusal flow; 14 instrumented tests. **Pending: on-device check with a real voice and the speaker vs headphones** |
| S5 | iOS sing-along: same | Same, plus accessibility audit passes |
| S6 | Accessibility and latency polish; device testing | TalkBack/VoiceOver pass; scoring windows verified on at least one physical device per platform |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Harmonium bleed scored as singing | Five defences in section 5; tests with silent "student" plus bleed |
| Echo cancellation differs across Android devices | Headphone suggestion; count-in calibration; guide-off mode; test on at least two devices |
| Echo cancellation colours or ducks quiet singing | AGC off where possible; measure; allow echo cancellation off with headphones |
| Octave errors on low male voices | YIN continuity plus SwiftF0 octave correction; weak-fundamental tests |
| ONNX Runtime adds app size | Measured: libonnxruntime.so is 23–39 MB per ABI. Release keeps ARM ABIs only and ships as an App Bundle (per-device download estimated 15–20 MB, unmeasured). Option: a reduced ONNX Runtime build with only SwiftF0's operators (a few MB) |
| The emulator and simulator can't hear a real voice | Debug WAV-injection audio source for automated runs; on-device checks in S4–S6 |
