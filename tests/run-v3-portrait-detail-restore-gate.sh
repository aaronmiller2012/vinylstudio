#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/{V533PortraitDetailTest,LayeredCellHighDetailRC116Test,OffsetCellRC117Test,FishScaleRC119Test,FishScaleClearanceRC132Test,FishScaleRC123GeometryTest,FishScaleRC131LatticeTest,CamoDetailRC167Test,CamoPaletteDefaultsRC165Test,ParseJavaSource}.java
for c in V533PortraitDetailTest LayeredCellHighDetailRC116Test OffsetCellRC117Test FishScaleRC119Test FishScaleClearanceRC132Test FishScaleRC123GeometryTest FishScaleRC131LatticeTest CamoDetailRC167Test CamoPaletteDefaultsRC165Test; do
    java -ea -Djava.awt.headless=true -Xmx850m -cp "$T" "com.halftonestudio.$c"
done
python3 tests/check-rc130-scale.py
python3 tests/check-rc136-cell-portrait.py
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{StudioActivity,LayeredCellPortraitEngine,NineLayerCraftPatternEngine,OrganicFlowPortraitEngine}.java
