#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC104: default cut width + Color dialog regression =====\n'
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/CutWidthDefaultsRC104Test.java
java -Xmx512m -cp "$TMP" com.halftonestudio.CutWidthDefaultsRC104Test
python3 tests/check-v25470-ui-defaults.py
python3 tests/check-v25441-visible-controls.py
printf '\n===== Inherited RC103 rendering and cutter-safety tests =====\n'
bash tests/run-v25469-continuity-gate.sh
printf '\nPASS RC104 default cut width, color UI and inherited rendering suite\n'
