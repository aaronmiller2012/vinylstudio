#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"; trap 'rm -rf -- "$TEMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TEMP" tests/V3MilestoneABWrapC1Test.java
java -Xmx850m -ea -cp "$TEMP" com.halftonestudio.V3MilestoneABWrapC1Test
python3 tests/check-v3-milestone-ab-wrap-c1-wiring.py
