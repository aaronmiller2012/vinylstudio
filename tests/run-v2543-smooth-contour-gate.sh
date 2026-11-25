#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2542-development-gate.sh
D=app/src/main/java/com/halftonestudio
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{SmoothStencilContours,SubpixelStencilContours,SharedStencilBoundaries,MultilayerStencilEngine,StencilRegionRefiner,SourceColorStencilQuantizer,PaletteHueShift,RegistrationMarks,VinylCutPreflight,VinylSubjectMask}.java \
 tests/SmoothStencilContoursTest.java tests/UnifiedStencilV253Test.java
java -Xmx768m -cp "$TMP" com.halftonestudio.SmoothStencilContoursTest
java -Xmx768m -cp "$TMP" com.halftonestudio.UnifiedStencilV253Test
python3 tests/check-v2543-release.py
