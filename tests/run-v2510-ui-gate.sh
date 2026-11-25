#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Historical rendering and exported SVGs must remain unchanged.
bash tests/run-v2509-gate.sh
python3 tests/check-v2510-ui.py
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/ViewportMath.java tests/ViewportMathTest.java
java -cp "$TMP" com.halftonestudio.ViewportMathTest
printf 'PASS v2.51.0 vector canvas, swatches and viewport gate\n'
