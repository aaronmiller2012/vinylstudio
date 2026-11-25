#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx750m -sourcepath app/src/main/java:tests -d "$T" tests/{V3Precision552Test,V3PrecisionSizeMatrixTest,V3AllStyleExportMatrixTest,V3MilestoneA1GeometryTest,ParseJavaSource}.java
java -ea -Xmx700m -cp "$T" com.halftonestudio.V3Precision552Test
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3PrecisionSizeMatrixTest
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,SpecialtyArtworkMetrics,ArtworkVisibility,PrecisionCurveSampler,MultilayerStencilEngine,VinylCutPreflight,SvgDxfR13Exporter,FinalCutSheetSafety,StandardCutSafety,LayerCutCounts}.java
python3 tests/check-v3-precision552.py
