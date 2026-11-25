#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC164 EIGHT CUTTABLE PATTERN FAMILIES / NINE PHYSICAL SHEETS =====\n'
python3 tests/check-rc164-pattern-rebuild.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
SRC=app/src/main/java/com/halftonestudio
javac -J-Xmx900m -sourcepath app/src/main/java -d "$T" \
    tests/{CraftPatternRC164Test,CraftPatternRC163Test}.java
java -ea -Xmx900m -cp "$T" com.halftonestudio.CraftPatternRC164Test
java -ea -Xmx900m -cp "$T" com.halftonestudio.CraftPatternRC163Test
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$SRC"/{StudioActivity,VinylStyleSuite,NineLayerCraftPatternEngine,CamouflagePalette,StudioThumbnailRegistry,StudioThumbnailPalette,StudioStylePreview,NewStyleColorSlots,NewStyleLayerPolicy,PhotoStyleDefaults,NewStyleVariantPolicy}.java
bash tests/run-rc152-thumbnail-jvm.sh
bash tests/run-rc162-render-responsiveness-gate.sh
printf '\nPASS RC164 pattern geometry, thumbnail, colors, export and inherited host regressions\n'
