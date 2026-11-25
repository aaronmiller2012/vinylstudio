#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
# V2.28 compact editing controls, with existing photo and vector engines preserved.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$OUT" app/src/main/java/com/halftonestudio/{SnipMask,SnipBackground,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours}.java tests/StudioRefreshTest.java
java -cp "$OUT" com.halftonestudio.StudioRefreshTest

python3 tests/check-v228-clean-ui.py

# New Android-free, multi-color geometry tests.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$OUT" app/src/main/java/com/halftonestudio/SnipMask.java app/src/main/java/com/halftonestudio/StencilSeparationEngine.java app/src/main/java/com/halftonestudio/LineRibbonPolicy.java app/src/main/java/com/halftonestudio/LineArtEngine.java app/src/main/java/com/halftonestudio/CmykWhitePaperBalance.java app/src/main/java/com/halftonestudio/CmykPortraitDefaults.java app/src/main/java/com/halftonestudio/LinePresenceTracker.java app/src/main/java/com/halftonestudio/PortraitBackgroundGuard.java app/src/main/java/com/halftonestudio/VinylEngine.java app/src/main/java/com/halftonestudio/AsciiScreenMotif.java app/src/main/java/com/halftonestudio/AsciiVectorGlyphs.java app/src/main/java/com/halftonestudio/StyleKit.java app/src/main/java/com/halftonestudio/RegistrationMarks.java tests/VinylEngineTest.java
java -cp "$OUT" com.halftonestudio.VinylEngineTest

javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/RegistrationMarksTest.java
java -cp "$OUT" com.halftonestudio.RegistrationMarksTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/CircleCropGeometryTest.java
java -cp "$OUT" com.halftonestudio.CircleCropGeometryTest

# v1.9 linked, palette, line modulation, physical units and SVG limits.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/VinylPalettes.java tests/VinylWorkbenchTest.java
java -cp "$OUT" com.halftonestudio.VinylWorkbenchTest

# v1.9.2 physical size guidance, overlapping spot layers and *per SVG* Cricut limit.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/VinylSizing.java app/src/main/java/com/halftonestudio/SmartHalftonePresets.java tests/VinylSizingTest.java
java -cp "$OUT" com.halftonestudio.VinylSizingTest

# v2.0 pattern families, 3-slider smart defaults, real SVG geometry and advisory safeguards.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/CutInsights.java tests/SmartHalftonePresetsTest.java
java -cp "$OUT" com.halftonestudio.SmartHalftonePresetsTest

# v2.1 nondestructive polygon Snip; Cricut SVGs cannot include clipPath elements.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/SnipMaskTest.java
java -cp "$OUT" com.halftonestudio.SnipMaskTest

# v2.2 gesture transforms and nondestructive S Pen polygon history.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/SnipTimeline.java app/src/main/java/com/halftonestudio/ViewportMath.java tests/SnipTimelineTest.java tests/ViewportMathTest.java
java -cp "$OUT" com.halftonestudio.SnipTimelineTest
java -cp "$OUT" com.halftonestudio.ViewportMathTest

# v2.3: removing the legacy editor must not change CMYK cut paths when application order changes.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/VinylOnlyWorkflowTest.java
java -cp "$OUT" com.halftonestudio.VinylOnlyWorkflowTest

# v2.5 independent color strength, sampled opaque-stack diagnostics and contour line response.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/VinylDiagnostics.java tests/VinylColorDiagnosticsTest.java
java -cp "$OUT" com.halftonestudio.VinylColorDiagnosticsTest

# v2.6 workflow navigation is explicit and independent of Android activity state.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/WorkflowFlow.java tests/WorkflowFlowTest.java
java -cp "$OUT" com.halftonestudio.WorkflowFlowTest

# v2.6 stencil must export independent flat-color shapes without dithering or clip paths.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/StencilModeTest.java
java -cp "$OUT" com.halftonestudio.StencilModeTest

# v2.8 vector contours, open paper, color assignment, snipped voids and SVG loop limits.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/StencilVectorTest.java
java -cp "$OUT" com.halftonestudio.StencilVectorTest

# v2.7 three-phase editor rules and contextual subpanel consistency.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/WorkspaceFlowTest.java
java -cp "$OUT" com.halftonestudio.WorkspaceFlowTest

# v2.7 source-integrated UI invariants for manual Android build.
python3 tests/check-workspace-ui.py

# v2.9 dedicated CMYK line families, independent channel controls and vinyl safety.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/LineArtEngineTest.java
java -cp "$OUT" com.halftonestudio.LineArtEngineTest

