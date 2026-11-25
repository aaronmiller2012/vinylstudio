#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,RegistrationMarks,NewStyleExport}.java   tests/{EightStyleIntegrationTest,EightStyleVariantsTest,EightStyleExportTest}.java
java -Xmx1g -cp "$T" com.halftonestudio.EightStyleIntegrationTest
java -Xmx1g -cp "$T" com.halftonestudio.EightStyleVariantsTest
java -Xmx512m -cp "$T" com.halftonestudio.EightStyleExportTest
python3 tests/check-v2548-eight-styles.py
bash tests/run-v2547-color-ownership-gate.sh
