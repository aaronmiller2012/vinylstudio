#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{SnipMask,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,VinylCutPreflight}.java \
  tests/UnifiedStencilV253Test.java tests/VinylStackReleaseTest.java tests/StencilRegionRefinerTest.java tests/V221TonalCountTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.UnifiedStencilV253Test
java -Xmx768m -cp "$TMP" com.halftonestudio.VinylStackReleaseTest
java -Xmx768m -cp "$TMP" com.halftonestudio.StencilRegionRefinerTest
java -Xmx768m -cp "$TMP" com.halftonestudio.V221TonalCountTest
bash tests/run-v2524-vector-gate.sh
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'Colors: Match my vinyl rolls' in s
assert 'tonalMatchMyVinyl' in s and 'options.matchVinylPalette=tonalMatchMyVinyl' in s
assert 'tonalMatchMyVinyl",tonalMatchMyVinyl' in s
assert 'tonalMatchMyVinyl",false' in s
assert 'short_gap_horizontal_pixels' in s
print('PASS Android UI bindings and project save/restore smoke')
PY
