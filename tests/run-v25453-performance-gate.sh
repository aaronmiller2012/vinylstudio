#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
printf '\n=== RC87: bounded photo decoding and weighted render scheduling ===\n'
javac -J-Xmx512m -sourcepath app/src/main/java -d "$TMP" tests/RC87PerformanceTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.RC87PerformanceTest
printf '\n=== RC87: full image parity across tiled band boundaries ===\n'
javac -J-Xmx512m -sourcepath app/src/main/java -d "$TMP" tests/RC87TiledLightingTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.RC87TiledLightingTest
python3 tests/check-v25453-performance.py
printf '\n=== RC86 mandatory regression gate (security, geometry, parity, archives) ===\n'
bash tests/run-v25452-stability-gate.sh
printf '\nPASS RC87 performance and inherited stability release gate\n'
