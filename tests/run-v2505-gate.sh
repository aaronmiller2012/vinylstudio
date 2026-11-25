#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== FROZEN VISUAL COMPATIBILITY =====\n'
bash tests/run-v2503-known-good-parity.sh
printf '\n===== CMYK DEFAULTS AND BOUNDED MICRO-GAPS =====\n'
bash tests/run-v2504-cmyk-continuity.sh
printf '\n===== TWO-PASS PORTRAIT RECOVERY =====\n'
bash tests/run-v2505-portrait-recovery.sh
printf '\n===== ALL TWELVE CUT GEOMETRIES =====\n'
bash tests/run-v2503-cut-geometry.sh
printf '\n===== LAYER, SNIP, UI AND PIPELINE =====\n'
bash tests/run-layer-cut-counts.sh
bash tests/run-universal-snip.sh
python3 tests/check-v2496-render-pipeline.py
python3 tests/check-v2504-ui-wiring.py
python3 tests/check-v2505-ui-wiring.py
printf '\nPASS v2.50.5 mandatory source gate\n'
