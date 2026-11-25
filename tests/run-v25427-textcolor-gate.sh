#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC61 COLOUR-SEPARATED ASCII AND WORD PORTRAITS =====\n'
python3 tests/check-v25427-textcolor.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,NewStyleExport,RegistrationMarks}.java tests/{MultilayerTextRC61Test,MultilayerTextRC60Test,TextPortraitRC59Test,EightStyleVariantsTest}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC61Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC60Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextPortraitRC59Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/VinylStyleSuite.java" "$D/TextHalftoneEngine.java" "$D/TextColorSeparation.java"
printf '\n===== RC60 GEOMETRY + INHERITED REGRESSION TESTS =====\n'
bash tests/run-v25426-multilayer-text-gate.sh
printf '\nPASS RC61 color separation and whole-word SVG geometry\n'
