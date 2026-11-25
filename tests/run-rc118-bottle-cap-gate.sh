#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC118 BOTTLE CAP PORTRAIT + INHERITED RC117 GATES =====\n'
bash tests/run-rc117-offset-cell-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" tests/BottleCapRC118Test.java tests/ProjectSettingsSchemaHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.BottleCapRC118Test
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
python3 tests/check-rc118-bottle-cap-integration.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java app/src/main/java/com/halftonestudio/BottleCapMosaicEngine.java
printf '\nPASS RC118 Bottle Cap release gate\n'
