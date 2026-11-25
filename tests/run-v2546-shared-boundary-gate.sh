#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{SmoothStencilContours,SubpixelStencilContours,SharedStencilBoundaries,MultilayerStencilEngine,StencilRegionRefiner,SourceColorStencilQuantizer,PaletteHueShift,RegistrationMarks,VinylCutPreflight,VinylSubjectMask}.java \
  tests/{SharedBoundaryV2546Test,SmoothStencilContoursTest,SubpixelStencilContoursTest,UnifiedStencilV253Test,CleanRegionEdgesV2545Test}.java
java -Xmx768m -cp "$TMP" com.halftonestudio.SharedBoundaryV2546Test
java -Xmx768m -cp "$TMP" com.halftonestudio.CleanRegionEdgesV2545Test
java -Xmx768m -cp "$TMP" com.halftonestudio.SmoothStencilContoursTest
java -Xmx768m -cp "$TMP" com.halftonestudio.SubpixelStencilContoursTest
java -Xmx768m -cp "$TMP" com.halftonestudio.UnifiedStencilV253Test
python3 tests/check-v2546-release.py
