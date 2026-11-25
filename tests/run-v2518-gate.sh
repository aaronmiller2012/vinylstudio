#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2517-gate.sh
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{MultiColorStippleEngine,CutMaterialProfile,StippleCoverageInspector,StipplePreflight}.java \
  tests/WorkflowAudit2518Test.java
java -cp "$TMP" WorkflowAudit2518Test
python3 tests/check-v2518-workflow.py
printf 'PASS v2.51.8 workflows, cut diagnostics and inherited geometry checks\n'
