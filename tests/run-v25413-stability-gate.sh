#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,TopographicLayersEngine,MaskedFacetClipper,StyleCutGeometryPreflight,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,RidgeLinesEngine,VinylStyleSuite,NewStyleColorSlots}.java tests/{StabilityGeometryTest,EightStyleIntegrationTest,GeometryCompatibilityTest}.java
java -cp "$T" com.halftonestudio.StabilityGeometryTest
java -Xmx1g -cp "$T" com.halftonestudio.EightStyleIntegrationTest
java -Xmx1g -cp "$T" com.halftonestudio.GeometryCompatibilityTest
python3 tests/check-v25413-stability-wiring.py
bash tests/run-v25412-palette-library-gate.sh
