#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC116 LAYERED CELL HIGH-DETAIL AND LEGACY SECURITY GATE =====\n'
bash tests/run-rc115-play-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/LayeredCellReleaseCandidateTest.java tests/LayeredCellHighDetailRC116Test.java \
  tests/LayerWorkspaceMotifTest.java tests/ProjectSettingsSchemaHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.LayeredCellReleaseCandidateTest
java -Xmx768m -cp "$TMP" com.halftonestudio.LayeredCellHighDetailRC116Test
java -Xmx768m -cp "$TMP" com.halftonestudio.LayerWorkspaceMotifTest
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
python3 tests/check-rc116-detail-integration.py
printf '\nPASS RC116 complete host gate\n'
