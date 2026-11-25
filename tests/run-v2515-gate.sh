#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2514-gate.sh
python3 tests/check-v2515-universal-hue.py
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{PaletteHueShift,SnipMask,SnipBackground,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours}.java tests/UniversalHueTonalTest.java
java -Xmx384m -cp "$TMP" UniversalHueTonalTest
javac -sourcepath app/src/main/java -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java
printf 'PASS v2.51.5 all-palette hue adjustment and inherited renderer gate\n'
