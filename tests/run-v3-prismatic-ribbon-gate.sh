#!/usr/bin/env bash
# Prismatic ribbon milestone: five appended styles (IDs 48-52) through the production
# JTS vector backend, post-Boolean cut conditioning, Snip containment, exports, sizes,
# cancellation, an exact pin of the Prismatic Diagonal 559 engine (conditioning off), and
# (M5) every Prismatic style through every export profile with no final-sheet cut review.
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
printf '\n===== PRISMATIC RIBBON STYLES =====\n'
javac -J-Xmx850m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/PrismaticRibbonStylesTest.java tests/PrismaticExportProfilesTest.java tests/V3AllStyleExportMatrixTest.java \
    app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java \
    app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx900m -cp "$T:$JAR" com.halftonestudio.PrismaticRibbonStylesTest
printf '\n===== PRISMATIC EXPORT PROFILES =====\n'
java -ea -Xmx900m -cp "$T:$JAR" com.halftonestudio.PrismaticExportProfilesTest
printf '\nPASS Prismatic ribbon gate\n'
