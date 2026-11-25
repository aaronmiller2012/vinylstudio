#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Frozen CMYK line output and all four prior stipple overlap modes remain supported.
bash tests/run-v2507-gate.sh
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{RegistrationMarks,SourceColorStencilQuantizer,PhotoPaletteOptimizer,StipplePhotoPalette,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance}.java tests/StipplePhotoReleaseTest.java
java -Xmx900m -cp "$TMP" StipplePhotoReleaseTest
python3 tests/check-v2508-ui.py
printf 'PASS: v2.50.8 mandatory source gate\n'
