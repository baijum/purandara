# Purandara

<img src="tools/brand/lockup.svg" alt="Purandara: gold steps rising to a dot, beside the name" height="72">

A free, fully offline companion app for practising Carnatic music, for Android and iOS.

**Website, user guide and Learn pages:** https://baijum.github.io/purandara/ (Learn explains shruti, the swaras, Adi tala, the speeds and the varisais for beginners)

It's named after **Purandara Dasa** (1484–1564), who is revered as the *Pitamaha*
(grandfather) of Carnatic music. He wrote the graded lessons that students
still start with today: *sarali varisai*, *janta varisai*, *alankarams* and *gitams*.

> **Status:** In active development. Everything works offline, with no account,
> ads or tracking. The first raga is Mayamalavagowla in Adi tala.

## Features

- **Varisai player:** 44 graded exercises (plain scale, Sarali, Madhya Sthayi,
  Janta, Vakra Janta and Dhattu varisai in Adi tala, and the seven sapta tala
  alankarams) played on a harmonium in tala over a real tanpura, with the
  cursor following the notation and a tala strip whose clap, finger count and
  wave each have their own click.
- **Gitams:** Purandara Dasa's four Malahari gitams (Sri Gananatha, Kunda
  Gaura, Kereya Neeranu, Padumanabha) and Analekara (Suddha Saveri), with
  their words under the swaras, section headings, and a choice of swaras,
  words or both. A piece that climbs past the harmonium at a high Sa offers
  the nearest Sa that fits.
- **Swarajatis:** Rara Venu Gopa Bala (Bilahari), written four swaras to a
  beat, each charanam sung as swaras and then with its words.
- **Practise one section** of a gitam or swarajati alone, once or on repeat.
  Choose your Sa (kattai, fine-tune ±50 cents), tempo, speed (kala), tuning
  (just or equal), connected or lightly separated harmonium notes, count-in,
  tala click and looping.
- **Repeat this cycle:** go over one tala cycle again and again, with the
  tanpura and tala running on, then Continue into the rest of the exercise.
  With sing-along on, the cycle's marks are judged afresh each time round.
- **The varisais in another raga:** sing the varisais and alankarams in one
  of seven melakartas (Mayamalavagowla, Natabhairavi, Keeravani,
  Kharaharapriya, Harikambhoji, Shankarabharanam, Kalyani), chosen on Home
  after hearing each one's scale, with progress kept per raga and swara
  numbers (R₂ G₂) in the notation.
- **Recovery and listen–repeat:** keep the tanpura sounding between attempts,
  with a timed break or Continue when ready. Listen to a harmonium model,
  then sing the same unit, with optional pitch feedback and response guide.
  Whole-avarta phrase support is gated on recorded musical review; no phrase
  boundaries are released yet.
- **Practise a whole set:** take Sarali Varisai, Madhya Sthayi, Janta, Vakra
  Janta or Dhattu one varisai after another. Each varisai climbs from first
  kala to the speed you choose, singing every speed 1–7 times (Speed 3, Sing 2
  is 1, 1, 2, 2, 3, 3). Each speed opens in the normal player with its
  count-in, room check, tempo, note marks and pass summaries, then moves on.
  The app remembers each set's plan and where you stopped.
- **Sing-along:** sing with an exercise and see your pitch as you go. Each
  note is marked in tune, close, high, low, an octave off or a different
  swara, with a summary after every pass. Echo cancellation lets you use the
  speaker; headphones work best. Strictness can be Gentle, Steady or Precise.
- **Home and set pages:** continue where you left off and see what's next,
  your Sa with a tanpura to tune to, and every set; each set's page says what
  it trains and shows each exercise's melodic shape and your progress.
- **Navigation:** a menu on Android (a drawer, kept open on wide screens),
  tabs on iPhone (Practise, Sa, Recordings) and a sidebar on iPad.
- **Shruti practice:** sing over the tanpura and watch your pitch against the
  swara lines; hold Sa, Pa or upper Sa for 10, 20 or 30 seconds.
- **Find my Sa:** sing your lowest and highest comfortable notes and get a
  suggested kattai.
- **Recordings:** record yourself and see the swaras the app heard
  (approximately), with your pitch on the swara lines; play back with the
  swaras highlighted, and re-read against another Sa or raga. Kept on the
  device only.
- **Accessibility:** TalkBack and VoiceOver labels throughout, large text
  supported, checked by automated accessibility tests on both platforms.

## Tech stack

| Layer | Technology |
|-------|------------|
| Shared logic | Kotlin Multiplatform (`:shared`): pure Kotlin in `commonMain`, `expect`/`actual` in `platform/` |
| Android | Kotlin, Jetpack Compose, Material 3, single activity, MVVM + `StateFlow` |
| iOS | Swift 6, SwiftUI, MVVM, iOS 16+, static `shared.framework` built by Gradle |
| Build | Gradle (Kotlin DSL) with version catalog (`gradle/libs.versions.toml`), XcodeGen for the iOS project |