# A syntax-only check catches Java editor mistakes even when Android SDK is unavailable.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/JavaSyntaxCheck.java
java -cp "$OUT" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/PhotoCropEditor.java \
  app/src/main/java/com/halftonestudio/VinylColorWheel.java \
  app/src/main/java/com/halftonestudio/LineRibbonPolicy.java app/src/main/java/com/halftonestudio/LineRibbonPolicy.java \
  app/src/main/java/com/halftonestudio/LineArtEngine.java \
  app/src/main/java/com/halftonestudio/CmykWhitePaperBalance.java app/src/main/java/com/halftonestudio/CmykPortraitDefaults.java app/src/main/java/com/halftonestudio/LinePresenceTracker.java app/src/main/java/com/halftonestudio/PortraitBackgroundGuard.java app/src/main/java/com/halftonestudio/VinylEngine.java app/src/main/java/com/halftonestudio/AsciiScreenMotif.java app/src/main/java/com/halftonestudio/AsciiVectorGlyphs.java app/src/main/java/com/halftonestudio/StyleKit.java \
  app/src/main/java/com/halftonestudio/SmartHalftonePresets.java \
  app/src/main/java/com/halftonestudio/RegionalPortraitDetail.java app/src/main/java/com/halftonestudio/CmykLineVisibility.java app/src/main/java/com/halftonestudio/ScaleAwarePhotoSampler.java \
  app/src/main/java/com/halftonestudio/ContinuousLineModes.java \
  app/src/main/java/com/halftonestudio/ContinuousLineSvg.java \
  app/src/main/java/com/halftonestudio/SourceColorStencilQuantizer.java \
  app/src/main/java/com/halftonestudio/SnipBackground.java

# v2.10 physical stack, neutral grayscale, image levels after saved snips.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/ImageLevels.java tests/EdgeLevelsStackTest.java
java -cp "$OUT" com.halftonestudio.EdgeLevelsStackTest

# v2.10.1 actual-source repair: post-snip Light plus live/empty-cut safety.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/LivePreviewLightTest.java
java -cp "$OUT" com.halftonestudio.LivePreviewLightTest

# v2.10.2 portrait balance: warm-photo cyan, midtone ribbons, physical cutter limits.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/PortraitCMYKRegressionTest.java
java -cp "$OUT" com.halftonestudio.PortraitCMYKRegressionTest

# v2.10.3 Android diagnostics/status integration; exact SVG paths do not wait for visibility scan.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/LayerRenderStatus.java tests/AndroidDiagnosticsIntegrationTest.java
java -cp "$OUT" com.halftonestudio.AndroidDiagnosticsIntegrationTest
python3 tests/check-android-diagnostics.py

# v2.10.4 all user-facing pattern and palette preset matrix, real generated SVGs.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/PresetMatrixRegressionTest.java
java -cp "$OUT" com.halftonestudio.PresetMatrixRegressionTest

# v2.10.4 Android-facing CMYK mode/preset selection and version integration.
python3 tests/check-cmyk-ui.py

# V2.11.2 shelf design invariants.
python3 tests/check-shelf-redesign.py

# V2.11.3 source integration and alpha preservation checks.
python3 tests/check-crop-registration-ui.py

# v2.12.1 integration, shape-def safety and retained v2.11.3 geometry contracts.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" tests/ShapeDefinitionTest.java
java -cp "$OUT" com.halftonestudio.ShapeDefinitionTest
python3 tests/check-v2121-merge.py

python3 tests/check-post-snip-size.py

python3 tests/check-v214-workspaces.py

python3 tests/check-v215-canvas-first.py
python3 tests/check-v217-safety.py

python3 tests/check-v218-all-styles.py

python3 tests/check-v219-maze.py

python3 tests/check-v220-canvas-ui.py

# V2.21 reproducible tonal-band population regression.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/MultilayerStencilEngine.java app/src/main/java/com/halftonestudio/SharedStencilBoundaries.java app/src/main/java/com/halftonestudio/SmoothStencilContours.java app/src/main/java/com/halftonestudio/SubpixelStencilContours.java tests/V221TonalCountTest.java
java -cp "$OUT" com.halftonestudio.V221TonalCountTest

javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/PortraitStructureMap.java tests/PortraitStructureMapTest.java
java -cp "$OUT" com.halftonestudio.PortraitStructureMapTest

# V2.26 vector tessellation has no Android dependency and is tested at full geometry level.
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$OUT" -d "$OUT" app/src/main/java/com/halftonestudio/RegistrationMarks.java app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java app/src/main/java/com/halftonestudio/TessellationEngine.java app/src/main/java/com/halftonestudio/PhotoCellImportance.java tests/TessellationEngineTest.java
java -cp "$OUT" com.halftonestudio.TessellationEngineTest
python3 tests/check-v226-ui.py
