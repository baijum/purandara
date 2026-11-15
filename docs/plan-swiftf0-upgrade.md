# Plan: upgrade SwiftF0 and ONNX Runtime

Status: **planned** (2026-11-15); the maintainer agreed the decisions
below as recommended. Step 1 (the comparison tool and evaluation) is done on
synthetic tracks and 10 of the student's recordings; decisions 3 and 4 were
settled by the results (*Results*).

## Where things stand

| | Now | Latest (2026-11-15) |
|---|---|---|
| SwiftF0 model | An early pre-0.1.2 export copied from ukulele-companion: 398 KB, about 95K parameters, SHA-256 `7e2390db…` | **v0.3.0**: 135 KB, 14 386 parameters, SHA-256 `6385e8c2ebc3872e82c9ff5946228de44cd3be77a750ea53698b7dbfe94b0a22`. The weights are the same as v0.2.0; v0.3.0 changed the Python package, not the model. |
| ONNX Runtime, Android | 1.30.0 (`gradle/libs.versions.toml`) | **1.32.0** (Maven Central) |
| ONNX Runtime, iOS | 1.26.0 (`iosApp/setup_onnxruntime.sh`) | **1.30.0**. This is the newest iOS package Microsoft has published (CocoaPods `onnxruntime-c`, `download.onnxruntime.ai`); 1.32 isn't out for iOS yet. |

According to the author's release notes, the v0.2 model scores higher on the
pitch benchmark (pitch F1 0.778 → 0.781 with a pitch range), runs about twice
as fast per frame, and is a third of the size. Source:
https://github.com/lars76/swift-f0 (MIT, Lars Nieradzik).

## What changed in the model

These details come from `swift_f0/core.py` at v0.3.0, the reference for how
the model is meant to be called.

| | Old model (ours) | v0.2/v0.3 model |
|---|---|---|
| Inputs | `audio` [1, n] float32 at 16 kHz | `audio` [1, n] float32 at 16 kHz, plus **`fmin` and `fmax`**: float32 scalars in Hz, clamped to 46.875–2093.75 |
| Outputs | pitch and confidence (float32) | **`pitch` as float64** Hz and `confidence`, one per frame |
| Frames | — | Hop of 256 samples (16 ms); frame *k* is centred on sample *k*·256 |
| Context | — | Each frame uses **2815 samples (176 ms) on each side**. For an exact answer a frame needs 11 frames before it and 10 after. Offline, the reference runs 1875 frames (30 s) per call with that overlap. |
| Voiced | Our own threshold of 0.5 | Voiced when confidence ≥ 0.5, the same threshold |
| Silence | — | The model gives random voiced frames on digital silence, so the reference sets confidence to 0 where a hop's peak is below 1e-3 (−60 dBFS). We must do the same; the gate is outside the model. |

Our callers would break as they are:
- Both supervisors pass only the audio input. The model now also needs `fmin` and `fmax`.
- iOS reads every output as `Float`, but pitch is now float64.
- We send one 2048-sample capture window, about 680 samples at 16 kHz. That's
  far shorter than the context the model needs, so the readings would be poor.

## Decisions (agreed 2026-11-15)

1. **The iOS / Android version gap.** Use the latest on each: **Android 1.32.0,
   iOS 1.30.0**. Move iOS to 1.32 once Microsoft publishes it. Both are well
   past the model's minimum (1.19), and we use only the basic C and Java APIs,
   which haven't changed. The alternative is to hold both at 1.30.0 for an
   exact match, but that means downgrading Android.
2. **Pitch range per call.** Pass `fmin` and `fmax` from the student's voice
   range around Sa: mandra Pa to tara Pa with a semitone of margin, clamped
   to the model's range. Narrowing the range is what raised F1 in v0.3.0. It may also remove
   the octave errors we bring SwiftF0 in to catch, because a pitch outside the
   range gets confidence 0 instead of a wrong octave. *Find my Sa* has no Sa
   yet, so it keeps the full range.
