#!/bin/bash
# Capture the App Store screenshots from the simulators into
# store-listing/screenshots/<slot>/ (1-library ... 5-find-sa).
#
#   bash tools/release/ios_screenshots.sh            # all three slots
#   bash tools/release/ios_screenshots.sh iphone-medium  # one slot: iphone-medium | iphone-large | ipad-13
#
# Uses the debug build's launch arguments (-resetSettings, -open, -autoplay,
# -singAlong, -singer) so sing-along and the Sa screens show a synthetic singer.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
BID=com.baijum.purandara.ios
APP=build/sim/Build/Products/Debug-iphonesimulator/Purandara.app

# slot | simulator name | expected size
SLOTS="iphone-medium|iPhone 18 Pro|1206x2622
iphone-large|iPhone 18 Pro Max|1320x2868
ipad-13|iPad Pro 13-inch (M5)|2064x2752"

udid() { xcrun simctl list devices available | grep -F "    $1 (" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/'; }

want="${1:-all}"
first_udid=$(udid "iPhone 18 Pro")
[ -n "$first_udid" ] || { echo "No 'iPhone 18 Pro' simulator" >&2; exit 1; }

echo "== Building for the simulator (log: build/sim.log)"
mkdir -p build
# A named destination builds arm64 only; the generic one also builds x86_64,
# which fails to link ONNX Runtime.
xcodebuild build -project iosApp/Purandara.xcodeproj -scheme Purandara -configuration Debug \
  -destination "platform=iOS Simulator,id=$first_udid" -derivedDataPath build/sim \
  > build/sim.log 2>&1 || { grep " error:" build/sim.log | head; exit 1; }

capture() {
  local slot=$1 name=$2 size=$3 sim out
  sim=$(udid "$name"); [ -n "$sim" ] || { echo "No '$name' simulator" >&2; exit 1; }
  out="store-listing/screenshots/$slot-$size"
  echo "== $slot: $name -> $out"
  mkdir -p "$out"
  xcrun simctl boot "$sim" 2>/dev/null || true
  xcrun simctl bootstatus "$sim" -b >/dev/null
  xcrun simctl ui "$sim" appearance light
  xcrun simctl status_bar "$sim" override --time 9:41 --batteryState charged --batteryLevel 100 \
    --wifiBars 3 --cellularBars 4 --dataNetwork wifi
  xcrun simctl install "$sim" "$APP"
  xcrun simctl privacy "$sim" grant microphone $BID
  shot() { local file=$1 wait=$2; shift 2
    xcrun simctl terminate "$sim" $BID 2>/dev/null || true
    xcrun simctl launch "$sim" $BID -resetSettings YES -sa 50 "$@" >/dev/null
    sleep "$wait"
    xcrun simctl io "$sim" screenshot "$out/$file.png" >/dev/null 2>&1
    xcrun simctl terminate "$sim" $BID 2>/dev/null || true; }
  shot 1-library 14                       # wait for the harmonium to load
  shot 2-singalong 30 -open sarali-3 -autoplay YES -singAlong YES -singer 6
  shot 3-gitam 10 -open gitam-1 -autoplay YES
  shot 4-shruti 8 -open shruti -singer 3
  shot 5-find-sa 20 -open find-sa -singer 0
  xcrun simctl status_bar "$sim" clear
  xcrun simctl shutdown "$sim"
  local got
  for f in "$out"/*.png; do
    got=$(sips -g pixelWidth -g pixelHeight "$f" | awk '/pixelWidth/{w=$2}/pixelHeight/{h=$2}END{print w"x"h}')
    [ "$got" = "$size" ] || { echo "$f is $got, expected $size" >&2; exit 1; }
  done
}

while IFS='|' read -r slot name size; do
  [ "$want" = all ] || [ "$want" = "$slot" ] || continue
  capture "$slot" "$name" "$size"
done <<<"$SLOTS"
echo "== Done. Look at every shot before uploading."
