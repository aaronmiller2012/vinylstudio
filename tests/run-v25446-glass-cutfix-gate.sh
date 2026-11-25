#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC80 GLASS PHYSICAL GEOMETRY GATE =====\n'
python3 tests/check-v25446-glass-cutfix.py
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" tests/StainedGlassCutFixRC80Test.java
java -Xmx768m -cp "$T" com.halftonestudio.StainedGlassCutFixRC80Test
printf '\n===== INHERITED RC79 / RC78 / RC70+ REGRESSION GATES =====\n'
bash tests/run-v25445-glass-portrait-gate.sh
printf '\nPASS RC80 GLASS CUT SAFETY GATE\n'