3. **Live listening (settled by the results):** a rolling buffer of the last
   176 ms at 16 kHz, 6 frames before the frame read and 4 after, so the
   reading is about 64–80 ms behind the newest audio. Because it lags, live
   listening uses SwiftF0 **only for octave correction**. The arbitrator's
   non-octave "strong disagreement" override is turned off live, because a
   reading that old disagrees with YIN at every note change and gamaka.
   SwiftF0 still runs every 5th window.
4. **Recordings (settled by the results):** the whole recording in 30 s
   chunks with overlap, as the reference does. This is the most accurate way
   to run the model and the fastest: about 0.4 s of model time per minute of
   audio on a Mac, against 6.6 s today. Each YIN frame takes the nearest
   SwiftF0 frame, and the arbitration is unchanged.

## Results (2026-11-15)

### Synthetic tracks

From `tools/pitch/compare_swiftf0.py`. The tracks are Sarali 1 at Sa A♯2, D3,
F♯3, G♯3, A♯3 and C4 on three vowels (aa, oo, ee), plus a breathy soft voice,
room noise at 12 dB SNR, a weak fundamental, a kampita and a jaru. Errors are
counted at the app's pitch-frame times:

| Method | Octave errors | Gross errors (> 50¢ on held notes) | Model time per minute of audio (Mac, 1 thread) |
|---|---|---|---|
| Old model, today's window | 6, all on the weak-fundamental track | 5 | 1.3 s live, 6.6 s on every window |
| New, short buffer (3 frames before, 2 after) | 0 | 0 | 1.2 s live, 5.9 s on every window |
| New, reference context (11 before, 10 after) | 163 | 2851 (the reading is 176 ms old, so it lags every note change) | 3.3 s live |
| New, whole recording in chunks | 178 | 156 | 0.4 s |
| New, whole recording with the Sa range | 0, but 1% of voiced frames are lost | 0 | 0.4 s |

What this showed:

- **The newest frame is never voiced.** The model gives low confidence on the
  first and last frame of any input, so option (b) of decision 3 is out. One
  or two frames of audio after a frame are enough for a full-confidence
  reading.
- **The full context reads high voices an octave up.** At Sa A♯3 and C4, on
  the open vowels (aa, ee), with the reference context or the whole
  recording, the model read some upper notes one or two octaves too high:
  Ma at 349 Hz as 1396 Hz, tara Sa as 1046 Hz. The short buffer doesn't, and
  neither does the old model. The Sa range turns these into "no pitch"
  (YIN decides alone) rather than fixing them.
- **The new model is more precise:** median error 1–2¢ against the old model's
  4–5¢. It also fixes the old model's octave errors on a weak fundamental.

These are synthetic voices, and the student's recordings below overturned
the short-buffer result.

### The student's recordings (10, Sa A♯2, iPad mini, 11–36 s)

There's no ground truth, so each method was compared with the full-context
reading (new-offline) and with the old model:

- **The 3-before / 2-after buffer reads junk on a real voice.** On many frames
  it reported about 2 kHz, the top of the model's range: 62% agreement with
  the full context. The synthetic voices hid this. How the buffer size
  compares with the full context, on the same frames:

  | Frames before / after | Agrees within 50¢ | Model time per call |
  |---|---|---|
  | 3 / 2 | 61.8% | 1.0 ms |
  | 11 / 2 | 96.1% | 2.0 ms |
  | **6 / 4** | **99.0%** | **1.7 ms** |
  | 11 / 4 | 99.4% | 2.2 ms |
  | 11 / 6 | 99.8% | 2.5 ms |
  | 11 / 10 | 100% | 2.9 ms |

  The audio *after* the frame matters most, and more audio before it adds
  little.
- **Full context against the old model:** they agree within 50¢ on about 97%
  of voiced frames. They are an octave apart on **7 short spots (0.4% of
  frames)**, all in soft passages (RMS about 0.007–0.01, a voice fading or
  between notes). In every spot the new model reads the lower octave:
  - 9:57:45 recording: 19.3 s and 21.0–21.3 s
  - 9:58:32 recording: 15.4–15.6 s
  - 9:59:05 recording: 4.2–4.3 s and 7.9–8.0 s

  In all but the last spot the app's saved pitch (YIN plus the old model) has
  no pitch at all. In the last, it also reads the lower octave (88 Hz),
  which suggests the voice itself drops there, like a creak at a phrase
  end. **To check by ear.**
