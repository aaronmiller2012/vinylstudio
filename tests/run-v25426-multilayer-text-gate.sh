#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC60 MULTILAYER ASCII/WORDS + FILLED SNIP BASE =====\n'
python3 tests/check-v25426-multilayer.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,NewStyleExport,RegistrationMarks}.java tests/{MultilayerTextRC60Test,TextPortraitRC59Test,EightStyleVariantsTest}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC60Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextPortraitRC59Test
# UI and new core engine changed: syntactic verification, no installed Android SDK assumed on host.
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/VinylStyleSuite.java" "$D/TextHalftoneEngine.java" "$D/NewStyleColorSlots.java"
printf '\n===== RC58 gallery + RC57 performance + RC55 library recovery =====\n'
bash tests/run-v25424-interface-gate.sh
printf '\nPASS RC60 multilayer-text release gate\n'
