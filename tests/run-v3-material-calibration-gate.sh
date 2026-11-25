#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java:tests -d "$T" tests/{MaterialCalibrationSheetTest,CalibrationRecordTest,CalibrationProfileTest,ArtworkCutMetricsTest,ParseJavaSource}.java
java -cp "$T" com.halftonestudio.MaterialCalibrationSheetTest
java -cp "$T" com.halftonestudio.CalibrationRecordTest
java -cp "$T" com.halftonestudio.CalibrationProfileTest
java -cp "$T" com.halftonestudio.ArtworkCutMetricsTest
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java

python3 tests/check-v3-universal-inspection.py
