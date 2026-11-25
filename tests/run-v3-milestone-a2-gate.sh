#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP" \
    app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,CutSafetyReviewPolicy,FinalCutSheetSafety,SvgDxfR13Exporter,CutSheetZipWriter,OutputDeviceProfile,RasterRegionClipper,WeedDifficulty,CutIntelligence,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
    tests/{V3MilestoneA1GeometryTest,V3MilestoneA1SnipTest,V3MilestoneA2GeometryTest,V3MilestoneA2SnipTest}.java
java -Xmx700m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA2GeometryTest
java -Xmx700m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA2SnipTest
