# Plan: Varisai player (first feature)

Status: **draft for review**. Nothing here is built yet beyond what
"Already in place" lists. Open decisions are marked **[Decision]**.

## 1. Goal

A student picks their Sa and an exercise, and the app plays it on the
harmonium in tala while showing where they are in the cycle. They sing along.

This is the first feature because every later one (pitch monitoring, the
guided pitch-matching loop, a practice plan) needs the same parts: exercise
data, timing, tala, and sample playback.

### In scope

- Sa setting: any semitone from G2 to C4 (see section 7).
- Exercise library: plain scale, Sarali Varisai 1–16, Madhya Sthayi 1–5,
  Janta Varisai 1–7, Vakra Janta 1–2, Dhattu Varisai 1–6.
- Playback in Adi tala, at kala 1–4, with an adjustable tempo.
- A notation view with the tala marks, highlighting the current akshara and swara.
- Optional Sa–Pa drone and an optional tala click.
- Android and iOS, with the same behaviour on both.

### Not in scope (later features)

- Microphone pitch monitoring and singing feedback.
- The guided listen → echo → check → retry loop.
- The 14-day plan, session timer, 0–3 score and tracker.
- Recording.
- Thara and Mandhra Sthayi, Janta 8 and Vakra Janta 3 (unconfirmed, see section 3.4).
- Gamakas, and ragas other than Mayamalavagowla.

## 2. Already in place

| Part | Where |
|---|---|
| 37 harmonium samples, C2–C5, exact 12-TET at A4 = 440 Hz | `audio/harmonium/<midi>.flac` |
| `Swara`, `Octave`, `Note` with just-intonation ratios | `shared/.../domain/Swara.kt` |
| `HarmoniumBank.playbackFor(note, saHz)` → sample + rate | `shared/.../domain/HarmoniumBank.kt` |
| Samples packaged in the APK (uncompressed) and the iOS bundle | `app/build.gradle.kts`, `iosApp/project.yml` |

## 3. Exercise data

### 3.1 Source

The exercises come from the `varisai` blocks in the Mayamalavagowla practice
workbook (`~/baijum/mayamalavagowla/notes.md`). They are traditional
sequences. They are copied as notation text into the shared module, one Kotlin
raw string per set, so every platform gets them with no resource loading.

```kotlin
// shared/src/commonMain/.../domain/catalog/SaraliVarisai.kt
internal val SARALI_VARISAI = """
1
S R S R | S R | G M || S R G M | P D | N S′
S′ N S′ N | S′ N | D P || S′ N D P | M G | R S
...
""".trimIndent()
```

Copy the text verbatim so it can be compared line by line with the workbook.

### 3.2 Notation grammar

| Token | Meaning |
|---|---|
| `S R G M P D N` | Swara letters. The raga decides the variant (section 4). |
| `′` (U+2032) after a letter | Tara sthayi (upper octave): `S′`. |
| A run of letters, e.g. `SR`, `S′S′`, `SRGM` | One akshara divided evenly between its swaras. |
| `,` | Karvai: hold the previous swara through this akshara. No gap. |
| `\|` | Ends a laghu or druta. Layout only; the parser doesn't use it for timing. |
| `\|\|` | Ends an avarta. |
| A number on its own line | Starts a new exercise. |

- Each whitespace-separated token is one akshara.
- Each line is one or more complete avartas.
- No mandra marker is defined yet. One gets added only when Mandhra Sthayi is
  confirmed.
- Unknown characters are a parse error. The parser never guesses.

### 3.3 Validation

Each exercise's tests check:

- Every avarta has exactly 8 aksharas (Adi tala).
- No exercise starts with a karvai.
- All swaras are within S to S′.

`|` positions are not enforced. The workbook says its bars are layout aids for
some irregular lines (Janta 7 writes `4 | 4` instead of `4 | 2 | 2`).

### 3.4 Excluded until confirmed

These fail validation or carry an explicit warning in the workbook. They are
not encoded until the source book or a teacher confirms them:

| Exercise | Problem |
|---|---|
| Thara Sthayi 1–5 | Unmarked octaves for R G M P above S′ |
| Mandhra Sthayi 1–5 | Lower-octave dots missing from the transcription |
| Janta 8 | 9 aksharas in the first avarta; comma timing unclear |
| Vakra Janta 3 | 10 aksharas per line; comma timing unclear |

