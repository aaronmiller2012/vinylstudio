#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
printf '\n===== RC125 PALETTE AND OFFSET CELL TESTS =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
 tests/RC125OffsetHostTest.java tests/RC124PaletteLayersHostTest.java \
 tests/ProjectSettingsSchemaHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC125OffsetHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.RC124PaletteLayersHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
python3 tests/check-rc125-integration.py
bash tests/run-rc124-colors-layers-gate.sh
printf '\nPASS RC125 mandatory host gate\n'
