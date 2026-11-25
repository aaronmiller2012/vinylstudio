#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" \
  tests/{V3OrganicRenderOptimizationTest,V3OrganicCachedRibbonParityTest,V3OrganicMorphologyParityTest,V3OrganicRenderPerformanceTest,V3OrganicDraftPerformanceTest}.java
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicRenderOptimizationTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicCachedRibbonParityTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicMorphologyParityTest
python3 tests/check-v3-organic-smoothing-ui.py
# Time on the host JVM only: we print timings, never set a device-independent
# time threshold that could hide a geometry or cut-safety regression.
java -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicRenderPerformanceTest

java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicDraftPerformanceTest
