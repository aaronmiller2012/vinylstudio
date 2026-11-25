#!/usr/bin/env bash
# Install the debug app + instrumentation APK on a connected device/emulator and run the
# on-device suite (StageOneInstrumentation). Exit 0 only when every case passes.
# Usage: scripts/run-device-tests.sh [adb-serial]
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
ADB=(adb); [[ $# -ge 1 ]] && ADB=(adb -s "$1")
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo 'No device: connect a phone with USB debugging or start an emulator.' >&2; exit 3; }
./gradlew --no-daemon --max-workers=1 --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
"${ADB[@]}" install -r -t app/build/outputs/apk/debug/app-debug.apk
"${ADB[@]}" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OUT="$("${ADB[@]}" shell am instrument -w -r com.halftonestudio.test/com.halftonestudio.StageOneInstrumentation)"
printf '%s\n' "$OUT" | grep -E 'INSTRUMENTATION_RESULT: (case\.|stage1)' | sed 's/INSTRUMENTATION_RESULT: //'
printf '%s\n' "$OUT" | grep -q 'INSTRUMENTATION_RESULT: stage1=PASS' || { echo 'DEVICE SUITE FAILED' >&2; exit 1; }
