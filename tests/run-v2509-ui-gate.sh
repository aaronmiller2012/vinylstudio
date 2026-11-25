#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v2509-ui.py
python3 tests/check-v2508-ui.py
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/{RegistrationMarks,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance}.java tests/StippleVectorExportSmokeTest.java
java -cp "$TMP" StippleVectorExportSmokeTest
printf 'PASS v2.50.9 focused vector swatches/source test\n'
