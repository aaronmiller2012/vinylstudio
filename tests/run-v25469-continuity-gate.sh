#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC103: source-preserving CMYK contour ribbons =====\n'
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/LineCoverageRC101Test.java tests/ContourLineRetentionRC103Test.java
java -Xmx512m -cp "$TMP" com.halftonestudio.ContourLineRetentionRC103Test
python3 tests/check-v25469-continuity.py
printf '\n===== RC102 inherited cut-safety and frozen Classic geometry =====\n'
bash tests/run-v25468-opaque-portrait-gate.sh
printf '\nPASS RC103 continuous portrait release gate\n'
