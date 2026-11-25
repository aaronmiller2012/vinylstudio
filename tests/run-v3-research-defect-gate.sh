#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{RegistrationMarks,RegistrationCutGeometry,StyleCutGeometryPreflight,ProjectEncodedOriginal,DxfR13Skeleton,OutputDeviceProfile,StudioRenderController,FastGeom}.java \
  tests/V3ResearchDefectsTest.java tests/V3PhotoKillBetweenTest.java
java -ea -Xmx600m -cp "$TMP" com.halftonestudio.V3ResearchDefectsTest
java -ea -Xmx200m -cp "$TMP" com.halftonestudio.V3PhotoKillBetweenTest