- **Live, the lag is the cost, not the octave.** Compared with the full
  context at the moment it's used, the 6 / 4 buffer is in the same octave on
  99.7% of frames with the Sa range (the old model: 99.4%). It's within 50¢
  on only 92%, because it is about 70 ms old. Hence octave correction only
  (decision 3).
- **The Sa range** halves the octave disagreements in the 9:59:05 recording,
  because 71–73 Hz is below mandra Pa. Elsewhere it changes nothing on this
  voice. On the synthetic high voices it is what removes the octave-up
  errors.

## Design

### Shared (`shared/`, pure Kotlin)

- `pitch/SwiftF0.kt` holds the model's constants (rate, hop, frequency range,
  left and lookahead frames for chunks and for the live buffer (6 / 4), chunk
  size, silence peak), and:
  - `fun silenceGate(audio16k, confidence)`: confidence 0 for hops below the peak.
  - `fun range(saHz: Double?): ClosedRange<Double>`: the pitch range for
    decision 2.
  - `class SwiftF0Chunker`: splits 16 kHz audio into chunk calls with overlap
    and joins the kept frames, as in the reference `detect`. Tested on fake
    model output.
  - `class SwiftF0Track(hz, confidence)` with `at(seconds)`, so RecordingAnalyzer
    can look up the nearest frame.
- `RecordingAnalyzer` takes a `SwiftF0Track` (built from the whole recording)
  instead of a per-window callback. `NeuralArbitrator` gets a live mode that
  allows only the octave correction (decision 3); Recordings keep every rule. Their thresholds (1.5 / 2.5 semitones, confidence
  fractions, 2 matching readings) are re-checked in Evaluation, not changed up
  front.
- `AudioResampler` stays unless Evaluation shows it costs accuracy. It's a
  simple moving average and decimation; the reference uses soxr. If it does
  matter, a better low-pass filter goes into the shared resampler.
- `RecordingLibrary.ANALYSIS_VERSION` becomes **2**. Recordings made with the
  old model are re-analysed automatically when opened, from their saved audio
  (automatic re-analysis instead of a Re-analyse button, agreed
  2026-11-15; built as part of this work, with a debug-only button for tuning).

### Android

- `libs.versions.toml`: onnxruntime 1.32.0.
- `NeuralPitchSupervisor`:
  - Feeds the `audio`, `fmin` and `fmax` inputs by name.
  - Reads `pitch` as double and `confidence`.
  - Applies the silence gate.
  - Two entry points: `estimateLive(range)` reads the frame 4 from the end
    of a rolling 176 ms 16 kHz buffer that the capture code fills on every
    hop, not only when SwiftF0 runs (decision 3), and `analyse(audio16k, range): SwiftF0Track` (chunked,
    decision 4).
  - Threads stay at 1 for live listening. Offline analysis may use 2; measure
    first.
- `RecordingDecoder` resamples the decoded audio to 16 kHz in a stream and
  calls `analyse` in chunks. It never holds 10 minutes of audio in memory:
  10 minutes at 16 kHz is about 38 MB as floats.
- `NeuralPitchSupervisorParseTest` is updated for float64 pitch and the new
  output names.

### iOS

- `setup_onnxruntime.sh`: 1.30.0 with its SHA-256. The primary URL is
  Microsoft's `download.onnxruntime.ai`. The ukulele-companion vendored
  copy has no 1.30.0, so it becomes the fallback for 1.26 only or is removed.
  The CI cache key follows the script automatically.
- `NeuralPitchSupervisor.swift`: the same changes as Android, through the C
  API:
  - three inputs, with `fmin` and `fmax` as 0-dimension float tensors;
  - output element type checked (`GetTensorElementType`) and read as
    `Double` or `Float`;
  - the silence gate;
  - `estimateLive` and `analyse`.

  Closures stay out of `@MainActor` code (see the AGENTS.md interop notes).
- `RecordingDecoder` (in `Recorder.swift`): streamed 16 kHz chunks into `analyse`.

### Both

