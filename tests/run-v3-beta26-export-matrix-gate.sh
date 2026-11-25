#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
python3 tests/check-v3-beta26-export-flow.py
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" \
   tests/V3AllStyleExportMatrixTest.java tests/V3ExportProfilesTest.java
java -ea -Xmx1100m -cp "$T" com.halftonestudio.V3AllStyleExportMatrixTest
java -ea -Xmx1100m -cp "$T" com.halftonestudio.V3ExportProfilesTest