## Layout

```
shared/         Kotlin Multiplatform module (commonMain / androidMain / iosMain / jvmMain)
app/            Android app (Jetpack Compose)
iosApp/         iOS app (SwiftUI). project.yml is the XcodeGen spec
audio/          Bundled samples, shared by both apps (Android assets, iOS bundle folder)
models/         SwiftF0 pitch model (ONNX), shared by both apps
tools/samples/  Offline scripts that prepare audio/ from source recordings
tools/brand/    Logo sources and the script that generates every icon (python3 tools/brand/build.py)
docs/           Feature plans
site/           The website: Learn, the user guide and search (python3 site/build.py; published by .github/workflows/site.yml)
```

## Sound

Swaras are played on a harmonium: one CC0 sample per semitone from C2 to D5,
pitch-corrected to exact equal temperament. Each swara's just-intonation
frequency is reached by playing the nearest sample a few cents faster or
slower (`HarmoniumBank` in `shared/`).

The drone is a real tanpura tuned pa SA SA sa (Sankalp Gulati's recording,
CC BY 4.0), cut into seamless loops at four pitches and played at the nearest
one for your Sa (`TanpuraBank`).

Pitch detection runs on the device: YIN, checked by the SwiftF0 neural pitch
model through ONNX Runtime (`models/README.md`).

See [ATTRIBUTION.md](ATTRIBUTION.md) for every recording's credit, and
`tools/samples/` to rebuild the samples.

## Docs

| Doc | What |
|---|---|
| [AGENTS.md](AGENTS.md) | Rules, code map, build and test, accessibility lessons |
| [docs/plan.md](docs/plan.md) | Varisai player |
| [docs/plan-pitch-monitor.md](docs/plan-pitch-monitor.md) | Sing-along with pitch feedback |
| [docs/plan-landing.md](docs/plan-landing.md) | Landing page (its layout is now Home and the set pages) |
| [docs/plan-navigation.md](docs/plan-navigation.md) | Navigation: Android drawer, iOS tabs and iPad sidebar, Home and set pages (built) |
| [docs/plan-shruti.md](docs/plan-shruti.md) | Shruti practice, strictness, choosing Sa |
| [docs/plan-articulation-breathing.md](docs/plan-articulation-breathing.md) | Optional note separation, recovery, reviewed phrases and listen–repeat (staged) |
| [docs/plan-alankaram-gitam.md](docs/plan-alankaram-gitam.md) | Alankarams and the Malahari gitams (done) |
| [docs/plan-gitam-swarajati-varnam.md](docs/plan-gitam-swarajati-varnam.md) | More gitams, swarajatis, varnams (in progress; open items in issues #1–#6) |
| [docs/plan-speed-layout.md](docs/plan-speed-layout.md) | Notation laid out by tala cycle at every speed: strip and notation share columns (done) |
| [docs/plan-speed-clarification.md](docs/plan-speed-clarification.md) | Written-pace multipliers, paired Janta speeds and the player’s About speed help |
| [docs/plan-repeat-cycle.md](docs/plan-repeat-cycle.md) | Repeat this cycle: go over one tala cycle until Continue, without stopping (built) |
| [docs/plan-other-ragas.md](docs/plan-other-ragas.md) | The varisais and alankarams in seven melakartas, chosen on Home (built) |
| [docs/plan-raga-followups.md](docs/plan-raga-followups.md) | Hear a raga first, swara numbers in the notation, progress per raga, the Learn page (built) |
| [docs/plan-practice-run.md](docs/plan-practice-run.md) | Practising a whole varisai set in one run: per-varisai speed climb and repeats (built) |
| [docs/plan-recordings.md](docs/plan-recordings.md) | Recordings with approximate swaras: record, analyse, play back (built) |
| [docs/plan-site-search.md](docs/plan-site-search.md) | Website: full-text search (done) |
| [docs/plan-learn.md](docs/plan-learn.md) | Website: the Learn section, beginner theory (done) |
| [docs/tuning.md](docs/tuning.md) | Just (tanpura) vs Equal (keyboard): exact ratios, cents, frequencies and implementation (the musicians' version is the guide's Tuning page) |
| [docs/branding.md](docs/branding.md) | The logo, icons, colours and type |
| [docs/resources.md](docs/resources.md) | Websites, books and datasets to check notation, theory, tuning and audio against |

## Building

```bash
# Shared module tests (fast, JVM)
./gradlew :shared:jvmTest

# Android
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug detekt
./gradlew :app:connectedDebugAndroidTest   # accessibility tests (needs an emulator)

# iOS (the Xcode build phase compiles the shared framework automatically)
bash iosApp/setup_onnxruntime.sh # once: fetches the ONNX Runtime xcframework (checksummed)
cd iosApp && xcodegen generate   # only needed after adding/removing files
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build test
```

## License

Code: MIT, see [LICENSE](LICENSE). Bundled third-party material is listed
with its licence in [ATTRIBUTION.md](ATTRIBUTION.md).
