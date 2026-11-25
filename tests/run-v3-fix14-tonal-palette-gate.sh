#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v3-fix14-tonal-ui.py
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx650m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/V3Fix14TonalPaletteTest.java tests/V3ColorPipelineRepairTest.java tests/UniversalHueTonalTest.java
java -ea -Xmx512m -cp "$TMP" com.halftonestudio.V3Fix14TonalPaletteTest
java -ea -Xmx512m -cp "$TMP" com.halftonestudio.V3ColorPipelineRepairTest
java -ea -Xmx512m -cp "$TMP" UniversalHueTonalTest
