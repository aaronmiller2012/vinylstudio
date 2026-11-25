#!/usr/bin/env bash
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
javac -J-Xmx850m -cp "$JAR" -sourcepath app/src/main/java:tests -d "$T" app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java tests/V3VectorBooleanTest.java tests/GeosVectorReferenceBackend.java tests/ConfettiWeaveRC158Test.java tests/PrismaticVectorBooleanTest.java tests/PrismaticWeaveV3Test.java
javac -cp "$JAR:$T" -sourcepath app/src/main/java:tests -d "$T" tests/vector-android-stubs/android/graphics/{Path,Matrix}.java tests/ArtworkVisibilityTest.java tests/SpecialtyArtworkMetricsTest.java tests/FootprintClearanceTest.java
java -ea -Xmx950m -cp "$T:$JAR" com.halftonestudio.ArtworkVisibilityTest
java -ea -Xmx950m -cp "$T:$JAR" com.halftonestudio.FootprintClearanceTest
java -ea -Xmx950m -cp "$T:$JAR" com.halftonestudio.SpecialtyArtworkMetricsTest
java -ea -Xmx950m -cp "$T:$JAR" com.halftonestudio.V3VectorBooleanTest
java -ea -Xmx950m -cp "$T:$JAR" com.halftonestudio.PrismaticVectorBooleanTest
python3 tests/check-v3-release-consistency.py
