#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
python3 tests/check-rc127-percent.py
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/RC127PercentLimitsHostTest.java tests/ProjectSettingsSchemaHostTest.java tests/AutoPhotoTunerTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC127PercentLimitsHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
java -Xmx768m -cp "$TMP" AutoPhotoTunerTest
bash tests/run-rc126-ui-gate.sh
printf '\nPASS RC127 bounded percentage controls and inherited RC126 host gate\n'
