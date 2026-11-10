---
name: ios-release
description: Release the Purandara iOS app to the App Store. Bumps the version and build number, archives and checks the Release build, uploads it to App Store Connect, refreshes the screenshots when the UI changed, and walks the user through submitting for review. Use when the user asks to make an iOS release, publish or upload to the App Store or TestFlight, or bump the iOS version.
---

# iOS release

Releases Purandara (bundle ID `com.baijum.purandara.ios`, team `9QBV46NATP`)
to the App Store. The App Store Connect app record already exists; 0.1.0 (1)
was the first submission (2026-11-10).

The scripted parts are in `tools/release/`; the listing text is in
`store-listing/app-store.md` and the screenshots in `store-listing/screenshots/`.

## 1. Agree the version

Read `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `iosApp/project.yml`
(the only place they live; the `.pbxproj` is generated). Ask the user for the
new version if they didn't give one (patch for fixes, minor for features).

- A new version: set `MARKETING_VERSION`, and `CURRENT_PROJECT_VERSION` to the
  next build number.
- Re-uploading the same version (a rejected or broken build): bump
  `CURRENT_PROJECT_VERSION` only. App Store Connect refuses a build number it
  has seen.

Never decrease either value.

## 2. Check the tree

- On `main`, clean, and up to date (`git status`, `git pull`). Release only
  merged work.
- The tests pass: `./gradlew :shared:jvmTest` and the iOS test command in
  AGENTS.md (it includes the accessibility audit). Don't upload over a failure;
  report it.
- If the release adds or changes user-visible features, check the site guide
  (`site/`) and `store-listing/app-store.md` describe them.

## 3. Archive, check and upload

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
| Build number already used | Bump `CURRENT_PROJECT_VERSION` and rerun. |
| Keychain prompt hangs the archive | The user enters their Mac password and clicks Always Allow. |

The API key `~/.appstoreconnect/private_keys/AuthKey_4AN3GH2C44.p8` can't be
used without its issuer ID, which isn't stored; the user chose Xcode's account.

## 4. Screenshots (when the UI changed)

```bash
bash tools/release/ios_screenshots.sh              # all slots
bash tools/release/ios_screenshots.sh iphone-medium
```

It builds the debug app for the simulator and captures five screens per slot
with the debug launch arguments (synthetic singer for sing-along and the Sa
screens), then checks every size. **Look at every image** before handing them
over: a shot taken before the harmonium loads shows greyed-out buttons, and
sing-along needs marks on the notes. Adjust the waits in the script if needed.

| Folder | App Store Connect slot | Simulator |
|---|---|---|
| `iphone-medium-1206x2622` | iPhone with Dynamic Island (medium display), **required** | iPhone 18 Pro |
| `ipad-13-2064x2752` | iPad 13" display, **required** | iPad Pro 13-inch (M5) |
| `iphone-large-1320x2868` | iPhone with Dynamic Island (large display), optional | iPhone 18 Pro Max |

Apple changes the required slots (in 2026 the medium display became the
required one); if App Store Connect rejects a size, check
https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications.
iPhone Duo screenshots become required in April 2027 for apps built with the
iOS 27.1 SDK.

## 5. Commit and tag

On a branch, commit the version bump (`iosApp/project.yml` and the regenerated
`.pbxproj`) and any screenshot or listing changes, and open a PR (ask before
pushing, as for any commit). After it merges, tag the release commit
`ios/v<version>` (the `ios/` prefix keeps it apart from future Android tags)
and push the tag, with the user's agreement.

## 6. Submit in App Store Connect (the user does this)

Processing takes 15-30 minutes after upload; Apple emails when the build is
ready. Then give the user these steps:

1. Apps → Purandara → **+** next to iOS App (a new version) and enter the
   version, **exactly** `MARKETING_VERSION`. A build whose version differs
   can't be attached.
2. **What's New in This Version**: draft it for the user from the merged PRs
   since the last `ios/v*` tag, in the site's plain style.
3. Upload new screenshots if they were retaken; update the description or
   keywords from `store-listing/app-store.md` if features changed.
4. **Build** → + → pick the uploaded build.
5. **Add for Review** → **Submit to App Review** (usually 24-48 hours).

## 7. After approval

- First release only: replace the "isn't in the App Store yet" text in the
  "Get the app" section of `site/pages/index.html` with the App Store link.
- If Apple rejects the build, read the reason with the user, fix it, bump the
  build number and go back to step 3.
