#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc149-wiring.py
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TEMP" \
  "$S"/{PrismaticDiagonalPortraitEngine,StyleKit,TopographicLayersEngine}.java \
  tests/PrismaticDiagonalRC149Test.java
java -ea -Xmx768m -cp "$TEMP" com.halftonestudio.PrismaticDiagonalRC149Test
javac -sourcepath app/src/main/java -d "$TEMP" tests/ParseJavaSource.java
java -cp "$TEMP" ParseJavaSource "$S"/{StudioActivity,StudioStylePreview,PrismaticDiagonalPortraitEngine,VinylStyleSuite}.java
bash tests/run-rc148-registration-safety-gate.sh
printf '\nPASS RC149 prismatic diagonal + inherited registration/reliability gate\n'
