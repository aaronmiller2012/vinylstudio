#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
bash tests/run-curve-release-checks.sh
bash tests/run-v2503-cut-geometry.sh
bash tests/run-layer-cut-counts.sh
bash tests/run-universal-snip.sh
python3 tests/check-v2494-quick-start.py
python3 tests/check-v2495-performance.py
python3 tests/check-v2496-render-pipeline.py
CACHE_BUDGET_TESTS="$(mktemp -d)"
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$CACHE_BUDGET_TESTS" app/src/main/java/com/halftonestudio/RenderPreparationCache.java tests/RenderBudgetTest.java
java -cp "$CACHE_BUDGET_TESTS" com.halftonestudio.RenderBudgetTest
rm -rf "$CACHE_BUDGET_TESTS"
QTMP="$(mktemp -d)"
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$QTMP" app/src/main/java/com/halftonestudio/VinylGuidePolicy.java tests/VinylGuidePolicyTest.java
java -cp "$QTMP" com.halftonestudio.VinylGuidePolicyTest
rm -rf "$QTMP"
STAGE2_TESTS="$(mktemp -d)"
trap 'rm -rf "$STAGE2_TESTS"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$STAGE2_TESTS" app/src/main/java/com/halftonestudio/{StudioRenderController,StudioProjectArchiveWriter,ProjectManifest,EngineBuild}.java tests/StageTwoFoundationTest.java
java -cp "$STAGE2_TESTS" com.halftonestudio.StageTwoFoundationTest
python3 tests/check-stage2-foundation.py
python3 tests/check-v2490-workspace.py
python3 tests/check-v2491-cleanup.py
python3 tests/check-v233-colors.py
python3 tests/check-v231-editor-polish.py
python3 tests/check-v241-performance.py
python3 tests/check-full-width-nav.py
python3 tests/check-post-snip-size.py
python3 tests/check-lighting-prepare-flow.py
python3 tests/check-v2473-prepare-cleanup.py
python3 tests/check-v248-ui-contract.py
python3 tests/check-v2481-completion.py
python3 tests/check-v2482-preview.py
python3 tests/check-v2484-style-workspace.py
python3 tests/check-v2485-unified-pages.py
python3 tests/check-v2486-palette-ui.py
python3 tests/check-v2487-style-navigation.py
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ImageLevels,ParallelGeometry,CancellationCheckpoint}.java tests/{ImageLevelsReference,ImageLevelsParityTest}.java
java -cp "$TEMP" com.halftonestudio.ImageLevelsParityTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,CmykStackOrder,ParallelGeometry,CancellationCheckpoint}.java tests/CmykStackOrderTest.java
java -cp "$TEMP" com.halftonestudio.CmykStackOrderTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" tests/CmykExtendedStackTest.java
java -Xmx512m -cp "$TEMP" com.halftonestudio.CmykExtendedStackTest
python3 tests/check-v246-layer-workspace.py
python3 tests/check-v247-preview-safety.py
python3 tests/check-v2471-compile-snapshot.py
python3 tests/check-v2472-stacking.py
bash tests/run-android-style-cancellation-host.sh
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{VinylPalettes,ColorWorkspacePalette}.java tests/PaletteExpansionTest.java
java -cp "$TEMP" com.halftonestudio.PaletteExpansionTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{VinylPalettes,ColorWorkspacePalette}.java tests/ColorWorkspacePaletteTest.java
java -cp "$TEMP" com.halftonestudio.ColorWorkspacePaletteTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{VinylPalettes,ColorWorkspacePalette}.java tests/SurfacePaletteOptimizationTest.java
java -cp "$TEMP" com.halftonestudio.SurfacePaletteOptimizationTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" tests/JavaSyntaxCheck.java
java -cp "$TEMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TEMP" app/src/main/java/com/halftonestudio/{SnipMask,SnipBackground,SourceColorStencilQuantizer,StencilRegionRefiner,RegistrationMarks,VinylSubjectMask,VinylLayerSupport,PaletteHueShift,MultilayerStencilEngine,SharedStencilBoundaries,SmoothStencilContours,SubpixelStencilContours}.java tests/StudioRefreshTest.java
java -cp "$TEMP" com.halftonestudio.StudioRefreshTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/RenderPreparationCache.java tests/RenderPerformanceSafetyTest.java
java -cp "$TEMP" com.halftonestudio.RenderPerformanceSafetyTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" tests/StencilRegionRefinerTest.java
java -cp "$TEMP" com.halftonestudio.StencilRegionRefinerTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/VinylCutPreflight.java tests/VinylStackReleaseTest.java
java -cp "$TEMP" com.halftonestudio.VinylStackReleaseTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/{CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ParallelGeometry,CancellationCheckpoint}.java tests/VinylEngineTest.java
java -cp "$TEMP" com.halftonestudio.VinylEngineTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" tests/VinylGenerationCancellationTest.java
java -cp "$TEMP" com.halftonestudio.VinylGenerationCancellationTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" tests/ScreenAndVinylBalanceTest.java
java -cp "$TEMP" com.halftonestudio.ScreenAndVinylBalanceTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java app/src/main/java/com/halftonestudio/TessellationEngine.java app/src/main/java/com/halftonestudio/PhotoCellImportance.java tests/CutSetupTest.java
java -cp "$TEMP" com.halftonestudio.CutSetupTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/SnipTimeline.java tests/SnipTimelineTest.java
java -cp "$TEMP" com.halftonestudio.SnipTimelineTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/{VinylSizing,SmartHalftonePresets,CutInsights}.java tests/SmartHalftonePresetsTest.java
java -cp "$TEMP" com.halftonestudio.SmartHalftonePresetsTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" app/src/main/java/com/halftonestudio/GlobalPatternSelection.java tests/GlobalPatternSelectionTest.java
java -cp "$TEMP" com.halftonestudio.GlobalPatternSelectionTest
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -cp "$TEMP" -d "$TEMP" tests/ShapeNoWaveTest.java
java -cp "$TEMP" com.halftonestudio.ShapeNoWaveTest
python3 tests/check-v235-no-shape-waves.py
printf 'PASS: V2.35 baseline compatibility smoke test for RC1\n' 

# Stage-1 archive abuse and export limit regression checks (pure Java)
SECURITY_TMP="$(mktemp -d)"
trap 'rm -rf "$TEMP" "$SECURITY_TMP"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$SECURITY_TMP" app/src/main/java/com/halftonestudio/{ProjectArchiveReader,ExportBounds,ProjectManifest,EngineBuild}.java tests/StageOneSecurityTest.java
java -cp "$SECURITY_TMP" com.halftonestudio.StageOneSecurityTest
python3 tests/check-stage1-hardening.py
