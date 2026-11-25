#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)";trap 'rm -rf "$TEMP"' EXIT
javac -sourcepath app/src/main/java -d "$TEMP" app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,PortraitStructureMap,AdaptiveCurvePortrait,SpaceFillingCurves,SpaceFillingRibbon,RibbonClearance,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,SnipMask,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift}.java tests/SpaceFillingCurveReleaseTest.java tests/AdaptiveCurvePortraitTest.java tests/CurveRenderRecoveryTest.java tests/RibbonSafetyV245Test.java
java -Xmx512m -cp "$TEMP" com.halftonestudio.SpaceFillingCurveReleaseTest
java -Xmx512m -cp "$TEMP" com.halftonestudio.AdaptiveCurvePortraitTest
java -Xmx512m -cp "$TEMP" com.halftonestudio.CurveRenderRecoveryTest
java -Xmx512m -cp "$TEMP" com.halftonestudio.RibbonSafetyV245Test
python3 tests/check-v242-curve-integration.py

python3 tests/check-v243-adaptive-integration.py
