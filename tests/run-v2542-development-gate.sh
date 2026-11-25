#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2541-development-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" "$D"/{CutWarningLocator,SpecialtyCutWarningLocator,SourceTilePlan,TessellationEngine,PhotoCellImportance,LayeredCellPortraitEngine,MultiColorStippleEngine,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,SpaceFillingRibbon,SpaceFillingCurves,RasterRegionClipper,SourceColorStencilQuantizer,RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ImageLevels,TiledImageLevels,PhysicalFeatureRules,StipplePhotoPalette,PaletteHueShift,SvgNumberWriter,LayeredCellExportSafety,CutMaterialProfile,StencilRegionRefiner,VinylSubjectMask,RibbonClearance,AdaptiveCurvePortrait,PortraitStructureMap}.java tests/V2542TilesAndWarningsTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.V2542TilesAndWarningsTest
python3 tests/check-v2542-source.py
python3 tests/check-v2542-compilefix.py
