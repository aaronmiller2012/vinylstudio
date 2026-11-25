#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v25419-cleanup.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
printf '\n== Single Line portrait geometry ==\n'
javac -sourcepath app/src/main/java -d "$T" \
  tests/android-host-stubs/android/graphics/Bitmap.java \
  tests/vector-android-stubs/android/graphics/{Path,Matrix}.java \
  "$D"/{RegionalPortraitDetail,ScaleAwarePhotoSampler,PortraitStructureMap,ContinuousLineModes,ProceduralPortraitLines,SpaceFillingCurves,AdaptiveCurvePortrait,SpaceFillingRibbon,RibbonClearance,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,SnipMask,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift}.java \
  tests/SingleLineSamplerV2534Test.java
java -Xmx512m -cp "$T" com.halftonestudio.SingleLineSamplerV2534Test
printf '\n== Eight photo-art style detail ==\n'
T2="$(mktemp -d)"; trap 'rm -rf "$T" "$T2"' EXIT
javac -sourcepath app/src/main/java -d "$T2" "$D"/{PhotoCellImportance,StyleKit,RegistrationMarks,CancellationCheckpoint,MultiColorStippleEngine,TessellationEngine,StainedGlassEngine,TopographicLayersEngine,MaskedFacetClipper,LowPolyDelaunayEngine,PixelFont5x7,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,RidgeLinesEngine,VinylStyleSuite,NewStyleColorSlots}.java tests/CellDetailV25414Test.java
java -Xmx1g -cp "$T2" com.halftonestudio.CellDetailV25414Test
python3 tests/check-v25414-detail-wiring.py
printf 'PASS v2.54.19 cleanup: removal, Single Line and eight photo art styles\n'
