#!/usr/bin/env bash
# Build 553: registration-mark behavior suites (RC68/RC144/RC148) were only reachable
# through the orphaned math-repair-extended chain, whose historical RC142 source-text
# assertion is obsolete. Run the executable registration geometry tests directly.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -nowarn -sourcepath app/src/main/java -d "$TMP" \
    "$S"/{RegistrationCutGeometry,RegistrationMarks,RegistrationPreviewGeometry,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
    tests/{RegistrationCutGeometryRC148Test,RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java 2>&1 | { grep -v '^Note:' || true; }
for suite in RegistrationCutGeometryRC148Test RegistrationDragRC144Test RegistrationSystemRC68Test RegistrationMarksTest; do
    java -ea -Xmx768m -cp "$TMP" com.halftonestudio."$suite"
done
printf 'PASS 553 registration geometry, drag, system and mark suites\n'
