# Plan: recordings with an external tanpura

Status: **in progress** (2026-11-15). Step 1 (shared) is built; the first
real-tanpura test changed the analysis (*Results*).

A follow-on to [`plan-recordings.md`](plan-recordings.md). The app plays no
drone while recording (decided 2026-11-15), but a student may sing with
their own tanpura: an electronic tanpura, a tanpura app on another device,
or a real one. Its Sa and Pa then sound under the voice for the whole
recording, and today's analysis isn't built for that.

## What the student decided (2026-11-15)

| Question | Decided |
|---|---|
| Setting | A switch on the Record screen, **External tanpura playing**. It is **remembered** for the next recording, saved with each recording, and can be changed on the recording's screen (the swaras are read again at once). |
| Learning the tanpura | With the switch on, recording starts with **about 3 s of tanpura alone**: "Listening to your tanpura…", then a cue, "Now sing". |
| The tanpura's Sa | The app works out the tanpura's Sa and **offers** it: "Your tanpura is at C♯3 +12¢ · Use this Sa". Nothing changes unless the student taps it. |

## What goes wrong today with a tanpura playing

- **Phantom notes.** In the pauses YIN and SwiftF0 read the tanpura, so
  Sa and Pa appear that weren't sung.
- **The voice gate fails.** The transcriber's floor is the median level of
  the frames with no pitch (the room between phrases). With a drone, those
  frames are pitched and loud, so the floor is wrong.
- **Soft notes lost.** Where the voice is softer than the tanpura, the
  detectors follow the tanpura.

## How it works

### Recording

- The switch sits on the Record screen under the Sa line, with one line of
  help: "Let it play for 3 seconds before you sing."
- With it on, the first **3 s** (`TANPURA_LISTEN_SECONDS`) show "Listening to
  your tanpura…" and a short countdown, then "Now sing". VoiceOver and
  TalkBack announce "Now sing", and the phone gives a light haptic tap. The
  timer still counts from 0, and the audio of the first 3 s is kept (playback
  includes it).
- If the student sings during the 3 s, the profile is still made from the
  quietest steady part of it (below), and the swaras ignore those 3 s.

### Analysis (`shared/`, pure Kotlin)

