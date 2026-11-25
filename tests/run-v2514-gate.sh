#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2513-gate.sh
python3 tests/check-v2514-ui.py
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/PaletteHueShift.java tests/PaletteHueShiftTest.java
java -cp "$TMP" PaletteHueShiftTest
printf 'PASS v2.51.4 hue adjustment and inherited geometry gate\n'
