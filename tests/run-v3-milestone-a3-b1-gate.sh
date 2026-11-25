#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{ExportColorLabels,AssemblyGuidePlan,AssemblyMaterialPlan,PhysicalSheetParity}.java \
  tests/V3MilestoneA3B1HostTest.java
java -Xmx256m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA3B1HostTest
javac -J-Xmx700m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/RasterRegionClipper.java \
  tests/V3MilestoneA3FractionalSnipTest.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3MilestoneA3FractionalSnipTest
# Check the Android PDF integration as well as its Java syntax without falsely
# claiming an Android SDK/device build has completed.
python3 tests/check-v3-milestone-a3-b1-wiring.py
