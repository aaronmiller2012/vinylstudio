#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,ProjectLoadOwnership,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
  tests/RC173W5SafetyTest.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.RC173W5SafetyTest
python3 tests/check-rc173-w5-integration.py
bash tests/run-rc173-w4-host-gate.sh
printf '\nPASS RC173-W5 inherited + cut safety + lifecycle ownership gates\n'
