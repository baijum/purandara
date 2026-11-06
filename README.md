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
shared/   Kotlin Multiplatform module (commonMain / androidMain / iosMain / jvmMain)
app/      Android app (Jetpack Compose)
iosApp/   iOS app (SwiftUI). project.yml is the XcodeGen spec
```

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

MIT. See [LICENSE](LICENSE).
