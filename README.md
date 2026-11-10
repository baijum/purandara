# Purandara

<img src="tools/brand/lockup.svg" alt="Purandara: gold steps rising to a dot, beside the name" height="72">

A free, fully offline companion app for practising Carnatic music, for Android and iOS.

**Website and user guide:** https://baijum.github.io/purandara/

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
- **Recovery and listen–repeat:** keep the tanpura sounding between attempts,
  with a timed break or Continue when ready. Listen to a harmonium model,
  then sing the same unit, with optional pitch feedback and response guide.
  Whole-avarta phrase support is gated on recorded musical review; no phrase
  boundaries are released yet.
- **Sing-along:** sing with an exercise and see your pitch as you go. Each
  note is marked in tune, close, high, low, an octave off or a different
  swara, with a summary after every pass. Echo cancellation lets you use the
  speaker; headphones work best. Strictness can be Gentle, Steady or Precise.
- **Landing page:** continue where you left off and see what's next, your Sa
  with a tanpura to tune to, and every set with what it trains, a picture of
  each exercise's melodic shape, and your progress.
- **Shruti practice:** sing over the tanpura and watch your pitch against the
  swara lines; hold Sa, Pa or upper Sa for 10, 20 or 30 seconds.
- **Find my Sa:** sing your lowest and highest comfortable notes and get a
  suggested kattai.
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
site/           The website and user guide (python3 site/build.py; published by .github/workflows/site.yml)
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
| [docs/plan-landing.md](docs/plan-landing.md) | Landing page |
| [docs/plan-shruti.md](docs/plan-shruti.md) | Shruti practice, strictness, choosing Sa |
| [docs/plan-articulation-breathing.md](docs/plan-articulation-breathing.md) | Optional note separation, recovery, reviewed phrases and listen–repeat (staged) |
| [docs/plan-alankaram-gitam.md](docs/plan-alankaram-gitam.md) | Alankarams and the Malahari gitams (done) |
| [docs/plan-gitam-swarajati-varnam.md](docs/plan-gitam-swarajati-varnam.md) | More gitams, swarajatis, varnams (in progress; open items in issues #1–#6) |
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
