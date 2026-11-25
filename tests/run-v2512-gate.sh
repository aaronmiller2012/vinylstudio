#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2511-gate.sh
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" app/src/main/java/com/halftonestudio/{MultiColorStippleEngine,TessellationEngine,PhotoCellImportance,RegistrationMarks}.java tests/AdaptiveStippleCoverageTest.java
java -cp "$T" AdaptiveStippleCoverageTest
python3 tests/check-v2512-wiring.py
printf 'PASS v2.51.2 adaptive coverage mandatory gate\n'
