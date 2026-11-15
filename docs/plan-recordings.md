# Plan: recordings with approximate swaras

Status: **built** (2026-11-15) on both apps. Checked with the debug
synthetic singer on the Android emulator and the iOS simulator (a sung
Sarali Varisai 1 reads back as its swaras); not yet tried with a real voice,
so the thresholds still need tuning on the student's own recordings.

Changes from the plan while building:

- The noise floor is capped at a quarter of the median voiced level: with
  little silence, the pitchless frames are loud consonants and note changes,
  not the room, and the uncapped floor silenced the whole recording.
- On iOS each phrase is one wrapping text (the sounding swara highlighted),
  and tapping a phrase plays from its start; the recording screen is a
  scroll view, not a list (the Dynamic Type audit). Android keeps a tappable
  swara per note.
- Deleting from the list by swiping is iOS only; on both apps a recording
  can be deleted from its own screen.
- `-resetSettings YES` (iOS) also clears recordings, for the UI tests.
- Recordings opens from a button in the *Your Sa* card, beside Practise Sa
  and Find my Sa, not a card of its own: a separate landing-page row failed
  the iOS Dynamic Type audit on CI (larger text pushed it out of view).

The student records themselves singing, and the app shows the swaras it
heard (approximately) and the pitch trace on the swara lines. Recordings are
kept on the device with a title and can be played back with the swaras
highlighted. Nothing is shown live.

## What the student decided (2026-11-15)

| Question | Decided |
|---|---|
| Sa and raga | Saved with the **current Sa**. Sa and an optional **raga** can be changed afterwards; the swaras update at once (the pitch frames are saved, so nothing is re-recorded or re-analysed). Without a raga, default names (R2, G3…). |
| Playback | **Yes**: play, pause and seek, with the current swara highlighted. |
| Tanpura | **No drone while recording.** A student who wants one uses an external tanpura. |
| Format | Whatever both platforms handle natively: **AAC in .m4a** (about 1 MB a minute at 96 kbps mono). |
| Title | **Auto title**: `Mayamalavagowla · Sa D · 15 Nov, 6:42 pm` (no raga: `Sa D · 15 Nov, 6:42 pm`). Can be renamed. |
| Sharing / export | **None.** Recordings never leave the app. |
| Where it lives | A **Recordings** row on the landing page, next to Shruti practice. |

## How it works for the student

1. **Landing page → Recordings** opens the list: title, length and date for
   each, newest first, with a **Record** button at the top. If there are none,
   a short explanation is shown instead.
2. **Record** opens the record screen with a large Record/Stop button, the
   elapsed time, a level meter (so the student can see the mic hears them; no
   pitch or swaras) and the current Sa. If anything is playing, such as the
   landing page tanpura, it stops.
3. **Stop** saves the audio, analyses it (with a progress bar; target: under
   a quarter of the recording's length on the iPad mini) and opens the
   recording.
4. **The recording screen** shows:
   - the title (tap to rename),
   - **Sa** and **Raga** (none, or one of the app's ragas), which can be
     changed. They use the existing Sa picker with its fine-tune,
   - **the swaras**, one line per phrase (see Analysis), with out-of-raga
     notes marked,
   - **the pitch trace** on the swara lines (`PitchLane`), scrollable along
     time,
   - **play / pause**: during playback the current swara is highlighted and
     the trace follows a cursor. Tapping a swara or the trace seeks there,
   - **Delete** (with a confirmation).
   - A line saying the swaras are **approximate**: gamakas and slides are
     read as their steadiest notes, so check them against the trace.

Limits: at most **10 minutes** per recording (a warning at 9:30, then it
stops and saves). If the app leaves the foreground, a call comes in or the
audio route changes, recording stops and keeps what it has. The screen stays
awake while recording.

## Analysis (`shared/`, pure Kotlin)

The audio is decoded back to PCM by the platform, then turned into
`PitchFrame`s by `PitchPipeline` at the usual hop (512 samples). It runs
**offline**, so SwiftF0 can run on more windows than the live 1-in-5. We'll
measure on the iPad mini and use every window if it meets the time target,
otherwise every second window. A recording analysed this way is saved with
an `analysisVersion`, so it can be re-analysed if the analyser improves.

`recording/SwaraTranscriber.kt` turns frames into notes, given Sa (Hz) and
an optional raga:

1. **Voice gate:** a frame is voiced if it has a pitch and its RMS is above
   the recording's own noise floor (a low percentile of its RMS plus a
   margin). With no drone, there's no bleed to model.
2. **Steady stretches:** walk the voiced frames as cents from Sa. A
   stretch is a run of frames staying within ±50¢ of one swarasthana for at
   least **100 ms**. Frames that aren't in a stretch are movement (slides,
   gamakas). They stay in the trace but aren't notes.
3. **Merge:** adjacent stretches of the same swara and octave, separated by
   a gap under **80 ms** (a consonant or a flicker), become one note.
4. **Name:** the median pitch of each note goes through `SwaraMapper` for
   the swara, octave, cents off and in-raga flag.
5. **Phrases:** a silence of **0.6 s** or more starts a new line.
6. **Length:** a note longer than twice the recording's median note length
   gets a `,` for each further multiple (at most 3), so held notes read as
   held: `S R G , M P , ,`.

```kotlin
data class HeardNote(
    val swara: Swara, val octave: Int,
    val startSeconds: Double, val durationSeconds: Double,
    val centsOff: Double, val inRaga: Boolean,
)
data class Transcription(val phrases: List<List<HeardNote>>)

object SwaraTranscriber {
    fun transcribe(frames: PitchTrack, saHz: Double, raga: Raga?): Transcription
}
```

