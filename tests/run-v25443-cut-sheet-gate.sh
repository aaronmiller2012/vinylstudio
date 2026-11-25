#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC77 EXPLICIT CUT SHEET SELECTION =====\n'
python3 tests/check-v25443-cut-sheet.py
python3 tests/check-rc72-release-metadata.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
    "$D/VinylEngine.java" tests/SubstrateAwareExportTest.java
java -ea -cp "$T" SubstrateAwareExportTest
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -Xmx768m -cp "$T" ParseJavaSource "$D"/{StudioActivity,VinylEngine,VinylColorEditor}.java
java -Xmx768m -cp "$T" CheckDuplicateJavaMethods app/src/main/java
printf '\n===== INHERITED RC76 PALETTES AND PHYSICAL GEOMETRY =====\n'
bash tests/run-v25442-palette-tap-gate.sh
printf '\nPASS RC77 CUT SHEET SOURCE GATE\n'
