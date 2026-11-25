#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc148-wiring.py
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TMP" \
    "$S"/{RegistrationCutGeometry,RegistrationMarks,RegistrationPreviewGeometry,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
    tests/{RegistrationCutGeometryRC148Test,RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java
for suite in RegistrationCutGeometryRC148Test RegistrationDragRC144Test RegistrationSystemRC68Test RegistrationMarksTest; do
    java -ea -Xmx768m -cp "$TMP" com.halftonestudio."$suite"
done
javac -sourcepath app/src/main/java -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource "$S"/{StudioActivity,RegistrationCutGeometry,AssemblyGuideDocument,AssemblyGuidePlan,ArtworkDocumentExport}.java
bash tests/run-rc147-registration-viewport-gate.sh
printf '\nPASS RC148 registration safety and inherited reliability gate\n'
