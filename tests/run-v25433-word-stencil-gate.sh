#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== RC67 WORD MOSAIC AND KNOCKOUT STENCILS =====\n'
python3 tests/check-java-switch-labels.py app/src/main/java
python3 tests/check-v25433-word-stencil.py
javac -sourcepath app/src/main/java -J-Xmx768m -d "$T" "$D"/{StyleKit,PixelFont5x7,WordVectorGlyphs,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,NewStyleExport,RegistrationMarks}.java tests/{WordStencilStylesRC67Test,WordMultilayerRC66Test,MultilayerTextRC60Test,MultilayerTextRC61Test,TextRegionsRC62Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.WordStencilStylesRC67Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.WordMultilayerRC66Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC60Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.MultilayerTextRC61Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.TextRegionsRC62Test
printf '\n===== RC64 ASCII SHAPES STABILITY =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" "$D"/{VinylEngine,AsciiVectorGlyphs,AsciiScreenMotif,WordScreenMotif}.java tests/{AsciiDensityRC64Test,WordShapesRC65Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.AsciiDensityRC64Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.WordShapesRC65Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,VinylEngine}.java
printf '\nPASS RC67 Word Mosaic/Knockout and inherited source gates\n'
