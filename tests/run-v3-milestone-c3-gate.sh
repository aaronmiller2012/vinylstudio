#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"; trap 'rm -rf -- "$TEMP"' EXIT
javac -J-Xmx650m -sourcepath app/src/main/java:tests -d "$TEMP" \
  tests/V3MilestoneC3Test.java app/src/main/java/com/halftonestudio/BoundedBinaryMaskPreview.java
java -Xmx512m -ea -cp "$TEMP" com.halftonestudio.V3MilestoneC3Test
python3 tests/check-v3-qualification-gates.py
python3 tests/check-v3-c3-wiring.py
