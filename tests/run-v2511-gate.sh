#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v2510-ui.py
python3 tests/check-v2509-ui.py
python3 tests/check-v2511-ui.py
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{MultiColorStippleEngine,TessellationEngine,PhotoCellImportance,RegistrationMarks}.java \
  tests/{StippleShapesCoverageTest,StippleDensityRegressionTest,StippleVectorExportSmokeTest,EightColorStippleTest}.java
java -cp "$TMP" StippleShapesCoverageTest
java -cp "$TMP" StippleVectorExportSmokeTest
java -cp "$TMP" StippleDensityRegressionTest
java -cp "$TMP" EightColorStippleTest
printf 'PASS v2.51.1 mandatory source and stipple geometry gate\n'
