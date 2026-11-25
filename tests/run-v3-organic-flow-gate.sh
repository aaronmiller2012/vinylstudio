#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx1000m -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" \
   tests/android-host-stubs/android/graphics/{Bitmap,Canvas,Paint,Path}.java \
   tests/V3OrganicFlowStylesTest.java tests/V3OrganicFlowCutAudit.java \
   tests/V3AllStyleExportMatrixTest.java
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicFlowStylesTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3OrganicFlowCutAudit 101.6
