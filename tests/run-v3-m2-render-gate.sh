#!/usr/bin/env bash
# M2 rendering correctness: mounting surface is display-only (D5), self-intersection checks
# fail closed (D11), and further M2 render contracts.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
python3 tests/check-gate-manifest.py
python3 tests/check-m2-render-inputs.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
  tests/V3MountingSurfaceGeometryTest.java tests/V3IntersectionCheckTest.java tests/V3AllStyleExportMatrixTest.java \
  tests/V3PreviewExportParityTest.java tests/V3StatusFeedbackTest.java \
  2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3StatusFeedbackTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3IntersectionCheckTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3MountingSurfaceGeometryTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3PreviewExportParityTest
printf 'PASS M2 render gate\n'
