#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC78 RELIABILITY GATE =====\n'
python3 tests/check-v25444-reliability.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,VinylEngine,VinylCrashReport,VinylColorEditor}.java
java -cp "$T" CheckDuplicateJavaMethods "$D"
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" tests/PhysicalSlotSafetyRC78Test.java
java -cp "$T" PhysicalSlotSafetyRC78Test
printf '\n===== RC77 THROUGH RC70 HISTORICAL GATES =====\n'
bash tests/run-v25443-cut-sheet-gate.sh
printf '\nPASS RC78 RELIABILITY GATE\n'
