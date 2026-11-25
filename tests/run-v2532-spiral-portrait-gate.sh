#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" tests/vector-android-stubs/android/graphics/{Path,Matrix}.java \
  "$D/RegionalPortraitDetail.java" "$D/ScaleAwarePhotoSampler.java" "$D/SpiralPortraitRibbon.java" \
  tests/SpiralPortraitRibbonV2532Test.java
java -Xmx512m -cp "$TMP" com.halftonestudio.SpiralPortraitRibbonV2532Test
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
v=Path('app/src/main/java/com/halftonestudio/SpecialtyVectorCache.java').read_text()
assert 'SpiralPortraitRibbon.generate(preparedPixelsSnapshot' in s
assert 'SpecialtyVectorCache.spiral(spiralGeometry,spiralGeometry.imageWidth,spiralGeometry.imageHeight,spiralGeometry.cutPath)' in s
assert 'spiralRibbonResult=spiralReady' in s
assert 'vectors.source==spiralRibbonResult' in s
assert 'exportedSpiral.svg()' in s
assert 'SpecialtyCutSafety.validateVinylRibbonSvg(svg)' in s
assert 'spiral-portrait-vinyl.svg' in s
assert 'new Path[]{path}' in v
assert 'if(continuousStyle!=ContinuousLineModes.LineStyle.SPIRAL)' in s
print('PASS Spiral route: photo preparation -> filled Path -> exact vector preview -> Cricut SVG export')
PY
bash tests/run-v2531-photo-sampler-gate.sh
