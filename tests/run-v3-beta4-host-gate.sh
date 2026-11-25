#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
# First run every inherited executable regression, not only static scans.
bash tests/run-v3-host-gate.sh
bash tests/run-tessellation-reference-gate.sh
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java -d "$TMP" \
    app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,CutSafetyReviewPolicy,FinalCutSheetSafety,SvgDxfR13Exporter,CutSheetZipWriter,OutputDeviceProfile,WeedDifficulty,CutIntelligence,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
    tests/V3Beta4FinalSheetTest.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3Beta4FinalSheetTest
javac -J-Xmx750m -sourcepath app/src/main/java -d "$TMP" \
    app/src/main/java/com/halftonestudio/{PhotoRevisionStore,PhotoPersistenceQueue}.java \
    tests/V3Beta4ProcessDeathTest.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3Beta4ProcessDeathTest
printf 'PASS VinylStudio v3 Beta4 final-sheet and process-death gates\n'

python3 tests/check-v3-beta4-integration.py
