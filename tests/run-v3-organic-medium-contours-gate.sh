#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" \
  "$S"/{OrganicFlowCutSmoother,OrganicFlowPortraitEngine,StyleKit,PatternPortraitTrioEngine}.java \
  tests/V3OrganicMediumContoursTest.java
java -ea -Xmx900m -cp "$T" com.halftonestudio.V3OrganicMediumContoursTest
