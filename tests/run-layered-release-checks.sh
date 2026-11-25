#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)";trap 'rm -rf "$OUT"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$OUT" app/src/main/java/com/halftonestudio/{RegistrationMarks,ColorWorkspacePalette,LayeredCellPortraitEngine,RasterRegionClipper,LayeredCellReleaseProfile,LayeredCellExportSafety}.java tests/LayeredCellReleaseCandidateTest.java
java -Xmx512m -cp "$OUT" com.halftonestudio.LayeredCellReleaseCandidateTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/LayerWorkspaceMotifTest.java
java -Xmx512m -cp "$OUT" com.halftonestudio.LayerWorkspaceMotifTest
python3 tests/check-layered-cell-android-wiring.py
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$OUT" tests/JavaSyntaxCheck.java
java -cp "$OUT" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/{StudioActivity,LayeredCellPortraitEngine,RasterRegionClipper,LayeredCellReleaseProfile,LayeredCellExportSafety}.java

# Lighting workflow must change layered-cell SVG cut geometry without touching source pixels.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$OUT" app/src/main/java/com/halftonestudio/{SnipMask,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ImageLevels,RegistrationMarks,RasterRegionClipper,LayeredCellPortraitEngine,WorkflowFlow,ParallelGeometry,CancellationCheckpoint}.java tests/LightingWorkflowRegressionTest.java
java -Xmx512m -cp "$OUT" com.halftonestudio.LightingWorkflowRegressionTest
