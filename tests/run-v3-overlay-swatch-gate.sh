#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/OpaqueOverlaySwatchPlannerTest.java tests/OpaqueOverlayPaletteMappingTest.java tests/OverprintCellHostTest.java
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.OpaqueOverlaySwatchPlannerTest
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.OpaqueOverlayPaletteMappingTest
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.OverprintCellHostTest
python3 - <<'PY'
from pathlib import Path
base=Path('app/src/main/java/com/halftonestudio')
a=(base/'StudioActivity.java').read_text()
r=(base/'LayeredCellPortraitEngine.java').read_text()
assert 'specialtyPhysicalPalette(key,input)' in a
assert 'OpaqueOverlayPaletteMapping.preset' in a
assert 'OpaqueOverlayPaletteMapping.saved(Arrays.copyOf(saved.baseColors,8)'  in a
assert 'OpaqueOverlayPaletteMapping.savedPreview' in a
assert 'setTessBasePalette(specialtyPhysicalPalette(key,full))' in a
assert 'layeredResult.swatchVisibility' in a and 'finished.swatchVisibility' in a
assert 'OpaqueOverlaySwatchPlanner.choose' in r
assert 'OpaqueOverlaySwatchPlanner.visibility' in r
assert 'overprintMarkPolygon' in r
# Raster export thumbnails must draw physical L1..LN, like vectors/real vinyl.
assert 'for(int layer=0;layer<result.options.layerCount;layer++)' in a
assert 'if(piece.layer!=layer)continue;' in a
assert 'paint.setColor(result.options.colors[layer]);' in a
print('PASS color-tile preview/commit parity, physical-layer visibility and raster-stack parity')
PY
