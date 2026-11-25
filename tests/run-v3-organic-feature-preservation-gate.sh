#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx800m -sourcepath app/src/main/java:tests -d "$T" \
  "$S"/{OrganicFlowCutSmoother,OrganicFlowPortraitEngine,StyleKit,PatternPortraitTrioEngine,SubpixelStencilContours}.java \
  tests/{V3OrganicFeaturePreservationTest,V3OrganicPortraitRetentionTest,V3OrganicCurveVisibleChangeTest}.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3OrganicFeaturePreservationTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3OrganicPortraitRetentionTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3OrganicCurveVisibleChangeTest
