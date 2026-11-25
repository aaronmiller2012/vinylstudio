#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC79 EDGE-AWARE STAINED GLASS PORTRAIT GATE =====\n'
python3 tests/check-v25445-glass-portrait.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" tests/StainedGlassPortraitRC79Test.java
java -Xmx768m -cp "$T" com.halftonestudio.StainedGlassPortraitRC79Test
printf '\n===== INHERITED RC78 RELIABILITY AND CUT/PALETTE GATES =====\n'
bash tests/run-v25444-reliability-gate.sh
printf '\nPASS RC79 STAINED GLASS PORTRAIT GATE\n'
