#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC117 offset cell + inherited RC116 security/export gate =====\n'
bash tests/run-rc116-detail-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/LayeredCellHighDetailRC116Test.java tests/OffsetCellRC117Test.java tests/ProjectSettingsSchemaHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.OffsetCellRC117Test
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
python3 tests/check-rc117-offset-integration.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
    app/src/main/java/com/halftonestudio/StudioActivity.java \
    app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java \
    app/src/main/java/com/halftonestudio/OffsetCellPortraitEngine.java
printf '\nPASS RC117 offset cell release gate\n'
