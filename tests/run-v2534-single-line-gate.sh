#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" \
    tests/android-host-stubs/android/graphics/Bitmap.java \
    tests/vector-android-stubs/android/graphics/{Path,Matrix}.java \
    "$D"/{RegionalPortraitDetail,ScaleAwarePhotoSampler,PortraitStructureMap,ContinuousLineModes,ProceduralPortraitLines,SpaceFillingCurves,AdaptiveCurvePortrait,SpaceFillingRibbon,RibbonClearance,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,SnipMask,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift}.java \
    tests/SingleLineSamplerV2534Test.java
java -Xmx512m -cp "$TMP" com.halftonestudio.SingleLineSamplerV2534Test
# The v2.53.3 regression gate retains all frozen CMYK fixtures, photo spiral,
# stencil topology and specialty preview/export parity tests.
bash tests/run-v2533-alpha-portrait-gate.sh
# Isolated tests for the unchanged legacy hosts are compiled explicitly.
bash tests/run-android-style-cancellation-host.sh
python3 - <<'PY2'
from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'portraitOptions.artworkWidthMm=width' in p
assert 'circular,width)' in p
assert 'SpiralPortraitRibbon.generate(preparedPixelsSnapshot' in p
print('PASS single-line source wiring, existing Spiral Portrait renderer preserved')
PY2
