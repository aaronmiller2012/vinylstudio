#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== RC59 artwork picker + text portrait detail =====\n'
python3 tests/check-v25425-textdetail.py
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots}.java tests/TextPortraitRC59Test.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextPortraitRC59Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/VinylStyleSuite.java" "$D/TextHalftoneEngine.java" "$D/PixelFont5x7.java"
printf '\n===== Inherited RC58 / RC57 / RC56 / RC55 regressions =====\n'
bash tests/run-v25424-interface-gate.sh
printf '\nPASS RC59 text portrait and navigation gate\n'
