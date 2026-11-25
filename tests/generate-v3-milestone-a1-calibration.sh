#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
DEST="${1:?Pass an empty output directory for the calibration files}"
mkdir -p -- "$DEST"
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java -d "$TMP" \
    app/src/main/java/com/halftonestudio/{StyleKit,SvgDxfR13Exporter,OutputDeviceProfile,ParallelGeometry,CancellationCheckpoint}.java \
    tests/V3MilestoneA1CalibrationExport.java
java -ea -cp "$TMP" com.halftonestudio.V3MilestoneA1CalibrationExport "$DEST"
