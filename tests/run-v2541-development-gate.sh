#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2540-workflow-gate.sh
TEMP="$(mktemp -d)";trap 'rm -rf "$TEMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TEMP" "$D"/{RegionalPortraitDetail,CmykLineVisibility}.java tests/RegionalDetailV2541Test.java
java -cp "$TEMP" com.halftonestudio.RegionalDetailV2541Test
javac -sourcepath app/src/main/java -d "$TEMP" "$D"/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ImageLevels,TiledImageLevels,CutWarningLocator}.java tests/{TiledLevelsV2541Test,CutWarningsV2541Test,CmykClassicVisibilityV2541Test}.java
java -cp "$TEMP" com.halftonestudio.TiledLevelsV2541Test
java -cp "$TEMP" com.halftonestudio.CutWarningsV2541Test
java -Xmx768m -cp "$TEMP" com.halftonestudio.CmykClassicVisibilityV2541Test
python3 tests/check-v2541-source.py
