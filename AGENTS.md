# AGENTS.md: AI agent instructions for Purandara

Purandara is a **free, fully offline** Kotlin Multiplatform app (Android + iOS)
for practising Carnatic music. It's named after Purandara Dasa, the *Pitamaha*
of Carnatic music.

**Status:** in active development. Built so far (each planned in `docs/` first):
the varisai player (`docs/plan.md`), sing-along with pitch feedback
(`docs/plan-pitch-monitor.md`), the landing page (`docs/plan-landing.md`), and
Shruti practice, strictness and choosing Sa (`docs/plan-shruti.md`), the
alankarams and Malahari gitams (`docs/plan-alankaram-gitam.md`), and the
gitams, swarajatis and varnams (`docs/plan-gitam-swarajati-varnam.md`: the
varnam stage is built — rests, per-section speed, the two-kalai strip and
all four varnams; they wait on their second-source checks, issue #7), and
the notation laid out by tala cycle at every speed
(`docs/plan-speed-layout.md`).
New features are planned and discussed before they are built; do not add
features speculatively.

## Reference documents

- [`docs/resources.md`](docs/resources.md) — websites, books, recordings and
  datasets to check notation, theory, tuning and audio against, including
  the sources recorded in closed issues #1–#7.
- [`ATTRIBUTION.md`](ATTRIBUTION.md) — every bundled third-party asset, its
  licence and the changes made.
- [`README.md`](README.md) — feature summary, layout, build commands and the
  docs index.
- The feature plans: [`docs/plan.md`](docs/plan.md) (varisai player; 3.2 is
  the notation grammar),
  [`docs/plan-pitch-monitor.md`](docs/plan-pitch-monitor.md),
  [`docs/plan-landing.md`](docs/plan-landing.md),
  [`docs/plan-shruti.md`](docs/plan-shruti.md),
  [`docs/plan-alankaram-gitam.md`](docs/plan-alankaram-gitam.md),
  [`docs/plan-gitam-swarajati-varnam.md`](docs/plan-gitam-swarajati-varnam.md) and
  [`docs/plan-speed-layout.md`](docs/plan-speed-layout.md).
- [`docs/tuning.md`](docs/tuning.md) — Just (tanpura) vs Equal (keyboard):
  exact ratios, cents, frequencies and the implementation. The musicians'
  version is `site/pages/guide/tuning.html`; update both if `Swara.ratio`
  changes.
- [`models/README.md`](models/README.md) — the SwiftF0 model and its checksum.
- [`docs/branding.md`](docs/branding.md) — the logo, icons, palette and type;
  regenerate icons with `python3 tools/brand/build.py`, never hand-edit them.
- [`site/`](site/) — the end-user website and guide; keep it in step with the
  app when features change.

## Adding notation (alankarams, gitams, swarajatis, varnams)

- **Two independent sources must agree**, or the piece waits; never write
  notation from memory. The per-piece sources and their status are in
  [`docs/resources.md`](docs/resources.md); the main ones are karnatik.com
  (primary), Shivkumar Kalyanaraman's archive (shivkumar.org), *Sangeeta
  Bala Padam* Parts 1–2, the Gurukulam & SF collection and the NIOS
  textbook. Where they differ, keep the primary and list the variant (or
  ask the user, who decides pathantarams).
- Re-encode in the app's grammar (`docs/plan.md` 3.2). karnatik.com marks
  octaves by case (capital = upper) and a trailing dot (lower); shivkumar.org
  often marks *length* by case (capital = two cells, `;` = two-cell hold, a
  stray `-` is a word separator) and its octave marks are lost in its HTML.
- List every fix and every source difference in the notation file's header,
  and keep the cross-check scripts in `tools/notation/` passing with the
  expected output recorded there. Credit sources in ATTRIBUTION.md.
- Words are simple Latin, long vowels single (`sri gananatha`), one syllable
  per cell, `-` for a held syllable.
- Tests catch typos: no leap over an octave in a piece, words align with
  cells, every note playable at every Sa the piece allows
  (`Varisai.playableSaMidis`; a piece above the samples gets a lower ceiling).

## Hard constraints

- No network dependencies. Every feature must work with the radio off.
- No analytics, tracking, or telemetry.
- No ads or monetisation code without prior discussion.
- No third-party SDKs without prior discussion.

  **Approved exceptions** (discussed and agreed; not precedent for others):

  | Dependency | Why | Approved |
  |---|---|---|
  | ONNX Runtime (MIT) + SwiftF0 pitch model (MIT, bundled) | Neural pitch checker for sing-along: corrects YIN octave errors. Runs fully offline; the iOS xcframework is downloaded at build time only. | 2026-11-06 (`docs/plan-pitch-monitor.md`) |
- Never commit `keystore.properties`, API keys, or secrets.
- Credit every bundled third-party asset in `ATTRIBUTION.md` in the same change,
  even CC0/public-domain ones: name each contributor, link the source, state the
  licence and what was modified.
- Accessibility is a first-class requirement (TalkBack / VoiceOver). Breaking it
  is treated as seriously as breaking functionality.

## Architecture

| Module | Rules |
|--------|-------|
| `shared/src/commonMain` | Pure Kotlin only, with **no platform imports**. Music theory, raga/tala models, and practice logic go here so both apps share it. Platform needs go through `expect`/`actual` in `platform/`. |
| `app/` (Android) | Jetpack Compose + Material 3 only (no XML layouts). Single activity. ViewModels expose `StateFlow`. Honour `LocalReduceMotion` for all animations. |
| `iosApp/` (iOS) | SwiftUI, `@StateObject`/`@Published` ViewModels. Honour `accessibilityReduceMotion`. Kotlin `object`s are reached as `Foo.shared`, top-level functions as `<File>Kt.fn()` (named after the file holding the `actual`, e.g. `Platform_iosKt.platformName()`). |
| `audio/` | Bundled samples, one copy shared by both apps (Android assets source dir; iOS bundle folder). Generated by `tools/samples/` — don't hand-edit. `BundledSamplesTest` (jvmTest) keeps it in sync with `HarmoniumBank` and `TanpuraBank`. |

### Code map

| Where | What |
|---|---|
| `shared/.../domain/` | `Swara`, `Raga` (+ `Ragas`), `Tala` (+ `Talas`: suladi talas from angas and jati, after ThaaLam), `Tuning`, `Shruti` (Sa choices, fine-tune, `VoiceRange`), `HarmoniumBank`, `TanpuraBank` |
| `shared/.../domain/varisai/` | `Varisai.playableSaMidis` (the Sa choices at which a piece fits the harmonium samples; the players offer the nearest when not); notation parser (headers, sections, repeats, words: grammar in `docs/plan.md` 3.2) and the catalog (`catalog/` holds the notation text: varisais, `Alankaram.kt`, `Gitam.kt`, `Swarajati.kt`, `Varnam.kt`, each with its sources and fixes in the header); `WordsDisplay` |
| `shared/.../domain/varisai/NotationLayout.kt` | The notation by tala cycle at the chosen speed (`forPractice`): cycles → rows → beat groups of `CellRef`s, `locate` for the highlight, speech with commas at the angas. Both apps draw the notation and the mirrored tala strip from it (`BeatRow`; iOS `ColumnsLayout`) |
| `shared/.../domain/varisai/Contour.kt` | Melodic shape of an exercise (the small line in the exercise list) |
| `shared/.../shruti/` | Shruti practice: `ShrutiSession` (room check, readings, hold), `HoldTracker`, `NoteCatcher` + `SaFinder` + `FindSaGuide` (Find my Sa) |
| `shared/.../practice/` | `PracticeHistory`: practised / best sing-along per exercise, continue and next up (stored on device as JSON) |
| `shared/.../domain/schedule/` | `Schedule`: timed note/beat/cursor events, `cursorAt`/`beatAt`; `ofSection` plays one section (`Varisai.sections`) with the piece's avarta numbers |
| `shared/.../audio/` | `SampleBank`, `SampleRenderer` (all mixing; platforms only decode and output) |
| `shared/.../pitch/` | `PitchDetector` (YIN), `AudioResampler`, `NeuralArbitrator`: ported from ukulele-companion with the interpolation sign fix |
| `shared/.../singalong/` | `PitchPipeline`, `SwaraMapper` (+ `Zone`, `Strictness`), `SingAlongClock`, `BleedModel` (room noise + leakage), `NoteScorer`, `SingAlongSession`: sing-along scoring |
| `app/.../audio/` | FLAC decode (`HarmoniumSamples`), `VarisaiAudioPlayer` (`AudioTrack` on a worker thread) |
| `app/.../audio/` (sing-along) | `MicSource` (`AudioRecordMicSource` with echo cancellation; debug-only `SyntheticSingerMicSource`), `SingAlongEngine`, `PitchListener` (Shruti practice, Find my Sa), `NeuralPitchSupervisor` |
| `app/.../viewmodel/` | `PracticeViewModel`, `SingAlongState`, `ShrutiState` |
| `app/.../ui/` | `PurandaraApp` (routes: landing, player, Shruti practice, Find my Sa), `library/` (landing page), `player/`, `shruti/`, `SaControls.kt` (Sa picker, fine-tune, strictness) |
| `iosApp/Purandara/Audio/` | FLAC decode (`HarmoniumSamples`), `VarisaiAudioPlayer` (renders ahead on a queue into `AVAudioPlayerNode` buffers; no Kotlin on the real-time thread) |
| `iosApp/Purandara/ViewModels/`, `Views/` | `PracticeViewModel`; `LibraryView` (landing page: continue, Sa + tanpura, sets with contours), `PlayerView` (+ settings sheet, `SaChoiceList`), `NotationView` (+ `TalaStripView`), `SingAlongViews`, `ShrutiView` (Shruti practice, Find my Sa) |
| `iosApp/Purandara/Audio/PitchListener.swift` | Mic tap buffers (on the player's engine, for echo cancellation) → windows → pitch frames; debug synthetic singer. Used by `SingAlongEngine`, Shruti practice and Find my Sa |
| `shared/.../singalong/Display.kt` | Swara labels (R₁, S′) and mark glyphs shared by both apps |
| `shared/.../singalong/PitchLane.kt` | The sing-along pitch lane's swara lines and range, from the piece's own notes |
| `tools/notation/crosscheck_gitams.py`, `crosscheck_swarajatis.py`, `crosscheck_varnams.py` | Compare `Gitam.kt` / `Swarajati.kt` / `Varnam.kt` with their web sources (not part of the build) |
| `tools/release/`, `store-listing/` | iOS App Store release: `ios_upload.sh` (archive, check, upload), `ios_screenshots.sh` (store screenshots from the simulators), the listing text and screenshots. Steps in the `ios-release` skill (`.claude/skills/ios-release/`) |
| `shared/src/iosMain/.../audio/FloatBridge.kt` | Bulk float copies between Swift buffers and Kotlin arrays |
| `tools/brand/` | Logo sources (wordmark and tagline outlines) and `build.py`, which generates the Android adaptive icon, the iOS app icons (light, dark, tinted), the favicon, `mark.svg`, the share image and the lockups from one geometry |
| `site/` | The end-user website and guide (https://baijum.github.io/purandara/): `pages/` HTML fragments with front matter, `templates/`, `static/` (CSS, the hero script, self-hosted fonts, app screenshots), `build.py` (standard library only) → `site/_build`. Published to GitHub Pages by `.github/workflows/site.yml`. Keep the guide in step with the app when features change. |

Swift interop notes: a Kotlin `Char` arrives in Swift as a number (use an enum's
`name` instead of `symbol`); a combining dot below (Ḍ) is clipped by `Text` and
fails the audit, so a lone lower-octave swara draws its dot as a shape; launch
arguments arrive as strings (`UserDefaults.integer(forKey:)`, not `as? Int`).
Kotlin default arguments are not visible from Swift (use the
small factories such as `PerformanceSettings.create(...)`, `RenderOptions.practice(...)`);
Kotlin names starting with `copy`/`new`/`init` get a `do` prefix in Swift, so avoid them
in API meant for iOS. Kotlin enum entries named after Objective-C keywords get a `_` suffix
(`SHORT` → `short_`); computed properties such as `zone` are methods in Swift (`zone()`).
Closures that a background queue calls must not be created inside `@MainActor` code (Swift
traps on the isolation check): build them in a `nonisolated` helper, and pass Kotlin values
to the main actor in a `Handoff`. Debug launch arguments `-open <varisai-id> -autoplay YES` open
(and play) an exercise for screenshots (`-open shruti` / `-open find-sa` open the Sa
screens); add `-singAlong YES -singer <cents>` for a synthetic singer on iOS, and
`-resetSettings YES` to start from default settings and an empty history (then `-sa <midi>` to pick a Sa). On Android (debug builds),
`adb shell am start -n com.baijum.purandara/.MainActivity --ef singer 0` replaces
the microphone with a synthetic singer (the value is cents off target), so
sing-along can be exercised on an emulator.

Sing-along on an iOS device: debug builds print a once-a-second `[singalong]`
line (mic and output levels, leakage, echo cancellation, route). Capture it with
`xcrun devicectl device process launch --console --device <id> com.baijum.purandara.ios -- -open sarali-1 -singAlong YES`.

Instrumented tests: if `connectedDebugAndroidTest` reports 0 tests or finishes
in seconds, the emulator is probably out of storage (the app carries ONNX
Runtime); check `app/build/reports/androidTests/connected/debug/index.html`
and `adb shell df -h /data`.

### Sound model

The drone is a real tanpura (pa SA SA sa): `audio/tanpura/<sa-midi>.flac`, seamless
loops at Sa A♯2/D3/F♯3/A♯3 prepared by `tools/samples/prepare_tanpura.py`;
`TanpuraBank.playbackFor(saHz)` picks the nearest and a rate within ±3 semitones.
The harmonium drone remains only as a fallback if the loops fail to load.

Swaras use the CC0 harmonium bank: `audio/harmonium/<midi>.flac`, MIDI 36–74 (C2–D5),
each exactly 12-TET at A4 = 440 Hz. `HarmoniumBank.playbackFor(note, saHz)` picks the
nearest sample and a playback rate (within ±half a semitone) that hits the swara's
just-intonation ratio (`Swara.ratio`). Android plays at that rate via `SoundPool`;
iOS via `AVAudioUnitVarispeed`. FLAC is stored uncompressed in the APK (`noCompress`)
so `AssetManager.openFd()` works.

Keep feature parity between Android and iOS. Every user-visible feature should land on both.

## Accessibility checklist

- Android: headings use `semantics { heading() }`; icons have a `contentDescription` (or `null` if decorative); custom drawing exposes semantics.
- iOS: headings use `.accessibilityAddTraits(.isHeader)`; controls have labels and hints; custom drawing exposes `accessibilityValue`.
- Long-press actions declare `onLongClickLabel` (Android) / `accessibilityHint` (iOS).

## Build and test

```bash
./gradlew :shared:jvmTest          # shared logic tests
./gradlew testDebugUnitTest        # Android unit tests
./gradlew assembleDebug lintDebug detekt
bash iosApp/setup_onnxruntime.sh   # once, before the first iOS build
cd iosApp && xcodegen generate     # after adding/removing Swift files
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Add tests in `shared/src/commonTest` for every piece of domain logic (kotlin.test + Kotest property tests).

Accessibility checks (run before UI changes are merged):

```bash
./gradlew :app:connectedDebugAndroidTest   # AccessibilityTest: labels, roles, states, 48dp targets (needs an emulator)
xcodebuild ... test                        # includes PurandaraUITests: performAccessibilityAudit() on each screen
```

Lessons from the iOS audit: the system `.secondary` grey fails contrast for small
text (use `Color.subdued`); menu pickers and text bar buttons clip or cap Dynamic
Type; visible text must appear in some element's label; translucent bars over
scrolling content fail contrast; the iOS 26 toolbar glass shadow lowers contrast
just below it; symbol characters (✓ ↑) fall back to fixed-size fonts, so use SF
Symbols; Form section footers cap Dynamic Type, so put explanations in the section; `Label` in a list row clips its title (use an `HStack` of image and text); rows visible at normal size that larger text pushes
off screen fail the Dynamic Type check (and a half-visible row at the bottom edge can fail
contrast), so keep a screen's first view to a few tall rows; buttons sharing a list row
need their own button style, and navigation links in one row all open the first, so push
through the path instead; at accessibility text sizes move secondary panels into the scrolling
content. The audit helper ignores three things: contrast findings (the check is
environment noise: it flags pairs that pass WCAG AA by wide margins and reports
different sets per device and runtime under one Xcode build, so contrast is
logged, not fatal — real contrast checking happens on the tuned local toolchain,
issue #8), Dynamic Type warnings on notation
cells (swaras, pairs, karvai: they shrink to fit their column by design), and,
for exercises taller than the screen (`longNotation`), issues on rows outside
the visible notation area (the lazy list keeps them loaded behind the
controls). Top-bar titles don't scale, so long ones are cut off: use
`Varisai.shortTitle` there. UI tests
launch with `-resetSettings YES` because settings persist between launches.
