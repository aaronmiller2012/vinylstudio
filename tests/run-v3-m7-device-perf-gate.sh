#!/usr/bin/env bash
# M7 (device engineering): P1 SVG number parser == Float.parseFloat (synthetic + exported
# tokens), P2 preflight parity and cancellation latency under concurrent workers, P3 stencil
# refiner identical output, B5 export names, P4 local micro-fragment cleanup topologically
# equal to the full re-union on real Chainmail/Confetti sheets (JTS).
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx750m -nowarn -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" tests/{M7DevicePerfTest,V3AllStyleExportMatrixTest}.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx900m -cp "$T" com.halftonestudio.M7DevicePerfTest
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
if [[ -z "$JAR" ]]; then JAR="$T/jts-core-1.20.0.jar";curl -fL --retry 2 --connect-timeout 15 --max-time 120 'https://repo.maven.apache.org/maven2/org/locationtech/jts/jts-core/1.20.0/jts-core-1.20.0.jar' -o "$JAR"; fi
printf '%s  %s\n' '6a783d8f9dba3d3cf7265435f134402f63c05838aa6cbcc4297ad3a5b2842baf' "$JAR" | sha256sum -c - >/dev/null
javac -J-Xmx750m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" tests/{M7MicroFragmentParityTest,V3AllStyleExportMatrixTest,M4ArtBench}.java "$D/JtsVectorBooleanBackend.java" 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1200m -cp "$T:$JAR" com.halftonestudio.M7MicroFragmentParityTest
printf 'PASS M7 device-performance equivalence contracts\n'
