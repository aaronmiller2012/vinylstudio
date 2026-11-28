#!/usr/bin/env bash
# install.sh <apk> [aot-mode]   Install with -r (never uninstalls, never clears data).
# aot-mode: speed (default) | speed-profile | verify | none (leave as installed).
. "$(dirname "$0")/devlib.sh"
apk=$1; mode=${2:-speed}
[[ -f $apk ]] || { echo "no apk $apk" >&2; exit 2; }
pkg=$(aapt2 dump packagename "$apk" 2>/dev/null || $(ls -d ~/Android/Sdk/build-tools/* | tail -1)/aapt2 dump packagename "$apk")
a install -r -t "$apk" || exit 1
if [[ $mode != none && $pkg != *.test ]]; then sh_ cmd package compile -m "$mode" -f "$pkg" >/dev/null; fi
echo "installed $pkg; dexopt: $(sh_ dumpsys package dexopt | grep -A2 "\[$pkg\]" | grep -m1 status | tr -d '\r' | xargs)"
