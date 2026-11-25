#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC75 VISIBLE CONTROLS & COLOR EDITOR =====\n'
python3 tests/check-v25441-visible-controls.py
python3 tests/check-rc75-navigation-compilefix.py
python3 tests/check-rc75-layers-crashfix.py
python3 tests/check-java-switch-labels.py app/src/main/java
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{StudioActivity,VinylColorWheel,VinylColorEditor}.java
java -cp "$T" CheckDuplicateJavaMethods app/src/main/java
printf '\n===== RC74 PHYSICAL LAYERS & PRIOR SOURCE REGRESSION =====\n'
bash tests/run-v25440-physical-layers-gate.sh
printf '\nPASS RC75 VISIBLE COLOR CONTROL GATE\n'