## 4. Shared domain model (`shared/`, pure Kotlin)

| Type | Responsibility |
|---|---|
| `Raga` | Maps the letters S R G M P D N to swaras. `Mayamalavagowla` = S R1 G3 M1 P D1 N3. |
| `Tala` | Aksharas per avarta and their marks. `Adi` = `x 1 2 3 x v x v`, grouped 4 + 2 + 2. |
| `Akshara` | Ordered list of swara slots, or a karvai. |
| `Varisai` | Id, set, number, title, and a list of avartas of aksharas. |
| `VarisaiParser` | Notation text → `Varisai` list, with errors that give the line and column. |
| `VarisaiCatalog` | All sets, parsed once and in order. |
| `Schedule` | (varisai, raga, Sa, kala, tempo) → timed events (below). |
| `Tuning` **[Decision 1]** | `JUST` (default) or `EQUAL`: how a swara becomes a frequency. |

### 4.1 Timing

- Tempo is in **aksharas per minute** (the tala beat). Default 60, range 30–120.
- **Kala** k puts 2^(k−1) written aksharas into each tala akshara. Swaras speed
  up while the tala beat stays the same, as the workbook describes.
- A karvai makes the previous note longer. It is never a rest.
- The schedule produces, in seconds from the start:
  - `NoteEvent(start, duration, note, frequencyHz, sample, rate)`
  - `BeatEvent(start, aksharaIndex, mark)` for the tala display and click
  - `CursorEvent(start, avarta, akshara, slot)` for the highlight

### 4.2 Tests

- **Parser:** every catalog exercise parses, and bad input gives clear errors.
- **Timing:**
  - Durations add up to whole avartas at every kala.
  - At 60 aksharas per minute, Sarali 1 in kala 1 lasts 32 s.
  - A karvai extends the previous note.
- **Property tests:**
  - Every note in every exercise is playable from the bank for Sa from G2 to C4.
  - The rate is always within half a semitone of normal.
- **Golden test:** the plain scale's schedule matches a hand-written table.

## 5. Audio

### 5.1 One shared renderer

The rendering is done in shared Kotlin. Each platform only decodes FLAC and
feeds the output to its audio system.

- `SampleRenderer` (commonMain) takes the decoded samples (`FloatArray` per
  MIDI note) and a `Schedule`, and fills a buffer of PCM frames on request.
  It handles:
  - **Pitch:** varispeed by linear interpolation at the event's rate.
  - **Joins:** a 30 ms legato crossfade between notes, and a release fade at
    the end.
  - **Drone:** looped Sa, Pa and upper Sa at a lower level, crossfading over
    the steady part of each clip so the 2.5 s samples can play indefinitely.
  - **Click:** a short synthesized tick, accented on the claps (`x`). No
    sample needed.
  - **Mixing:** the drone and harmonium levels.
