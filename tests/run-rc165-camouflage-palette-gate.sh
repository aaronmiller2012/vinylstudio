#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC165 CAMOUFLAGE FAMILY VINYL PALETTE GATE =====\n'
python3 tests/check-current-release.py
python3 tests/check-rc165-camouflage-palette.py
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP" \
    tests/{CamoPaletteDefaultsRC165Test,CraftPatternRC164Test}.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.CamoPaletteDefaultsRC165Test
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.CraftPatternRC164Test
javac -d "$TMP" tests/ParseJavaSource.java
SRC=app/src/main/java/com/halftonestudio
java -cp "$TMP" ParseJavaSource "$SRC"/{StudioActivity,CamouflagePalette,VinylStyleSuite,NineLayerCraftPatternEngine}.java
bash tests/run-rc152-thumbnail-jvm.sh
bash tests/run-rc162-render-responsiveness-gate.sh
printf '\nPASS RC165 palettes, cut sheets, inherited engines/registration and rendering gate\n'
