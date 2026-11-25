#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
python3 tests/check-v3-prepared-export-flow.py
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$T" tests/V3PreSaveCutExportTest.java
java -Xmx800m -ea -cp "$T" com.halftonestudio.V3PreSaveCutExportTest
