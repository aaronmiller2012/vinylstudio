#!/usr/bin/env bash
# M5 performance/determinism gate: conditioned-only local micro-fragment removal equals the shared
# implementation, conditioned Prismatic geometry is identical for repeat / sequential / slow-backend
# runs, and cut preparation and TSP 2-opt are bounded by workload instead of wall clock.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
SHA=6a783d8f9dba3d3cf7265435f134402f63c05838aa6cbcc4297ad3a5b2842baf
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
if [[ -z "$JAR" ]]; then
  JAR="$T/jts-core-1.20.0.jar"
  curl -fL --retry 2 --connect-timeout 15 --max-time 120 'https://repo.maven.apache.org/maven2/org/locationtech/jts/jts-core/1.20.0/jts-core-1.20.0.jar' -o "$JAR"
fi
printf '%s  %s\n' "$SHA" "$JAR" | sha256sum -c -
javac -J-Xmx850m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/M5DeterministicGeometryTest.java tests/PrismaticWeaveV3Test.java \
    app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx900m -cp "$T:$JAR" com.halftonestudio.M5DeterministicGeometryTest
printf '\nPASS M5 Prismatic performance and deterministic geometry gate\n'
