# Purandara

A free, fully offline companion app for practising Carnatic music, for Android and iOS.

It's named after **Purandara Dasa** (1484–1564), who is revered as the *Pitamaha*
(grandfather) of Carnatic music. He wrote the graded lessons that students
still start with today: *sarali varisai*, *janta varisai*, *alankarams* and *gitams*.

> **Status:** Foundation only. No features yet.

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
tools/samples/  Offline scripts that prepare audio/ from source recordings
```

## Sound

Swaras are played on a harmonium: one CC0 sample per semitone from C2 to C5,
pitch-corrected to exact equal temperament. Each swara's just-intonation
frequency is reached by playing the nearest sample a few cents faster or
slower (`HarmoniumBank` in `shared/`). See [ATTRIBUTION.md](ATTRIBUTION.md)
for the recordings' credits, and `tools/samples/prepare_harmonium.py` to rebuild them.

## Building

```bash
# Shared module tests (fast, JVM)
./gradlew :shared:jvmTest

# Android
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug detekt

# iOS (the Xcode build phase compiles the shared framework automatically)
cd iosApp && xcodegen generate   # only needed after adding/removing files
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build test
```

## License

Code: MIT, see [LICENSE](LICENSE). Bundled third-party material is listed
with its licence in [ATTRIBUTION.md](ATTRIBUTION.md).
