#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc157-sliders-palette-layers.py
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
python3 tests/check-rc140-transfer.py
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TEMP" \
    "$S"/{StyleKit,TopographicLayersEngine,PatternPortraitTrioEngine,FallenConfettiEngine,PrismaticDiagonalPortraitEngine,VinylStyleSuite,PhotoStyleDefaults,NewStyleColorSlots,NewStyleVariantPolicy,PaletteStylePolicy}.java \
    tests/Api26RandomRC156Test.java tests/FallenConfettiRC155Test.java tests/ConfettiToneRC154Test.java tests/ConfettiStackRC154Test.java tests/PatternTrioRC152Test.java \
    tests/SliderRangeRC157Test.java tests/StyleControlPaletteRC157Test.java
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.SliderRangeRC157Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.StyleControlPaletteRC157Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.Api26RandomRC156Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.FallenConfettiRC155Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.ConfettiToneRC154Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.ConfettiStackRC154Test
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.PatternTrioRC152Test
javac -sourcepath app/src/main/java -d "$TEMP" tests/ParseJavaSource.java
java -cp "$TEMP" ParseJavaSource "$S"/{StudioActivity,PatternPortraitTrioEngine,FallenConfettiEngine,StudioStylePreview,VinylStyleSuite,StudioThumbnailRegistry,PhotoStyleDefaults,PaletteStylePolicy,VinylPaletteMapping,SliderRangePolicy,NewStyleLayerPolicy}.java
bash tests/run-rc152-thumbnail-jvm.sh
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TEMP" \
    "$S"/{RegistrationCutGeometry,RegistrationMarks,RegistrationPreviewGeometry,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
    tests/{RegistrationCutGeometryRC148Test,RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java
for suite in RegistrationCutGeometryRC148Test RegistrationDragRC144Test RegistrationSystemRC68Test RegistrationMarksTest; do
  java -ea -Xmx768m -cp "$TEMP" com.halftonestudio."$suite"
done
printf '\nPASS RC157 control ranges, preset tone order, layer base protection, inherited natural confetti and registrations\n'
