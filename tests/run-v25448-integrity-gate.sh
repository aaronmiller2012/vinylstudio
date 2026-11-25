#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== RC82 INTEGRITY REGRESSIONS ====='
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" tests/RC82IntegrityTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.RC82IntegrityTest
python3 tests/check-v25448-integrity.py
echo '===== INHERITED RC81 PALETTE / CUT REGRESSIONS ====='
bash tests/run-v25447-palette-harmony-gate.sh
