#!/usr/bin/env bash
# M5 colour: palette/colour-state invariants (mounting surface, Global Reverse, CMYK physical order, export
# names), photo-palette active subset (highlights are not parked) and the Confetti solid-base policy on a
# real photograph through the production palette path and the real Confetti engine.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
CP="$T"; [[ -n "$JAR" ]] && CP="$T:$JAR"
printf '\n===== M5 COLOUR (build 561) =====\n'
javac -J-Xmx700m -nowarn ${JAR:+-cp "$JAR"} -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M5ColorStateTest,M5PhotoSubsetTest,M5ConfettiBaseTest,M4ArtBench}.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx300m  -cp "$CP" com.halftonestudio.M5ColorStateTest
java -ea -Xmx600m  -Djava.awt.headless=true -cp "$CP" com.halftonestudio.M5PhotoSubsetTest tests/m4/photos | tail -2
java -ea -Xmx1000m -Djava.awt.headless=true -cp "$CP" com.halftonestudio.M5ConfettiBaseTest tests/m4/photos
printf '\nPASS M5 colour gate\n'
