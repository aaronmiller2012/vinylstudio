#!/usr/bin/env bash
# M8 / I1: cut-inspector findings are representation-invariant. StyleCutGeometryPreflight
# (real Chainmail/Confetti/Fishnet sheets + synthetic necks/gaps/contacts/tiny contours) and
# FootprintClearance (JTS footprints of every selectable style) must give identical results
# for rotated ring starts, reversed traversal, shuffled ring/hole/polygon order and all combined.
# C2: grouped cut repair decides feasibility before conditioning (no discarded repair work).
# Confetti: overlay pieces overlap or clear a weed gap; findings at 8/11 in bounded.
# C2b: capped, deterministic windowed repair of infeasible organic sheets.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
javac -J-Xmx750m -nowarn -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" tests/{M8RingOrderInvarianceTest,M4ArtBench}.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1200m -cp "$T" com.halftonestudio.M8RingOrderInvarianceTest
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
if [[ -z "$JAR" ]]; then JAR="$T/jts-core-1.20.0.jar";curl -fL --retry 2 --connect-timeout 15 --max-time 120 'https://repo.maven.apache.org/maven2/org/locationtech/jts/jts-core/1.20.0/jts-core-1.20.0.jar' -o "$JAR"; fi
printf '%s  %s\n' '6a783d8f9dba3d3cf7265435f134402f63c05838aa6cbcc4297ad3a5b2842baf' "$JAR" | sha256sum -c - >/dev/null
javac -J-Xmx750m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" tests/{M8FootprintInvarianceTest,M8RepairFeasibilityTest,M8ConfettiClearanceTest,M8WindowedRepairTest,V3AllStyleExportMatrixTest,M4ArtBench}.java app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1200m -cp "$T:$JAR" com.halftonestudio.M8FootprintInvarianceTest
java -ea -Xmx1500m -cp "$T:$JAR" com.halftonestudio.M8RepairFeasibilityTest
java -ea -Xmx1500m -cp "$T:$JAR" com.halftonestudio.M8ConfettiClearanceTest
java -ea -Xmx1500m -cp "$T:$JAR" com.halftonestudio.M8WindowedRepairTest
printf 'PASS M8 geometry: inspector invariance, repair feasibility, Confetti sheet clearance\n'
