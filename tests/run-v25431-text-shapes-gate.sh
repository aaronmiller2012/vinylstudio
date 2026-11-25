#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC65 WORD / ASCII INDEPENDENT SHAPES =====\n'
python3 tests/check-java-switch-labels.py app/src/main/java
python3 tests/check-v25431-text-shapes.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{VinylEngine,WordScreenMotif,AsciiScreenMotif,AsciiVectorGlyphs}.java \
  tests/{WordShapesRC65Test,AsciiShapesRC63Test,AsciiDensityRC64Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.WordShapesRC65Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiShapesRC63Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiDensityRC64Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,VinylEngine,WordScreenMotif,AsciiVectorGlyphs,SmartHalftonePresets}.java
printf '\n===== LEGACY TEXT PROJECT AND LIBRARY COMPATIBILITY =====\n'
python3 tests/check-v25426-multilayer.py
python3 tests/check-v25428-regions.py
python3 tests/check-v25429-ascii-shapes.py
python3 tests/check-v25430-ascii-vector.py
java -ea -Xmx768m -cp "$T" com.halftonestudio.WordShapesRC65Test
printf '\nPASS RC65 distinct tiles, full-word Shapes, CMYK/spot, Snips, SVG, and saved style IDs\n'
