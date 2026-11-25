#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# The existing development gate is also run by the NAS build wrapper.
# Do not downgrade the older classic CMYK fixtures.
bash tests/run-v2542-development-gate.sh
D=app/src/main/java/com/halftonestudio
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{SmoothStencilContours,SubpixelStencilContours,SharedStencilBoundaries,MultilayerStencilEngine,StencilRegionRefiner,SourceColorStencilQuantizer,PaletteHueShift,RegistrationMarks,VinylCutPreflight,VinylSubjectMask}.java \
  tests/{SmoothStencilContoursTest,UnifiedStencilV253Test,SubpixelStencilContoursTest}.java
java -Xmx768m -cp "$TMP" com.halftonestudio.SmoothStencilContoursTest
java -Xmx768m -cp "$TMP" com.halftonestudio.UnifiedStencilV253Test
java -Xmx768m -cp "$TMP" com.halftonestudio.SubpixelStencilContoursTest
python3 tests/check-v2544-release.py
