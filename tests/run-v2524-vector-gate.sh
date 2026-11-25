#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" tests/vector-android-stubs/android/graphics/{Path,Matrix}.java \
  "$D/CancellationCheckpoint.java" "$D/RegionalPortraitDetail.java" "$D/ScaleAwarePhotoSampler.java" "$D"/{CmykLineVisibility,RegistrationMarks,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance,LayeredCellPortraitEngine,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,SpaceFillingRibbon,RibbonClearance,SpaceFillingCurves,SpecialtyVectorCache,RasterRegionClipper,VinylSubjectMask,SourceColorStencilQuantizer,StencilRegionRefiner,PaletteHueShift,AdaptiveCurvePortrait,PortraitStructureMap}.java \
  tests/SpecialtyVectorParityV2524Test.java
java -Xmx768m -cp "$TMP" com.halftonestudio.SpecialtyVectorParityV2524Test
python3 tests/check-v2524-vector-ui.py
