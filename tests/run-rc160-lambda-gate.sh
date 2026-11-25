#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc160-lambda.py
python3 tests/check-rc160-lambda-capture.py
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
python3 tests/check-rc140-transfer.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
 "$S"/{StyleKit,TopographicLayersEngine,PatternPortraitTrioEngine,FallenConfettiEngine,InterlockingChainmailEngine,PrismaticDiagonalPortraitEngine,FoundationColorPolicy,TessellationMotifEngine,VinylStyleSuite,PhotoStyleDefaults,NewStyleColorSlots,NewStyleVariantPolicy,PaletteStylePolicy,NewStyleLayerPolicy,SliderRangePolicy,VinylPaletteMapping}.java \
 tests/{FoundationColorRC159Test,ConfettiWeaveRC158Test,Api26RandomRC156Test,FallenConfettiRC155Test,ConfettiToneRC154Test,ConfettiStackRC154Test,PatternTrioRC152Test,SliderRangeRC157Test,StyleControlPaletteRC157Test}.java
for test in FoundationColorRC159Test SliderRangeRC157Test StyleControlPaletteRC157Test Api26RandomRC156Test \
 FallenConfettiRC155Test ConfettiToneRC154Test ConfettiStackRC154Test PatternTrioRC152Test ConfettiWeaveRC158Test; do
 java -ea -Xmx768m -cp "$T" com.halftonestudio."$test"
done
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$S"/{StudioActivity,FoundationColorPolicy,TessellationMotifEngine,PatternPortraitTrioEngine,FallenConfettiEngine,InterlockingChainmailEngine,StudioStylePreview,VinylStyleSuite,StudioThumbnailRegistry,StudioThumbnailPalette,PhotoStyleDefaults,PaletteStylePolicy,NewStyleLayerPolicy}.java
bash tests/run-rc152-thumbnail-jvm.sh
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
 "$S"/{RegistrationCutGeometry,RegistrationMarks,RegistrationPreviewGeometry,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
 tests/{RegistrationCutGeometryRC148Test,RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java
for test in RegistrationCutGeometryRC148Test RegistrationDragRC144Test RegistrationSystemRC68Test RegistrationMarksTest; do
 java -ea -Xmx768m -cp "$T" com.halftonestudio."$test"
done
printf '\nPASS RC160 foundation + lambda capture colors, tessellation base, confetti/chainmail, registration and transfer\n'
