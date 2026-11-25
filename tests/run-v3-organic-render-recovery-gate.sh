#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" \
  tests/{V3PreflightHashParityTest,V3OrganicRenderStressTest,V3OrganicDraftPerformanceTest}.java
python3 tests/check-v3-organic-preview-rescue.py
java -ea -Xmx1100m -cp "$T" com.halftonestudio.V3PreflightHashParityTest
# Host workload intentionally contains 850x987 noisy pixels and an 11-in,
# 0.254-mm minimum cut setting: it caught the slow selectable styles.
# These timings are diagnostic, not Android/device speed guarantees.
for style in 0 1 2; do
  java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicRenderStressTest "$style" 279.4 .254 6
done
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicDraftPerformanceTest
printf '\nPASS Organic Flow render recovery, draft visibility and preflight parity\n'
