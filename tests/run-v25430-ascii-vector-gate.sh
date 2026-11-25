#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC64 PHYSICAL GLYPH GEOMETRY AND SCREEN DETAIL =====\n'
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs}.java \
  tests/{AsciiDensityRC64Test,AsciiVectorContoursRC64Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiVectorContoursRC64Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiDensityRC64Test
printf '\n===== RC63 CMYK / SVG / LEGACY TEXT AND PROJECT SAFETY =====\n'
bash tests/run-v25429-ascii-shapes-gate.sh
printf '\n===== RC64 SOURCE AND RELEASE CONTRACT =====\n'
python3 tests/check-v25430-ascii-vector.py
printf '\nPASS RC64 adaptive, size-aware vector ASCII screens and inherited regressions\n'
