#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" \
  tests/{V3EdgeMapSelectionParityTest,V3PhotoCacheParityTest,V3ChangedOnlyCutSafetyTest,V3OptimizedOrganicGeometryFingerprintTest,V3PreflightHashParityTest}.java
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3EdgeMapSelectionParityTest
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3PhotoCacheParityTest
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3ChangedOnlyCutSafetyTest
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3OptimizedOrganicGeometryFingerprintTest
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3PreflightHashParityTest
printf '\nPASS high-gain optimization, geometry and cutter safety parity\n'
