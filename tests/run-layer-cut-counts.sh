#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" app/src/main/java/com/halftonestudio/{SnipMask,SnipBackground,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance,LayeredCellPortraitEngine,RasterRegionClipper,LayerCutCounts,ParallelGeometry,CancellationCheckpoint}.java tests/LayerCutCountsTest.java
java -cp "$TMP" com.halftonestudio.LayerCutCountsTest
python3 tests/check-v2492-cutcounts.py
