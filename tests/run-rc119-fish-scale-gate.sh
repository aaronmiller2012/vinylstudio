#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC119 FISH SCALE + RC118 INHERITED RELEASE GATE =====\n'
bash tests/run-rc118-bottle-cap-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
 tests/FishScaleRC119Test.java tests/ProjectSettingsSchemaHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.FishScaleRC119Test
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
python3 tests/check-rc119-fish-scale-integration.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
 app/src/main/java/com/halftonestudio/StudioActivity.java \
 app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java \
 app/src/main/java/com/halftonestudio/FishScaleMosaicEngine.java
printf '\nPASS RC119 Fish Scale release gate\n'
