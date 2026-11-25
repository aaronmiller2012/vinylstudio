#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/V3RasterMotifPolishTest.java tests/ConfettiWeaveRC158Test.java tests/CamoDetailRC167Test.java tests/FishnetCrochetRC168Test.java tests/V3OrganicUniformLayerSmoothingTest.java
for test in V3RasterMotifPolishTest ConfettiWeaveRC158Test CamoDetailRC167Test FishnetCrochetRC168Test V3OrganicUniformLayerSmoothingTest; do
  java -ea -Xmx950m -cp "$T" "com.halftonestudio.$test"
done
python3 tests/check-v3-release-consistency.py
