#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/V531PerformanceArchitectureTest.java tests/V531ProfileRegistry.java tests/ParseJavaSource.java tests/V3OrganicRenderOptimizationTest.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.V531PerformanceArchitectureTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3OrganicRenderOptimizationTest
java -cp "$T" com.halftonestudio.V531ProfileRegistry > "$T/registry.csv"
diff -u qualification/render-profile-registry.csv "$T/registry.csv"
python3 tests/test_profile_summary.py
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java app/src/main/java/com/halftonestudio/SpecialtyVectorCache.java
python3 tests/check-v3-release-consistency.py
