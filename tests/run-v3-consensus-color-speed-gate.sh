#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3ConsensusOverprintTest.java
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.V3ConsensusOverprintTest
python3 - <<'PYTEST'
from pathlib import Path
base=Path('app/src/main/java/com/halftonestudio')
a=(base/'StudioActivity.java').read_text()
r=(base/'LayeredCellPortraitEngine.java').read_text()
p=(base/'OpaqueOverlaySwatchPlanner.java').read_text()
assert 'new OpaqueOverlaySwatchPlanner.PreparedPalette(o.colors,o.layerCount)' in r
assert 'x0,y0,x1,y1,mmPx,cellMaskCoverage' in r
assert 'final double[][] regionCost=new double[8][count]' in p
for name in ('SpecialtyPrepare','OverprintGeometry','StandardGeometry','NewStylePrepare','NewStyleGeometry'):
    assert 'VinylStudio.'+name in a, f'Missing real-device trace {name}'
print('PASS render cache ownership, single coverage scan, prepared color losses and Perfetto sections')
PYTEST