With the switch on, **the pitch is SwiftF0's alone** (`RecordingAnalyzer(...,
tanpura = true)`): YIN locks onto periods the voice shares with the
tanpura's strings (see *Results*). Without the model it falls back to YIN.
The level of each frame is kept as before. Because the pitch is read
differently, changing the switch afterwards re-analyses the recording from
its audio (a few seconds, with the progress bar); `changeTanpura` marks it
for analysis so an interrupted one happens on the next opening. The
profile and the voice gate work from the saved frames:

1. **`TanpuraProfile.from(track, listenSeconds)`** reads the first 3 s of
   frames:
   - **Level:** the median RMS of those frames (the tanpura's loudness at
     the mic).
   - **Its Sa:** the pitches heard, folded into one octave, give a histogram
     of pitch classes. The tanpura's strings are Pa (or Ma / Ni) and Sa, so
     the two biggest peaks are a fifth apart (or a fourth). Its Sa is the
     lower note of the fifth. Its exact pitch is the median of the frames in
     that peak, in Hz with cents. If there is no clear pair, there's no
     offer, and only the level is used.
2. **`SwaraTranscriber.transcribe(..., tanpura: TanpuraProfile?)`** with a
   profile:
   - Ignores the listening seconds.
   - **Voice gate:** a frame is voice only if its level is clearly above the
     tanpura, at least `VOICE_OVER_TANPURA` (×1.6, about +4 dB; tuned on the
     test mixes and real recordings). This replaces the room-floor estimate,
     like `BleedModel`'s margin over the app's own drone.
   - **Drone pitches:** a frame at the tanpura's Sa or Pa (any octave, within
     ±50¢) counts as voice only if it is louder still (×2.5). The voice
     singing Sa over a Sa drone adds to the level, so a sung Sa still
     passes; the drone alone doesn't.
3. **The offer** appears when the tanpura's Sa differs from the recording's
   Sa by more than 10¢: "Your tanpura is at C♯3 +12¢. Use this Sa?" Tapping
   it sets the recording's Sa and fine-tune (`changeSa`).

### Storage

`Recording` gains `externalTanpura: Boolean = false`. The profile isn't
stored: it is computed from the saved frames whenever the swaras are read
(quick: 3 s of frames). The remembered switch is a preference beside the
Sa (Android `PracticePreferences`, iOS `UserDefaults`).

### The recording's screen

- A row, **External tanpura**, with a switch. Changing it reads the swaras
  again at once.
- The offer, when there is one, as a line with a button under the Sa row.
- The pitch trace shades the listening seconds and draws the tanpura's Sa
  as a faint line, so the student sees what was ignored.

## Results (2026-11-15)

The bundled tanpura loop at Sa A♯2 (`audio/tanpura/46.flac`) mixed under a
synthetic voice singing Sarali up, a note per 0.5 s with breaths, after 3 s
of tanpura alone:

- **The profile works:** Sa A♯ and its Pa are read from the 3 s.
- **YIN fails, even with the tanpura 6 dB under the voice:** it reads
  mandra Sa and Pa through most notes (`TanpuraMixTest`, YIN only). The
  tanpura's low Sa string (58 Hz) is a sub-octave of the sung Sa and its Pa
  lines up with sung notes, so YIN finds their shared period. A level gate
  can't fix a pitch read wrongly.
- **SwiftF0 follows the voice** (the comparison tool's model, whole-file
  chunks):

  | Tanpura vs voice | Notes read right (of 8) |
  |---|---|
  | 6 dB under | **8** |
  | level | 5 (Sa misread, two missed) |
  | 6 dB over | mostly lost |

  Hence SwiftF0's pitch alone with the switch on, and the guide's advice to
  keep the tanpura a little quieter than the voice at the mic.

## Accessibility

- The switch is a labelled toggle with its help text in its description.
- "Now sing" is announced (iOS `UIAccessibility.post(.announcement)`,
  Android `announceForAccessibility` / a polite live region), so a student
  using a screen reader knows when to start.
- The offer's button reads "Use the tanpura's Sa, C♯3 plus 12 cents".
- `AccessibilityTest` and `PurandaraUITests` cover the switch, the
  listening state and the offer.

## Tests

- `commonTest`, using pitch tracks built directly:
  - A drone of Sa and Pa frames at a steady level, with a voice singing
    Sarali above it: the swaras are the Sarali, with no phantom Sa or Pa in
    the pauses.
  - A sung Sa over the Sa drone is still a note.
  - A voice softer than the tanpura gives no notes rather than wrong ones.
  - The profile's Sa from Sa + Pa, Sa + Ma, and a tanpura detuned by +12¢;
    no Sa from noise.
  - Switching the setting on an existing track changes the swaras.
- **Real tanpura sound:** a development-only jvmTest (needs `ffmpeg`, like
  `RenderDemoTest`) mixes the bundled tanpura loops (`audio/tanpura/`) under
  the synthetic voice at three levels (tanpura 6 dB under the voice, level
  with it, 6 dB over), runs the shared analyser and checks the swaras. The
  results go in this plan.
- **The student's recordings** with their own tanpura on the iPad, to tune
  `VOICE_OVER_TANPURA` and the drone-pitch margin.

## Limits (said in the guide)

- A tanpura louder than the voice at the mic costs notes: put it a little
  further from the phone than you are.
- The tanpura should play for the whole recording. One started midway
  isn't learnt.

## Website and docs

The Recordings guide page gains a section, *Singing with your own tanpura*.
The plan-recordings decision table notes the follow-on.

## Not in this plan

Removing the tanpura from the audio (playback stays as recorded); the app's
own tanpura while recording; detecting a tanpura without the switch.

## Build order

1. Shared: `TanpuraProfile`, the transcriber with a profile, the
   `Recording` field, and tests (including the bundled-tanpura mixes).
2. Android: the switch, the listening state, the recording screen's row and
   the offer.
3. iOS: the same.
4. Accessibility tests, the guide, AGENTS.md.
5. Tune on the student's recordings with their tanpura.
