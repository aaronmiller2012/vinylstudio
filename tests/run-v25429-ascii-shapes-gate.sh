#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC63 CMYK ASCII MOTIF / CUT-SAFETY GATE =====\n'
python3 tests/check-v25429-ascii-shapes.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
# sourcepath autoloads all actual engine dependencies, without Android SDK.
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" "$D"/VinylEngine.java "$D"/AsciiScreenMotif.java "$D"/AsciiVectorGlyphs.java \
  tests/AsciiShapesRC63Test.java tests/VinylEngineTest.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiShapesRC63Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.VinylEngineTest
printf '\n===== EDITOR + SOURCE PARSER =====\n'
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/StudioActivity.java "$D"/VinylEngine.java \
  "$D"/AsciiScreenMotif.java "$D"/AsciiVectorGlyphs.java "$D"/SmartHalftonePresets.java
printf '\n===== RC62 TEXT (SAVED LEGACY PROJECT COMPATIBILITY) =====\n'
javac -sourcepath app/src/main/java -J-Xmx768m -d "$T" "$D"/{StyleKit,PixelFont5x7,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,NewStyleExport,RegistrationMarks}.java tests/{TextRegionsRC62Test,MultilayerTextRC61Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextRegionsRC62Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC61Test
printf '\n===== PROJECT LIBRARY CRASH/RECOVERY TEST =====\n'
javac -sourcepath app/src/main/java -d "$T" "$D"/{StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader}.java tests/StudioProjectLibraryTest.java
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
printf '\nPASS RC63 ASCII shape screen and legacy text / project-safety regression gates\n'
