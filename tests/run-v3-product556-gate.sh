#!/usr/bin/env bash
# Build 556: one all-layers SVG per export so Design Space needs a single upload.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
python3 tests/check-gate-manifest.py
python3 tests/check-v3-product556.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests -d "$T" tests/V3CombinedLayerSvgTest.java tests/V3ExportProfilesTest.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3CombinedLayerSvgTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3ExportProfilesTest
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{CombinedLayerSvg,CutSheetZipWriter,StudioProductFlows,OutputDeviceProfile}.java
printf 'PASS 556 product gate\n'
