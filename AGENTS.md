# AGENTS.md: AI agent instructions for Purandara

Purandara is a **free, fully offline** Kotlin Multiplatform app (Android + iOS)
for practising Carnatic music. It's named after Purandara Dasa, the *Pitamaha*
of Carnatic music.

**Status:** foundation only. Features will be planned and discussed before they are built.
Do not add features speculatively.

## Hard constraints

- No network dependencies. Every feature must work with the radio off.
- No analytics, tracking, or telemetry.
- No ads or monetisation code without prior discussion.
- No third-party SDKs without prior discussion.
- Never commit `keystore.properties`, API keys, or secrets.
- Accessibility is a first-class requirement (TalkBack / VoiceOver). Breaking it
  is treated as seriously as breaking functionality.

## Architecture

| Module | Rules |
|--------|-------|
| `shared/src/commonMain` | Pure Kotlin only, with **no platform imports**. Music theory, raga/tala models, and practice logic go here so both apps share it. Platform needs go through `expect`/`actual` in `platform/`. |
| `app/` (Android) | Jetpack Compose + Material 3 only (no XML layouts). Single activity. ViewModels expose `StateFlow`. Honour `LocalReduceMotion` for all animations. |
| `iosApp/` (iOS) | SwiftUI, `@StateObject`/`@Published` ViewModels. Honour `accessibilityReduceMotion`. Kotlin `object`s are reached as `Foo.shared`, top-level functions as `<File>Kt.fn()` (named after the file holding the `actual`, e.g. `Platform_iosKt.platformName()`). |

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
cd iosApp && xcodegen generate     # after adding/removing Swift files
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Add tests in `shared/src/commonTest` for every piece of domain logic (kotlin.test + Kotest property tests).
