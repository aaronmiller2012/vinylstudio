#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc138-crop-lines.py
# Mandatory inherited regression includes all 228 goldens, 75 current thumbnails,
# project migrations, RC134 parity and RC137 11-inch line coverage.
bash tests/run-rc137-quality-gate.sh
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TEMP"     tests/RC138CropLinesHostTest.java tests/LineCoverageRC101Test.java tests/CircleCropGeometryTest.java
java -Xmx768m -cp "$TEMP" com.halftonestudio.RC138CropLinesHostTest
java -Xmx768m -cp "$TEMP" com.halftonestudio.CircleCropGeometryTest
printf '
PASS RC138 crop line release: circular, rectangular, alpha, classic/modern, Cricut SVG, unchanged baseline
'
