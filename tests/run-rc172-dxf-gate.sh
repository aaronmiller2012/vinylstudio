#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
echo '===== RC172 SILHOUETTE BASIC DXF R13 / STAGED CUT TESTS ====='
python3 tests/check-current-release.py
python3 tests/check-rc172-dxf-integration.py
D="$(mktemp -d)"; trap 'rm -rf -- "$D"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$D" \
  app/src/main/java/com/halftonestudio/OutputDeviceProfile.java \
  app/src/main/java/com/halftonestudio/CutSvgCompatibility.java \
  app/src/main/java/com/halftonestudio/SvgDxfR13Exporter.java \
  app/src/main/java/com/halftonestudio/CutSheetZipWriter.java \
  app/src/main/java/com/halftonestudio/StagedCutZip.java \
  tests/RC172DxfR13Test.java
java -ea -cp "$D" com.halftonestudio.RC172DxfR13Test
javac -J-Xmx900m -sourcepath app/src/main/java -d "$D" tests/RC172ProductionSvgInteropTest.java
java -ea -cp "$D" com.halftonestudio.RC172ProductionSvgInteropTest
bash tests/run-rc171-output-targets-gate.sh
echo 'PASS RC172 DXF R13 and inherited RC171/RC170 host gates'
