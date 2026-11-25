#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java -d "$TMP" \
    app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,CutSafetyReviewPolicy,FinalCutSheetSafety,SvgDxfR13Exporter,CutSheetZipWriter,OutputDeviceProfile,WeedDifficulty,CutIntelligence,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
    tests/{V3Beta4FinalSheetTest,V3MilestoneA1GeometryTest,V3MilestoneA1SnipTest}.java \
    app/src/main/java/com/halftonestudio/RasterRegionClipper.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3Beta4FinalSheetTest
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA1GeometryTest
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA1SnipTest