- Why shared:
  - Timing is the same on both platforms to the sample.
  - It can be tested on the JVM by rendering to WAV and measuring pitch and
    onsets, the same checks used for the demo.
  - SoundPool plus coroutine delays (the ukulele app's approach) drift and
    jitter, which matters for tala.
- Memory: 37 notes × 2.5 s × 44.1 kHz as Float is about 16 MB once decoded.
  That's acceptable. Could be halved with 16-bit storage if needed.

### 5.2 Platform glue

| | Android | iOS |
|---|---|---|
| Decode | `MediaExtractor` + `MediaCodec` → PCM, once at first use, off the main thread | `AVAudioFile` → `AVAudioPCMBuffer` |
| Output | `AudioTrack` (float, streaming) fed by a dedicated thread calling the renderer | Renders ahead on a background queue into about 100 ms buffers scheduled on an `AVAudioPlayerNode` |
| UI sync | Cursor and beat events posted against `AudioTrack` playback position | Events matched against the player node's sample time |
| Audio focus / session | Request focus; pause on loss | `AVAudioSession` `.playback`; pause on interruption |

Kotlin code never runs in the iOS real-time callback. Rendering ahead avoids
garbage-collection pauses on the audio thread. Starting playback takes about
100–200 ms, which is fine for a player.

## 6. Screens

The same structure on both platforms: Compose + Material 3 on Android, SwiftUI
on iOS.

1. **Library:** exercises grouped by set (Plain scale, Sarali, Madhya Sthayi,
   Janta, Vakra Janta, Dhattu), each with its number and a one-line notation preview.
2. **Player:**
   - **Notation:** one row per avarta, with the tala marks (`x 1 2 3 x v x v`)
     above each akshara cell. The current akshara is highlighted, and the
     current swara within it at kala 2 and above.
   - **Transport:** play/stop, repeat (once, or loop), and a count-in of one
     clapped avarta. The workbook says to clap a cycle first.
   - **Controls:** tempo, kala (1–4), drone on/off, click on/off.
3. **Settings:** Sa (note name plus octave, with kattai shown alongside),
   tuning **[Decision 1]**, drone and harmonium levels.

Sa, tempo, kala and toggles persist: `SharedPreferences` on Android,
`UserDefaults` on iOS. Default Sa is C3, as in the workbook.

### 6.1 Accessibility

- All controls have labels and values: the tempo says "60 beats per minute"
  and kala says "Second kala".
- Each avarta row reads as text, e.g. "Avarta 1: S R S R, S R, G M".
  A karvai is read as "hold".
- No live announcements during playback, because they would talk over the
  music. The highlight is visual only. A screen reader user hears the
  harmonium and click instead.
- With reduce motion on, the highlight moves without animation.
- Play/stop is reachable without scrolling and has a hardware-keyboard /
  media-button shortcut where the platform supports it.

## 7. Sa range

- Exercises in scope span Sa to S′, so the highest note is Sa + 12 semitones.
  The bank tops out at C5, so the highest Sa is C4.
- The workbook's lowest suggestion is B♭2. Starting the picker at G2 leaves
  room for deep voices.
- So the picker offers G2–C4. That covers typical men's and women's ranges with
  no gender setting.
- Later Thara exercises will need up to about tara Pa, so a high Sa will need
  samples above C5 (the source pack goes to D5). That's handled when Thara is confirmed.

## 8. How the app differs from the workbook

| Topic | Workbook | App |
|---|---|---|
| Reference | Keyboard, equal temperament | Harmonium; just intonation by default **[Decision 1]** |
| Sa | C3, with B2, B♭2 or C♯3 as alternatives | Any semitone G2–C4 |
| Excluded sets | Printed with warnings | Not shipped until confirmed |
| Text | Personal workbook prose | **[Decision 2]** |

## 9. Decisions

1. **Tuning.**
   - Default: just intonation (`Swara.ratio`), which matches a tanpura.
   - Option: equal temperament, to match a keyboard such as the PSS-F30.
   - The two differ by up to about 14 cents for R1, G3, D1 and N3.
     *Proposed: just by default, equal as a setting.*
2. **Workbook prose.**
   - The workbook README marks it personal and not for redistribution.
   - *Proposed:* the app's help text is written fresh. The workbook supplies
     only structure and the traditional exercise sequences.
   - Confirm, or allow reusing your text.
3. **Default tempo:** 60 aksharas per minute. The workbook suggests 50–60 for first practice.

## 10. Milestones

Each milestone ends with tests passing on the JVM, Android host, iOS simulator
and Xcode, and is reviewable on its own.

| # | Milestone | Done when |
|---|---|---|
| M1 | `Raga`, `Tala`, parser, catalog, `Schedule` (shared) | All in-scope exercises parse and validate; timing tests pass |
| M2 | `SampleRenderer` (shared) | Rendered WAV of Sarali 1 has every note within ±1 cent and onsets within ±1 ms of the schedule (JVM test) |
| M3 | Android: decode, `AudioTrack` output, Library and Player screens | Plays every exercise in sync; TalkBack pass |
| M4 | iOS: decode, `AVAudioEngine` output, Library and Player screens | Same as M3; VoiceOver pass |
| M5 | Settings, persistence, count-in, polish | Parity check across both apps |

## 11. Afterwards

In rough order:

1. Microphone pitch monitor.
2. Guided pitch-matching loop (the workbook's pattern cards A–F).
3. The 14-day plan with the 0–3 score and an on-device tracker.
4. Thara and Mandhra once confirmed.
