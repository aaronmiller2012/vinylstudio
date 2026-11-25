#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
printf '\n===== RC73 WHITE L1 / ONE-SHEET REGRESSION =====\n'
python3 tests/check-v25439-silhouette-l1.py
python3 tests/check-java-switch-labels.py app/src/main/java
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{AutoSilhouetteUnderlay,VinylEngine,SolidSnipBase}.java \
  tests/AutoSilhouetteL1RC73Test.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.AutoSilhouetteL1RC73Test
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,AutoSilhouetteUnderlay}.java
java -cp "$T" CheckDuplicateJavaMethods app/src/main/java
printf '\n===== INHERITED RC72 GATE =====\n'
bash tests/run-v25438-auto-silhouette-gate.sh
printf '\nPASS RC73 WHITE L1 AND INHERITED SOURCE CHECKS\n'
