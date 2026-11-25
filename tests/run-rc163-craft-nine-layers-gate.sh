#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC163 FIVE CUTTABLE CRAFT STYLES / NINE PHYSICAL LAYERS =====\n'
python3 tests/check-rc163-craft-nine-layers.py
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP" tests/CraftPatternRC163Test.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.CraftPatternRC163Test
javac -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource app/src/main/java/com/halftonestudio/{StudioActivity,VinylStyleSuite,NineLayerCraftPatternEngine,StudioThumbnailRegistry,StudioThumbnailPalette,StudioStylePreview,NewStyleColorSlots,NewStyleLayerPolicy,PhotoStyleDefaults,FoundationColorPolicy,PaletteStylePolicy,ColorWorkspacePalette}.java
bash tests/run-rc162-render-responsiveness-gate.sh
printf '\nPASS RC163 craft nine-layer release and complete inherited RC162 host gate\n'
