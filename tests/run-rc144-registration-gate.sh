#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC144 REGISTRATION DRAG AND EXPORT GATE =====\n'
python3 tests/check-registration-drag-rc144.py
python3 tests/check-v25434-registration.py
# Fast inherited static coverage; historical multi-hour geometry suites remain opt-in.
python3 tests/check-rc143-quality.py
python3 tests/check-rc142-style-cleanup.py
python3 tests/check-rc133-thumbnails.py
python3 tests/check-rc140-assembly-guide.py
python3 tests/check-rc140-transfer.py
if [[ "${RC144_FULL_INHERITED:-0}" == "1" ]]; then
    bash tests/run-rc143-quality-gate.sh
fi
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TMP" \
  "$D"/{RegistrationPreviewGeometry,RegistrationMarks,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
  tests/{RegistrationDragRC144Test,RegistrationSystemRC68Test,RegistrationMarksTest}.java
java -ea -Xmx768m -cp "$TMP" com.halftonestudio.RegistrationDragRC144Test
java -ea -Xmx768m -cp "$TMP" com.halftonestudio.RegistrationSystemRC68Test
java -ea -Xmx768m -cp "$TMP" com.halftonestudio.RegistrationMarksTest
javac -sourcepath app/src/main/java -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource "$D"/{StudioActivity,RegistrationPreviewGeometry,RegistrationMarks,VinylEngine}.java
printf '\nPASS RC144 registration editor, physical SVG parity, and inherited RC143 checks\n'
