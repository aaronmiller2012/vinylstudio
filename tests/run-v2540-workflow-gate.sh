#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v2540-workflow.py
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/CutSvgCompatibility.java tests/CutSvgCompatibilityV2540Test.java
java -cp "$TMP" com.halftonestudio.CutSvgCompatibilityV2540Test
bash tests/run-v2535-ui-gate.sh
