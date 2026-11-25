#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" \
    "$S"/{OrganicFlowCutSmoother,OrganicFlowPortraitEngine,StyleKit,PatternPortraitTrioEngine}.java \
    tests/{V3AllStyleExportMatrixTest,V3OrganicNoChangeRegressionTest,V3OrganicUniformStrideTest,V3OrganicUniformLayerSmoothingTest}.java
java -ea -Xmx1400m -cp "$T" com.halftonestudio.V3OrganicNoChangeRegressionTest

java -ea -Xmx900m -cp "$T" com.halftonestudio.V3OrganicUniformStrideTest

java -ea -Xmx900m -cp "$T" com.halftonestudio.V3OrganicUniformLayerSmoothingTest
