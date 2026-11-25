#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{StyleKit,PixelFont5x7,LowPolyDelaunayEngine,StainedGlassEngine,PhotoCellImportance,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,TopographicLayersEngine,RidgeLinesEngine,VinylStyleSuite,MaskedFacetClipper,NewStyleColorSlots,ColorWorkspacePalette,VinylPalettes,PaletteHueShift,NewStylePalettePresets}.java tests/NewStylePalettePresetsTest.java
java -cp "$T" com.halftonestudio.NewStylePalettePresetsTest
python3 tests/check-v25412-palette-ui.py
bash tests/run-v25411-layer-operations-gate.sh
