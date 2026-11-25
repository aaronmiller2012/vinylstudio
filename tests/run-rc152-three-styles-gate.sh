#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc152-three-styles.py
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
python3 tests/check-rc140-transfer.py
TEMP="$(mktemp -d)";trap 'rm -rf "$TEMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TEMP" \
    "$S"/{StyleKit,TopographicLayersEngine,PatternPortraitTrioEngine,PrismaticDiagonalPortraitEngine,VinylStyleSuite,PhotoStyleDefaults,NewStyleColorSlots,NewStyleVariantPolicy,PaletteStylePolicy}.java \
    tests/PatternTrioRC152Test.java
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.PatternTrioRC152Test
javac -sourcepath app/src/main/java -d "$TEMP" tests/ParseJavaSource.java
java -cp "$TEMP" ParseJavaSource "$S"/{StudioActivity,PatternPortraitTrioEngine,StudioStylePreview,VinylStyleSuite,StudioThumbnailRegistry,PhotoStyleDefaults}.java
bash tests/run-rc152-thumbnail-jvm.sh
# Verify inherited registration geometry without running historical release-metadata assertions.
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TEMP" \
    "$S"/{RegistrationCutGeometry,RegistrationMarks,RegistrationPreviewGeometry,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
    tests/{RegistrationCutGeometryRC148Test,RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java
for suite in RegistrationCutGeometryRC148Test RegistrationDragRC144Test RegistrationSystemRC68Test RegistrationMarksTest; do
  java -ea -Xmx768m -cp "$TEMP" com.halftonestudio."$suite"
done
printf '\nPASS RC152 three independent cut engines, thumbnails, registration and transfer contracts\n'
