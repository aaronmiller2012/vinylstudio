#!/usr/bin/env bash
# Build 555: Chainmail/shared-palette visibility fix and parallel vector geometry.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
[[ -n "$JAR" && -f "$JAR" ]] || { echo 'JTS 1.20.0 jar unavailable (set VINYL_JTS_JAR)' >&2; exit 4; }
python3 tests/check-gate-manifest.py
python3 tests/check-v3-product555.py
javac -J-Xmx800m -nowarn -cp "$JAR" -sourcepath app/src/main/java:tests -d "$T" \
    tests/{V3ChainmailVisibilityTest,V3ParallelGeometryTest,V3AllStyleExportMatrixTest}.java \
    app/src/main/java/com/halftonestudio/JtsVectorBooleanBackend.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx2g -cp "$T:$JAR" com.halftonestudio.V3ChainmailVisibilityTest
java -ea -Xmx2g -cp "$T:$JAR" com.halftonestudio.V3ParallelGeometryTest
printf 'PASS 555 product gate\n'
