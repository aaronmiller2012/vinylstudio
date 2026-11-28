#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
python3 tests/check-gate-manifest.py
bash tests/run-nas-source-preflight.sh
javac -d "$T" "$D"/{CutRepairGrid,SharedExportFiles,ParallelGeometry}.java tests/M6ReliabilityTest.java tests/ParseJavaSource.java
java -ea -cp "$T" com.halftonestudio.M6ReliabilityTest
java -cp "$T" ParseJavaSource "$D"/{CutGeometryConditioner,StudioProductFlows,StudioActivity}.java
javac -J-Xmx700m -d "$T" -cp "$T" -sourcepath app/src/main/java:tests/android-host-stubs tests/{M6CutRepairTest,M6StyleContractTest}.java
java -ea -Xmx800m -cp "$T" com.halftonestudio.M6CutRepairTest
java -ea -Xmx500m -cp "$T" com.halftonestudio.M6StyleContractTest
python3 tests/check-m6-wiring.py
