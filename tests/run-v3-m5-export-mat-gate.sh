#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-${JTS_JAR:-}}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
[[ -n "$JAR" && -f "$JAR" ]] || { echo 'JTS 1.20.0 jar unavailable (set VINYL_JTS_JAR)' >&2; exit 4; }
CP="$JAR"
javac -J-Xmx700m -cp "$CP" -sourcepath app/src/main/java:tests/android-host-stubs:tests -d "$T" \
    tests/M5ExportMatTest.java tests/MatFitQualificationTest.java tests/V3AllStyleExportMatrixTest.java
java -ea -Xmx900m -cp "$T:$CP" com.halftonestudio.M5ExportMatTest
java -ea -Xmx900m -cp "$T:$CP" com.halftonestudio.MatFitQualificationTest
