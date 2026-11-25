#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v3-color-pipeline-integration.py
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/V3ColorPipelineRepairTest.java tests/UniversalHueTonalTest.java
java -ea -Xmx650m -cp "$TMP" com.halftonestudio.V3ColorPipelineRepairTest
java -ea -Xmx650m -cp "$TMP" UniversalHueTonalTest
