#!/usr/bin/env bash
# Build 561 (3.5.0, M5 completion) release gate: release metadata consistency, the gate
# manifest, and the production wiring of every adopted M5 engine change (M5ReleaseWiringTest).
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== M5 RELEASE (build 561) =====\n'
python3 tests/check-gate-manifest.py
python3 tests/check-v3-release-consistency.py
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
[[ -n "$JAR" && -f "$JAR" ]] || { echo 'JTS 1.20.0 jar unavailable (set VINYL_JTS_JAR)' >&2; exit 4; }
javac -J-Xmx800m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M5ReleaseWiringTest,M4ArtBench,M5Fixtures}.java \
    app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1500m -cp "$T:$JAR" com.halftonestudio.M5ReleaseWiringTest
printf '\nPASS M5 release gate\n'
