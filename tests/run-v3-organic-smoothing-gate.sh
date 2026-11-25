#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
S=app/src/main/java/com/halftonestudio
javac -J-Xmx1024m -sourcepath app/src/main/java -d "$T" \
    "$S"/{OrganicFlowPortraitEngine,OrganicFlowCutSmoother,SubpixelStencilContours,StyleKit,VinylStyleSuite,NewStyleExport,FinalCutSheetSafety}.java \
    tests/{V3AllStyleExportMatrixTest,V3OrganicSmoothingSliderTest}.java
java -ea -Xmx1400m -cp "$T" com.halftonestudio.V3OrganicSmoothingSliderTest
python3 tests/check-v3-organic-smoothing-ui.py
