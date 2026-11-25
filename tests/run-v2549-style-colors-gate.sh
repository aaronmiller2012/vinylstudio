#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots}.java tests/StyleColorsIntegrationTest.java
java -Xmx1g -cp "$T" com.halftonestudio.StyleColorsIntegrationTest
python3 tests/check-v2549-style-colors.py
bash tests/run-v2548-eight-styles-gate.sh
