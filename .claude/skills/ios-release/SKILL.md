---
name: ios-release
description: Release the Purandara iOS app to the App Store. Runs the tests, bumps the version (iOS and Android together), writes the What's New and release notes, archives and checks the Release build, uploads it to App Store Connect, smoke-tests it on the iPad, tags it, creates the GitHub release, refreshes the screenshots when the UI changed, and walks the user through submitting for review. Use when the user asks to make a release, an iOS release, publish or upload to the App Store or TestFlight, or bump the version.
---

# iOS release

Releases Purandara (bundle ID `com.baijum.purandara.ios`, team `9QBV46NATP`)
to the App Store. The App Store Connect app record already exists; 0.1.0 (1)
was the first submission (2026-11-10). Android isn't on Google Play yet, so
only iOS is uploaded, but both apps carry the same version so one tag fits
both when Android ships.

The scripted parts are in `tools/release/`; the listing text is in
`store-listing/app-store.md` and the screenshots in `store-listing/screenshots/`.
Release commits and tags go straight to `main` (no PR).

## 1. Check the tree and run the tests

All of these must pass before anything is bumped:

- On `main`, clean, and up to date (`git status`, `git pull`), and
  `gh auth status` is logged in. If the tree is dirty, stop and ask.
- `./gradlew :shared:jvmTest`, the iOS test command in AGENTS.md (it includes
  the accessibility audit), and `./gradlew testDebugUnitTest lintDebug detekt`
  (the shared code ships in both apps). Don't release over a failure; report it.
- If the release changes user-visible features, check the site guide (`site/`)
  and `store-listing/app-store.md` describe them.

## 2. Agree the version

Find the previous release: `git describe --tags --abbrev=0 --match 'v*'`.
Versions are semantic with a `v` tag prefix (`v0.1.0`). If the user didn't
give a version, ask which kind of release this is:

| Kind | Change | Example |
|---|---|---|
| Major | bump major, reset the others | 0.4.2 → 1.0.0 |
| Feature | bump minor, reset patch | 0.4.2 → 0.5.0 |
| Bugfix | bump patch | 0.4.2 → 0.4.3 |

Then bump, never decreasing anything:

- `iosApp/project.yml`: `MARKETING_VERSION` to the version,
  `CURRENT_PROJECT_VERSION` + 1 (the only place they live; regenerate the
  `.pbxproj` with `cd iosApp && xcodegen generate`).
- `app/build.gradle.kts`: `versionName` to the version, `versionCode` + 1.

Re-uploading the same version (a rejected or broken build): bump
`CURRENT_PROJECT_VERSION` only; App Store Connect refuses a build number it
has seen. That needs no new tag.

## 3. Write the notes

Read what changed: `git log <previous-tag>..HEAD --oneline` (and the PR
descriptions for anything unclear). Write two texts and show both to the user
in copyable blocks:

**What's New** (App Store "What's New in This Version"): at most **500
characters** so the same text fits Google Play later; 1-4 short bullets.

**GitHub release notes**: 5-10 bullets under `## What's New` with
`### New Features`, `### Improvements`, `### Bug Fixes` (omit empty sections).

Rules for both, in the site's plain style:

1. Drop internal changes entirely: refactoring, CI, dependency bumps, docs,
   tests, tooling.
2. Describe what a student notices, not the implementation: "Sing-along marks
   notes more reliably on Bluetooth headsets", not "Fix AVAudioSession route
   handling".
3. Group related fixes into one line.
4. No code identifiers or jargon beyond the music words the app itself uses
   (swara, kattai, avarta).
5. The first release (0.1.0) has no previous tag; describe the app instead.

## 4. Commit the release

```bash
git add iosApp/project.yml iosApp/Purandara.xcodeproj/project.pbxproj app/build.gradle.kts
git commit -m "Release <version> (iOS build <N>, Android versionCode <M>)"
```

Don't push yet: the build is uploaded and checked first, so a failed upload
doesn't leave a tag behind.

## 5. Archive, check and upload

```bash
bash tools/release/ios_upload.sh
```

It regenerates the Xcode project, archives the Release build, checks it and
uploads it with `xcodebuild -exportArchive` (`tools/release/ExportOptions.plist`).
It takes 5-10 minutes; run it in the background. The checks fail the script if:

- the version or build number in the app differs from `project.yml`;
- `ITSAppUsesNonExemptEncryption` is not NO (it skips the export compliance
  question in App Store Connect);
