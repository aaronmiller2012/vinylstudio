#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2512-gate.sh
python3 tests/check-v2513-ui.py
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{MultiColorStippleEngine,TessellationEngine,PhotoCellImportance,RegistrationMarks}.java tests/DensitySize2513Test.java
java -cp "$TMP" DensitySize2513Test
printf 'PASS v2.51.3 density and physical dot-size mandatory gate\n'