- `models/swift_f0_model.onnx` is replaced by the v0.3.0 file.
  `models/README.md` records the new size, parameters, checksum, tag, input and
  output names, and states that the silence gate lives in our code.
- ATTRIBUTION.md: the SwiftF0 entry names v0.3.0, keeps the MIT licence and
  author, and adds the training repository (`lars76/swift-f0-training`). The
  ONNX Runtime entry records the versions.
- AGENTS.md: the approved-exceptions row stays as it is (same dependencies).
  The code map mentions `SwiftF0.kt`.

## Evaluation (before switching over)

We need ground truth, so the comparison uses tracks whose pitch we know, plus
the student's real recordings for agreement and stability.

1. **A comparison tool, `tools/pitch/compare_swiftf0.py`.** It isn't part of
   the build. It uses Python with `onnxruntime` from pip and runs inside a venv
   in the tool's own directory. It runs, on each WAV:
   - the old model (current window method);
   - the new model live (a) and (b);
   - the new model offline;
   - each of these with and without a pitch range.

   It reports, per run: octave errors, gross errors (more than 50¢), the median
   error in cents on voiced frames, voicing misses, and time per minute of
   audio.
2. **Synthetic tracks with exact pitch**, generated by the tool:
   - the synthetic singer's Sarali, at Sa A♯2, D3 and F♯3 (male and female
     ranges);
   - a kampita on Ri, a jaru between Sa and Pa;
   - a breathy, soft voice;
   - fan and room noise underneath;
   - a strong second harmonic, the classic case where YIN reads an octave off.
3. **The student's recordings from the iPad.** These have no ground truth, so we
   check two things: where old and new disagree by an octave (listened to by
   hand), and how many short octave jumps each produces.
4. **Through the app.** The full app pipeline (YIN plus arbitration) runs with
   each model on the same tracks, using `RecordingAnalyzerTest`-style jvm tests.
   The test feeds pre-computed SwiftF0 frames exported by the tool as fixtures,
   because jvmTest doesn't run ONNX.

**Switch-over criteria:**
- The new model has no more octave and gross errors than the old one on every
  track group.
- It is at least as fast on the iPad mini, live and offline.
- Live listening shows no audible lag in the sing-along feedback.

Measured on the iPad mini and the Android emulator. If the live path fails the
criteria, see decision 3.

## Tests

- `commonTest`:
  - `SwiftF0Chunker`: chunk edges and overlap, the last chunk, audio shorter
    than one hop, and that the frame count matches the reference.
  - The silence gate.
  - `range(saHz)` clamping.
  - `SwiftF0Track.at`.
  - RecordingAnalyzer with a `SwiftF0Track` that disagrees with YIN by an
    octave: the arbitration picks SwiftF0 as before.
- Android unit tests: output parsing for float64 and float32, and missing
  outputs, which give null so YIN is used alone.
- Both apps:
  - The existing sing-along, Shruti practice and Recordings tests stay green.
  - The synthetic Sarali recording still reads back as its swaras.
  - The model loads on the iPad (a debug log line gives the model's input
    names at load).
- iOS unit test: the bundled model has the expected input and output names, so
  a wrong model file fails CI.

## Not in this plan

- Using SwiftF0's note segmentation (`segment_notes`) in place of our
  transcriber. It's Python and works differently, though its 80 ms pitch hold
  is worth comparing with ours when tuning.
- Making SwiftF0 the main detector instead of YIN.
- Downloading models at runtime. They stay bundled; the app stays offline.

## Build order

1. The comparison tool and the synthetic tracks. Run the evaluation, then
   decide decision 3 from the numbers.
2. Shared: `SwiftF0.kt` (constants, gate, range, chunker, track),
   RecordingAnalyzer on a track, the automatic re-analysis, and tests.
3. Android: ONNX Runtime 1.32.0, the supervisor, the decoder, tests, and
   timing on the emulator.
4. iOS: ONNX Runtime 1.30.0, the supervisor, the decoder, tests, and timing on
   the iPad mini.
5. The model swap, models/README, ATTRIBUTION, AGENTS. Full test suites on
   both apps.
6. Retune the Recordings thresholds on the student's recordings with the new
   model (step 5 of `plan-recordings.md`).