- `PrivacyInfo.xcprivacy` is missing, or the binary calls a required-reason API
  (UserDefaults, file timestamps, boot time, disk space, keyboards) the manifest
  doesn't declare. Add the category with the right reason code to
  `iosApp/Purandara/PrivacyInfo.xcprivacy` (Apple: "Describing use of required
  reason API") rather than weakening the check;
- anything is embedded in `Frameworks/` (`shared.framework` and ONNX Runtime are
  static and must only be linked).

`--no-upload` stops after the checks.

### Upload failures

| Error | Fix |
|---|---|
| `App Store Connect access for "9QBV46NATP" is required` | No Apple Account in Xcode. Ask the user to sign in: Xcode → Settings → Accounts → +. Then rerun. |
| `App record ... not found` | The record was deleted or the bundle ID changed; the user creates it at App Store Connect → Apps → + (values in `store-listing/app-store.md`). |
| Build number already used | Bump `CURRENT_PROJECT_VERSION`, amend the release commit, rerun. |
| Keychain prompt hangs the archive | The user enters their Mac password and clicks Always Allow. |

The API key `~/.appstoreconnect/private_keys/AuthKey_4AN3GH2C44.p8` can't be
used without its issuer ID, which isn't stored; the user chose Xcode's account.

## 6. Smoke-test the release build on the iPad

Advisory: if no device is connected, say so and go on. The archived app is
development-signed, so it installs on the user's iPad mini:

```bash
xcrun devicectl list devices            # find the connected iPad's identifier
xcrun devicectl device install app --device <id> \
  build/Purandara.xcarchive/Products/Applications/Purandara.app
xcrun devicectl device process launch --device <id> com.baijum.purandara.ios
```

Ask the user to open an exercise and play it (and try sing-along if the release
touched it). A crash or silence stops the release: fix, bump the build number,
go back to step 5.

## 7. Tag, push and create the GitHub release

With the user's go-ahead (if they asked for the release to be pushed, that
counts):

```bash
git tag -a v<version> -m "v<version>"
git push origin main v<version>
gh release create v<version> --title "v<version>" --notes "$(cat <<'EOF'
## What's New
...
EOF
)"
```

Attach no binaries: the app is distributed through the stores.

## 8. Screenshots (when the UI changed)

```bash
bash tools/release/ios_screenshots.sh              # all slots
bash tools/release/ios_screenshots.sh iphone-medium
```

It builds the debug app for the simulator and captures five screens per slot
with the debug launch arguments (synthetic singer for sing-along and the Sa
screens), then checks every size. **Look at every image** before handing them
over: a shot taken before the harmonium loads shows greyed-out buttons, and
sing-along needs marks on the notes. Adjust the waits in the script if needed.
Commit changed screenshots to `main`.

| Folder | App Store Connect slot | Simulator |
|---|---|---|
| `iphone-medium-1206x2622` | iPhone with Dynamic Island (medium display), **required** | iPhone 18 Pro |
| `ipad-13-2064x2752` | iPad 13" display, **required** | iPad Pro 13-inch (M5) |
| `iphone-large-1320x2868` | iPhone with Dynamic Island (large display), optional | iPhone 18 Pro Max |

Apple changes the required slots (in 2026 the medium display became the
required one), and resizing other sizes with `sips` gets rejected; capture at
the slot's native size. If App Store Connect rejects a size, check
https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications.
iPhone Duo screenshots become required in April 2027 for apps built with the
iOS 27.1 SDK.

## 9. Submit in App Store Connect (the user does this)

Processing takes 15-30 minutes after upload; Apple emails when the build is
ready. Then give the user these steps:

1. Apps → Purandara → **+** next to iOS App (a new version) and enter the
   version, **exactly** `MARKETING_VERSION`. A build whose version differs
   can't be attached.
2. **What's New in This Version**: paste the text from step 3.
3. Upload new screenshots if they were retaken; update the description or
   keywords from `store-listing/app-store.md` if features changed.
4. **Build** → + → pick the uploaded build.
5. **Add for Review** → **Submit to App Review** (usually 24-48 hours).

## 10. Report and follow up

Tell the user: the tag and GitHub release URL, the version and build number,
the upload result, the smoke test result (pass, skipped or failed), and what's
left for them in App Store Connect.

After approval:

- First release only: replace the "isn't in the App Store yet" text in the
  "Get the app" section of `site/pages/index.html` with the App Store link.
- If Apple rejects the build, read the reason with the user, fix it, bump the
  build number and go back to step 5.
