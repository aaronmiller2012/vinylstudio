#!/usr/bin/env bash
# Mandatory RC137 quality/reliability host gate. Source-only checks do not
# replace the Android SDK compilation or on-device visual/Cricut tests.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc137-quality.py
python3 tests/check-rc136-cell-portrait.py
bash tests/run-rc134-speed-jvm.sh
bash tests/run-rc129-picker-gate.sh
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP"   tests/StudioRenderControllerTest.java   tests/LineCoverageRC101Test.java tests/LineCoverage11InchTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.StudioRenderControllerTest
java -Xmx768m -cp "$TMP" com.halftonestudio.LineCoverage11InchTest
printf '\nPASS RC137/RC142 quality gate: source, OOM recovery, 11-inch line coverage, 228 geometry goldens, 75 picker thumbnails\n'
