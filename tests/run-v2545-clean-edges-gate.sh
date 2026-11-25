#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2544-subpixel-gate.sh
D=app/src/main/java/com/halftonestudio
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{SmoothStencilContours,SubpixelStencilContours,SharedStencilBoundaries,MultilayerStencilEngine,StencilRegionRefiner,SourceColorStencilQuantizer,PaletteHueShift,RegistrationMarks,VinylCutPreflight,VinylSubjectMask}.java tests/CleanRegionEdgesV2545Test.java
java -Xmx768m -cp "$TMP" com.halftonestudio.CleanRegionEdgesV2545Test
python3 tests/check-v2545-release.py
