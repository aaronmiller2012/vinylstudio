#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== RC84 IMAGE-AWARE STYLE / TSP / GAMMA TESTS ====='
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TMP" \
  tests/ImageLevelsReference.java tests/RC84PerformanceTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC84PerformanceTest
python3 tests/check-v25450-performance.py
echo '===== INHERITED RC83 GUIDE + RC82 THROUGH RC70 CHECKS ====='
bash tests/run-v25449-guide-gate.sh
