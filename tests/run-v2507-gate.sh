#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== PRESERVE v2.49.4 LINE AND v2.50.5 CMYK FIXES =====\n'
bash tests/run-v2505-gate.sh
printf '\n===== 8-COLOR STIPPLE / FOUR OVERLAP MODES / SVG =====\n'
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{RegistrationMarks,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance}.java \
  tests/EightColorStippleTest.java tests/TessellationEngineTest.java tests/StippleDensityRegressionTest.java
java -Xmx512m -cp "$TMP" EightColorStippleTest
java -Xmx768m -cp "$TMP" StippleDensityRegressionTest
java -Xmx512m -cp "$TMP" com.halftonestudio.TessellationEngineTest
javac -sourcepath app/src/main/java -cp "$TMP" -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/{StudioActivity,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance}.java
python3 tests/check-v2506-stipple-wiring.py
printf '\nPASS v2.50.7 source-level release gate\n'
