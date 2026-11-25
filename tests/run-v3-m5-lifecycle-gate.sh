#!/usr/bin/env bash
# M5 lifecycle/reliability: render snapshot completeness, lane/stale-result races, cut-preparation
# queue starvation, and a regression matrix over every active suite style.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-${JTS_JAR:-}}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
[[ -n "$JAR" && -f "$JAR" ]] || { echo 'JTS 1.20.0 jar unavailable (set VINYL_JTS_JAR)' >&2; exit 4; }
JTS="$JAR"; CP_EXTRA="-cp $JTS"
printf '\n===== M5 LIFECYCLE (build 561) =====\n'
# shellcheck disable=SC2086
javac -J-Xmx700m -nowarn $CP_EXTRA -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M5SnapshotCopyTest,M5RenderSchedulingTest,M5AllActiveStylesTest}.java \
    2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx500m -cp "$T" com.halftonestudio.M5SnapshotCopyTest
java -ea -Xmx300m -cp "$T" com.halftonestudio.M5RenderSchedulingTest
java -ea -Xmx1500m -cp "$T${JTS:+:$JTS}" com.halftonestudio.M5AllActiveStylesTest
printf '\nPASS M5 lifecycle gate\n'
