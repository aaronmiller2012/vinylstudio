#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== RC68 REGISTRATION GEOMETRY AND EXPORT INVARIANTS =====\n'
python3 tests/check-java-switch-labels.py app/src/main/java
python3 tests/check-v25434-registration.py
python3 tests/check-v25419-cleanup.py
python3 tests/check-v25433-word-stencil.py
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{RegistrationMarks,NewStyleExport,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine}.java \
  tests/{RegistrationMarksTest,RegistrationSystemRC68Test,CutSetupTest}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.RegistrationMarksTest
java -ea -Xmx768m -cp "$T" com.halftonestudio.RegistrationSystemRC68Test
java -ea -Xmx768m -cp "$T" com.halftonestudio.CutSetupTest
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,RegistrationMarks,VinylEngine,TessellationEngine,LayeredCellPortraitEngine,MultilayerStencilEngine,NewStyleExport}.java
printf '\n===== RETAINED RC67 ARTWORK COMPATIBILITY =====\n'
bash tests/run-v25433-word-stencil-gate.sh
printf '\nPASS RC68 mandatory registration and saved-project source gate\n'
