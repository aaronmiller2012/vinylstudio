#!/usr/bin/env bash
# M5 Crochet + contour smoothing: the FINISHED cut sheets of the real Crochet engine are
# measured with exact JTS morphology (min width, necks, sub-gap waste, hole size, Snip
# containment, cut-ready stage is a no-op) on real photos at 3-11 in, all layer counts and
# input extremes; the opt-in camouflage de-staircasing pass is checked for staircase
# reduction, silhouette/nesting preservation, L1 untouched and Digital camo untouched.
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
printf '\n===== M5 CROCHET FINISHED GEOMETRY + CONTOUR SMOOTHING =====\n'
javac -J-Xmx900m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M4ArtBench,M5CutGeometryProbe,M5CrochetBench,M5SmoothingBench,M5CrochetGeometryTest,M5CamoDestairTest}.java \
    2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1200m -cp "$T:$JAR" com.halftonestudio.M5CrochetGeometryTest tests/m4/photos
java -ea -Xmx1200m -cp "$T:$JAR" com.halftonestudio.M5CamoDestairTest tests/m4/photos
printf '\nPASS M5 crochet + contour smoothing gate\n'