The thresholds are constants, tuned against the test tracks and the
student's real recordings.

## Storage (on device only)

In the app's private files: `recordings/<id>.m4a`, `recordings/<id>.pitch`
and one index, `recordings.json`. These are pure Kotlin models like
`PracticeHistory`: immutable, the platforms persist the encoded text, and an
unreadable index gives an empty list rather than a crash.

```kotlin
@Serializable
data class Recording(
    val id: String,                 // random UUID
    val createdMillis: Long,
    val durationSeconds: Double,
    val saMidi: Int, val saCents: Int,
    val ragaKey: String? = null,
    val customTitle: String? = null,
    val analysisVersion: Int,
) {
    fun title(timeZone: TimeZone): String = customTitle ?: autoTitle(...)
}
class RecordingLibrary(recordings: List<Recording>) { add, rename, setSa, setRaga, delete, encode/decode }
```

- The **auto title is derived, not stored**, so choosing a raga later
  updates it. Renaming sets `customTitle`, and clearing the name goes back
  to the auto title. The date format follows the device locale.
- `.pitch` is a small binary file in a versioned format: hop seconds, then
  per frame a Float32 Hz (0 = unvoiced) and a Float32 RMS. 10 minutes is
  about 400 KB. Encoded and decoded in `commonMain`.
- Deleting removes all three entries. On launch, files without an index
  entry are cleaned up, and so are index entries without audio.
- Recordings are part of the app's data and follow the platform's normal
  backup rules, like the settings.

## Platforms

Same features on both. Platform code only captures, encodes, decodes and
plays. Everything else is shared.

| | Android | iOS |
|---|---|---|
| Capture | `AudioRecord`, `UNPROCESSED` source where supported, else `VOICE_RECOGNITION` (no AGC, no echo cancellation), mono | Its own `AVAudioEngine` input tap, session `.record` with mode `.measurement` (no AGC or voice processing), mono |
| Encode | `MediaCodec` AAC-LC → `MediaMuxer` .m4a, on the capture thread | `AVAudioFile` with AAC settings, written from the tap |
| Decode for analysis | `MediaExtractor` + `MediaCodec` | `AVAudioFile` read into PCM buffers |
| SwiftF0 | existing `NeuralPitchSupervisor` path | existing ONNX path |
| Playback | `MediaPlayer` | `AVAudioPlayer` |
| Mic permission | existing request flow | existing request flow |

The recorder doesn't use the player's engine on iOS: no drone means no echo
cancellation is needed, and a separate engine keeps unprocessed input away
from the player's voice-processing switching.

Debug support so both can be checked on emulators and simulators:
`-open recordings` on iOS and a matching route on Android. The synthetic
singer (`-singer` / `--ef singer`) feeds the recorder too, so a recording
of a known varisai can be made without a voice.

## UI and accessibility

- **List rows:** one element read as "title, 2 minutes 14 seconds, 15
  November". Delete is a swipe action plus an accessibility action (iOS
  `accessibilityAction`, Android `customActions`), always with a
  confirmation.
- **Record button:** its label and state change (Record / Stop recording).
  The elapsed time updates quietly (iOS `.updatesFrequently`, Android a
  polite live region at most every 10 s). The level meter is decorative.
- **Swaras:** each phrase is one element read naturally ("Sa, Ri, Ga held,
  Ma, Pa; tara Sa"), with out-of-raga notes said as such. During playback
  the highlight doesn't move focus.
- **Trace:** custom drawing with an `accessibilityValue` summary ("38
  notes in 6 phrases, from mandra Pa to tara Sa").
- **Playback:** a labelled play/pause and a slider for position (adjustable
  action on iOS, `progressBarRangeInfo` + `setProgress` on Android).
- **Reduce motion:** the trace cursor jumps instead of scrolling smoothly.
- Headings on each screen. iOS uses `Varisai.shortTitle`-style truncation
  rules for the top bar, so the full title is in the content, not the bar.
- `AccessibilityTest` and `PurandaraUITests` cover the list, record and
  recording screens.

## Tests

- `commonTest`: the transcriber on synthetic tracks: steady notes, a held
  note, a slide between two notes (no note in between), a kampita on Ri (one
  note, not R-G-R-G), an octave jump, silence splitting phrases, noise below
  the floor, Sa changed after the fact, raga naming (R2 vs G1) and
  out-of-raga flags. Property tests: notes never overlap, and they stay
  within the track and in time order.
- `RecordingLibrary` and `.pitch` encode/decode round trips, corrupt
  input, auto title with and without a raga, rename and clear.
- End to end on device/emulator: a synthetic-singer recording of Sarali 1
  is transcribed as its swaras.

## Website and docs

A guide page `site/pages/guide/recordings.html` (what it does, why the
swaras are approximate, that recordings stay on the device), plus a mention
on the landing page and in the README. AGENTS.md's status and code map are
updated when built.

## Not in this plan

Sharing, export or import; a drone while recording; live swaras; naming
gamakas; tala or rhythm detection; comparing a recording with a varisai;
trimming or editing; background recording.

## Build order

1. Shared: `PitchTrack` + `.pitch` format, `SwaraTranscriber`,
   `RecordingLibrary`, with tests.
2. Android: recorder, decode + analysis, list / record / recording screens,
   playback.
3. iOS: the same.
4. Accessibility tests on both, the site guide, AGENTS.md / README.
5. Tune the thresholds on the student's own recordings on the iPad.
