#!/bin/bash
# Archive the iOS app (Release) and upload it to App Store Connect.
#
#   bash tools/release/ios_upload.sh            # archive, check, upload
#   bash tools/release/ios_upload.sh --no-upload  # archive and check only
#
# Version and build number come from iosApp/project.yml (MARKETING_VERSION,
# CURRENT_PROJECT_VERSION); bump them first. Signing and upload use the Apple
# Account signed in to Xcode (Settings > Accounts, team 9QBV46NATP).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ARCHIVE=build/Purandara.xcarchive
APP="$ARCHIVE/Products/Applications/Purandara.app"
UPLOAD=yes
[ "${1:-}" = "--no-upload" ] && UPLOAD=no

version=$(sed -n 's/^ *MARKETING_VERSION: *"\(.*\)"/\1/p' iosApp/project.yml)
build=$(sed -n 's/^ *CURRENT_PROJECT_VERSION: *"\(.*\)"/\1/p' iosApp/project.yml)
echo "== Purandara $version ($build)"

[ -d iosApp/Frameworks/onnxruntime.xcframework ] || bash iosApp/setup_onnxruntime.sh
(cd iosApp && xcodegen generate >/dev/null)

mkdir -p build
rm -rf "$ARCHIVE" build/export
echo "== Archiving (log: build/archive.log)"
if ! xcodebuild archive -project iosApp/Purandara.xcodeproj -scheme Purandara \
    -configuration Release -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE" -allowProvisioningUpdates > build/archive.log 2>&1; then
  grep -E " error:|error:" build/archive.log | head -20
  echo "Archive failed; see build/archive.log" >&2
  exit 1
fi

echo "== Checking the archive"
fail=0
plist() { /usr/libexec/PlistBuddy -c "Print :$1" "$APP/Info.plist" 2>/dev/null || echo "(missing)"; }
[ "$(plist CFBundleShortVersionString)" = "$version" ] || { echo "Version is $(plist CFBundleShortVersionString), expected $version"; fail=1; }
[ "$(plist CFBundleVersion)" = "$build" ] || { echo "Build is $(plist CFBundleVersion), expected $build"; fail=1; }
[ "$(plist ITSAppUsesNonExemptEncryption)" = "false" ] || { echo "ITSAppUsesNonExemptEncryption is not NO"; fail=1; }
[ -f "$APP/PrivacyInfo.xcprivacy" ] || { echo "PrivacyInfo.xcprivacy is missing from the app"; fail=1; }
# shared.framework and ONNX Runtime are static: embedding them fails App Store validation.
if [ -d "$APP/Frameworks" ] && [ -n "$(ls -A "$APP/Frameworks")" ]; then
  echo "Frameworks/ is not empty: $(ls "$APP/Frameworks")"; fail=1
fi
# Required-reason APIs must be declared in PrivacyInfo.xcprivacy.
declared=$(cat iosApp/Purandara/PrivacyInfo.xcprivacy)
need() { grep -q "$1" <<<"$declared" || { echo "Binary uses $2 but $1 is not declared in PrivacyInfo.xcprivacy"; fail=1; }; }
syms=$(nm -u "$APP/Purandara")
grep -q "NSUserDefaults" <<<"$syms" && need NSPrivacyAccessedAPICategoryUserDefaults NSUserDefaults
grep -qE "^_(f|l)?stat(at)?$|^_getattrlist$" <<<"$syms" && need NSPrivacyAccessedAPICategoryFileTimestamp "stat/fstat"
grep -qE "^_mach_absolute_time$|systemUptime" <<<"$syms" && need NSPrivacyAccessedAPICategorySystemBootTime mach_absolute_time
grep -qE "^_(f)?statfs$|volumeAvailableCapacity" <<<"$syms" && need NSPrivacyAccessedAPICategoryDiskSpace statfs
grep -q "activeInputModes" <<<"$syms" && need NSPrivacyAccessedAPICategoryActiveKeyboards activeInputModes
[ $fail -eq 0 ] || { echo "Archive checks failed" >&2; exit 1; }
echo "   ok: version, build, encryption key, privacy manifest, no embedded frameworks"

if [ $UPLOAD = no ]; then echo "== Archive ready: $ARCHIVE"; exit 0; fi

echo "== Uploading (log: build/upload.log)"
if ! xcodebuild -exportArchive -archivePath "$ARCHIVE" \
    -exportOptionsPlist tools/release/ExportOptions.plist -exportPath build/export \
    -allowProvisioningUpdates > build/upload.log 2>&1; then
  grep -A3 "error:" build/upload.log | head -20
  echo "Upload failed; see build/upload.log" >&2
  exit 1
fi
echo "== Uploaded Purandara $version ($build). App Store Connect processes it in 15-30 minutes."
