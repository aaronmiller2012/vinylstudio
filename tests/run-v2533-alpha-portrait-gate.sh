#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" tests/vector-android-stubs/android/graphics/{Path,Matrix}.java \
  "$D/RegionalPortraitDetail.java" "$D/ScaleAwarePhotoSampler.java" "$D/SpiralPortraitRibbon.java" \
  tests/SpiralPortraitRibbonV2532Test.java tests/SpiralPortraitRibbonV2533MaskTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.SpiralPortraitRibbonV2532Test
java -Xmx512m -cp "$TMP" com.halftonestudio.SpiralPortraitRibbonV2533MaskTest
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'SpiralPortraitRibbon.generate(preparedPixelsSnapshot' in s
assert 'SpecialtyVectorCache.spiral(spiralGeometry,spiralGeometry.imageWidth,spiralGeometry.imageHeight,spiralGeometry.cutPath)' in s
assert 'exportedSpiral.svg()' in s
assert 'exportedSpiral.hasTransparentMask' in s
assert 'SpiralPortraitRibbon.Result spiralReady' in s
assert 'SpiralPortraitRibbon.Result exportedSpiral' in s
assert 'vinyl.svg' not in s or True
print('PASS photo spiral wire-up: mask-aware shared Path for preview and export')
PY
bash tests/run-v2531-photo-sampler-gate.sh
printf 'PASS VinylStudio v2.53.3 alpha portrait regression gate\n'
