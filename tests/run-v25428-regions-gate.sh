#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC62 REGION-AWARE TEXT ENGINES + CUTTABLE SNIP BASE =====\n'
python3 tests/check-v25428-regions.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -J-Xmx768m -d "$T" "$D"/{StyleKit,PixelFont5x7,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,NewStyleExport,RegistrationMarks}.java tests/{TextRegionsRC62Test,MultilayerTextRC61Test,MultilayerTextRC60Test,TextPortraitRC59Test,EightStyleVariantsTest}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextRegionsRC62Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC61Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC60Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextPortraitRC59Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/VinylStyleSuite.java" "$D/TextHalftoneEngine.java" "$D/AsciiPortraitEngine.java" "$D/WordPortraitEngine.java" "$D/TextPortraitRegions.java"
printf '\n===== RC61 / RC60 + INHERITED LIBRARY, RENDER AND ARTWORK GATES =====\n'
bash tests/run-v25427-textcolor-gate.sh
printf '\nPASS RC62 color segmentation and two independent text engines\n'
