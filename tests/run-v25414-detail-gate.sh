#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{PhotoCellImportance,StyleKit,RegistrationMarks,CancellationCheckpoint,MultiColorStippleEngine,TessellationEngine,StainedGlassEngine,TopographicLayersEngine,MaskedFacetClipper,LowPolyDelaunayEngine,PixelFont5x7,TspArtEngine,LinocutEngine,TruchetTilesEngine,TextColorSeparation,TextPortraitRegions,AsciiPortraitEngine,WordPortraitEngine,WordKnockoutEngine,WordVectorGlyphs,TextHalftoneEngine,RidgeLinesEngine,VinylStyleSuite,NewStyleColorSlots}.java tests/CellDetailV25414Test.java
java -Xmx1g -cp "$T" com.halftonestudio.CellDetailV25414Test
python3 tests/check-v25414-detail-wiring.py
bash tests/run-v25413-stability-gate.sh
