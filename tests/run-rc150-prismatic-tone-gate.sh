#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc150-tone-wiring.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
 "$S"/{StyleKit,TopographicLayersEngine,PrismaticDiagonalPortraitEngine}.java \
 tests/{PrismaticDiagonalRC149Test,PrismaticToneRC150Test}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.PrismaticDiagonalRC149Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.PrismaticToneRC150Test
python3 tests/check-rc149-wiring.py
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$S"/{StudioActivity,PrismaticDiagonalPortraitEngine,VinylStyleSuite}.java
# Inherited registration/saving/export and RC143 lint wiring remain mandatory.
bash tests/run-rc148-registration-safety-gate.sh
printf '\nPASS RC150 new tonal geometry plus inherited RC148 safety gate\n'
