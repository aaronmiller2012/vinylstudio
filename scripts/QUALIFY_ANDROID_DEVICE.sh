#!/usr/bin/env bash
set -Eeuo pipefail
# Read-only qualification collector. Does not install, delete, force-stop,
# rotate, change theme/font settings or clear logs on the owner's device.
# Use ADB_SERIAL to select a device when multiple are connected.
command -v adb >/dev/null || { echo 'adb is required on the connected workstation.' >&2; exit 2; }
ADB=(adb)
[[ -z "${ADB_SERIAL:-}" ]] || ADB+=(-s "$ADB_SERIAL")
"${ADB[@]}" get-state | grep -qx device || { echo 'ADB device offline or unauthorized' >&2;exit 3; }
OUT="${1:-device-qualification-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p -- "$OUT"
APP=com.halftonestudio
"${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r' > "$OUT/android-version.txt"
"${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r' > "$OUT/android-api.txt"
"${ADB[@]}" shell getprop ro.product.model | tr -d '\r' > "$OUT/device-model.txt"
"${ADB[@]}" shell pm path "$APP" > "$OUT/installed-app.txt"
if ! grep -q 'package:' "$OUT/installed-app.txt"; then echo "VinylStudio ($APP) not installed" >&2; exit 4; fi
"${ADB[@]}" shell dumpsys package "$APP" > "$OUT/package.txt"
# A stale APK invalidates performance and export comparisons. Read-only check.
EXPECTED_CODE="${VINYL_EXPECTED_VERSION_CODE:-470}"
if ! grep -Eq "versionCode=${EXPECTED_CODE}([^0-9]|$)" "$OUT/package.txt"; then
  echo "Wrong installed build. Expected versionCode ${EXPECTED_CODE}; inspect $OUT/package.txt" >&2
  exit 5
fi
"${ADB[@]}" shell dumpsys meminfo "$APP" > "$OUT/memory.txt" || true
# Android's gfxinfo framestats are read-only and help quantify jank. Capture
# actual device frame data instead of claiming synthetic JVM tests measure FPS.
"${ADB[@]}" shell dumpsys gfxinfo "$APP" framestats > "$OUT/gfxinfo-framestats.txt" || true
"${ADB[@]}" shell dumpsys activity top > "$OUT/activity-top.txt" || true
"${ADB[@]}" logcat -d -v time AndroidRuntime:E '*:S' > "$OUT/fatal-log.txt" || true
cat > "$OUT/MANUAL-CHECKS.txt" <<'TXT'
VINYLSTUDIO v3 Beta19 A/B/C3 — DEVICE QUALIFICATION (manual, not certified by this script)
Back up project library before replacing an APK or testing process termination.
1. Fresh install/update on the same debug signing key, open a saved project.
2. Test photo import, crop, undo/redo, quick-start and Export on API 30–34 and 35–36.
3. Rotate device and change display/font size with a project OPEN; verify edit state retained.
4. Toggle dark mode, return to Activity; inspect all dialogs and system/IME insets.
5. Repeat project switching and large bitmap preview on a 3–4 GB Android device.
6. Export a multi-layer SVG ZIP and DXF ZIP; inspect cut-sheet-safety.csv.
7. With a backup, simulate process stop at multiple queued-save states; verify last committed photo pair and check no mismatched original.
8. Before and after slider dragging, capture read-only meminfo/gfxinfo again and compare native RAM and missed frames.
9. Inspect failure toasts, render timing and crash log, then record PASS/FAIL per case.
10. Compare baseline Beta18 against Beta19 ribbon previews for subpixel lines without changing saved SVG/DXF cut geometry.
Do not regard a launch without crashes as proof of physical cut safety.
TXT
printf 'Collected READ-ONLY Android diagnostics under %s\n' "$OUT"
printf 'Manual qualification and real cutter import remain mandatory.\n'
